import socket, random, re, time, sys, threading

LISTEN = ("0.0.0.0", 5060)
LOCAL = "10.0.2.2"          # what the emulator uses to reach this host
MEDIA_PORT = 40220
INVITE_AFTER = float(sys.argv[1]) if len(sys.argv) > 1 else 4.0
BUSY_AFTER_ACK = float(sys.argv[2]) if len(sys.argv) > 2 else 0.0

def log(m): print("[%s] %s" % (time.strftime("%H:%M:%S"), m), flush=True)

srv = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
srv.bind(LISTEN)
srv.settimeout(1.0)
log("local SIP server on udp/%d, invite after %.1fs" % (LISTEN[1], INVITE_AFTER))

rtp = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
rtp.bind(("0.0.0.0", MEDIA_PORT))
rtp.settimeout(0.5)

contact = {"addr": None, "uri": None}
state = {"registered": False, "answered": False, "bye": False, "stop": False,
         "invite_sent": False, "invite_time": 0.0}

def branch(): return "z9hG4bK" + "".join(random.choice("0123456789abcdef") for _ in range(16))

def sdp():
    return "\r\n".join([
        "v=0",
        "o=local 1 1 IN IP4 " + LOCAL,
        "s=LocalTest",
        "c=IN IP4 " + LOCAL,
        "t=0 0",
        "m=audio %d RTP/AVP 0 8 101" % MEDIA_PORT,
        "a=rtpmap:0 PCMU/8000",
        "a=rtpmap:8 PCMA/8000",
        "a=rtpmap:101 telephone-event/8000",
        "a=sendrecv",
        "",
    ])

def resp(req, code, reason, extra_lines=None, with_sdp=False, to_tag=None, contact=None):
    def g(name):
        m = re.search(r"^%s:\s*(.+)$" % name, req, re.I | re.M)
        return m.group(1).strip() if m else ""
    to = g("To")
    if code != 100 and "tag=" not in to:
        to += ";tag=" + (to_tag or "".join(random.choice("abcdef0123456789") for _ in range(10)))
    lines = ["SIP/2.0 %d %s" % (code, reason), "Via: " + g("Via"), "From: " + g("From"),
             "To: " + to, "Call-ID: " + g("Call-ID"), "CSeq: " + g("CSeq")]
    if contact:
        lines.append("Contact: " + contact)
    if extra_lines: lines += extra_lines
    body = sdp() if with_sdp else ""
    if with_sdp: lines.append("Content-Type: application/sdp")
    lines.append("Content-Length: %d" % len(body))
    return ("\r\n".join(lines) + "\r\n\r\n" + body).encode()

def handle(data, addr):
    text = data.decode(errors="replace")
    if not text.strip():
        return
    first = text.split("\r\n", 1)[0]
    log("<- %s (from %s:%d)" % (first, addr[0], addr[1]))
    if first.startswith("SIP/2.0"):
        code = first.split(" ")[1] if len(first.split(" ")) > 1 else ""
        if code == "200" and re.search(r"CSeq: \d+ INVITE", text, re.I):
            inv_text = state.get("invite_out_text", "")
            def inv_hdr(name, default):
                m = re.search(r"^%s:\s*(.+)$" % name, inv_text, re.I | re.M)
                return m.group(1).strip() if m else default
            our = state.get("invite_out", "")
            inv_to = inv_hdr("To", "<%s>" % our)
            if "tag=" not in inv_to:
                to_m = re.search(r"^To:.*?;tag=([^\r\n]+)", text, re.I | re.M)
                if to_m:
                    inv_to += ";tag=" + to_m.group(1).strip()
            ack = "\r\n".join([
                "ACK %s SIP/2.0" % our,
                "Via: SIP/2.0/UDP %s:%d;branch=%s;rport" % (LOCAL, LISTEN[1], branch()),
                "Max-Forwards: 70",
                "From: " + inv_hdr("From", "<sip:x>;tag=x"),
                "To: " + inv_to,
                "Call-ID: " + inv_hdr("Call-ID", "x"),
                "CSeq: 1 ACK",
                "Content-Length: 0",
                "",
            ]).encode()
            srv.sendto(ack, addr)
            state["ack"] = True
            state["ack_time"] = time.time()
            state["answered"] = True
            state["answered_addr"] = addr
            log("app ANSWERED our INVITE - sent ACK:\n" + ack.decode(errors="replace"))
        elif code.startswith("1"):
            pass
        else:
            log("our INVITE got final:\n" + text)
            inv_text = state.get("invite_out_text", "")
            def inv_h(name, default):
                m = re.search(r"^%s:\s*(.+)$" % name, inv_text, re.I | re.M)
                return m.group(1).strip() if m else default
            def resp_h(name, default=""):
                m = re.search(r"^%s:\s*(.+)$" % name, text, re.I | re.M)
                return m.group(1).strip() if m else default
            cseq = resp_h("CSeq")
            cseq_ack = re.sub(r"\s+INVITE\s*$", " ACK", cseq) if cseq else "1 ACK"
            ackf = "\r\n".join([
                "ACK %s SIP/2.0" % state.get("invite_out", ""),
                "Via: " + resp_h("Via"),
                "Max-Forwards: 70",
                "From: " + inv_h("From", "<sip:x>;tag=x"),
                "To: " + resp_h("To"),
                "Call-ID: " + inv_h("Call-ID", "x"),
                "CSeq: " + cseq_ack,
                "Content-Length: 0",
                "",
            ]).encode()
            srv.sendto(ackf, addr)
            log("ACKed non-2xx final (stops %s retransmissions)" % code)
        return
    method = first.split(" ")[0]

    if method == "REGISTER":
        cm = re.search(r"^Contact:\s*(.+)$", text, re.I | re.M)
        contact_uri = cm.group(1).strip() if cm else "sip:unknown@%s:%d" % (addr[0], addr[1])
        was = state["registered"]
        contact["addr"] = addr
        contact["uri"] = contact_uri
        state["registered"] = True
        state["reg_time"] = time.time()
        srv.sendto(resp(text, 200, "OK", extra_lines=[
            "Expires: 3600",
            "Contact: " + contact_uri + ";expires=3600",
        ]), addr)
        if not was:
            log("REGISTERED -> contact %s (via %s:%d)" % (contact_uri, addr[0], addr[1]))
    elif method == "INVITE":
        ct = "<sip:%s:%d>" % (LOCAL, LISTEN[1])
        if state["answered"]:
            # re-INVITE (hold / resume / session refresh) - answer it
            srv.sendto(resp(text, 100, "Trying"), addr)
            srv.sendto(resp(text, 200, "OK", with_sdp=True, contact=ct), addr)
            log("re-INVITE answered (hold/resume)")
            return
        htag = "h" + "".join(random.choice("abcdef0123456789") for _ in range(10))
        srv.sendto(resp(text, 100, "Trying"), addr)
        srv.sendto(resp(text, 180, "Ringing", to_tag=htag, contact=ct), addr)
        log("INVITE from app - answering (200 OK + SDP)")
        srv.sendto(resp(text, 200, "OK", with_sdp=True, to_tag=htag, contact=ct), addr)
        state["answered"] = True
        state["answered_req"] = text
        state["answered_addr"] = addr
    elif method == "ACK":
        if state["answered"] and not state.get("ack"):
            state["ack"] = True
            state["ack_time"] = time.time()
            log("ACK received - app call is ACTIVE")
    elif method == "REFER":
        log("REFER full:\n" + text)
        tm = re.search(r"Refer-To:\s*<?([^\s;>]+)", text, re.I)
        target = tm.group(1) if tm else "?"
        state["refer"] = target
        srv.sendto(resp(text, 202, "Accepted"), addr)
        log("REFER received -> transferring to %s" % target)
    elif method == "BYE":
        srv.sendto(resp(text, 200, "OK"), addr)
        log("BYE from app - call ended")
        state["bye"] = True
        state["stop"] = True
    elif method in ("OPTIONS", "INFO", "UPDATE", "NOTIFY"):
        srv.sendto(resp(text, 200, "OK"), addr)
    else:
        srv.sendto(resp(text, 405, "Method Not Allowed"), addr)

def send_invite(from_user="+15559876543"):
    addr = contact["addr"]
    if not addr:
        log("no contact yet - waiting")
        return
    from_uri = "sip:%s@%s" % (from_user, LOCAL)
    raw = contact["uri"] or "sip:test@%s" % LOCAL
    bm = re.search(r"<([^>]+)>", raw)
    to_uri = bm.group(1) if bm else raw
    um = re.match(r"sip:([^@;>]+)", to_uri)
    aor_user = um.group(1) if um else "test"
    msg = "\r\n".join([
        "INVITE %s SIP/2.0" % to_uri,
        "Via: SIP/2.0/UDP %s:%d;branch=%s;rport" % (LOCAL, LISTEN[1], branch()),
        "Max-Forwards: 70",
        "From: <%s>;tag=%s" % (from_uri, "l" + "".join(random.choice("abcdef0123456789") for _ in range(10))),
        "To: <sip:%s@%s>" % (aor_user, LOCAL),
        "Call-ID: %d@localserver" % random.randint(10**8, 10**9),
        "CSeq: 1 INVITE",
        "Contact: <sip:%s:%d>" % (LOCAL, LISTEN[1]),
        "Content-Type: application/sdp",
        "Content-Length: %d" % len(sdp()),
        "",
    ]) + "\r\n" + sdp()
    log("---- INVITE OUT ----\n" + msg + "-------------------")
    srv.sendto(msg.encode(), addr)
    state["invite_out"] = to_uri
    state["invite_out_text"] = msg
    state["invite_sent"] = True
    state["invite_time"] = time.time()
    log("INVITE sent to app at %s:%d" % addr)

def rtp_loop():
    seq, ts = 0, 0
    while not state["stop"]:
        if state.get("ack"):
            pkt = bytearray(12)
            pkt[0] = 0x80
            pkt[1] = 0
            pkt[2] = (seq >> 8) & 0xFF
            pkt[3] = seq & 0xFF
            pkt[4] = (ts >> 24) & 0xFF
            pkt[5] = (ts >> 16) & 0xFF
            pkt[6] = (ts >> 8) & 0xFF
            pkt[7] = ts & 0xFF
            peer = state.get("rtp_peer")
            if peer:
                try:
                    rtp.sendto(bytes(pkt) + b"\x7e" * 160, peer)
                except OSError:
                    pass
            seq += 1
            ts += 160
        try:
            _d, _a = rtp.recvfrom(4096)
            if state.get("rtp_peer") != _a:
                state["rtp_peer"] = _a
                log("RTP from app at %s:%d - replying" % _a)
            if len(_d) >= 13 and (_d[1] & 0x7f) == 101:
                log("RTP telephone-event: code=%d end=%s" % (_d[12], bool(_d[13] & 0x80) if len(_d) > 13 else False))
        except socket.timeout:
            pass
        except OSError:
            break
        time.sleep(0.005)

threading.Thread(target=rtp_loop, daemon=True).start()

deadline = time.time() + 900
while time.time() < deadline and not state["stop"]:
    if state["registered"] and not state["invite_sent"] and time.time() - state.get("reg_time", time.time()) >= INVITE_AFTER:
        send_invite()
    if BUSY_AFTER_ACK > 0 and state.get("ack") and not state.get("busy_sent") \
            and time.time() - state.get("ack_time", time.time()) >= BUSY_AFTER_ACK:
        state["busy_sent"] = True
        log("call active for %.1fs - sending second INVITE (busy test)" % BUSY_AFTER_ACK)
        send_invite(from_user="+15551234567")
    try:
        data, addr = srv.recvfrom(65535)
        handle(data, addr)
    except socket.timeout:
        pass
    except OSError:
        break

log("server finished: registered=%s invited=%s answered=%s ack=%s bye=%s busy_sent=%s refer=%s" % (
    state["registered"], state["invite_sent"], state["answered"], state.get("ack"), state["bye"],
    state.get("busy_sent"), state.get("refer")))
