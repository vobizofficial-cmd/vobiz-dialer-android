# Vobiz Dialer — Documentation Index

**Project:** Native Android SIP dialer for Vobiz telephony with inbound/outbound PSTN calling
**Repo:** D:\SohaiL\Vobiz\Dialer\vobiz-dialer
**Last Updated:** 2026-10-04

## Read in This Order

1. **`ARCHITECTURE.md`** — understand the system
2. **`INBOUND_FLOW.md`** — how PSTN calls reach the app
3. **`OUTBOUND_FLOW.md`** — how the app places calls
4. **`REGISTRATION.md`** — SIP registration and the WSS quirk
5. **`DASHBOARD_SETUP.md`** — Vobiz Console configuration
6. **`TROUBLESHOOTING.md`** — when things break
7. **`DECISIONS.md`** — why things are the way they are
10. **`KNOWN_ISSUES.md`** — current limitations
11. **`AI_HANDOFF.md`** — if you're an AI continuing this work

## Quick Reference

- Backend: Node.js + Express, deployed on Vercel
- Android: Kotlin + Jetpack Compose + LibLinphone
- Inbound: Conference + FCM (bypasses `sip_registered` limitation)
- Outbound: Direct SIP via trunk (`<trunk-id>.sip.vobiz.ai`)
- Recording: LibLinphone native recorder to `cacheDir`

## Never Do

- Never commit `google-services.json`, service account keys, or `.env`
- Never log passwords or Auth Tokens
- Never hardcode credentials in the app
- Never remove `tools/e2e/` scripts