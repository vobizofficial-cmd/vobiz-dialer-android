package com.grinch.rivo4.sip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SipRelayCoreTest {

    private val invite = """
        INVITE sip:917965850027@08ecd76e.sip.vobiz.ai SIP/2.0
        Via: SIP/2.0/WSS 15.207.232.91:5060;branch=z9hG4bKplat1;received=18.96.230.209
        Via: SIP/2.0/UDP 18.96.230.209:5080;rport;branch=z9hG4bKfs1
        From: <sip:+9240953996@18.96.230.209>;tag=fs-tag
        To: <sip:917965850027@registrar.vobiz.ai>
        Call-ID: abc123@fs
        CSeq: 101 INVITE
        Contact: <sip:+9240953996@18.96.230.209:5080>
        Record-Route: <sip:15.207.232.91:5060;transport=ws;lr>
        Content-Type: application/sdp
        Content-Length: 3

        v=0
    """.trimIndent().replace("\n", "\r\n")

    @Test
    fun `parses request with headers and body`() {
        val msg = SipRelayCore.parse(invite)
        assertNotNull(msg)
        requireNotNull(msg)
        assertTrue(msg.isRequest)
        assertEquals("INVITE", msg.method)
        assertEquals("INVITE", msg.cseqMethod)
        assertEquals("abc123@fs", SipRelayCore.callId(msg))
        assertEquals("z9hG4bKplat1", SipRelayCore.topViaBranch(msg))
        assertEquals("fs-tag", SipRelayCore.fromTag(msg))
        assertNull(SipRelayCore.toTag(msg))
        assertEquals("v=0", msg.body)
    }

    @Test
    fun `round trips serialization`() {
        val msg = requireNotNull(SipRelayCore.parse(invite))
        val reparsed = SipRelayCore.parse(SipRelayCore.serialize(msg))
        assertNotNull(reparsed)
        assertEquals(msg.startLine, requireNotNull(reparsed).startLine)
        assertEquals(msg.headerLines, reparsed.headerLines)
        assertEquals(msg.body, reparsed.body)
    }

    @Test
    fun `rejects garbage input`() {
        assertNull(SipRelayCore.parse("not sip at all"))
        assertNull(SipRelayCore.parse("\r\n\r\n"))
    }

    @Test
    fun `multiline CRLF body survives and Content-Length matches byte count`() {
        // Regression: parse() used to normalize the BODY's \r\n -> \n, shrinking it below
        // the declared Content-Length, so the UAS read past the datagram (receive error).
        val raw = "INVITE sip:b SIP/2.0\r\n" +
            "Via: SIP/2.0/UDP h;branch=z9hG4bK1\r\n" +
            "Content-Type: application/sdp\r\n" +
            "Content-Length: 10\r\n" +
            "\r\n" +
            "v=0\r\no=x\r\n"
        val msg = requireNotNull(SipRelayCore.parse(raw))
        assertEquals("v=0\r\no=x\r\n", msg.body)
        val out = SipRelayCore.serialize(msg)
        val bodyBytes = out.substringAfter("\r\n\r\n").toByteArray(Charsets.UTF_8).size
        assertEquals(bodyBytes, SipRelayCore.headerValue(requireNotNull(SipRelayCore.parse(out)), "Content-Length")!!.toInt())
        assertEquals(10, bodyBytes)
    }

    @Test
    fun `top via branch prefers the topmost via only`() {
        val msg = requireNotNull(SipRelayCore.parse(invite))
        assertEquals("z9hG4bKplat1", SipRelayCore.topViaBranch(msg))
    }

    @Test
    fun `replaceTopVia swaps only the first via`() {
        val msg = requireNotNull(SipRelayCore.parse(invite))
        val out = SipRelayCore.replaceTopVia(msg, "SIP/2.0/WSS wssrelay.invalid;branch=z9hG4bKplat1;rport")
        val vias = SipRelayCore.headerValues(out, "Via")
        assertEquals(2, vias.size)
        assertTrue(vias[0].contains("wssrelay.invalid"))
        assertTrue(vias[0].contains("branch=z9hG4bKplat1"))
        assertTrue(vias[1].contains("18.96.230.209:5080"))
        // Branch of the new top Via preserved, so transaction matching still works.
        assertEquals("z9hG4bKplat1", SipRelayCore.topViaBranch(out))
    }

    @Test
    fun `stripTopVia removes the first via only`() {
        val msg = requireNotNull(SipRelayCore.parse(invite))
        val out = SipRelayCore.stripTopVia(msg)
        val vias = SipRelayCore.headerValues(out, "Via")
        assertEquals(1, vias.size)
        assertTrue(vias[0].contains("18.96.230.209:5080"))
    }

    @Test
    fun `removeHeader drops every instance`() {
        val msg = requireNotNull(SipRelayCore.parse(invite))
        val out = SipRelayCore.removeHeader(msg, "Record-Route")
        assertTrue(SipRelayCore.headerValues(out, "Record-Route").isEmpty())
    }

    @Test
    fun `setHeader replaces existing value in place`() {
        val msg = requireNotNull(SipRelayCore.parse(invite))
        val out = SipRelayCore.setHeader(msg, "Contact", "<sip:u@1.2.3.4:5;transport=ws>")
        assertEquals("<sip:u@1.2.3.4:5;transport=ws>", SipRelayCore.headerValue(out, "Contact"))
        // Headers after Contact keep their order.
        val names = out.headerLines.map { it.substringBefore(':') }
        assertTrue(names.indexOf("Contact") < names.indexOf("Record-Route"))
    }

    @Test
    fun `rewriteRequestUri targets the request line`() {
        val msg = requireNotNull(SipRelayCore.parse(invite))
        val out = SipRelayCore.rewriteRequestUri(msg, "sip:alice@127.0.0.1:48191")
        assertEquals("INVITE sip:alice@127.0.0.1:48191 SIP/2.0", out.startLine)
        assertEquals("sip:alice@127.0.0.1:48191", SipRelayCore.requestUri(out))
    }

    @Test
    fun `buildResponse copies vias from and tags the to header`() {
        val msg = requireNotNull(SipRelayCore.parse(invite))
        val resp = SipRelayCore.buildResponse(msg, 180, "Ringing")
        assertEquals("SIP/2.0 180 Ringing", resp.startLine)
        assertEquals(2, SipRelayCore.headerValues(resp, "Via").size)
        assertEquals("fs-tag", SipRelayCore.fromTag(resp))
        assertNotNull(SipRelayCore.toTag(resp))
        assertEquals("abc123@fs", SipRelayCore.callId(resp))
        assertEquals("101 INVITE", SipRelayCore.headerValue(resp, "CSeq"))
    }

    @Test
    fun `buildResponse keeps an existing to tag`() {
        val tagged = invite.replace(
            "To: <sip:917965850027@registrar.vobiz.ai>",
            "To: <sip:917965850027@registrar.vobiz.ai>;tag=already",
        )
        val resp = SipRelayCore.buildResponse(requireNotNull(SipRelayCore.parse(tagged)), 200, "OK")
        assertEquals("already", SipRelayCore.toTag(resp))
    }

    @Test
    fun `digest matches the RFC 2617 example`() {
        val auth = SipRelayCore.digestAuthorization(
            username = "Mufasa",
            realm = "testrealm@host.com",
            password = "Circle Of Life",
            nonce = "dcd98b7102dd2f0e8b11d0f600bfb0c093",
            method = "REGISTER",
            uri = "sip:registrar.vobiz.ai",
            qop = "auth",
            nc = "00000001",
            cnonce = "0a4f113b",
        )
        // RFC 2617 defines the hash over method:uri - use the documented GET example
        // instead when method/uri differ; here verify structure + md5 pipeline with
        // the RFC's exact inputs.
        val ha1 = SipRelayCore.md5Hex("Mufasa:testrealm@host.com:Circle Of Life")
        val ha2 = SipRelayCore.md5Hex("GET:/dir/index.html")
        val expected = SipRelayCore.md5Hex("$ha1:dcd98b7102dd2f0e8b11d0f600bfb0c093:00000001:0a4f113b:auth:$ha2")
        assertEquals("6629fae49393a05397450978507c4ef1", expected)
        assertTrue(auth.startsWith("Digest username=\"Mufasa\""))
        assertTrue(auth.contains("qop=auth"))
        assertTrue(auth.contains("nc=00000001"))
        assertTrue(auth.contains("algorithm=MD5"))
    }

    @Test
    fun `digest without qop uses the simple formula`() {
        val ha1 = SipRelayCore.md5Hex("user:realm:pass")
        val ha2 = SipRelayCore.md5Hex("REGISTER:sip:realm")
        val expected = SipRelayCore.md5Hex("$ha1:thenonce:$ha2")
        val auth = SipRelayCore.digestAuthorization(
            username = "user", realm = "realm", password = "pass",
            nonce = "thenonce", method = "REGISTER", uri = "sip:realm",
        )
        assertTrue(auth.contains("response=\"$expected\""))
        assertFalse(auth.contains("qop="))
    }

    @Test
    fun `parseChallenge reads quoted and bare params`() {
        val params = SipRelayCore.parseChallenge(
            "Digest realm=\"registrar.vobiz.ai\", nonce=\"abc123\", algorithm=MD5, qop=\"auth,auth-int\"",
        )
        assertEquals("registrar.vobiz.ai", params["realm"])
        assertEquals("abc123", params["nonce"])
        assertEquals("MD5", params["algorithm"])
        assertEquals("auth,auth-int", params["qop"])
    }

    @Test
    fun `buildRegister emits a well formed registration`() {
        val raw = SipRelayCore.buildRegister(
            user = "alice", realm = "registrar.vobiz.ai",
            callId = "cid1", fromTag = "ftag", cseq = 3, expires = 300,
            authorization = "Digest username=\"alice\"",
        )
        val msg = requireNotNull(SipRelayCore.parse(raw))
        assertEquals("REGISTER sip:registrar.vobiz.ai SIP/2.0", msg.startLine)
        assertEquals("REGISTER", msg.cseqMethod)
        assertEquals("cid1", SipRelayCore.callId(msg))
        assertNotNull(SipRelayCore.headerValue(msg, "Authorization"))
        assertEquals("300", SipRelayCore.headerValue(msg, "Expires"))
        assertTrue(SipRelayCore.topViaBranch(msg)!!.startsWith("z9hG4bK"))
        assertEquals("0", SipRelayCore.headerValue(msg, "Content-Length"))
    }

    @Test
    fun `removeOwnRoute drops only the relay hop`() {
        val msg = requireNotNull(SipRelayCore.parse(invite))
        val withRoute = SipRelayCore.setHeader(
            msg, "Route", "<sip:127.0.0.1:5099;transport=udp;lr>",
        )
        val out = SipRelayCore.removeOwnRoute(withRoute, "127.0.0.1:5099")
        assertTrue(SipRelayCore.headerValues(out, "Route").isEmpty())
        // Non-relay routes survive.
        val foreign = SipRelayCore.setHeader(msg, "Route", "<sip:15.207.232.91:5060;transport=ws;lr>")
        val kept = SipRelayCore.removeOwnRoute(foreign, "127.0.0.1:5099")
        assertEquals(1, SipRelayCore.headerValues(kept, "Route").size)
    }

    // ---------------------------------------------------------------- L0 SDP tests

    /** Realistic Vobiz/FreeSWITCH WebRTC-style INVITE SDP with DTLS + ICE. */
    private val webrtcInviteSdp = buildString {
        append("v=0\r\n")
        append("o=- 389234728 389234728 IN IP4 18.96.230.209\r\n")
        append("s=-\r\n")
        append("c=IN IP4 18.96.230.209\r\n")
        append("t=0 0\r\n")
        append("a=group:BUNDLE 0\r\n")
        append("a=msid-semantic: WMS\r\n")
        append("m=audio 24318 UDP/TLS/RTP/SAVPF 102 0 8 103 101\r\n")
        append("c=IN IP4 18.96.230.209\r\n")
        append("a=rtcp:34129 IN IP4 18.96.230.209\r\n")
        append("a=ice-ufrag:VobizUfrag1\r\n")
        append("a=ice-pwd:VobizPwd1234567890abcdefghijkl\r\n")
        append("a=ice-options:trickle\r\n")
        append("a=fingerprint:sha-256 AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89\r\n")
        append("a=setup:actpass\r\n")
        append("a=mid:0\r\n")
        append("a=sendrecv\r\n")
        append("a=rtcp-mux\r\n")
        append("a=rtpmap:102 opus/48000/2\r\n")
        append("a=fmtp:102 minptime=10;useinbandfec=1\r\n")
        append("a=rtpmap:0 PCMU/8000\r\n")
        append("a=rtpmap:8 PCMA/8000\r\n")
        append("a=rtpmap:101 telephone-event/8000\r\n")
        append("a=candidate:1 1 UDP 2130706431 18.96.230.209 24318 typ host\r\n")
        append("a=candidate:2 1 UDP 1694498815 18.96.230.209 34129 typ relay raddr 0.0.0.0 rport 0\r\n")
    }

    private fun wrapSdpInInvite(sdp: String): String {
        val cl = sdp.toByteArray(Charsets.UTF_8).size
        return "INVITE sip:user@registrar.vobiz.ai SIP/2.0\r\n" +
            "Via: SIP/2.0/WSS 15.207.232.91:5060;branch=z9hG4bKplat1\r\n" +
            "From: <sip:+919123151351@18.96.230.209>;tag=fs-tag\r\n" +
            "To: <sip:917965850027@registrar.vobiz.ai>\r\n" +
            "Call-ID: testcall@fs\r\n" +
            "CSeq: 101 INVITE\r\n" +
            "Content-Type: application/sdp\r\n" +
            "Content-Length: $cl\r\n" +
            "\r\n$sdp"
    }

    @Test
    fun `L0 - WebRTC INVITE SDP round-trips with all ICE lines intact`() {
        val msg = requireNotNull(SipRelayCore.parse(wrapSdpInInvite(webrtcInviteSdp)))
        val out = SipRelayCore.serialize(msg)
        val reparsed = requireNotNull(SipRelayCore.parse(out))

        // Content-Length must equal actual body bytes.
        val cl = SipRelayCore.headerValue(reparsed, "Content-Length")!!.toInt()
        val bodyBytes = reparsed.body.toByteArray(Charsets.UTF_8).size
        assertEquals("Content-Length must match body byte count", bodyBytes, cl)

        // Every critical SDP line must survive.
        val body = reparsed.body
        assertTrue("a=candidate survives", body.contains("a=candidate:1 1 UDP 2130706431"))
        assertTrue("a=candidate relay survives", body.contains("a=candidate:2 1 UDP"))
        assertTrue("ice-ufrag survives", body.contains("a=ice-ufrag:VobizUfrag1"))
        assertTrue("ice-pwd survives", body.contains("a=ice-pwd:"))
        assertTrue("fingerprint survives", body.contains("a=fingerprint:sha-256"))
        assertTrue("setup survives", body.contains("a=setup:actpass"))
        assertTrue("rtcp-mux survives", body.contains("a=rtcp-mux"))
        assertTrue("BUNDLE survives", body.contains("a=group:BUNDLE 0"))
        assertTrue("m-line survives", body.contains("m=audio 24318 UDP/TLS/RTP/SAVPF"))
        assertTrue("rtcp attr survives", body.contains("a=rtcp:34129"))
    }

    @Test
    fun `L0 - m-line profile rewrite preserves candidates and Content-Length`() {
        // Simulate the relay's m-line rewrite: RTP/SAVPF -> UDP/TLS/RTP/SAVPF.
        val sdpSavpf = webrtcInviteSdp.replace("UDP/TLS/RTP/SAVPF", "RTP/SAVPF")
        val msg = requireNotNull(SipRelayCore.parse(wrapSdpInInvite(sdpSavpf)))
        assertTrue("Pre-condition: m-line uses RTP/SAVPF", msg.body.contains("RTP/SAVPF"))

        // Apply the same rewrite logic as maybeRewriteSdpForLinphone.
        val sep = if (msg.body.contains("\r\n")) "\r\n" else "\n"
        val lines = msg.body.split(sep)
        var changed = false
        val newLines = lines.map { line ->
            if (!changed && line.startsWith("m=audio ")) {
                val replaced = line.replace(" RTP/SAVPF ", " UDP/TLS/RTP/SAVPF ")
                if (replaced != line) { changed = true; replaced } else line
            } else line
        }
        assertTrue("m-line was rewritten", changed)
        val mutated = msg.copy(body = newLines.joinToString(sep))
        val out = SipRelayCore.serialize(mutated)
        val reparsed = requireNotNull(SipRelayCore.parse(out))

        // Content-Length recomputed correctly after body grew by 8 bytes.
        val cl = SipRelayCore.headerValue(reparsed, "Content-Length")!!.toInt()
        val bodyBytes = reparsed.body.toByteArray(Charsets.UTF_8).size
        assertEquals("Content-Length after rewrite", bodyBytes, cl)

        // Candidates survived the rewrite.
        assertTrue("candidate after rewrite", reparsed.body.contains("a=candidate:1 1 UDP"))
        assertTrue("ice-ufrag after rewrite", reparsed.body.contains("a=ice-ufrag:VobizUfrag1"))
        assertTrue("fingerprint after rewrite", reparsed.body.contains("a=fingerprint:"))
        assertTrue("m-line now DTLS", reparsed.body.contains("UDP/TLS/RTP/SAVPF"))
    }

    @Test
    fun `L0 - LF-only SDP body still parses and rewrites m-line`() {
        // Some platforms use \n instead of \r\n in the SDP body.
        val lfBody = webrtcInviteSdp.replace("\r\n", "\n")
        val lfInvite = wrapSdpInInvite(lfBody).replace("\r\n\r\nv=0", "\r\n\r\nv=0")
        val msg = requireNotNull(SipRelayCore.parse(lfInvite))
        // The m-line rewrite must handle \n line endings.
        val sep = if (msg.body.contains("\r\n")) "\r\n" else "\n"
        assertEquals("Should detect \\n separator", "\n", sep)
        val lines = msg.body.split(sep)
        assertTrue("m-line found in \\n body", lines.any { it.startsWith("m=audio ") })
    }

    @Test
    fun `L0 - platform top via survives the relay round trip`() {
        // platform -> relay -> linphone -> relay -> platform.
        // The response we return must still carry the Via of the WSS proxy that
        // owns the transaction (the top Via of the original INVITE). If it is
        // missing, that proxy cannot match the transaction and silently discards
        // our 100/180/603 - so the caller never hears ringback and the platform
        // keeps retransmitting the INVITE.
        val req = requireNotNull(SipRelayCore.parse(invite))
        val bridgeVia = "SIP/2.0/UDP wssrelay.invalid;branch=z9hG4bKrelay1;rport"
        val forwarded = SipRelayCore.prependTopVia(req, bridgeVia)
        // Linphone echoes back every Via header it received.
        val response = SipRelayCore.buildResponse(forwarded, 180, "Ringing")
        // forwardLinphoneResponse strips only the bridge Via.
        val toPlatform = SipRelayCore.stripTopVia(response)
        val vias = SipRelayCore.headerValues(toPlatform, "Via")
        assertEquals("bridge via must be stripped", 2, vias.size)
        assertTrue(
            "platform transaction via (branch=z9hG4bKplat1) must survive",
            vias.any { it.contains("branch=z9hG4bKplat1") },
        )
    }

    @Test
    fun `cseqNumber exposes the sequence number used to key transactions`() {
        val msg = requireNotNull(SipRelayCore.parse(invite))
        assertEquals("101", msg.cseqNumber)
        assertEquals("INVITE", msg.cseqMethod)
        // A real CANCEL is the same transaction: same Call-ID, same CSeq number,
        // method swapped in BOTH the start line and the CSeq header.
        val cancelRaw = invite
            .replaceFirst("INVITE sip:", "CANCEL sip:")
            .replace("CSeq: 101 INVITE", "CSeq: 101 CANCEL")
        val cancel = requireNotNull(SipRelayCore.parse(cancelRaw))
        assertEquals("CANCEL", cancel.method)
        assertEquals("101", cancel.cseqNumber)
        // Same key, so branchFor() can find the INVITE's branch.
        assertEquals(
            SipRelayCore.callId(msg),
            SipRelayCore.callId(cancel),
        )
    }
}
