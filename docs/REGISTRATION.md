# SIP Registration

## Current Setup

- **Transport:** WSS (WebSocket Secure)
- **Registrar:** `wss://registrar.vobiz.ai:5063/`
- **Endpoint:** `sairam8391265128911238046`
- **Password:** Stored in `CredentialStore` (encrypted)

## The `sip_registered` Quirk (Critical)

**Problem:** Vobiz's API reports `sip_registered: "false"` for endpoints that register via WSS, even though the REGISTER succeeds (200 OK).

**Evidence:**
- App Logcat: `Registration state: Ok`
- Vobiz API: `sip_registered: "false"`
- Voice Application `<Dial><User>` rejects because of this flag

**Impact:**
- Inbound via Voice Application → fails
- Outbound via trunk → works (doesn't check this flag)

**Workaround:** Conference + FCM inbound (see `INBOUND_FLOW.md`)

## WSS Relay Architecture

```
LibLinphone (loopback UDP 127.0.0.1:5099)
↓
WssRelay.kt (bidirectional bridge)
↓
WSS WebSocket (wss://registrar.vobiz.ai:5063)
↓
Vobiz Registrar
```

**Why the relay exists:** LibLinphone doesn't support WSS natively — it needs UDP/TCP/TLS. The relay translates between UDP (local) and WSS (remote).

## SDP Munging

Vobiz sends WebRTC-style SDP with `RTP/SAVPF` profile and `a=fingerprint` but no `a=crypto`. LibLinphone 5.5.24 rejects this with 488.

**Fix:** `WssRelay.maybeRewriteSdpForLinphone()` rewrites:
- `RTP/SAVPF` → `UDP/TLS/RTP/SAVPF`

This makes LibLinphone classify the offer as DTLS (not SDES).

## Registration Parameters

| Parameter | Value | Reason |
|-----------|-------|--------|
| `registerEnabled` | true | Keep registration alive |
| `refreshInterval` | 120 | Refresh before NAT expiry |
| `keepAliveInterval` | 15 | CRLF keep-alive |
| `expires` | 120 | Registration TTL |