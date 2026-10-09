package com.grinch.rivo4.sip

import android.util.Log
import com.grinch.rivo4.auth.SipCredentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import kotlin.random.Random
import java.nio.charset.StandardCharsets
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * In-app WSS SIP bridge.
 *
 * The Linphone 5.5.24 SDK shipped in this app has no WebSocket transport, and the
 * Vobiz registrar's non-WS location store is broken platform-side (REGISTER -> 200
 * but INVITE never delivered; only wss://registrar.vobiz.ai:5063/ delivers). This
 * bridge registers to the platform over WSS (digest) and exposes a loopback UDP
 * "proxy" (127.0.0.1:5099) that Linphone registers against, so all call signaling
 * flows: Linphone <-> UDP loopback <-> bridge <-> WSS <-> Vobiz.
 *
 * Behavior (B2BUA-ish, per dialog-table design):
 *  - Linphone REGISTER/OPTIONS to the loopback are answered locally (swallowed).
 *  - Requests Linphone -> platform: top Via rewritten (branch kept, stored for
 *    restore), own Route consumed, Contact of initial INVITE rewritten to the
 *    bridge's registered WSS contact, in-dialog R-URI rewritten to the platform
 *    dialog target.
 *  - Requests platform -> Linphone: own top Via inserted, Record-Route stripped,
 *    R-URI rewritten to Linphone's observed contact.
 *  - Responses in both directions: restore/strip the bridge Via, never share
 *    route sets across the legs.
 *  - Bridge keeps the WSS REGISTER fresh (75% of granted expiry), answers the
 *    platform's OPTIONS, sends its own OPTIONS keepalive every 30s, reconnects
 *    with backoff, and feeds VobizRegistrationState.
 */
object WssRelay {

    private const val TAG = "WssRelay"
    private const val WSS_URL = "wss://registrar.vobiz.ai:5063/"
    private const val WS_SUBPROTOCOL = "sip"
    const val RELAY_PORT = 5099
    private const val RELAY_LOOPBACK = "127.0.0.1"
    private const val REGISTER_EXPIRES_S = 300
    private const val KEEPALIVE_INTERVAL_S = 30L
    // Registrar challenge pacing: a rejected digest (stale/provisioning-race password)
    // used to retry every round-trip (~250ms) until the server rate-limited us with
    // "403 Try later". Cap in-cycle attempts, space them out, then retry slowly.
    private const val CHALLENGE_MAX_ATTEMPTS = 3
    private const val CHALLENGE_RETRY_DELAY_S = 2L
    private const val REGISTER_RETRY_DELAY_S = 30L
    private const val BRANCH_PREFIX = "z9hG4bKrelay"
    private const val ROUTE_MARKER = "$RELAY_LOOPBACK:$RELAY_PORT"
    private const val VIA_SENT_BY = "$RELAY_LOOPBACK:$RELAY_PORT"
    private const val MAX_RECONNECT_DELAY_S = 30L

    private val running = AtomicBoolean(false)
    private val registered = AtomicBoolean(false)
    private val startStopLock = Any()

    /** Bumped on every connect/teardown so callbacks of superseded sockets are ignored. */
    private val connGeneration = AtomicInteger(0)

    /** Keepalive is scheduled once for the process lifetime. */
    private val keepaliveScheduled = AtomicBoolean(false)

    /** Consecutive 401/407 challenges for the current registration cycle. */
    private val challengeAttempts = AtomicInteger(0)

    /** At most one pending delayed REGISTER retry (challenge give-up / 403 recovery). */
    private val registerRetryPending = AtomicBoolean(false)

    @Volatile private var creds: SipCredentials? = null
    @Volatile private var stateListener: ((VobizRegistrationState) -> Unit)? = null
    @Volatile private var ws: WebSocket? = null
    @Volatile private var udp: DatagramSocket? = null
    @Volatile private var linphoneAddr: InetSocketAddress? = null
    @Volatile private var linphoneContactUri: String? = null
    @Volatile private var bridgeContactUri: String? = null
    @Volatile private var grantedExpiryS: Int = REGISTER_EXPIRES_S
    @Volatile private var reconnectDelayS = 1L
    /** a=setup value from the platform's most recent offer (tracked for answer rewriting). */
    @Volatile private var lastOfferSetup: String? = null

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val scheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "wss-relay-sched").apply { isDaemon = true }
        }
    private val registerCSeq = AtomicInteger(1)
    private val registerCallId: String = "relay-" + Random.nextLong(Long.MIN_VALUE, Long.MAX_VALUE).toString(16).trimStart('-')
    private val registerFromTag: String = Random.nextLong(Long.MIN_VALUE, Long.MAX_VALUE).toString(16).trimStart('-')

    /** branch -> original top Via + Linphone's source addr (transactions Linphone originated). */
    private val outboundBranches = ConcurrentHashMap<String, PendingOutbound>()

    /** Bridge-originated branches (REGISTER/OPTIONS keepalive): swallow their responses. */
    private val ownBranches = Collections.synchronizedSet(mutableSetOf<String>())

    /** "callId|cseq" -> branch we used when forwarding the INVITE, so its CANCEL/ACK can reuse it. */
    private val forwardedInviteBranches = ConcurrentHashMap<String, String>()

    /** "callId|cseq" -> whether Linphone's final INVITE response was a 2xx. */
    private val inviteFinalWas2xx = ConcurrentHashMap<String, Boolean>()

    private data class PendingOutbound(val originalVia: String, val clientAddr: InetSocketAddress)

    // ---------------------------------------------------------------- lifecycle

    fun start(credentials: SipCredentials, listener: (VobizRegistrationState) -> Unit) {
        synchronized(startStopLock) {
            val alreadyRunning = running.get()
            val credsChanged = alreadyRunning && creds != credentials
            if (alreadyRunning && !credsChanged) {
                stateListener = listener
                return
            }
            if (alreadyRunning) {
                // Credentials rotated: drop the old session and rebuild.
                running.set(false)
                connGeneration.incrementAndGet()
                registered.set(false)
                runCatching { ws?.close(1000, "creds") }
                ws = null
                runCatching { udp?.close() }
                udp = null
                outboundBranches.clear()
                ownBranches.clear()
            }
            running.set(true)
            creds = credentials
            stateListener = listener
            registered.set(false)
            reconnectDelayS = 1L
            notifyState(VobizRegistrationState.Connecting)
            startUdpListener()
            connectWebSocket()
            if (keepaliveScheduled.compareAndSet(false, true)) {
                scheduler.scheduleWithFixedDelay(::sendKeepalive, KEEPALIVE_INTERVAL_S, KEEPALIVE_INTERVAL_S, TimeUnit.SECONDS)
            }
            Log.i(TAG, "relay started for ${credentials.username}@${credentials.domain}")
        }
    }

    fun stop() {
        synchronized(startStopLock) {
            if (!running.getAndSet(false)) return
            connGeneration.incrementAndGet()
            registered.set(false)
            val socket = ws
            ws = null
            runCatching { socket?.close(1000, "bye") }
            runCatching { udp?.close() }
            udp = null
            linphoneAddr = null
            outboundBranches.clear()
            ownBranches.clear()
            notifyState(VobizRegistrationState.Idle)
            Log.i(TAG, "relay stopped")
        }
    }

    fun onNetworkChanged() {
        if (!running.get()) return
        Log.i(TAG, "network changed - forcing reconnect")
        registered.set(false)
        reconnectDelayS = 1L
        connGeneration.incrementAndGet()
        val socket = ws
        ws = null
        runCatching { socket?.close(1001, "network") }
        connectWebSocket()
    }

    private fun notifyState(state: VobizRegistrationState) {
        runCatching { stateListener?.invoke(state) }
            .onFailure { Log.w(TAG, "state listener failed", it) }
    }

    // ---------------------------------------------------------------- WebSocket

    private fun connectWebSocket() {
        if (!running.get()) return
        val c = creds ?: return
        val gen = connGeneration.incrementAndGet()
        val request = Request.Builder()
            .url(WSS_URL)
            .header("Sec-WebSocket-Protocol", WS_SUBPROTOCOL)
            .build()
        Log.i(TAG, "connecting $WSS_URL (subprotocol=$WS_SUBPROTOCOL, gen=$gen)")
        val socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (gen != connGeneration.get()) {
                    Log.i(TAG, "stale connection open - closing (gen=$gen)")
                    webSocket.close(1000, "superseded")
                    return
                }
                Log.i(TAG, "WSS open (${response.code})")
                ws = webSocket
                reconnectDelayS = 1L
                sendRegister(c, null)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (gen != connGeneration.get()) return
                runCatching { handlePlatformMessage(text) }
                    .onFailure { Log.e(TAG, "platform message handling failed", it) }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (gen != connGeneration.get()) return
                runCatching { handlePlatformMessage(bytes.utf8()) }
                    .onFailure { Log.e(TAG, "platform binary message handling failed", it) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (gen != connGeneration.get()) return
                Log.w(TAG, "WSS closed: $code $reason")
                handleDisconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (gen != connGeneration.get()) return
                Log.w(TAG, "WSS failure: ${t.message} (http=${response?.code})")
                handleDisconnect()
            }
        })
        if (gen == connGeneration.get()) ws = socket
    }

    private fun handleDisconnect() {
        if (!running.get()) return
        ws = null
        registered.set(false)
        notifyState(VobizRegistrationState.Connecting)
        val delay = reconnectDelayS
        reconnectDelayS = (delay * 2).coerceAtMost(MAX_RECONNECT_DELAY_S)
        Log.i(TAG, "reconnecting in ${delay}s")
        scheduler.schedule({
            if (running.get() && ws == null) connectWebSocket()
        }, delay, TimeUnit.SECONDS)
    }

    private fun sendRegister(c: SipCredentials, authorization: String?) {
        if (authorization == null) challengeAttempts.set(0)
        val branch = newBranch()
        ownBranches.add(branch)
        val raw = SipRelayCore.buildRegister(
            user = c.username,
            realm = c.domain,
            callId = registerCallId,
            fromTag = registerFromTag,
            cseq = registerCSeq.getAndIncrement(),
            expires = REGISTER_EXPIRES_S,
            authorization = authorization,
            branch = branch,
        )
        sendToPlatform(raw)
    }

    private fun sendKeepalive() {
        if (!running.get() || !registered.get()) return
        val c = creds ?: return
        val branch = newBranch()
        ownBranches.add(branch)
        val raw = buildString {
            append("OPTIONS sip:").append(c.domain).append(" SIP/2.0\r\n")
            append("Via: SIP/2.0/WSS ").append(VIA_SENT_BY).append(";branch=").append(branch).append("\r\n")
            append("Max-Forwards: 70\r\n")
            append("From: <sip:").append(c.username).append('@').append(c.domain).append(">;tag=").append(registerFromTag).append("\r\n")
            append("To: <sip:").append(c.username).append('@').append(c.domain).append(">\r\n")
            append("Call-ID: ").append(registerCallId).append("\r\n")
            append("CSeq: ").append(registerCSeq.getAndIncrement()).append(" OPTIONS\r\n")
            append("User-Agent: VobizDialer/2.4.5\r\n")
            append("Content-Length: 0\r\n\r\n")
        }
        sendToPlatform(raw)
    }

    private fun sendToPlatform(raw: String) {
        val socket = ws
        if (socket == null) {
            Log.w(TAG, "dropping outbound message - WSS not connected")
            return
        }
        val ok = socket.send(raw)
        if (!ok) Log.w(TAG, "WSS send() rejected")
    }

    private fun newBranch(): String = BRANCH_PREFIX + Random.nextLong(Long.MIN_VALUE, Long.MAX_VALUE).toString(16).trimStart('-')

    /**
     * Branch for a top Via we are about to send to Linphone.
     *
     * RFC 3261 rules we must honour or Linphone cannot match the transaction:
     *  - CANCEL reuses the INVITE's branch (§9.1). A fresh branch yields
     *    481 Call/Transaction Does Not Exist and the call rings forever after
     *    the caller hangs up.
     *  - ACK of a *non-2xx* final response also reuses the INVITE's branch
     *    (§17.1.1), otherwise Linphone never sees the transaction complete and
     *    retransmits 487/603 on a timer while answering the stray ACK 405.
     *  - ACK of a 2xx starts a NEW transaction (§13.2.2.1) -> mint a fresh branch.
     */
    private fun branchFor(msg: SipRelayCore.SipMessage): String {
        val key = "${SipRelayCore.callId(msg) ?: ""}|${msg.cseqNumber}"
        when (msg.method) {
            "CANCEL" -> {
                forwardedInviteBranches[key]?.let { return it }
                Log.w(TAG, "CANCEL without a tracked INVITE branch - minting a new one ($key)")
            }
            "ACK" -> {
                val was2xx = inviteFinalWas2xx.remove(key)
                val stored = forwardedInviteBranches.remove(key)
                if (stored != null && was2xx == false) return stored
            }
        }
        val branch = newBranch()
        if (msg.method == "INVITE" && SipRelayCore.toTag(msg) == null) {
            if (forwardedInviteBranches.size > 64) {
                forwardedInviteBranches.clear()
                inviteFinalWas2xx.clear()
            }
            forwardedInviteBranches[key] = branch
        }
        return branch
    }

    // ---------------------------------------------------------------- platform -> bridge

    private fun handlePlatformMessage(text: String) {
        val msg = SipRelayCore.parse(text) ?: run {
            Log.w(TAG, "unparseable platform message (${text.length} chars)")
            return
        }
        // BYE drives teardown: dump it whole (never carries SDP) so a dialog
        // mismatch, a missing Route or a 481 is visible without a packet capture.
        if (msg.method == "BYE" || msg.cseqMethod == "BYE") {
            Log.i(TAG, "=== ${msg.method} FROM PLATFORM (${msg.statusCode}) ===\n${text.trim()}")
        }
        // Log already done in SipRelayCore.parse
        if (!msg.isRequest) {
            handlePlatformResponse(msg)
        } else {
            handlePlatformRequest(msg)
        }
    }

    private fun handlePlatformResponse(msg: SipRelayCore.SipMessage) {
        val branch = SipRelayCore.topViaBranch(msg)
        if (branch != null && branch.startsWith(BRANCH_PREFIX)) {
            // Response to something the bridge itself sent.
            ownBranches.remove(branch)
            when (msg.cseqMethod) {
                "REGISTER" -> handleRegisterResponse(msg)
                else -> Log.d(TAG, "ignoring response to bridge ${msg.cseqMethod} (${msg.statusCode})")
            }
            return
        }
        val pending = branch?.let { outboundBranches[it] }
        if (pending == null) {
            Log.w(TAG, "response with no tracked branch (${msg.statusCode} ${msg.cseqMethod})")
            return
        }
        if (msg.statusCode >= 200) outboundBranches.remove(branch)
        // Response traveling to Linphone: restore its original top Via and drop ours.
        var out = SipRelayCore.replaceTopVia(msg, pending.originalVia)
        out = stripBridgeRouteHeaders(out)
        sendToLinphone(SipRelayCore.serialize(out), pending.clientAddr)
    }

    private fun handleRegisterResponse(msg: SipRelayCore.SipMessage) {
        when {
            msg.statusCode == 401 || msg.statusCode == 407 -> {
                val c = creds ?: return
                val headerName = if (msg.statusCode == 401) "WWW-Authenticate" else "Proxy-Authenticate"
                val challenge = SipRelayCore.headerValue(msg, headerName)
                if (challenge == null) {
                    Log.w(TAG, "${msg.statusCode} without challenge")
                    notifyState(VobizRegistrationState.Failed("Registrar challenge missing"))
                    return
                }
                val params = SipRelayCore.parseChallenge(challenge)
                val realm = params["realm"] ?: c.domain
                val nonce = params["nonce"] ?: return
                val qop = params["qop"]?.split(",")?.map { it.trim() }?.firstOrNull { it == "auth" }
                val attempt = challengeAttempts.incrementAndGet()
                // Challenge shape only - never credentials or the nonce value itself.
                Log.i(
                    TAG,
                    "REGISTER challenge #$attempt realm=$realm " +
                        "algorithm=${params["algorithm"] ?: "MD5"} " +
                        "qop=${params["qop"] ?: "-"} stale=${params["stale"] ?: "-"} " +
                        "nonceLen=${nonce.length}"
                )
                if (attempt > CHALLENGE_MAX_ATTEMPTS) {
                    // Persistently rejected digest (wrong/stale password, e.g. the
                    // provisioning-race window). Stop before the server rate-limits
                    // us with 403 and keep a slow retry alive for self-recovery.
                    Log.w(TAG, "digest rejected $attempt times in a row - backing off ${REGISTER_RETRY_DELAY_S}s")
                    challengeAttempts.set(0)
                    notifyState(VobizRegistrationState.Failed("Registrar keeps challenging - check credentials"))
                    scheduleRegisterRetry(REGISTER_RETRY_DELAY_S)
                    return
                }
                val uri = "sip:${c.domain}"
                val auth = SipRelayCore.digestAuthorization(
                    username = c.username,
                    realm = realm,
                    password = c.password,
                    nonce = nonce,
                    method = "REGISTER",
                    uri = uri,
                    qop = qop,
                )
                if (attempt == 1) {
                    sendRegister(c, auth)
                } else {
                    // Paced retry: a wrong digest would otherwise loop at full RTT speed.
                    scheduler.schedule(
                        { if (running.get()) sendRegister(c, auth) },
                        CHALLENGE_RETRY_DELAY_S * attempt, TimeUnit.SECONDS,
                    )
                }
            }
            msg.statusCode in 200..299 -> {
                challengeAttempts.set(0)
                registered.set(true)
                reconnectDelayS = 1L
                grantedExpiryS = parseGrantedExpiry(msg)
                val contact = SipRelayCore.contactUri(msg)
                if (!contact.isNullOrBlank()) bridgeContactUri = contact
                Log.i(TAG, "REGISTER ok, expiry=${grantedExpiryS}s, contact=$bridgeContactUri")
                notifyState(VobizRegistrationState.Registered)
                scheduleRegisterRefresh()
            }
            else -> {
                Log.w(TAG, "REGISTER failed: ${msg.startLine}")
                registered.set(false)
                challengeAttempts.set(0)
                notifyState(VobizRegistrationState.Failed("Registrar rejected registration"))
                // e.g. "403 Try later" after a challenge storm: recover on a timer
                // instead of requiring a process restart.
                scheduleRegisterRetry(REGISTER_RETRY_DELAY_S)
            }
        }
    }

    /** One-shot delayed REGISTER (fresh, unauthenticated) unless already pending. */
    private fun scheduleRegisterRetry(delayS: Long) {
        if (!registerRetryPending.compareAndSet(false, true)) return
        scheduler.schedule({
            registerRetryPending.set(false)
            val c = creds
            if (running.get() && c != null && !registered.get()) {
                Log.i(TAG, "retrying platform REGISTER after ${delayS}s")
                sendRegister(c, null)
            }
        }, delayS, TimeUnit.SECONDS)
    }

    private fun parseGrantedExpiry(msg: SipRelayCore.SipMessage): Int {
        SipRelayCore.headerValue(msg, "Expires")?.toIntOrNull()?.let { return it }
        val contact = SipRelayCore.headerValue(msg, "Contact") ?: return REGISTER_EXPIRES_S
        Regex("""expires=(\d+)""").find(contact)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return REGISTER_EXPIRES_S
    }

    private fun scheduleRegisterRefresh() {
        val c = creds ?: return
        val delay = (grantedExpiryS * 3L / 4L).coerceAtLeast(30L)
        scheduler.schedule({
            if (running.get() && registered.get()) {
                Log.i(TAG, "refreshing platform REGISTER")
                sendRegister(c, null)
            }
        }, delay, TimeUnit.SECONDS)
    }

    private fun handlePlatformRequest(msg: SipRelayCore.SipMessage) {
        val method = msg.method
        if (method == "OPTIONS" && SipRelayCore.toTag(msg) == null) {
            // Platform keepalive/probe: answer locally, never forward.
            val resp = SipRelayCore.buildResponse(msg, 200, "OK")
            sendToPlatform(SipRelayCore.serialize(resp))
            return
        }
        deliverToLinphone(msg)
    }

    // ---------------------------------------------------------------- forwarding: platform -> Linphone

    /**
     * Rewrite inbound WebRTC offer SDP: if m-line uses RTP/SAVPF (or RTP/SAVP)
     * and a=fingerprint is present (session or media level), promote the profile
     * to UDP/TLS/RTP/SAVPF so linphone classifies the offer as DTLS instead of SDES-SRTP.
     * This avoids the "no matching crypto algo" / "proto mismatch" 488 rejections.
     */
    private fun maybeRewriteSdpForLinphone(msg: SipRelayCore.SipMessage): SipRelayCore.SipMessage {
        if (msg.method != "INVITE" || msg.body.isNullOrBlank()) return msg
        val body = msg.body
        // Diagnostic: log the raw SDP we are about to forward to Linphone.
        val hasFingerprint = body.contains("a=fingerprint:")
        val hasCandidate = body.contains("a=candidate")
        val hasIceUfrag = body.contains("ice-ufrag")
        val setupValue = Regex("""a=setup:(\w+)""").find(body)?.groupValues?.get(1) ?: "none"
        lastOfferSetup = setupValue
        val mLine = body.lines().firstOrNull { it.startsWith("m=") } ?: "no-m-line"
        val bodyBytes = body.toByteArray(Charsets.UTF_8).size
        Log.i(TAG, "OFFER SDP: bytes=$bodyBytes cl=${SipRelayCore.headerValue(msg, "Content-Length") ?: "?"} fp=$hasFingerprint cand=$hasCandidate ufrag=$hasIceUfrag setup=$setupValue m=\"$mLine\"")
        if (!hasFingerprint) return msg
        // Handle both \r\n and \n line endings.
        val sep = if (body.contains("\r\n")) "\r\n" else "\n"
        val lines = body.split(sep)
        var changed = false
        val newLines = lines.map { line ->
            if (!changed && line.startsWith("m=audio ")) {
                val replaced = line
                    .replace(" RTP/SAVPF ", " UDP/TLS/RTP/SAVPF ")
                    .replace(" RTP/SAVP ", " UDP/TLS/RTP/SAVP ")
                if (replaced != line) {
                    changed = true
                    replaced
                } else line
            } else line
        }
        if (!changed) {
            Log.i(TAG, "OFFER m-line already DTLS or no match - no rewrite")
            return msg
        }
        val newBody = newLines.joinToString(sep)
        val newCand = newBody.contains("a=candidate")
        val newUfrag = newBody.contains("ice-ufrag")
        Log.i(TAG, "REWRITE m-line: cand_after=$newCand ufrag_after=$newUfrag bytes=${newBody.toByteArray(Charsets.UTF_8).size}")
        return msg.copy(body = newBody)
    }

    private fun deliverToLinphone(msg: SipRelayCore.SipMessage) {
        val clientAddr = linphoneAddr
        if (clientAddr == null) {
            Log.w(TAG, "no Linphone socket yet - dropping ${msg.method} (${SipRelayCore.callId(msg)})")
            return
        }
        var out = maybeRewriteSdpForLinphone(msg)
        // Insert our top Via ABOVE the platform's chain so the response comes back
        // with a branch we can strip. replaceTopVia would destroy the platform's
        // own transaction Via, leaving our 100/180/603 unmatchable and discarded.
        out = SipRelayCore.prependTopVia(
            out,
            "SIP/2.0/UDP $VIA_SENT_BY;branch=${branchFor(msg)};rport",
        )
        // Add Record-Route so Linphone's dialog route set includes our relay.
        // This ensures BYE/ACK/re-INVITE from Linphone route back through us.
        if (msg.method == "INVITE" && SipRelayCore.toTag(msg) == null) {
            out = SipRelayCore.setHeader(out, "Record-Route",
                "<sip:$RELAY_LOOPBACK:$RELAY_PORT;transport=udp;lr>")
        }
        // Route headers pointing at us are ours to consume.
        out = SipRelayCore.removeOwnRoute(out, ROUTE_MARKER)
        // In-dialog or not: the target on the Linphone leg is Linphone's own contact.
        val target = linphoneContactUri
        if (target != null) {
            out = SipRelayCore.rewriteRequestUri(out, target)
        }
        Log.i(TAG, "platform -> linphone: ${msg.method} ${SipRelayCore.callId(msg)} ruri=${SipRelayCore.requestUri(out)}")
        if (msg.method == "BYE") {
            // What Linphone will actually receive after the rewrites above.
            Log.i(TAG, "=== BYE AS FORWARDED TO LINPHONE ===\n${SipRelayCore.serialize(out).trim()}")
        }
        sendToLinphone(SipRelayCore.serialize(out), clientAddr)
    }

    // ---------------------------------------------------------------- forwarding: Linphone -> platform

    private fun handleLinphoneMessage(raw: String, src: InetSocketAddress) {
        linphoneAddr = src
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("SIP/2.0") && !trimmed.contains(" SIP/")) {
            // CRLF keepalive or other non-SIP datagram.
            return
        }
        val msg = SipRelayCore.parse(raw) ?: run {
            Log.w(TAG, "unparseable Linphone message from $src (${raw.length} chars)")
            return
        }
        if (msg.method == "BYE" || msg.cseqMethod == "BYE") {
            Log.i(TAG, "=== ${msg.method} FROM LINPHONE (${msg.statusCode}) ===\n${raw.trim()}")
        }
        // Log already done in SipRelayCore.parse for inbound, but this is outbound from Linphone
        Log.i("SipRelay", "<< LP→PLATFORM: ${msg.startLine} cid=${SipRelayCore.callId(msg) ?: "?"} cseq=${msg.cseqMethod} via=${SipRelayCore.topViaBranch(msg) ?: "?"} cl=${raw.toByteArray(Charsets.UTF_8).size}")
        if (!msg.isRequest) {
            forwardLinphoneResponse(msg, src)
        } else {
            forwardLinphoneRequest(msg, src)
        }
    }

    private fun forwardLinphoneRequest(msg: SipRelayCore.SipMessage, src: InetSocketAddress) {
        when (msg.method) {
            "REGISTER" -> {
                // Linphone registers against the loopback: answer locally, never forward.
                linphoneContactUri = SipRelayCore.contactUri(msg)
                val resp = localRegisterOk(msg)
                sendToLinphone(SipRelayCore.serialize(resp), src)
                Log.i(TAG, "swallowed Linphone REGISTER (contact=$linphoneContactUri)")
                return
            }
            "OPTIONS" -> {
                val resp = SipRelayCore.buildResponse(msg, 200, "OK")
                sendToLinphone(SipRelayCore.serialize(resp), src)
                return
            }
        }
        if (!registered.get()) {
            Log.w(TAG, "not registered with platform - dropping ${msg.method}")
            val resp = SipRelayCore.buildResponse(msg, 503, "Service Unavailable")
            sendToLinphone(SipRelayCore.serialize(resp), src)
            return
        }
        val originalTopVia = SipRelayCore.headerValue(msg, "Via") ?: return
        val branch = SipRelayCore.topViaBranch(msg) ?: return

        var out = msg
        // Consume our own loose-route hop if Linphone routed the request through us.
        out = SipRelayCore.removeOwnRoute(out, ROUTE_MARKER)
        // Never share Linphone's route set with the platform.
        out = SipRelayCore.removeHeader(out, "Record-Route")
        // Bridge's top Via (branch preserved so responses can be restored).
        out = SipRelayCore.replaceTopVia(
            out,
            "SIP/2.0/WSS wssrelay.invalid;branch=$branch;rport",
        )
        // The registered bridge contact is how the platform addresses us in-dialog.
        val bridgeContact = bridgeContactUri
        if (bridgeContact != null && msg.method == "INVITE" && SipRelayCore.toTag(msg) == null) {
            out = SipRelayCore.setHeader(out, "Contact", "<$bridgeContact>")
        }

        outboundBranches[branch] = PendingOutbound(originalTopVia, src)
        sendToPlatform(SipRelayCore.serialize(out))
        Log.i(TAG, "linphone -> platform: ${msg.method} branch=$branch")
    }

    private fun forwardLinphoneResponse(msg: SipRelayCore.SipMessage, src: InetSocketAddress) {
        if (!registered.get()) return
        // Strip our bridge Via (top) so the platform sees only its own Via chain.
        var out = SipRelayCore.stripTopVia(msg)
        out = SipRelayCore.removeHeader(out, "Record-Route")
        // Diagnostic: log the answer SDP Linphone generated (before any rewrite).
        if (msg.statusCode in 200..299 && msg.cseqMethod == "INVITE" && msg.body.isNotBlank()) {
            val body = msg.body
            val setupValue = Regex("""a=setup:(\w+)""").find(body)?.groupValues?.get(1) ?: "none"
            val hasCand = body.contains("a=candidate")
            val hasUfrag = body.contains("ice-ufrag")
            val hasFp = body.contains("a=fingerprint")
            val mLine = body.lines().firstOrNull { it.startsWith("m=") } ?: "no-m-line"
            Log.i(TAG, "ANSWER SDP (native): bytes=${body.toByteArray(Charsets.UTF_8).size} setup=$setupValue cand=$hasCand ufrag=$hasUfrag fp=$hasFp m=\"$mLine\"")
        }
        // RFC 4145: if the OFFER was a=setup:active OR actpass, the answer MUST be passive.
        // Linphone's native answer is active, which breaks DTLS handshake when offer is actpass/active.
        // We MUST rewrite the answer's a=setup to passive for both active and actpass offers.
        if (msg.statusCode in 200..299 && msg.cseqMethod == "INVITE" && msg.body.isNotBlank()) {
            val offerSetup = lastOfferSetup ?: "none"
            val needsRewrite = offerSetup == "active" || offerSetup == "actpass"
            if (needsRewrite) {
                out = rewriteSetupAttribute(out, isAnswer = true)
                Log.i(TAG, "Rewrote answer setup to passive (offer was $offerSetup)")
            } else {
                Log.i(TAG, "Kept native answer setup (offer was $offerSetup)")
            }
        }
        // Use the REGISTER-accepted contact (bridgeContactUri) for the 200 OK Contact.
        // The 14:46 call proved this works: the registrar routes ACK/BYE over the
        // existing WSS flow when the Contact matches the registration binding.
        // NO Record-Route in 200 OK — the 14:46 call worked without it; adding it
        // breaks the platform's ACK for 2xx (which goes to Contact, not route set).
        if (msg.statusCode in 200..299 && msg.cseqMethod == "INVITE") {
            val bridgeContact = bridgeContactUri
            if (bridgeContact != null) out = SipRelayCore.setHeader(out, "Contact", "<$bridgeContact>")
        }
        if (msg.cseqMethod == "INVITE" && msg.statusCode >= 200) {
            // Record BEFORE sending, so a fast platform ACK cannot race past it.
            inviteFinalWas2xx["${SipRelayCore.callId(msg) ?: ""}|${msg.cseqNumber}"] =
                msg.statusCode in 200..299
        }
        // Diagnostic: dump the exact 200 OK we send to the platform (full headers).
        if (msg.statusCode in 200..299 && msg.cseqMethod == "INVITE") {
            val serialized = SipRelayCore.serialize(out)
            Log.i(TAG, "=== 200 OK FORWARDED TO PLATFORM ===\n${serialized.trim()}")
        }
        sendToPlatform(SipRelayCore.serialize(out))
        Log.i(TAG, "linphone -> platform: ${msg.statusCode} ${msg.cseqMethod}")
    }

    /**
     * Rewrite a=setup attribute in SDP answer to passive (RFC 4145).
     * Platform sends active in offer; we MUST answer passive for DTLS handshake.
     */
    private fun rewriteSetupAttribute(msg: SipRelayCore.SipMessage, isAnswer: Boolean): SipRelayCore.SipMessage {
        if (msg.body.isNullOrBlank()) return msg
        val lines = msg.body.split("\r\n").toMutableList()
        val setupValue = if (isAnswer) "passive" else "actpass" // Answer MUST be passive
        var inMedia = false
        var setupFound = false
        for (i in lines.indices) {
            val line = lines[i]
            if (line.startsWith("m=")) inMedia = line.startsWith("m=audio")
            if (inMedia && line.startsWith("a=setup:")) {
                lines[i] = "a=setup:$setupValue"
                setupFound = true
            }
        }
        if (!setupFound && inMedia) {
            val mIdx = lines.indexOfFirst { it.startsWith("m=audio") }
            if (mIdx >= 0) lines.add(mIdx + 1, "a=setup:passive")
        }
        return msg.copy(body = lines.joinToString("\r\n"))
    }

    private fun localRegisterOk(req: SipRelayCore.SipMessage): SipRelayCore.SipMessage {
        val expires = Regex("""expires=(\d+)""").find(SipRelayCore.headerValue(req, "Contact") ?: "")
            ?.groupValues?.get(1)?.toIntOrNull()
            ?: SipRelayCore.headerValue(req, "Expires")?.toIntOrNull()
            ?: 120
        val contact = SipRelayCore.headerValue(req, "Contact") ?: "<unknown>"
        var resp = SipRelayCore.buildResponse(
            req, 200, "OK",
            listOf("Expires: $expires", "Contact: $contact"),
        )
        // Add a To tag the way a registrar would.
        val to = SipRelayCore.headerValue(resp, "To")
        if (to != null && SipRelayCore.toTag(resp) == null) {
            resp = SipRelayCore.setHeader(resp, "To", "$to;tag=relay${Random.nextInt(100000, 999999)}")
        }
        return resp
    }

    private fun sendToLinphone(raw: String, addr: InetSocketAddress) {
        val socket = udp ?: return
        runCatching {
            val data = raw.toByteArray(Charsets.UTF_8)
            socket.send(DatagramPacket(data, data.size, addr))
        }.onFailure { Log.w(TAG, "UDP send to Linphone failed", it) }
    }

    private fun stripBridgeRouteHeaders(msg: SipRelayCore.SipMessage): SipRelayCore.SipMessage =
        SipRelayCore.removeOwnRoute(msg, ROUTE_MARKER)

    // ---------------------------------------------------------------- UDP listener (Linphone side)

    private fun startUdpListener() {
        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(RELAY_LOOPBACK, RELAY_PORT))
        }
        udp = socket
        Thread({
            Log.i(TAG, "UDP listener on $RELAY_LOOPBACK:$RELAY_PORT")
            val buf = ByteArray(64 * 1024)
            while (running.get() && udp === socket) {
                runCatching {
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val raw = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                    handleLinphoneMessage(raw, InetSocketAddress(packet.address, packet.port))
                }.onFailure {
                    if (running.get() && udp === socket) Log.w(TAG, "UDP receive failed", it)
                }
            }
            runCatching { socket.close() }
            Log.i(TAG, "UDP listener exited")
        }, "wss-relay-udp").apply { isDaemon = true; start() }
    }
}
