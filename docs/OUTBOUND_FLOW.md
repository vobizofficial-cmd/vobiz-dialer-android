# Outbound Call Flow

## Overview

Outbound calls use the **SIP trunk directly**, not a Voice Application.

## Flow

```
User dials +919631465841
↓
SipCallController.placeCall() constructs INVITE:
sip:+919631465841@<trunk-id>.sip.vobiz.ai
From: <sip:+917965850027@<trunk-id>.sip.vobiz.ai>
↓
INVITE sent via WSS relay → Vobiz
↓
Vobiz trunk challenges with 401 → app responds with trunk credentials
↓
Vobiz routes to PSTN destination
↓
Audio flows (DTLS-SRTP)
```

## Key Facts

- **Trunk domain:** `<trunk-id>.sip.vobiz.ai` (e.g., `08ecd76e.sip.vobiz.ai`)
- **Auth:** `dialer_trunk_auth` / `<trunk-password>` (trunk credentials)
- **Caller ID:** Set via `setFromHeader()` on `CallParams`
- **Recording:** Armed via `params.setRecordFile()` before `invite`

## Why Outbound Works But Inbound Doesn't

| Direction | Routing Path | Registration Needed? |
|-----------|-------------|---------------------|
| **Outbound** | Direct INVITE to trunk | No |
| **Inbound** | Voice App checks `sip_registered` | Yes → broken for WSS |

## Troubleshooting

See `TROUBLESHOOTING.md` for common outbound failures.