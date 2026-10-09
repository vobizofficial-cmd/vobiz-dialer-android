# Architectural Decisions

## ADR-001: Use WSS Relay Instead of Native UDP

**Decision:** Route SIP through a loopback UDP ↔ WSS relay.

**Rationale:** Vobiz's WebRTC endpoints require WSS. Native UDP registration succeeds locally but doesn't produce `sip_registered: true`.

**Consequences:**
- ✅ INVITE delivery works
- ✅ DTLS negotiation works
- ❌ `sip_registered` flag stays false (platform quirk)
- ❌ Inbound via Voice App fails

## ADR-002: Conference + FCM for Inbound

**Decision:** Park inbound calls in a Vobiz conference, wake app via FCM.

**Rationale:** Voice Application `<Dial><User>` rejects WSS endpoints. Inbound Trunks don't support WSS. Conference + FCM bypasses registration entirely.

**Consequences:**
- ✅ Works when app is killed
- ✅ Works across all OEMs
- ❌ Requires backend (Node.js on Vercel)
- ❌ Requires Firebase project

## ADR-003: Node.js Backend on Vercel

**Decision:** Backend in JavaScript (Node.js 20), Express framework, deployed on Vercel.

**Rationale:** Official Vobiz SDK is JavaScript. Firebase Admin SDK is native JavaScript. Vercel free tier covers usage.

**Consequences:**
- ✅ Fast cold start
- ✅ Zero-config deployment
- ✅ 3 dependencies total

## ADR-003: LibLinphone over PJSIP

**Decision:** Use LibLinphone 5.5.24 instead of compiling PJSIP.

**Rationale:** Maven dependency, Kotlin API, no NDK compilation.

**Consequences:**
- ✅ Fast iteration
- ❌ API version mismatch risk (must verify against 5.5.x Javadoc)