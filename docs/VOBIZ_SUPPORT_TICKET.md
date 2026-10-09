# Vobiz Support Ticket: PSTN→DID Inbound Call Fails with "Endpoint Not Registered" (2020)

## Summary
PSTN calls to DID `+917965850027` reach the Android app (INVITE delivered via WSS relay, 180 Ringing sent, `IncomingReceived` fires), but the platform's Dial logic hangs up after ~10 seconds with **hangup cause 2020 "Endpoint Not Registered"** because the API field `sip_registered` remains `"false"` despite successful SIP registration.

## Account Details
- **Auth ID**: `MA_4272LINL`
- **Endpoint ID**: `269321988178847`
- **Username**: `sairam8391265128911238046`
- **SIP URI**: `sip:sairam8391265128911238046@registrar.vobiz.ai`
- **DID**: `+917965850027`
- **Application ID**: `85076776948601220` (Vobiz WebRTC Playground, `default_app: true`)

## Call Flow (Working Parts)
1. PSTN → DID `+917965850027`
2. Platform routes to Voice Application `85076776948601220`
3. Application `answer_url` returns `<Dial dialMusic="real" timeout="45"><User>sip:user@registrar.vobiz.ai</User></Dial>`
4. Platform sends INVITE to endpoint via WSS (`wss://registrar.vobiz.ai:5063/`)
5. **In-app WSS relay** bridges to Linphone loopback UDP (port 5099)
6. App receives INVITE, SDP munged (`RTP/SAVPF` → `UDP/TLS/RTP/SAVPF` for DTLS)
7. App sends `180 Ringing` → `IncomingReceived` fires → UI notification shows
8. Caller hears ringback (`dialMusic="real"` works ✅)

## Failure Point
9. Platform's Dial logic checks `GET /Endpoint/{id}/` → `sip_registered: "false"`
10. After ~10 seconds, Dial logic sends BYE with **hangup cause 2020 "Endpoint Not Registered"**
11. Call never reaches `200 OK` / connected state

## Evidence

### CDR Excerpts (Recent Failed Calls)
| Call UUID | Direction | Hangup Cause | Code | Duration | Disposition |
|-----------|-----------|--------------|------|----------|-------------|
| `b7540694-d2a5-4edf-9f91-2d6f6adca800` | inbound | Endpoint Not Registered | 2020 | 0s | recv_refuse |
| `c04ce135-44fa-40b9-a834-0ad3e55994cb` | inbound | Endpoint Not Registered | 2020 | 0s | recv_refuse |

### Dial Events (webhook.site)
**DialAction (ringing):**
```json
{
  "Event": "DialAction",
  "DialStatus": "completed",
  "DialRingStatus": true,
  "CallStatus": "ringing"
}
```

**DialHangup (10s later):**
```json
{
  "Event": "DialHangup",
  "DialAction": "hangup",
  "DialBLegHangupCause": "NORMAL_CLEARING",
  "DialBLegHangupCauseCode": "2020",
  "DialBLegHangupCauseName": "Endpoint Not Registered",
  "DialBLegHangupSource": "Vobiz",
  "DialBLegTo": "sip:sairam8391265128911238046@registrar.vobiz.ai",
  "StartTime": "2026-10-04 00:23:42",
  "EndTime": "2026-10-04 00:23:52"
}
```

### Endpoint API State (Persistent)
```json
{
  "alias": "VobizDialer",
  "application": "/v1/Account/MA_4272LINL/Application/85076776948601220/",
  "endpoint_id": "269321988178847",
  "sip_registered": "false",
  "sip_uri": "sip:sairam8391265128911238046@registrar.vobiz.ai",
  "username": "sairam8391265128911238046"
}
```
*Note: `sip_registration` object absent (per docs, only present when `sip_registered: "true"`)*

### SIP REGISTER Traces
**WSS Registration (port 5063) - Works for INVITE delivery:**
```
REGISTER sip:registrar.vobiz.ai SIP/2.0
Via: SIP/2.0/WSS registrar.vobiz.ai;branch=z9hG4bK...
Contact: <sip:sairam8391265128911238046@registrar.vobiz.ai;transport=ws>
```
Response: `200 OK`, `contact=sip:user@37.19.221.201:63278;transport=ws`, `expires=300s`

**UDP Registration (port 5060) - 200 OK but flag unchanged:**
```
Register refresher [200] reason [OK] for proxy [<sip:registrar.vobiz.ai:5060;transport=udp>]
```
Platform API still shows `sip_registered: "false"`

## Root Cause Analysis
The Vobiz platform maintains two separate registration tracking systems:
1. **SIP Registrar (WSS port 5063)**: Accepts WebSocket registrations, delivers INVITEs correctly, but **does not update** `sip_registered` flag
2. **Registration Tracking API (`sip_registered` field)**: Only updated by UDP/TCP registrations to the standard registrar (port 5060/5061), which has a "broken location store" for inbound delivery per platform admission

The Dial logic (`<Dial><User>endpoint</User></Dial>`) checks `sip_registered` before connecting the B-leg. Since WSS registration doesn't update this flag, the Dial logic rejects the call with 2020 after a ~10s timeout.

## Attempted Fixes
| Fix Attempted | Result |
|---------------|--------|
| WSS relay registration (port 5063) | INVITE delivery works ✅, but `sip_registered` stays false ❌ |
| Contact header fix (use `@registrar.vobiz.ai`) | REGISTER 200 OK ✅, flag unchanged ❌ |
| Parallel UDP registration (port 5060) | REGISTER 200 OK ✅, flag unchanged ❌ |
| `dialMusic="real"` + timeout 45s | Ringback works ✅, but Dial logic kills at 10s ❌ |

## Request
**Please fix the platform so that WSS/WebSocket registrations update the `sip_registered` flag**, or provide an API to manually set the registration status for endpoints that register via WSS.

Alternative: Document that Voice Applications with `<Dial><User>` require UDP/TCP registration, and WSS-only endpoints cannot be used as Dial destinations. Provide guidance on using Inbound Trunks for WSS/WebRTC endpoints.

## Questions for Vobiz
1. Is `sip_registered: "false"` for WSS-registered endpoints a known limitation?
2. Does the UDP registrar on port 5060 actually store registrations, or is its location store completely non-functional?
3. Can we use an Inbound Trunk with Primary URI `sip:user@registrar.vobiz.ai` to bypass the endpoint registration check?
4. Is there an API to create/link Inbound Trunks programmatically?
5. What is the expected registration flow for WebRTC/Android endpoints using the Linphone SDK?

## Test Call IDs (Last 3)
1. `b7540694-d2a5-4edf-9f91-2d6f6adca800` (2026-10-04 00:23:42 UTC)
2. `c04ce135-44fa-40b9-a834-0ad3e55994cb` (2026-10-04 00:13:42 UTC)
3. `110de943-af0f-4896-8696-08ff0096c2b4` (2026-10-03 22:37:17 UTC)

## App Configuration
- Linphone SDK: 5.5.24
- Transport: WSS relay (port 5063) + loopback UDP (port 5099)
- Media: DTLS-SRTP (`a=fingerprint`, `a=setup:actpass`), AVPF enabled
- SDP munging: `RTP/SAVPF` → `UDP/TLS/RTP/SAVPF` when `a=fingerprint` present