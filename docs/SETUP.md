# Complete Setup Guide — Vobiz Dialer

This guide documents the **entire end-to-end setup** for the Vobiz Dialer project, so any developer (or AI) can reproduce, debug, and maintain the system.

---

## Table of Contents

1. [System Overview](#system-overview)
2. [Prerequisites](#prerequisites)
3. [Vobiz Dashboard Configuration](#vobiz-dashboard-configuration)
4. [Backend Deployment (Railway)](#backend-deployment-railway)
5. [Android App Build & Run](#android-app-build--run)
6. [Testing & Verification](#testing--verification)
7. [Known Issues](#known-issues)
8. [Troubleshooting](#troubleshooting)
9. [Architecture Reference](#architecture-reference)

---

## System Overview

```
┌─────────────┐     ┌──────────────────┐     ┌─────────────────────────┐
│   PSTN      │────▶│  Vobiz Platform  │────▶│   Android App           │
│  Caller     │     │  (SIP Registrar, │     │   (LibLinphone + WSS    │
│  +91xxxx... │     │   Voice Apps,    │     │   Relay)                │
└─────────────┘     │   Trunks)        │     └─────────────────────────┘
                    └────────┬─────────┘
                             │ HTTPS Webhooks
                             ▼
                    ┌─────────────────────────┐
                    │   Backend (Node.js)     │
                    │   Railway: vobiz-       │
                    │   dialer-backend.up.    │
                    │   railway.app           │
                    └─────────────────────────┘
```

**Call Flow (Inbound)**:
1. PSTN caller dials `+917965850027`
2. Vobiz routes to Voice Application → `POST /answer`
3. Backend returns `<Dial><User>sip:endpoint@registrar.vobiz.ai</User></Dial>`
4. Platform sends SIP INVITE to registrar
5. App's WSS Relay receives INVITE via `wss://registrar.vobiz.ai:5063/`
6. Relay forwards to Linphone on `127.0.0.1:5099` (UDP)
7. App rings → User answers → DTLS-SRTP media

**Call Flow (Outbound)**:
1. User dials in app
2. App sends INVITE via WSS Relay → Outbound Trunk (`08ecd76e.sip.vobiz.ai`)
3. Trunk challenges 401 → App responds with trunk credentials
4. Vobiz routes to PSTN → Call connected

---

## Prerequisites

### Accounts & Credentials
- **Vobiz Account**: Console access at https://console.vobiz.ai
- **Firebase Project**: For FCM push notifications (project: `vobiz-fire`)
- **GitHub Account**: For repos (org: `vobizofficial-cmd`)
- **Railway Account**: For backend hosting

### Local Development
- **Android Studio** (latest, with JBR JDK 17)
- **Android SDK** (API 31+)
- **Node.js 20+** (for backend)
- **PowerShell 7+** / Bash (for scripts)
- **adb** (Android Debug Bridge)

### Repositories
```bash
# Android App
git clone https://github.com/vobizofficial-cmd/vobiz-dialer-android.git

# Backend
git clone https://github.com/vobizofficial-cmd/vobiz-dialer-backend.git
```

---

## Vobiz Dashboard Configuration

### 1. API Credentials
**Console → Account → API Credentials**
```
Auth ID:     MA_4272LINL
Auth Token:  (store securely, add to Railway env)
```

### 2. Phone Number (DID)
**Voice → Phone Numbers**
- Number: `+917965850027`
- Voice Enabled: ✅ **ON**
- Attached to Application: **Vobiz WebRTC Playground** (NOT to trunk)

### 3. Voice Application
**Voice → Applications**
- Name: `Vobiz WebRTC Playground`
- App ID: `85076776948601220`
- Answer URL: `https://vobiz-dialer-backend.up.railway.app/answer`
- Answer Method: `POST`
- Hangup URL: `https://vobiz-dialer-backend.up.railway.app/hangup`
- Public URI: ✅ **Enabled**
- Attached Numbers: `+917965850027`

### 4. SIP Endpoint
**Voice → Endpoints**
- Alias: `VobizDialer`
- Username: `sairam8391265128911238046` (auto-managed by app)
- Password: **Auto-rotated by app on each login** (24-char random)
- Allow Voice: ✅ **Yes**
- Application: Linked to `Vobiz WebRTC Playground`

### 5. Outbound Trunk
**SIP Trunks → Outbound Trunks**
- Name: `VobizDialer-Outbound`
- Trunk ID: `08ecd76e-75aa-460f-bed0-3228030070fe`
- Direction: `outbound`
- SIP Domain: `08ecd76e.sip.vobiz.ai` (auto-generated)
- **Credential**: `dialer_trunk_auth` / `omsairamji`

### 6. Trunk Credential
**SIP Trunks → Credentials**
- Username: `dialer_trunk_auth`
- Password: `omsairamji`
- Realm: `MA_4272LINL.sip.vobiz.ai`

---

## Backend Deployment (Railway)

### 1. Create Railway Project
- Go to https://railway.app
- New Project → Deploy from GitHub repo
- Select `vobizofficial-cmd/vobiz-dialer-backend`

### 2. Environment Variables
Add in Railway Dashboard → Variables:
```
FIREBASE_SERVICE_ACCOUNT=<full JSON from Firebase Console → Project Settings → Service Accounts → Generate New Private Key>
VOBIZ_AUTH_ID=MA_4272LINL
VOBIZ_AUTH_TOKEN=<your Vobiz API token>
BACKEND_URL=https://vobiz-dialer-backend.up.railway.app
ANSWER_MODE=dial
PORT=3000
REDIS_URL=<optional - e.g., redis://default:pass@host:port>
```

### 3. Deploy & Verify
- Railway auto-deploys on push to `main`
- Verify: `curl https://vobiz-dialer-backend.up.railway.app/health`

Expected response:
```json
{
  "status": "ok",
  "devices": 0,
  "firebase": true,
  "project": "vobiz-fire",
  "mode": "dial",
  "redis": "connected",
  "startedAt": "2026-10-09T18:22:47.000Z"
}
```

### 4. Test Endpoints
```bash
# Test Answer webhook
curl -X POST https://vobiz-dialer-backend.up.railway.app/answer \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "CallUUID=test&From=+919876543210&To=+917965850027&Direction=inbound"

# Test Probe
curl -X POST https://vobiz-dialer-backend.up.railway.app/probe-answer \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "CallUUID=probe&From=+919876543210&To=+917965850027"
```

---

## Android App Build & Run

### 1. Open in Android Studio
- File → Open → Select `vobiz-dialer-android` folder
- Let Gradle sync complete

### 2. Build Debug APK
```powershell
$env:JAVA_HOME="$env:LOCALAPPDATA\Programs\Android Studio\jbr"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat assembleFossDebug --console=plain
```

### 3. Install on Device/Emulator
```powershell
adb install -r app/build/outputs/apk/foss/debug/VobizDialer-2.4.5-foss-x86_64.apk
```

### 4. Run & Login
- Open app on device
- Login with Vobiz credentials:
  - Email: `martandinfa01@gmail.com`
  - Password: `Omsairamji@321`
- App auto-provisions:
  - Rotates endpoint password
  - Links endpoint to Voice App
  - Attaches DID to Voice App
  - Registers FCM token with backend
- Verify: Logs show `Provisioning complete: endpoint=..., trunk=..., app=...`
- Verify: `VobizRegistrationState.Registered` in UI

---

## Testing & Verification

### 1. Backend Health
```bash
curl https://vobiz-dialer-backend.up.railway.app/health
# Check: firebase=true, mode=dial, redis=connected
```

### 2. Outbound Call
- Open app dialpad
- Dial any valid number (e.g., `+919876543210`)
- Verify: Call connects, two-way audio works

### 3. Inbound Call
- From another phone, call `+917965850027`
- Verify: App rings (IncomingCallRinger: loudspeaker + vibration)
- Verify: User can answer → Two-way audio
- **KNOWN ISSUE**: Call disconnects at ~32 seconds

### 4. FCM Token Registration
```bash
curl https://vobiz-dialer-backend.up.railway.app/health
# Check: "devices" > 0 after app login
```

### 5. Dial Logs
```bash
curl https://vobiz-dialer-backend.up.railway.app/dial-log
# Shows recent call events with timestamps
```

---

## Known Issues

### 1. Inbound Call Disconnects at ~32 Seconds ⚠️ CRITICAL
**Status**: **UNRESOLVED** — Blocking production use for inbound

**Symptoms**:
- Inbound call connects successfully
- Two-way audio works initially
- Call ends automatically at ~32 seconds (no user action)
- No error in app UI; call just drops

**Evidence from Logs**:
```
MEDIA[t+5s]  dir=incoming state=StreamsRunning rtpRecv=... rtpSent=... ice=Connected srtp=DTLS ...
MEDIA[t+30s] dir=incoming state=StreamsRunning rtpRecv=... rtpSent=... ice=Connected srtp=DTLS ...
[Call ends - no media stats after ~32s]
```

**Root Cause Hypotheses**:
1. **DTLS Renegotiation Failure**: Platform sends re-INVITE with new SDP at ~30s; WSS relay doesn't handle SDP update correctly
2. **Media Timeout**: Platform's media timer fires (no RTP received) despite stats showing RTP flowing
3. **ICE Restart**: Network change triggers ICE restart that fails
4. **WSS Relay Branch Handling**: Re-INVITE branch mapping fails in `forwardedInviteBranches`

**Files to Investigate**:
| File | Function | Line |
|------|----------|------|
| `WssRelay.kt` | `rewriteSetupAttribute()` | ~719 |
| `WssRelay.kt` | `maybeRewriteSdpForLinphone()` | ~519 |
| `WssRelay.kt` | `deliverToLinphone()` / `forwardLinphoneRequest()` | ~558/~618 |
| `LinphoneService.kt` | Media encryption config | 418-435 |
| `SipRelayCore.kt` | SDP parsing | — |

**Workarounds Attempted**:
- ✅ `core.setMediaEncryptionMandatory(false)` — allows initial DTLS handshake
- ✅ Answer `a=setup` rewrite to `passive` (RFC 4145)
- ✅ ICE + STUN enabled (`stun.l.google.com:19302`)
- ✅ `inc_timeout = 120s` (registration expiry)
- ✅ AVPF mode enabled
- ❌ Media still times out at ~32s

**Debugging Commands**:
```bash
# Capture SIP trace
adb logcat -s WssRelay,SipRelay,VobizSip,VobizLP -v time > sip_trace.log

# Look for at ~30s mark:
# - Re-INVITE from platform
# - SDP changes (new fingerprint, ICE ufrag/pwd)
# - Branch mapping in forwardedInviteBranches
# - DTLS renegotiation attempt
```

**Vobiz Support Ticket**: See `docs/VOBIZ_SUPPORT_TICKET.md`

### 2. `sip_registered` = `false` in Dashboard
**Status**: **EXPECTED BEHAVIOR** — Not a bug

**Explanation**: Vobiz platform's location store only tracks UDP/TLS registrations. WSS registrations (port 5063) don't update the `sip_registered` flag. Inbound calls **still work** because the WSS relay maintains the binding.

**Evidence**: 14:46 test call proved inbound works despite `sip_registered=false`.

### 3. Multiple Endpoints Linked to Same App
**Status**: **COSMETIC** — App uses first match

Dashboard shows 4 endpoints linked to App `85076776948601220`:
1. `sairam8391265128911238046` (VobizDialer) — **Used by app**
2. `vobizdialer2962331181284600730` (VobizDialer-Forced)
3. `agent38595212724972894` (WebDialer-Agent1)
4. `play386289296770860326881349` (Vobiz WebRTC Playground Endpoint)

App's `VobizProvisioner` finds endpoint by Answer URL `endpoint=` parameter → matches `sairam8391265128911238046`.

---

## Troubleshooting

### App Won't Register (WSS Relay Fails)
**Check**:
1. Network: `wss://registrar.vobiz.ai:5063` reachable?
2. Credentials: Endpoint password matches what app set?
3. Logs: `WssRelay` → `REGISTER challenge`, `REGISTER ok`

**Fix**: Logout/login in app → triggers fresh provisioning + password rotation.

### Outbound Call Fails (401/403)
**Check**:
1. Trunk credential `dialer_trunk_auth` exists in dashboard
2. Outbound trunk `08ecd76e.sip.vobiz.ai` is active
3. `OutboundAuth` resolves correct trunk credentials

**Fix**: Verify trunk credential password in dashboard matches `omsairamji`.

### No Audio (One-Way)
**Check**:
1. UDP ports 10000-20000 open on device/network
2. STUN working: `stun.l.google.com:19302`
3. ICE candidates gathered (check media stats: `ice=Connected`)

### Backend Returns Wrong Callback URLs
**Check**: `BACKEND_URL` env var in Railway matches deployed URL.

**Fix**: Update Railway variable → Redeploy.

### FCM Not Working
**Check**:
1. `google-services.json` in `app/` (for FCM)
2. `FIREBASE_SERVICE_ACCOUNT` in Railway has correct project (`vobiz-fire`)
3. App registers token: `TokenRegistrar.registerAsync()` called on login

---

## Architecture Reference

### Key Files (Android)

| File | Responsibility |
|------|----------------|
| `VobizProvisioner.kt` | Full auto-provisioning: login → numbers → app → endpoint → trunk |
| `LinphoneService.kt` | LibLinphone core lifecycle, WSS relay integration, media config |
| `WssRelay.kt` | WSS↔UDP bridge, SIP rewriting, registration keepalive |
| `SipRelayCore.kt` | SIP message parsing, header manipulation, SDP munging |
| `OutboundAuth.kt` | Maps From header → trunk credentials |
| `SipCallController.kt` | Call state machine, notifications, recording |
| `CredentialStore.kt` | Encrypted credential persistence |
| `VobizApi.kt` | Vobiz REST API client |
| `VobizRegistrationState.kt` | UI-facing registration state |

### Key Files (Backend)

| File | Responsibility |
|------|----------------|
| `index.js` | Express server, all webhook endpoints |
| `providers.mjs` | (If exists) Provider configurations |

### SIP Parameters

| Parameter | Value | Purpose |
|-----------|-------|---------|
| WSS Registrar | `wss://registrar.vobiz.ai:5063/` | SIP over WebSocket |
| Relay Loopback | `127.0.0.1:5099` (UDP) | Linphone ↔ Relay |
| Registrar Expires | `300s` (platform) / `120s` (app) | Registration refresh |
| Keepalive | `30s` (OPTIONS) | WSS connection health |
| ICE/STUN | `stun.l.google.com:19302` | NAT traversal |
| Media Encryption | DTLS-SRTP (mandatory=false) | Secure audio |
| AVPF | Enabled | RTP/SAVPF profile |

---

## Maintenance Checklist

### Weekly
- [ ] Check Railway backend logs for errors
- [ ] Verify `/health` returns `firebase:true, redis:connected`
- [ ] Monitor `/dial-log` for failed calls

### Monthly
- [ ] Rotate Vobiz Auth Token (Console → API Credentials → Regenerate)
- [ ] Update Railway `VOBIZ_AUTH_TOKEN` → Redeploy
- [ ] Check Firebase project quota (FCM)

### On Inbound Failure
1. Capture `adb logcat -s WssRelay,SipRelay,VobizSip -v time`
2. Check `/dial-log` for hangup cause
3. Review `docs/VOBIZ_SUPPORT_TICKET.md` for platform escalation

---

## Emergency Contacts

- **Vobiz Support**: support@vobiz.ai (reference account `MA_4272LINL`)
- **Railway Status**: https://status.railway.app
- **Firebase Console**: https://console.firebase.google.com/project/vobiz-fire

---

## Version History

| Date | Version | Changes |
|------|---------|---------|
| 2026-10-10 | 2.4.5 | Backend URL updated to `vobiz-dialer-backend.up.railway.app`; callback URLs fixed |
| 2026-10-09 | 2.4.5 | Initial push to GitHub; WSS relay architecture |

---

*Last updated: 2026-10-10*
*Maintainer: Vobiz Team*