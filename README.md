# Vobiz Dialer

Vobiz SIP dialer for internal use — PSTN → DID inbound with WebRTC media via WSS relay.
**Internal use only — not published to Google Play.**

## Status

- **Inbound breakthrough**: WSS relay + SDP munging delivers INVITE, app rings, DTLS initialized ✅
- **Caller hears ringback**: `dialMusic="real"` works ✅
- **Registration quirk**: `sip_registered` stays `"false"` for WSS (platform limitation) — does not affect inbound
- **Known Issue**: Inbound calls disconnect at ~32 seconds (media timeout / DTLS renegotiation issue)
- **Escalation**: See [docs/VOBIZ_SUPPORT_TICKET.md](docs/VOBIZ_SUPPORT_TICKET.md)

## Quick Start

### Prerequisites
- Android Studio (with bundled JDK 17 / JBR)
- Android SDK (API 31+)
- Emulator or physical device (Android 12+)

### Build & Run (Debug)
```powershell
$env:JAVA_HOME="$env:LOCALAPPDATA\Programs\Android Studio\jbr"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat assembleFossDebug --console=plain
```

### Install on Device
```powershell
adb install -r app/build/outputs/apk/foss/debug/VobizDialer-2.4.5-foss-x86_64.apk
```

Package: `it.stivy.rivo.personal.debug` (installs beside any existing Vobiz build)

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         ANDROID APP (Kotlin + LibLinphone)                  │
│  ┌──────────────┐  ┌──────────────────┐  ┌──────────────────────────────┐  │
│  │ Dialpad/Logs │  │ SipCallController│  │ LinphoneService (Foreground) │  │
│  └──────────────┘  └──────────────────┘  └──────────────────────────────┘  │
│                          │                          │                       │
│                          ▼                          ▼                       │
│                   ┌──────────────┐         ┌────────────────────────┐      │
│                   │ OutboundAuth │         │ WssRelay (127.0.0.1:5099)│      │
│                   │ Call Recording        │ WSS → UDP Bridge         │      │
│                   └──────────────┘         └────────────────────────┘      │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                    WSS (wss://registrar.vobiz.ai:5063/)
                                    │
┌─────────────────────────────────────────────────────────────────────────────┐
│                           VOBIZ PLATFORM                                     │
│  ┌──────────────┐  ┌──────────────────┐  ┌──────────────────────────────┐  │
│  │ Voice App    │  │ SIP Registrar    │  │ Outbound Trunk               │  │
│  │ (Answer URL) │◀─│ wss://:5063      │  │ 08ecd76e.sip.vobiz.ai        │  │
│  └──────────────┘  └──────────────────┘  └──────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                    HTTPS (Webhooks)
                                    │
┌─────────────────────────────────────────────────────────────────────────────┐
│                         BACKEND (Node.js on Railway)                        │
│  ┌──────────────┐  ┌──────────────────┐  ┌──────────────────────────────┐  │
│  │ /answer      │  │ /register-token  │  │ /dial-status, /dial-callback │  │
│  │ Returns      │  │ Stores FCM       │  │ Logs call events             │  │
│  │ <Dial> XML   │  │ tokens           │  │                              │  │
│  └──────────────┘  └──────────────────┘  └──────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Key Components

| File | Purpose |
|------|---------|
| `WssRelay.kt` | WSS bridge (port 5063) → loopback UDP (5099), SDP munging for DTLS |
| `LinphoneService.kt` | SIP core lifecycle, dual proxy (WSS + UDP), DTLS media, AVPF |
| `SipRelayCore.kt` | SIP parsing, REGISTER Contact fix, header rewriting |
| `VobizProvisioner.kt` | Auth, password rotation, endpoint/trunk/app linking |
| `OutboundAuth.kt` | Maps From header → trunk credentials for outbound INVITE |
| `VobizApi.kt` | REST client for Vobiz API (endpoints, trunks, apps, numbers) |
| `CredentialStore.kt` | EncryptedSharedPreferences for SIP credentials |
| `SipCallController.kt` | Call state machine, notifications, recording |
| `IncomingCallRinger.kt` | Loudspeaker ringtone + continuous vibration |
| `TokenRegistrar.kt` | Registers FCM token with backend |

## Call Flows

### Inbound Call (PSTN → App)
```
1. Caller dials +917965850027
2. Vobiz routes to Voice App → POST /answer (backend)
3. Backend returns <Dial><User>sip:sairam...@registrar.vobiz.ai</User></Dial>
4. Platform sends SIP INVITE to registrar.vobiz.ai
5. WSS Relay receives INVITE via wss://registrar.vobiz.ai:5063/
6. Relay forwards to Linphone via 127.0.0.1:5099 (UDP)
7. LinphoneService → SipCallController → IncomingCallRinger → UI
8. User answers → DTLS-SRTP media established
```

### Outbound Call (App → PSTN)
```
1. User dials number in app
2. SipCallController.placeCall() creates INVITE
3. OutboundAuth resolves From header → trunk credentials
4. INVITE sent via WSS Relay → Vobiz trunk (08ecd76e.sip.vobiz.ai)
5. Trunk challenges 401 → app responds with digest auth
6. Vobiz routes to PSTN → call connected
7. DTLS-SRTP media flows
```

## Vobiz Dashboard Configuration

### Required Resources (Pre-configured)

| Resource | Value | Dashboard Location |
|----------|-------|-------------------|
| **Auth ID** | `MA_4272LINL` | Account → API Credentials |
| **Auth Token** | (in Railway env) | Account → API Credentials |
| **DID** | `+917965850027` | Voice → Phone Numbers |
| **Voice App** | `Vobiz WebRTC Playground` (ID: `85076776948601220`) | Voice → Applications |
| **SIP Endpoint** | `sairam8391265128911238046` (ID: `269321988178847`) | Voice → Endpoints |
| **Outbound Trunk** | `08ecd76e.sip.vobiz.ai` (ID: `08ecd76e-...`) | SIP Trunks → Outbound Trunks |
| **Trunk Credential** | `dialer_trunk_auth` / `omsairamji` | SIP Trunks → Credentials |

### Voice Application Settings
```
Answer URL:  https://vobiz-dialer-backend.up.railway.app/answer
Answer Method: POST
Hangup URL:  https://vobiz-dialer-backend.up.railway.app/hangup
Public URI:  Enabled
Attached Numbers: +917965850027 (must be attached here, NOT to trunk)
```

### SIP Endpoint Settings
```
Alias: VobizDialer
Username: sairam8391265128911238046 (auto-managed)
Password: Auto-rotated by app on each login (24-char random)
Allow Voice: Yes
Application: Linked to "Vobiz WebRTC Playground"
```

### Outbound Trunk Settings
```
Name: VobizDialer-Outbound
Direction: outbound
SIP Domain: 08ecd76e.sip.vobiz.ai (auto-generated)
Credential: dialer_trunk_auth (username) / omsairamji (password)
```

## Backend Configuration (Railway)

### Environment Variables
| Variable | Required | Description |
|----------|----------|-------------|
| `FIREBASE_SERVICE_ACCOUNT` | Yes | Full Firebase Admin SDK JSON |
| `VOBIZ_AUTH_ID` | Yes | `MA_4272LINL` |
| `VOBIZ_AUTH_TOKEN` | Yes | Vobiz API Token |
| `BACKEND_URL` | Yes | `https://vobiz-dialer-backend.up.railway.app` |
| `ANSWER_MODE` | No | `dial` (default) or `conference` |
| `PORT` | No | `3000` (Railway sets) |
| `REDIS_URL` | Optional | For persistence across restarts |

### Backend Endpoints
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/answer` | POST/GET | Vobiz Answer webhook → returns Dial XML |
| `/hangup` | POST | Vobiz Hangup webhook |
| `/register-token` | POST | Register FCM device token |
| `/dial-status` | POST | Dial action callback (diagnostic) |
| `/dial-callback` | POST | Real-time B-leg events |
| `/probe-answer` | POST/GET | Probe route for CDR visibility |

### Answer Webhook Response (Dial XML)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Response>
    <Dial timeout="45" dialMusic="real" callerId="+917965850027"
          callbackUrl="https://vobiz-dialer-backend.up.railway.app/dial-callback"
          action="https://vobiz-dialer-backend.up.railway.app/dial-status"
          method="POST" redirect="false">
        <User>sip:sairam8391265128911238046@registrar.vobiz.ai</User>
    </Dial>
    <Speak>The customer is not available. Please try again later.</Speak>
    <Hangup/>
</Response>
```

## Development

### Local Backend Development
```bash
cd Backend/Vobiz-backend
npm install
cp .env.example .env
# Edit .env with credentials
npm run dev
# Runs on http://localhost:3000
```
- Debug builds use `http://10.0.2.2:3000` (emulator → host)

### Override Backend URL at Build Time
```bash
./gradlew -Pvobiz.backend.url=https://your-backend.railway.app assembleFossDebug
```

### Logcat Filters
```bash
# SIP/Registration
adb logcat -s WssRelay,VobizSip,VobizLP

# Call events
adb logcat -s VobizSip:V

# All Vobiz tags
adb logcat -s "*Vobiz*"
```

## Known Issues

### 1. Inbound Call Disconnects at ~32 Seconds
**Symptom**: Inbound call connects, audio works, but call ends automatically at ~32 seconds with no user action.

**Root Cause**: Likely DTLS-SRTP renegotiation failure or media timeout. The platform may send a re-INVITE with updated SDP that the WSS relay doesn't handle correctly, or the DTLS handshake times out.

**Evidence**:
- `core.setMediaEncryptionMandatory(false)` allows initial DTLS handshake
- `a=setup:passive` rewrite on answer (RFC 4145)
- Media stats show RTP flowing until disconnect

**Workarounds Tried**:
- ✅ DTLS mandatory = false
- ✅ Answer `a=setup` rewrite to passive
- ✅ ICE + STUN enabled
- ❌ Media timeout (inc_timeout) set to 120s

**Files to Investigate**:
- `WssRelay.kt` → `rewriteSetupAttribute()` (line ~719)
- `LinphoneService.kt` → media encryption config (lines 418-435)
- `SipRelayCore.kt` → SDP parsing/munging

### 2. `sip_registered` = `false` in Dashboard
**Expected**: WSS registrations don't update platform's location store flag. Inbound still works via WSS relay.

### 3. Multiple Endpoints Linked to Same App
Dashboard shows 4 endpoints linked to App `85076776948601220`. App uses first match (`sairam8391265128911238046`).

## Testing Checklist

- [ ] Backend `/health` returns `{"status":"ok","firebase":true,"mode":"dial"}`
- [ ] Backend `/answer` returns Dial XML with correct callback URLs
- [ ] App login → "Provisioning complete" in logs
- [ ] App shows `Registered` state (WssRelay)
- [ ] Outbound call connects, two-way audio
- [ ] Inbound call rings app, user can answer
- [ ] Inbound call audio works for > 32 seconds (KNOWN FAILURE)
- [ ] FCM token registered in backend (`/health` shows devices > 0)

## Debugging Inbound 32s Disconnect

### Enable Detailed Media Logging
In `LinphoneService.kt`, media stats already log every 5s:
```
MEDIA[t+5s] dir=incoming state=StreamsRunning rtpRecv=... rtpSent=... ice=Connected srtp=DTLS ...
```

### Capture SIP Trace
```bash
adb logcat -s WssRelay,SipRelay,VobizSip -v time > sip_trace.log
```

### Check for Re-INVITE at ~30s
Look for:
- Platform re-INVITE with new SDP
- DTLS renegotiation attempt
- ICE restart
- 488/491 responses

### Vobiz Support Ticket
See [docs/VOBIZ_SUPPORT_TICKET.md](docs/VOBIZ_SUPPORT_TICKET.md) for platform-side investigation.

## Repositories

- **Android**: https://github.com/vobizofficial-cmd/vobiz-dialer-android
- **Backend**: https://github.com/vobizofficial-cmd/vobiz-dialer-backend

## License

GPL v3.0 (forked from RivoPhoneApp-PreAvatar, ShizuCallRecorder 1.3.3)