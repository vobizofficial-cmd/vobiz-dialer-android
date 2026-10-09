# AI Handoff Guide

## Read This First

If you're an AI picking up this project, read these files in order:

1. `README.md` — index
2. `ARCHITECTURE.md` — system overview
3. `KNOWN_ISSUES.md` — current limitations
4. `DECISIONS.md` — why things are as they are
5. `INBOUND_FLOW.md` — inbound call path
6. `TROUBLESHOOTING.md` — when things break

## Critical Rules

1. **Never commit secrets** — no `google-services.json`, no `.env`, no service account keys
2. **Never log passwords or Auth Tokens**
3. **Never hardcode credentials** in the Android app
4. **Verify LibLinphone API against 5.5.x Javadoc** before using
5. **Do not remove `tools/e2e/` scripts** — they're the E2E test harness
6. **Do not touch R8/proguard or recording path arming**

## Current State

| Component | Status |
|-----------|--------|
| Outbound calls | ✅ Working |
| WSS registration | ✅ Working |
| Recording | ✅ Working |
| Caller ID selection | ✅ Working |
| Inbound via Conference + FCM | 🔄 In progress |
| Backend on Vercel | 🔄 In progress |

## What to Do Next

1. Deploy backend to Vercel
2. Test inbound call from real PSTN
3. Verify FCM wake-up when app is killed
4. Confirm two-way audio via conference

## Escalation

If blocked, produce `docs/VOBIZ_SUPPORT_TICKET.md` with:
- Auth ID (no token)
- Endpoint ID + DID + Application ID
- FCM logs
- CDR excerpts
- Exact question