package com.grinch.rivo4.sip

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.random.Random

/**
 * Pure SIP message parsing/mutation helpers for [WssRelay].
 * No Android or socket dependencies - JVM unit testable.
 */
object SipRelayCore {

    data class SipMessage(
        val startLine: String,
        val headerLines: List<String>,
        val body: String,
    ) {
        val isRequest: Boolean get() = !startLine.startsWith("SIP/")
        val method: String get() = if (isRequest) startLine.substringBefore(' ') else ""
        val statusCode: Int get() = if (isRequest) -1 else startLine.split(" ").getOrNull(1)?.toIntOrNull() ?: -1
        val cseqMethod: String
            get() = headerValue(this, "CSeq")?.trim()?.substringAfter(' ') ?: ""
        /** Sequence number of the CSeq header ("101 INVITE" -> "101"). */
        val cseqNumber: String
            get() = headerValue(this, "CSeq")?.trim()?.substringBefore(' ') ?: ""
    }

    fun parse(raw: String): SipMessage? {
        // Split header/body at the first blank line, but preserve the body byte-for-byte.
        // Normalizing the body's line endings (\r\n -> \n) shrinks it below the declared
        // Content-Length, which makes the receiving UAS read past the datagram and report
        // a receive error - so only the header block may be normalized.
        val head: String
        val body: String
        val crlfSep = raw.indexOf("\r\n\r\n")
        if (crlfSep >= 0) {
            head = raw.substring(0, crlfSep)
            body = raw.substring(crlfSep + 4)
        } else {
            val lfSep = raw.indexOf("\n\n")
            if (lfSep < 0) return null
            head = raw.substring(0, lfSep)
            body = raw.substring(lfSep + 2)
        }
        val lines = head.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        val start = lines.firstOrNull() ?: return null
        if (!start.startsWith("SIP/2.0") && !listOf(
                "INVITE", "ACK", "BYE", "CANCEL", "REGISTER", "OPTIONS",
                "INFO", "UPDATE", "REFER", "NOTIFY", "MESSAGE", "PRACK", "SUBSCRIBE"
            ).any { start.startsWith("$it ") }
        ) return null
        return SipMessage(start, lines.drop(1), body)
    }

    fun serialize(msg: SipMessage): String {
        // Content-Length must always match the serialized body byte count; recompute it
        // so a mutated (or preserved) body never disagrees with the declared length.
        val len = msg.body.toByteArray(StandardCharsets.UTF_8).size
        var replaced = false
        val lines = ArrayList<String>(msg.headerLines.size + 1)
        for (line in msg.headerLines) {
            val isLen = line.startsWith("Content-Length:", ignoreCase = true) ||
                line.startsWith("l:", ignoreCase = true)
            if (isLen) {
                if (!replaced) { lines.add("Content-Length: $len"); replaced = true }
            } else {
                lines.add(line)
            }
        }
        if (!replaced) lines.add("Content-Length: $len")
        return (listOf(msg.startLine) + lines).joinToString("\r\n") + "\r\n\r\n" + msg.body
    }

    fun headerValue(msg: SipMessage, name: String): String? {
        val prefix = "$name:"
        return msg.headerLines.firstOrNull { it.startsWith(prefix, ignoreCase = true) }
            ?.substring(prefix.length)?.trim()
    }

    fun headerValues(msg: SipMessage, name: String): List<String> {
        val prefix = "$name:"
        return msg.headerLines.filter { it.startsWith(prefix, ignoreCase = true) }
            .map { it.substring(prefix.length).trim() }
    }

    /** Replaces (or appends when absent) a header, keeping other headers in place. */
    fun setHeader(msg: SipMessage, name: String, value: String): SipMessage {
        val prefix = "$name:"
        var replaced = false
        val lines = msg.headerLines.map { line ->
            if (line.startsWith(prefix, ignoreCase = true)) {
                if (replaced) null else { replaced = true; "$name: $value" }
            } else line
        }.filterNotNull()
        return if (replaced) msg.copy(headerLines = lines)
        else msg.copy(headerLines = lines + "$name: $value")
    }

    fun removeHeader(msg: SipMessage, name: String): SipMessage {
        val prefix = "$name:"
        return msg.copy(headerLines = msg.headerLines.filterNot { it.startsWith(prefix, ignoreCase = true) })
    }

    /** Branch parameter of the top Via, or null. */
    fun topViaBranch(msg: SipMessage): String? =
        headerValue(msg, "Via")?.let { via ->
            Regex("""branch=([^;,\s]+)""").find(via)?.groupValues?.get(1)
        }

    fun replaceTopVia(msg: SipMessage, newViaValue: String): SipMessage {
        val lines = msg.headerLines.toMutableList()
        val idx = lines.indexOfFirst { it.startsWith("Via:", ignoreCase = true) || it.startsWith("v:", ignoreCase = true) }
        if (idx < 0) return msg.copy(headerLines = listOf("Via: $newViaValue") + lines)
        lines[idx] = "Via: $newViaValue"
        return msg.copy(headerLines = lines)
    }

    /** Adds this hop's Via ABOVE the existing chain (RFC 3261 proxy behaviour),
     *  preserving every upstream Via so responses can be matched by each hop. */
    fun prependTopVia(msg: SipMessage, newViaValue: String): SipMessage {
        val lines = msg.headerLines.toMutableList()
        val idx = lines.indexOfFirst { it.startsWith("Via:", ignoreCase = true) || it.startsWith("v:", ignoreCase = true) }
        if (idx < 0) return msg.copy(headerLines = listOf("Via: $newViaValue") + lines)
        return msg.copy(headerLines = lines.subList(0, idx) + "Via: $newViaValue" + lines.subList(idx, lines.size))
    }

    fun stripTopVia(msg: SipMessage): SipMessage {
        val lines = msg.headerLines.toMutableList()
        val idx = lines.indexOfFirst { it.startsWith("Via:", ignoreCase = true) || it.startsWith("v:", ignoreCase = true) }
        if (idx < 0) return msg
        lines.removeAt(idx)
        return msg.copy(headerLines = lines)
    }

    /** Rewrites the request-URI of a request's start line ("METHOD uri SIP/2.0"). */
    fun rewriteRequestUri(msg: SipMessage, newUri: String): SipMessage {
        if (!msg.isRequest) return msg
        val parts = msg.startLine.split(' ', limit = 3)
        if (parts.size < 3) return msg
        return msg.copy(startLine = "${parts[0]} $newUri ${parts[2]}")
    }

    fun requestUri(msg: SipMessage): String? =
        if (msg.isRequest) msg.startLine.split(' ', limit = 3).getOrNull(1) else null

    fun toTag(msg: SipMessage): String? =
        headerValue(msg, "To")?.let { Regex(""";\s*tag=([^;,\s]+)""").find(it)?.groupValues?.get(1) }

    fun fromTag(msg: SipMessage): String? =
        headerValue(msg, "From")?.let { Regex(""";\s*tag=([^;,\s]+)""").find(it)?.groupValues?.get(1) }

    fun callId(msg: SipMessage): String? = headerValue(msg, "Call-ID") ?: headerValue(msg, "i")

    /** URI inside <> if present, trimmed. Params outside the angle brackets (e.g.
     *  `;+sip.instance="<urn:uuid:...>"`) must not be swallowed, so the FIRST `>`
     *  after the FIRST `<` terminates the URI. */
    fun contactUri(msg: SipMessage): String? =
        headerValue(msg, "Contact")?.let { raw ->
            val angle = raw.indexOf('<')
            val uri = if (angle >= 0) {
                val end = raw.indexOf('>', angle + 1)
                if (end > angle) raw.substring(angle + 1, end) else raw.substring(angle + 1)
            } else {
                raw.substringBefore(';').trim()
            }
            uri.takeIf { it.isNotEmpty() }
        }

    /** Builds a response for [req] with the mandatory dialog-identifying headers. */
    fun buildResponse(req: SipMessage, code: Int, reason: String, extraHeaders: List<String> = emptyList()): SipMessage {
        val vias = headerValues(req, "Via").map { "Via: $it" }
        val from = headerValue(req, "From")?.let { "From: $it" }
        val to = headerValue(req, "To")?.let { base ->
            if (toTag(req) != null) "To: $base" else "To: $base;tag=relay${Random.nextInt(100000, 999999)}"
        }
        val callId = callId(req)?.let { "Call-ID: $it" }
        val cseq = headerValue(req, "CSeq")?.let { "CSeq: $it" }
        val headers = (vias + listOfNotNull(from, to, callId, cseq) + extraHeaders).toMutableList()
        if (headers.none { it.startsWith("User-Agent:", ignoreCase = true) }) {
            headers.add("User-Agent: VobizDialer")
        }
        return SipMessage("SIP/2.0 $code $reason", headers, "")
    }

    fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /**
     * Digest authorization value for a REGISTER/INVITE challenge.
     * Supports plain digest and qop=auth (MD5).
     */
    fun digestAuthorization(
        username: String,
        realm: String,
        password: String,
        nonce: String,
        method: String,
        uri: String,
        qop: String? = null,
        nc: String = "00000001",
        cnonce: String = Random.nextLong(Long.MIN_VALUE, Long.MAX_VALUE).toString(16),
    ): String {
        val ha1 = md5Hex("$username:$realm:$password")
        val ha2 = md5Hex("$method:$uri")
        val response = if (qop != null) {
            md5Hex("$ha1:$nonce:$nc:$cnonce:$qop:$ha2")
        } else {
            md5Hex("$ha1:$nonce:$ha2")
        }
        val sb = StringBuilder()
            .append("Digest username=\"").append(username).append("\"")
            .append(", realm=\"").append(realm).append("\"")
            .append(", nonce=\"").append(nonce).append("\"")
            .append(", uri=\"").append(uri).append("\"")
            .append(", response=\"").append(response).append("\"")
            .append(", algorithm=MD5")
        if (qop != null) {
            sb.append(", qop=").append(qop).append(", nc=").append(nc)
                .append(", cnonce=\"").append(cnonce).append("\"")
        }
        return sb.toString()
    }

    /** Parses WWW-Authenticate/Proxy-Authenticate params into a map (quoted values unescaped). */
    fun parseChallenge(headerValue: String): Map<String, String> {
        val inner = headerValue.substringAfter("Digest", headerValue).trim()
        val result = mutableMapOf<String, String>()
        Regex("""(\w+)=("(?:[^"\\]|\\.)*"|[^\s,]+)""").findAll(inner).forEach { m ->
            var v = m.groupValues[2]
            if (v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length - 1)
            result[m.groupValues[1].lowercase()] = v
        }
        return result
    }

    fun buildRegister(
        user: String,
        realm: String,
        callId: String,
        fromTag: String,
        cseq: Int,
        expires: Int,
        authorization: String? = null,
        branch: String = "z9hG4bK" + Random.nextLong(Long.MIN_VALUE, Long.MAX_VALUE).toString(16).trimStart('-'),
    ): String {
        // Use the real registrar domain in Contact so the platform tracks registration correctly
        val contact = "<sip:$user@$realm;transport=ws>"
        val sb = StringBuilder()
            .append("REGISTER sip:").append(realm).append(" SIP/2.0\r\n")
            .append("Via: SIP/2.0/WSS $realm;branch=").append(branch).append("\r\n")
            .append("Max-Forwards: 70\r\n")
            .append("To: <sip:").append(user).append('@').append(realm).append(">\r\n")
            .append("From: <sip:").append(user).append('@').append(realm).append(">;tag=").append(fromTag).append("\r\n")
            .append("Call-ID: ").append(callId).append("\r\n")
            .append("CSeq: ").append(cseq).append(" REGISTER\r\n")
            .append("Contact: ").append(contact).append("\r\n")
            .append("Expires: ").append(expires).append("\r\n")
            .append("Supported: path,gruu,outbound\r\n")
            .append("User-Agent: VobizDialer/2.4.5\r\n")
        if (authorization != null) sb.append("Authorization: ").append(authorization).append("\r\n")
        sb.append("Content-Length: 0\r\n\r\n")
        return sb.toString()
    }

    /** Strip the first Route header when it points at the relay's loopback leg. */
    fun removeOwnRoute(msg: SipMessage, loopbackRouteMarker: String): SipMessage {
        val routes = headerValues(msg, "Route")
        if (routes.isEmpty()) return msg
        val first = routes.first()
        if (!first.contains(loopbackRouteMarker)) return msg
        // remove exactly the first Route occurrence
        var removed = false
        val lines = msg.headerLines.filterNot { line ->
            if (!removed && line.startsWith("Route:", ignoreCase = true) && line.contains(loopbackRouteMarker)) {
                removed = true; true
            } else false
        }
        return msg.copy(headerLines = lines)
    }
}
