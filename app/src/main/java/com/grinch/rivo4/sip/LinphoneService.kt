package com.grinch.rivo4.sip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.grinch.rivo4.BuildConfig
import com.grinch.rivo4.R
import com.grinch.rivo4.auth.CredentialStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import org.linphone.core.Call
import org.linphone.core.CallLog
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.AVPFMode
import org.linphone.core.MediaEncryption
import org.linphone.core.NatPolicy
import org.linphone.core.ProxyConfig
import org.linphone.core.RegistrationState as LinphoneRegistrationState

class LinphoneService : Service() {

    private var core: Core? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        appContext = this
try {
                // Global crash handler for native Linphone crashes
                Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                    Log.e(TAG, "UNCAUGHT EXCEPTION in ${thread.name}", throwable)
                }
                
                val factory = Factory.instance()
            factory.setLoggerDomain("VobizDialer")
            if (BuildConfig.DEBUG) {
                @Suppress("DEPRECATION")
                factory.setDebugMode(true, "VobizLP")
                factory.enableLogcatLogs(true)
            }

            val created = factory.createCore(null, null, applicationContext)
            // Ringing is fully app-driven (IncomingCallRinger: loudspeaker loop +
            // continuous vibration). Silence liblinphone's own ringtone so only one
            // ring plays for an incoming call.
            created.disableCallRinging(true)
            
            // Registration/call/network listener: drives _registrationState, which the
            // login screen gates on (Registered|Failed). Must be attached to the core.
            created.addListener(listener)
            // Extra call-log diagnostics (call state logging lives in `listener`).
            created.addListener(object : CoreListenerStub() {
                override fun onCallLogUpdated(core: Core, log: CallLog) {
                    Log.i(TAG, "CALLLOG status=${log.status} dir=${log.dir} " +
                        "from=${log.fromAddress.asStringUriOnly()}")
                }
            })
            
            val startResult = created.start()
            Log.i(TAG, "Core started (result=$startResult, autoIterate=${created.isAutoIterateEnabled})")
            core = created
            sCore = created
            serviceScope.launch {
                configMutex.withLock { applyConfig() }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize Linphone core", t)
            _registrationState.value = VobizRegistrationState.Failed("Failed to initialize SIP engine")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        instance = null
        SipCallController.cancelAllNotifications(this)
        runCatching { WssRelay.stop() }
        core?.let { c ->
            runCatching { c.stop() }.onFailure { Log.w(TAG, "Core.stop() failed", it) }
        }
        core = null
        sCore = null
        _registrationState.value = VobizRegistrationState.Idle
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.setShowBadge(false)
            notificationManager.createNotificationChannel(channel)
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notification = builder
            .setContentTitle(getString(R.string.app_name))
            .setContentText("SIP service running")
            .setSmallIcon(R.drawable.ic_call_ongoing)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private val listener = object : CoreListenerStub() {
        override fun onRegistrationStateChanged(
            core: Core,
            proxyConfig: ProxyConfig,
            state: LinphoneRegistrationState,
            message: String
        ) {
            Log.i(
                TAG,
                "Registration state: $state${if (message.isNotEmpty()) " - $message" else ""}" +
                    " | epoch=${System.currentTimeMillis() / 1000}"
            )
            // In WSS-relay mode Linphone only ever talks to the loopback bridge,
            // whose local 200 OK would always read "Ok". Real registration health
            // is reported by WssRelay itself, so ignore the SDK's view here.
            if (relayMode) return
            // Ignore events from a stale proxy being torn down by a re-login: its
            // Cleared event would otherwise clobber the active account's Registered.
            val defaultProxy = core.defaultProxyConfig
            if (defaultProxy != null && proxyConfig != defaultProxy) return
            _registrationState.value = when (state) {
                LinphoneRegistrationState.Ok -> VobizRegistrationState.Registered
                LinphoneRegistrationState.Progress,
                LinphoneRegistrationState.Refreshing -> VobizRegistrationState.Connecting
                LinphoneRegistrationState.Failed ->
                    VobizRegistrationState.Failed(message.ifBlank { "Registration failed" })
                else -> VobizRegistrationState.Idle
            }
        }

        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String
        ) {
            Log.i(TAG, "Call state: $state${if (message.isNotEmpty()) " - $message" else ""}")
            when (state) {
                Call.State.StreamsRunning -> startMediaStatsLoop(call)
                Call.State.End, Call.State.Released, Call.State.Error -> {
                    logMediaStats(call, "final")
                    stopMediaStatsLoop(call)
                }
                else -> {}
            }
            try {
                SipCallController.handleCallEvent(applicationContext, call, state)
            } catch (t: Throwable) {
                Log.e(TAG, "handleCallEvent failed", t)
            }
        }

        override fun onNetworkReachable(core: Core, reachable: Boolean) {
            Log.i(TAG, "Network reachable: $reachable")
            if (reachable) {
                Log.i(TAG, "Network restored - refreshing registers at epoch=${System.currentTimeMillis() / 1000}")
                if (relayMode) runCatching { WssRelay.onNetworkChanged() }
                runCatching { core.refreshRegisters() }
                    .onFailure { Log.w(TAG, "refreshRegisters failed", it) }
            }
        }
    }

    private val mediaStatsJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()

    /**
     * Logs RTP/RTCP/ICE/DTLS counters every 5s while streams are up. This is the
     * evidence needed to tell "no RTP received" (media path broken -> liblinphone's
     * nortp timeout fires ~30s) apart from a SIP-side hangup.
     */
    private fun startMediaStatsLoop(call: Call) {
        val key = call.callLog?.callId ?: return
        if (mediaStatsJobs.containsKey(key)) return
        val job = serviceScope.launch {
            var tick = 0
            while (isActive) {
                delay(5000)
                tick++
                logMediaStats(call, "t+${tick * 5}s")
                if (call.state == Call.State.Released || call.state == Call.State.End ||
                    call.state == Call.State.Error
                ) break
            }
            mediaStatsJobs.remove(key)
        }
        mediaStatsJobs[key] = job
    }

    private fun stopMediaStatsLoop(call: Call) {
        val key = call.callLog?.callId ?: return
        mediaStatsJobs.remove(key)?.cancel()
    }

    private fun logMediaStats(call: Call, label: String) {
        runCatching {
            val s = call.audioStats
            if (s == null) {
                Log.w(TAG, "MEDIA[$label] stats not available yet (state=${call.state})")
                return@runCatching
            }
            Log.i(
                TAG,
                "MEDIA[$label] dir=${call.dir} state=${call.state} dur=${call.duration}s " +
                    "rtpRecv=${s.rtpPacketRecv} rtpSent=${s.rtpPacketSent} " +
                    "rtcpDown=${"%.1f".format(s.rtcpDownloadBandwidth)}kbps rtcpUp=${"%.1f".format(s.rtcpUploadBandwidth)} " +
                    "ice=${s.iceState} srtp=${s.srtpSource} suite=${s.srtpSuite} " +
                    "rtt=${"%.0f".format(s.roundTripDelay)}ms loss=${"%.1f".format(s.localLossRate)} " +
                    "late=${s.latePacketsCumulativeNumber} enc=${call.currentParams?.mediaEncryption}"
            )
        }.onFailure { Log.w(TAG, "MEDIA[$label] stats failed: $it") }
    }

    companion object {
        private const val TAG = "VobizSip"
        private const val CHANNEL_ID = "vobiz_sip_service"
        private const val NOTIFICATION_ID = 4400
        private const val INCOMING_RING_TIMEOUT_S = 120

        /** Registration expiry in seconds: refresh ~every 2 min; Vobiz wants <= 300s. */
        private const val REGISTER_EXPIRES_S = 120

        /** Loopback address of the in-app WSS bridge's UDP leg. */
        private const val RELAY_LOOPBACK_ADDR = "127.0.0.1"

        @Volatile
        var sCore: Core? = null
            private set

        /** True while Linphone is pointed at the loopback WSS bridge instead of the registrar directly. */
        @Volatile
        var relayMode: Boolean = false
            private set

        @Volatile
        var instance: LinphoneService? = null
            private set

        @Volatile
        private var appContext: Context? = null
            private set

        val core: Core?
            get() = sCore

        private val _registrationState =
            MutableStateFlow<VobizRegistrationState>(VobizRegistrationState.Idle)
        val registrationState: StateFlow<VobizRegistrationState> = _registrationState.asStateFlow()

        /** Linphone's Core is not thread-safe for reconfiguration; all config work takes this mutex. */
        private val configMutex = Mutex()
        private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun start(context: Context) {
            val intent = Intent(context, LinphoneService::class.java)
            runCatching { context.startForegroundService(intent) }
                .onFailure { Log.e(TAG, "Unable to start LinphoneService", it) }
        }

        /**
         * Tears down any existing proxy/auth configuration and registers with the
         * credentials currently in [CredentialStore]. Safe to call at any time:
         * - If the engine is not up yet, [onCreate] applies the config right after start.
         * - If it is up, existing proxies are removed first so a new login or logout
         *   always results in exactly one (or zero) registrations.
         */
        fun reconfigureAndRegister() {
            if (CredentialStore.hasSipCredentials()) {
                _registrationState.value = VobizRegistrationState.Connecting
            }
            serviceScope.launch {
                configMutex.withLock {
                    val active = sCore
                    if (active == null) {
                        Log.i(TAG, "reconfigure requested but core not started yet - will apply on start")
                        return@withLock
                    }
                    teardown(active)
                    applyConfig()
                }
            }
        }

        /** Health updates from [WssRelay]; only applied while relay mode is active. */
        internal fun onRelayState(state: VobizRegistrationState) {
            if (relayMode) _registrationState.value = state
        }

        /** Removes all proxies and auth infos (sends de-registrations as a side effect). */
        private fun teardown(core: Core) {
            runCatching {
                core.proxyConfigList.forEach { core.removeProxyConfig(it) }
                core.authInfoList.forEach { core.removeAuthInfo(it) }
            }.onFailure { Log.w(TAG, "teardown failed", it) }
        }

        private fun applyConfig() {
            val core = sCore ?: return
                val creds = CredentialStore.getSip()
                if (creds == null) {
                    Log.w(TAG, "SIP credentials not configured - skipping registration")
                    relayMode = false
                    runCatching { WssRelay.stop() }
                    _registrationState.value =
                        VobizRegistrationState.Failed("No SIP credentials configured")
                    return
                }
            try {
                // Debug: Core hash for cross-verification
                Log.i(TAG, "applyConfig on core ${System.identityHashCode(core)}")

                // DTLS needs a certificate directory (self-signed cert for DTLS-SRTP)
                val appCtx = appContext ?: return
                val certDir = File(appCtx.filesDir, "linphone-certs").apply { mkdirs() }
                core.userCertificatesPath = certDir.absolutePath
                Log.i(TAG, "Cert dir: ${certDir.absolutePath} writable=${certDir.canWrite()}")
                
                // Ensure DTLS certificate exists (generate if missing)
                val certFile = File(certDir, "dtls.pem")
                val keyFile = File(certDir, "dtls.key")
                if (!certFile.exists() || !keyFile.exists()) {
                    Log.i(TAG, "Generating DTLS certificate properly...")
                    runCatching {
                        // Proper way: enable DTLS mandatory, let Linphone generate, then verify
                        core.mediaEncryption = MediaEncryption.DTLS
                        core.setMediaEncryptionMandatory(true)
                        // Wait for cert generation (Linphone does this async, needs time)
                        Thread.sleep(2000)
                        // Verify files exist
                        if (certFile.exists() && keyFile.exists()) {
                            Log.i(TAG, "DTLS certificate generated successfully")
                        } else {
                            Log.w(TAG, "DTLS certificate generation may have failed")
                        }
                        core.mediaEncryption = MediaEncryption.None
                        core.setMediaEncryptionMandatory(false)
                    }.onFailure { Log.e(TAG, "DTLS cert generation failed", it) }
                }
                
                Log.i(TAG, "Cert dir: ${certDir.absolutePath} writable=${certDir.canWrite()} cert=${certFile.exists()} key=${keyFile.exists()}")

                // ICE + STUN (device is behind NAT)
                core.natPolicy = core.createNatPolicy().apply {
                    isIceEnabled = true
                    isStunEnabled = true
                    stunServer = "stun.l.google.com:19302"
                }
                Log.i(TAG, "NAT: ice=true stun=true")

                // Enable WebRTC-compatible codecs
                val wanted = setOf("opus", "pcmu", "pcma")
                core.audioPayloadTypes
                    .filter { it.mimeType.lowercase() in wanted }
                    .forEach { it.enable(true); Log.i(TAG, "Enabled ${it.mimeType}/${it.clockRate}") }

                // The Vobiz/FreeSWITCH media leg offers DTLS-SRTP (SAVPF + a=fingerprint +
                // a=setup:actpass, with no a=crypto). Linphone's default media encryption is
                // None, which cannot match a secure SAVP stream, so liblinphone rejects the
                // incoming INVITE with 488 "No matching crypto algo" *before* the call can
                // ring. Enable DTLS so inbound offers negotiate; mandatory starts true to
                // force negotiation while debugging.
                //
                // AVPF: the platform offers RTP/SAVPF (the AVPF profile). Without AVPF the
                // local capabilities only cover RTP/SAVP, liblinphone finds "no corresponding
                // stream in local capabilities description" and replies 488. Enable AVPF so
                // the local capability stream matches the offered SAVPF+DTLS configuration.
                runCatching {
                    core.setAvpfMode(AVPFMode.Enabled)
                    Log.i(TAG, "AVPF mode -> ${core.avpfMode}")
                }.onFailure { Log.w(TAG, "setAvpfMode failed", it) }
                val dtlsSupported = runCatching {
                    core.isMediaEncryptionSupported(MediaEncryption.DTLS)
                }.getOrDefault(false)
                Log.i(TAG, "DTLS supported=$dtlsSupported, current=${core.mediaEncryption}")
                if (dtlsSupported) {
                    val rc = runCatching { core.setMediaEncryption(MediaEncryption.DTLS) }
                        .getOrElse { -1 }
                    Log.i(TAG, "setMediaEncryption(DTLS) -> rc=$rc")
                    // The platform's WebRTC offer (RTP/SAVPF + a=fingerprint + setup:active,
                    // no a=crypto) is classified by liblinphone as SRTP-only, which with a
                    // DTLS-only core triggers "no corresponding stream in local capabilities"
                    // and a 488 before the call can ring (linphone-sdk #442, unresolved).
                    // Mandatory=false lets the offer/answer proceed instead of rejecting.
                    core.setMediaEncryptionMandatory(false)
                    Log.i(TAG, "mediaEncryptionMandatory=false (DTLS preferred, mismatch tolerated)")
                } else {
                    Log.w(TAG, "DTLS not supported by this build - secure offers may be rejected")
                }

                // Inbound delivery only works over the platform's WSS flow: the
                // non-WS location store answers REGISTER with 200 and keepalives,
                // but inbound forks are refused instantly and the endpoint record
                // stays sip_registered=false (no INVITE ever reaches the client).
                // The in-app bridge registers against wss://registrar.vobiz.ai:5063/
                // and Linphone talks to its loopback UDP proxy instead.
                val transport = "UDP"
                relayMode = true
                WssRelay.start(creds) { state -> onRelayState(state) }
                val transportParam = ";transport=udp"
                val port = WssRelay.RELAY_PORT
                val serverHost = RELAY_LOOPBACK_ADDR

                val proxy = core.createProxyConfig()
                proxy.serverAddr = "sip:$serverHost:$port$transportParam"
                val identity = Factory.instance()
                    .createAddress("sip:${creds.username}@${creds.domain}")
                proxy.identityAddress = identity
                proxy.isRegisterEnabled = true
                proxy.expires = REGISTER_EXPIRES_S
                core.setKeepAliveEnabled(true)

                // ONLY the loopback relay proxy. Direct UDP/TLS registrations to
                // registrar.vobiz.ai were removed: they created extra location-store
                // contacts, so the platform routed inbound INVITEs to the direct
                // contact (outside the relay), where Linphone answered 486 Busy
                // (Railway: DialHangupCause=USER_BUSY) and the relay never saw the
                // call. The WSS relay is the sole inbound path.

                // Configure NAT policy with STUN for ALL proxies so ICE candidates gathered for media
                val natPolicy = core.createNatPolicy().apply {
                    isIceEnabled = true
                    isStunEnabled = true
                    stunServer = "stun.l.google.com:19302"
                }
                core.natPolicy = natPolicy

                val authInfo = Factory.instance().createAuthInfo(
                    creds.username, creds.username, creds.password, null, null, creds.domain
                )
                core.addAuthInfo(authInfo)

                // Outbound INVITEs are challenged with the From-header user
                // (belle_sip_auth_event_create uses the From URI user for the auth
                // lookup), while the digest response must carry the trunk's username
                // (the credential attached to the outbound trunk). OutboundAuth maps
                // the From user to the trunk credentials, scoped by domain to the
                // outbound trunk so REGISTER (identity@registrar) never picks it up.
                val plan = OutboundAuth.resolve(
                    CredentialStore.getEffectiveCallerId(),
                    creds,
                    CredentialStore.getTrunkConfig(),
                )
                if (plan.registerOutboundAuth) {
                    val outboundAuth = Factory.instance().createAuthInfo(
                        plan.fromUser, plan.digestUser, plan.digestPassword, null, null, plan.outboundDomain
                    )
                    core.addAuthInfo(outboundAuth)
                    Log.i(
                        TAG,
                        "Outbound auth registered: from=${plan.fromUser} " +
                            "digest=${plan.digestUser} domain=${plan.outboundDomain}"
                    )
                }

                core.addProxyConfig(proxy)
                core.defaultProxyConfig = proxy

                // inc_timeout = incoming ring timeout (Android default 45s, max 300s).
                // Ring a little longer so users can reach the phone; then auto-reject
                // with 486 so the caller hears busy and a missed call is logged.
                core.setIncTimeout(INCOMING_RING_TIMEOUT_S)
                // in_call_timeout (max call duration) intentionally left at default 0 = unlimited.

                _registrationState.value = VobizRegistrationState.Connecting
                Log.i(
                    TAG,
                    "REGISTER initiated: sip:${creds.username}@${creds.domain} " +
                        "via $serverHost:$port ($transport)" +
                        if (relayMode) " [WSS relay -> ${creds.domain}]" else ""
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Registration setup failed", t)
                _registrationState.value =
                    VobizRegistrationState.Failed("Registration setup failed: ${t.message}")
            }
        }
    }
}
