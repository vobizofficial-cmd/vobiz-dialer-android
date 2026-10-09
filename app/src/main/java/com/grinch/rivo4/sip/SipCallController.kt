package com.grinch.rivo4.sip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.grinch.rivo4.R
import com.grinch.rivo4.auth.CredentialStore
import com.grinch.rivo4.modal.db.VobizCallHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.Reason

/**
 * Bridges LibLinphone call events to the app UI: holds the current call state,
 * shows incoming/ongoing call notifications and launches SipCallActivity.
 */
object SipCallController {

    private const val TAG = "VobizSip"
    private const val CHANNEL_INCOMING = "vobiz_sip_incoming_call_v2"
    private const val CHANNEL_INCOMING_LEGACY = "vobiz_sip_incoming_call"
    private const val CHANNEL_ONGOING = "vobiz_sip_active_call"
    private const val NOTIF_INCOMING = 4410
    private const val NOTIF_ONGOING = 4411
    private const val KEY_INCOMING_CHANNEL_MIGRATED = "vobiz_incoming_channel_migrated"

    data class SipCallUiState(
        val active: Boolean = false,
        val state: Call.State = Call.State.Idle,
        val number: String = "",
        val displayName: String = "",
        val isIncoming: Boolean = false,
        val startedAt: Long = 0L,
        val connectedAt: Long = 0L,
        val muted: Boolean = false,
        val speakerOn: Boolean = false,
        val held: Boolean = false,
        val bluetoothOn: Boolean = false,
        val recording: Boolean = false,
        val declinedByUser: Boolean = false,
        val endedReason: String = "",
    )

    private val _uiState = MutableStateFlow(SipCallUiState())
    val uiState: StateFlow<SipCallUiState> = _uiState.asStateFlow()

    @Volatile
    private var currentCall: Call? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lookupExecutor = Executors.newSingleThreadExecutor()
    @Volatile
    private var lastRecordingPath: String = ""
    @Volatile
    private var recordPending = false
    @Volatile
    private var ringerInstance: IncomingCallRinger? = null

    @Volatile
    private var pendingConference: String? = null
    @Volatile
    private var pendingCaller: String? = null

    fun setPendingConference(conference: String, caller: String) {
        pendingConference = conference
        pendingCaller = caller
        Log.i(TAG, "Pending conference set: $conference from $caller")
    }

    fun clearPendingConference() {
        pendingConference = null
        pendingCaller = null
    }

    private fun ringer(context: Context): IncomingCallRinger =
        ringerInstance ?: IncomingCallRinger(context.applicationContext).also { ringerInstance = it }

    /** True when [call] is not in a terminal state (End/Released/Error). */
    private fun isLive(call: Call?): Boolean = call != null &&
        call.state != Call.State.End &&
        call.state != Call.State.Released &&
        call.state != Call.State.Error

    fun handleCallEvent(context: Context, call: Call, state: Call.State) {
        val app = context.applicationContext
        val number = remoteNumber(call)
        Log.i(TAG, "Call event: $state number='$number' dir=${call.dir}")

        when (state) {
            Call.State.IncomingReceived -> {
                val existing = currentCall
                if (existing != null && existing != call && isLive(existing)) {
                    // Clean up any stale ringing call before accepting new one
                    if (existing.state == Call.State.IncomingReceived || _uiState.value.isIncoming) {
                        Log.i(TAG, "Replacing stale incoming call with new one")
                        runCatching { existing.decline(Reason.Busy) }
                            .onFailure { Log.w(TAG, "Stale decline failed", it) }
                    } else if (_uiState.value.active) {
                        Log.w(TAG, "Incoming call '$number' while call active - declining busy")
                        runCatching { call.decline(Reason.Busy) }
                            .onFailure { Log.w(TAG, "Busy decline failed", it) }
                        return
                    }
                }
                currentCall = call
                lastRecordingPath = ""
                recordPending = false
                _uiState.value = SipCallUiState(
                    active = true,
                    state = state,
                    number = number,
                    isIncoming = true,
                    startedAt = System.currentTimeMillis(),
                )
                ringer(app).start()
                resolveContactName(app, number) { name ->
                    _uiState.update { it.copy(displayName = name) }
                    showIncomingNotification(app, number, name)
                }
            }

            Call.State.OutgoingInit,
            Call.State.OutgoingProgress,
            Call.State.OutgoingRinging,
            Call.State.OutgoingEarlyMedia -> {
                val prev = _uiState.value
                currentCall = call
                _uiState.value = if (prev.active && prev.isIncoming) {
                    prev.copy(state = state)
                } else {
                    SipCallUiState(
                        active = true,
                        state = state,
                        number = if (number.isEmpty()) prev.number else number,
                        isIncoming = false,
                        startedAt = System.currentTimeMillis(),
                        muted = prev.muted,
                        speakerOn = prev.speakerOn,
                    )
                }
                if (state == Call.State.OutgoingInit && prev.startedAt == 0L && !prev.active) {
                    _uiState.update { it.copy(startedAt = System.currentTimeMillis()) }
                }
                if (state == Call.State.OutgoingInit) {
                    mainHandler.post { launchCallUi(app) }
                    resolveContactName(app, _uiState.value.number) { name ->
                        _uiState.update { it.copy(displayName = name) }
                    }
                }
            }

            Call.State.Connected, Call.State.StreamsRunning -> {
                val prev = _uiState.value
                _uiState.value = prev.copy(
                    active = true,
                    state = state,
                    number = if (number.isEmpty()) prev.number else number,
                    held = if (state == Call.State.StreamsRunning) false else prev.held,
                    connectedAt = if (prev.connectedAt == 0L && state == Call.State.StreamsRunning) {
                        System.currentTimeMillis()
                    } else prev.connectedAt,
                )
                val label = _uiState.value.displayName.ifEmpty { _uiState.value.number }
                mainHandler.post {
                    ringer(app).stop()
                    cancelIncomingNotification(app)
                    showOngoingNotification(app, label)
                }
                if (state == Call.State.StreamsRunning && recordPending && lastRecordingPath.isNotEmpty()) {
                    recordPending = false
                    runCatching { call.startRecording() }
                        .onFailure { Log.w(TAG, "Pending recording start failed", it) }
                    val now = call.isRecording
                    _uiState.update { it.copy(recording = now) }
                    Log.i(TAG, "Pending recording started (isRecording=$now path=$lastRecordingPath)")
                }
            }

            Call.State.Pausing,
            Call.State.Paused,
            Call.State.Resuming,
            Call.State.PausedByRemote,
            Call.State.Updating,
            Call.State.UpdatedByRemote -> {
                val held = when (state) {
                    Call.State.Pausing, Call.State.Paused, Call.State.PausedByRemote -> true
                    Call.State.Resuming -> false
                    else -> _uiState.value.held
                }
                _uiState.update { it.copy(state = state, held = held) }
            }

            Call.State.Error -> {
                val reason = call.reason?.name ?: "Error"
                _uiState.update { it.copy(state = state, endedReason = reason) }
                mainHandler.post {
                    ringer(app).stop()
                    cancelIncomingNotification(app)
                    cancelOngoingNotification(app)
                }
            }

            Call.State.End, Call.State.Released -> {
                recordPending = false
                if (state == Call.State.Released) {
                    val reason = call.reason?.name
                        ?: _uiState.value.endedReason.ifEmpty { "Ended" }
                    _uiState.update { it.copy(active = false, state = state, endedReason = reason) }
                    recordCallLog(_uiState.value, call)
                    if (currentCall == call) currentCall = null
                } else {
                    _uiState.update { it.copy(state = state) }
                }
                mainHandler.post {
                    ringer(app).stop()
                    cancelIncomingNotification(app)
                    cancelOngoingNotification(app)
                }
            }

            else -> _uiState.update { it.copy(state = state) }
        }
    }

    /** Returns true when the call was placed over SIP; false = fall back to system Telecom. */
    fun placeCall(context: Context, rawNumber: String): Boolean {
        val core = LinphoneService.core ?: return false
        if (LinphoneService.registrationState.value !is VobizRegistrationState.Registered) return false

        val existing = currentCall
        if (existing != null && _uiState.value.active && isLive(existing)) {
            Log.w(TAG, "placeCall refused - another call is active (state=${existing.state})")
            return false
        }

        val number = rawNumber.trim().replace(Regex("[\\s\\-\\(\\)]"), "")
        if (number.isEmpty() || number.startsWith("voicemail:", ignoreCase = true) || number.contains('#')) {
            return false
        }
        // Auth info registered in LinphoneService is keyed by this From user, so the
        // identity (or selected caller ID) must always be explicit on the INVITE.
        val plan = CredentialStore.getSip()?.let {
            OutboundAuth.resolve(CredentialStore.getEffectiveCallerId(), it, CredentialStore.getTrunkConfig())
        }
        val outboundDomain = plan?.outboundDomain ?: CredentialStore.DEFAULT_DOMAIN
        val fromUser = plan?.fromUser
        val effectiveCallerId = CredentialStore.getEffectiveCallerId()?.trim()?.takeIf { it.isNotEmpty() }
        val target = when {
            number.startsWith("sip:", ignoreCase = true) || number.startsWith("sips:", ignoreCase = true) -> number
            else -> "sip:$number@$outboundDomain"
        }

        return try {
            lastRecordingPath = ""
            recordPending = false
            val recordPath = newRecordFilePath(context)
            val params = runCatching { core.createCallParams(null) }.getOrNull()
            var armed = false
            if (params != null) {
                runCatching {
                    params.setVideoEnabled(false)
                    if (recordPath.isNotEmpty()) {
                        params.setRecordFile(recordPath)
                        armed = true
                    }
                    if (fromUser != null) {
                        val fromHeaderVal = "<sip:$fromUser@$outboundDomain>"
                        runCatching {
                            params.setFromHeader(fromHeaderVal)
                            Log.i(TAG, "Applied setFromHeader: $fromHeaderVal")
                        }.onFailure {
                            Log.w(TAG, "setFromHeader failed, applying P-Preferred-Identity fallback", it)
                        }
                    }
                    if (effectiveCallerId != null) {
                        runCatching {
                            params.addCustomHeader(
                                "P-Preferred-Identity",
                                "<sip:$effectiveCallerId@$outboundDomain>"
                            )
                            Log.i(TAG, "Applied P-Preferred-Identity header for $effectiveCallerId")
                        }
                    }
                }.onFailure { Log.w(TAG, "Failed to arm call params / caller ID", it) }
            }
            val call = if (params != null) core.inviteWithParams(target, params) else core.invite(target)
            if (call == null) {
                Log.w(TAG, "invite returned null for $target")
                return false
            }
            lastRecordingPath = if (armed) recordPath else ""
            currentCall = call
            _uiState.value = SipCallUiState(
                active = true,
                state = Call.State.OutgoingInit,
                number = number,
                isIncoming = false,
                startedAt = System.currentTimeMillis(),
            )
            mainHandler.post { launchCallUi(context.applicationContext) }
            Log.i(TAG, "SIP invite placed: $target from='${fromUser ?: "default"}' domain=$outboundDomain recordFile='$lastRecordingPath'")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "SIP invite failed for $target", t)
            false
        }
    }

    /** Called from FCM receiver when an incoming call FCM arrives. */
    fun showIncomingCallFromFcm(context: Context, caller: String, conference: String) {
        val app = context.applicationContext
        _uiState.value = SipCallUiState(
            active = true,
            state = Call.State.IncomingReceived,
            number = caller,
            isIncoming = true,
            startedAt = System.currentTimeMillis(),
        )
        ringer(app).start()
        showIncomingNotification(app, caller, "")
        launchCallUi(app)
        Log.i(TAG, "FCM incoming call shown: $caller conference=$conference")
    }

    fun accept(context: Context) {
        val conf = pendingConference
        if (conf != null) {
            Log.i(TAG, "Joining conference: $conf")
            val domain = CredentialStore.getSip()?.domain ?: "registrar.vobiz.ai"
            val target = "sip:$conf@$domain"
            val core = LinphoneService.core ?: return
            val call = core.invite(target)
            if (call != null) {
                currentCall = call
                pendingConference = null
                pendingCaller = null
                _uiState.value = _uiState.value.copy(active = true)
            }
        } else {
            val call = currentCall ?: run {
                Log.w(TAG, "accept() ignored - no active call")
                return
            }
            Log.i(TAG, "Accepting incoming call")
            val recordPath = newRecordFilePath(context)
            val params = runCatching { LinphoneService.core?.createCallParams(call) }.getOrNull()
            var armed = false
            if (params != null) {
                runCatching {
                    if (recordPath.isNotEmpty()) {
                        params.setRecordFile(recordPath)
                        armed = true
                    }
                }.onFailure { Log.w(TAG, "Failed to arm record path", it) }
            } else {
                Log.w(TAG, "createCallParams(call) returned null - accepting with default params")
            }
            lastRecordingPath = if (armed) recordPath else ""
            val ret = if (params != null) call.acceptWithParams(params) else call.accept()
            Log.i(TAG, "call.accept returned $ret recordFile='$lastRecordingPath'")
            mainHandler.post { launchCallUi(context.applicationContext) }
        }
    }

    fun decline() {
        val call = currentCall ?: return
        Log.i(TAG, "Declining incoming call")
        _uiState.update { it.copy(declinedByUser = true) }
        call.decline(Reason.Declined)
    }

    fun endCall() {
        val call = currentCall ?: return
        Log.i(TAG, "Terminating call")
        call.terminate()
    }

    fun sendDtmf(c: Char): Boolean {
        val call = currentCall ?: return false
        val ret = call.sendDtmf(c)
        Log.i(TAG, "DTMF '$c' sendDtmf returned $ret")
        return ret == 0
    }

    private fun recordCallLog(s: SipCallUiState, call: Call) {
        if (s.number.isEmpty()) return
        val now = System.currentTimeMillis()
        val connectedAt = s.connectedAt
        val duration = if (connectedAt > 0L) ((now - connectedAt) / 1000).coerceAtLeast(0L) else 0L
        val date = if (s.startedAt > 0L) s.startedAt else now - duration * 1000
        recordVobizHistory(s, call, date, now, duration)
    }

    private fun recordVobizHistory(
        s: SipCallUiState,
        call: Call,
        date: Long,
        endTime: Long,
        duration: Long,
    ) {
        runCatching {
            val callId = call.callLog?.callId ?: ""
            val logStatus = call.callLog?.status
            val status = when {
                s.connectedAt > 0L -> "Completed"
                s.declinedByUser -> "Rejected"
                s.state == Call.State.Error -> "Failed"
                logStatus == Call.Status.Missed -> "Missed"
                logStatus == Call.Status.Declined -> if (s.isIncoming) "Rejected" else "Busy"
                logStatus == Call.Status.AcceptedElsewhere -> "Answered elsewhere"
                logStatus == Call.Status.DeclinedElsewhere -> "Declined elsewhere"
                logStatus == Call.Status.Aborted || logStatus == Call.Status.EarlyAborted -> "Cancelled"
                else -> if (s.isIncoming) "Missed" else "No answer"
            }
            val recPath = if (lastRecordingPath.isNotEmpty() && java.io.File(lastRecordingPath).exists()) {
                lastRecordingPath
            } else ""
            VobizCallHistory.record(
                callId = callId,
                direction = if (s.isIncoming) "incoming" else "outgoing",
                number = s.number,
                displayName = s.displayName,
                startTime = date,
                endTime = endTime,
                durationSeconds = duration,
                status = status,
                failureReason = if (status == "Failed") s.endedReason else "",
                did = CredentialStore.getSelectedDid() ?: "",
                recordingPath = recPath,
            )
            Log.i(TAG, "Vobiz history recorded: status=$status callId=$callId recordingPath=${recPath.ifEmpty { "<none>" }}")
        }.onFailure { t ->
            Log.w(TAG, "Vobiz history insert failed", t)
        }
    }

    fun toggleMute(): Boolean {
        val call = currentCall ?: return false
        val newMuted = !call.microphoneMuted
        call.microphoneMuted = newMuted
        Log.i(TAG, "Mic muted=$newMuted")
        _uiState.update { it.copy(muted = newMuted) }
        return newMuted
    }

    fun toggleSpeaker(): Boolean {
        val call = currentCall ?: return false
        val enable = !_uiState.value.speakerOn
        val wanted = if (enable) AudioDevice.Type.Speaker else AudioDevice.Type.Earpiece
        val target = try {
            call.core.audioDevices.firstOrNull { it.type == wanted }
        } catch (t: Throwable) {
            Log.w(TAG, "Audio device lookup failed", t)
            null
        }
        if (target == null) {
            Log.w(TAG, "Audio device of type $wanted not found")
            return _uiState.value.speakerOn
        }
        call.outputAudioDevice = target
        Log.i(TAG, "Audio output -> ${target.type} (${target.deviceName})")
        _uiState.update { it.copy(speakerOn = enable, bluetoothOn = false) }
        return enable
    }

    fun toggleHold(): Boolean {
        val call = currentCall ?: return false
        val isHeld = call.state == Call.State.Paused ||
            call.state == Call.State.PausedByRemote ||
            call.state == Call.State.Pausing
        val ret = if (isHeld) {
            Log.i(TAG, "Resuming call")
            call.resume()
        } else {
            Log.i(TAG, "Holding call")
            call.pause()
        }
        val nowHeld = !isHeld
        Log.i(TAG, "hold toggle -> held=$nowHeld ret=$ret")
        _uiState.update { it.copy(held = nowHeld) }
        return nowHeld
    }

    fun toggleBluetooth(): Boolean {
        val call = currentCall ?: return false
        val outType = call.outputAudioDevice?.type
        val btOn = outType == AudioDevice.Type.Bluetooth || outType == AudioDevice.Type.BluetoothA2DP
        val target = if (btOn) {
            val wanted = if (_uiState.value.speakerOn) AudioDevice.Type.Speaker else AudioDevice.Type.Earpiece
            call.core.audioDevices.firstOrNull { it.type == wanted }
        } else {
            call.core.audioDevices.firstOrNull {
                it.type == AudioDevice.Type.Bluetooth || it.type == AudioDevice.Type.BluetoothA2DP
            }
        }
        if (target == null) {
            Log.w(TAG, "Bluetooth audio device not available")
            return btOn
        }
        call.outputAudioDevice = target
        val nowBt = !btOn
        Log.i(TAG, "Audio output -> ${target.type} (${target.deviceName}) bluetoothOn=$nowBt")
        _uiState.update {
            it.copy(bluetoothOn = nowBt, speakerOn = if (nowBt) false else it.speakerOn)
        }
        return nowBt
    }

    fun transferCall(targetNumber: String): Boolean {
        val call = currentCall ?: return false
        val number = targetNumber.trim().replace(Regex("[\\s\\-\\(\\)]"), "")
        if (number.isEmpty()) return false
        val domain = CredentialStore.getSip()?.domain ?: CredentialStore.DEFAULT_DOMAIN
        val target = when {
            number.startsWith("sip:", ignoreCase = true) || number.startsWith("sips:", ignoreCase = true) -> number
            else -> "sip:$number@$domain"
        }
        val ret = call.transfer(target)
        Log.i(TAG, "Blind transfer to $target -> ret=$ret")
        return ret == 0
    }

    fun toggleRecording(): Boolean {
        val call = currentCall ?: return false
        if (call.isRecording) {
            call.stopRecording()
            Log.i(TAG, "Recording stopped (path=$lastRecordingPath)")
        } else if (recordPending) {
            recordPending = false
            Log.i(TAG, "Pending recording cancelled")
        } else if (lastRecordingPath.isEmpty()) {
            Log.w(TAG, "Recording start ignored - no record path armed")
        } else if (_uiState.value.state == Call.State.StreamsRunning) {
            call.startRecording()
            Log.i(TAG, "Recording started (isRecording=${call.isRecording} path=$lastRecordingPath)")
        } else {
            recordPending = true
            Log.i(TAG, "Recording queued until media streams run")
        }
        val now = call.isRecording || recordPending
        _uiState.update { it.copy(recording = now) }
        return now
    }

    private fun newRecordFilePath(context: Context): String = try {
        val dir = java.io.File(
            context.getExternalFilesDir(null) ?: context.filesDir,
            "recordings",
        ).apply { mkdirs() }
        java.io.File(dir, "vobiz_call_${System.currentTimeMillis()}.mkv").absolutePath
    } catch (t: Throwable) {
        Log.w(TAG, "Failed to create record path", t)
        ""
    }

    fun resetState() {
        if (_uiState.value.active || currentCall != null) return
        _uiState.value = SipCallUiState()
    }

    fun cancelAllNotifications(context: Context) {
        val app = context.applicationContext
        mainHandler.post {
            cancelIncomingNotification(app)
            cancelOngoingNotification(app)
        }
    }

    private fun remoteNumber(call: Call): String = try {
        // Incoming: the R-URI user is our DID; the caller's number is carried in
        // the From display name (e.g. "919123151351" <sip:+91xxxxxxxxxx@...>).
        // Prefer the display name, fall back to the URI username (outgoing).
        val addr = call.remoteAddress
        val display = addr?.displayName?.trim().orEmpty()
        if (display.isNotEmpty()) display
        else addr?.username
            ?: call.remoteAddressAsString
            ?: ""
    } catch (t: Throwable) {
        ""
    }

    /** Resolves the contact display name for [number]; always invokes [onResult] on the main thread ("" = not found). */
    private fun resolveContactName(context: Context, number: String, onResult: (String) -> Unit) {
        if (number.isEmpty()) {
            mainHandler.post { onResult("") }
            return
        }
        val app = context.applicationContext
        lookupExecutor.execute {
            val name = runCatching {
                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(number),
                )
                app.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull()
            mainHandler.post { onResult(name.orEmpty().trim()) }
        }
    }

    // ------------------------------------------------------------- notifications

    private fun callUiIntent(context: Context, action: String? = null): Intent =
        Intent(context, SipCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            action?.let { putExtra(SipCallActivity.EXTRA_ACTION, it) }
        }

    private fun launchCallUi(context: Context) {
        try {
            context.startActivity(callUiIntent(context))
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to launch SipCallActivity", t)
        }
    }

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val prefs = context.getSharedPreferences("vobiz_channels", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_INCOMING_CHANNEL_MIGRATED, false)) {
            // Best-effort: some OEMs silently no-op deleteNotificationChannel, which is
            // why the incoming channel uses a new id (existing channels never update).
            runCatching { nm.deleteNotificationChannel(CHANNEL_INCOMING_LEGACY) }
            prefs.edit().putBoolean(KEY_INCOMING_CHANNEL_MIGRATED, true).apply()
        }
        val incoming = NotificationChannel(
            CHANNEL_INCOMING,
            "Incoming call",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incoming Vobiz SIP call alerts"
            // Ringtone and continuous vibration are driven by IncomingCallRinger
            // (loudspeaker loop); the notification is visual only (banner + full-screen).
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val ongoing = NotificationChannel(
            CHANNEL_ONGOING,
            "Ongoing call",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active Vobiz SIP call"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(incoming)
        nm.createNotificationChannel(ongoing)
    }

    private fun showIncomingNotification(context: Context, number: String, name: String = "") {
        ensureChannels(context)
        val label = when {
            name.isNotEmpty() -> name
            number.isNotEmpty() -> number
            else -> "Unknown caller"
        }
        val person = Person.Builder().setName(label).build()
        val answerPi = PendingIntent.getActivity(
            context, 1,
            callUiIntent(context, SipCallActivity.ACTION_ACCEPT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val declinePi = PendingIntent.getActivity(
            context, 2,
            callUiIntent(context, SipCallActivity.ACTION_DECLINE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val fullScreenPi = PendingIntent.getActivity(
            context, 3,
            callUiIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_call_ongoing)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentTitle(label)
            .setContentText("Incoming Vobiz call")
            .setContentIntent(fullScreenPi)
            .setFullScreenIntent(fullScreenPi, true)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, declinePi, answerPi))
            .build()
        try {
            context.getSystemService(NotificationManager::class.java).notify(NOTIF_INCOMING, notification)
            Log.i(TAG, "Incoming call notification shown for '$label'")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to post incoming notification", t)
        }
    }

    private fun showOngoingNotification(context: Context, number: String) {
        ensureChannels(context)
        val label = number.ifEmpty { "Ongoing call" }
        val person = Person.Builder().setName(label).build()
        val hangupPi = PendingIntent.getActivity(
            context, 4,
            callUiIntent(context, SipCallActivity.ACTION_END),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentPi = PendingIntent.getActivity(
            context, 5,
            callUiIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_call_ongoing)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(contentPi)
            .setFullScreenIntent(contentPi, true)
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(person, hangupPi))
            .build()
        try {
            context.getSystemService(NotificationManager::class.java).notify(NOTIF_ONGOING, notification)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to post ongoing notification", t)
        }
    }

    private fun cancelIncomingNotification(context: Context) {
        runCatching {
            context.getSystemService(NotificationManager::class.java).cancel(NOTIF_INCOMING)
        }
    }

    private fun cancelOngoingNotification(context: Context) {
        runCatching {
            context.getSystemService(NotificationManager::class.java).cancel(NOTIF_ONGOING)
        }
    }
}