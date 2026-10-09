# Inbound Call Debugging Guide

## Architecture Overview

```
PSTN Caller → Vobiz Network → Voice App (85076776948601220)
                                    ↓
                              answer_url (webhook.site)
                                    ↓
                              <Dial><User>endpoint</User></Dial>
                                    ↓
                              Platform SIP → WSS Registrar (port 5063)
                                    ↓
                              In-app WSS Relay (wss://registrar.vobiz.ai:5063/)
                                    ↓
                              Loopback UDP (127.0.0.1:5099) → Linphone
                                    ↓
                              App: IncomingReceived → 180 Ringing → UI
```

## Key Components

### 1. WSS Relay (`WssRelay.kt`)
- Registers to `wss://registrar.vobiz.ai:5063/` with digest auth
- Exposes loopback UDP proxy at `127.0.0.1:5099`
- Linphone registers to loopback, relay bridges to WSS
- **SDP Munging**: Rewrites `m=audio ... RTP/SAVPF` → `UDP/TLS/RTP/SAVPF` when `a=fingerprint` present
- Keeps WSS REGISTER fresh (75% of expiry), sends OPTIONS keepalive every 30s

### 2. Linphone Config (`LinphoneService.kt`)
- Dual proxy configs:
  - **Proxy 1 (WSS)**: `sip:127.0.0.1:5099;transport=udp` → default, handles inbound
  - **Proxy 2 (UDP)**: `sip:registrar.vobiz.ai:5060;transport=udp` → attempts to update `sip_registered` flag
- Media: DTLS preferred, mandatory=false, AVPF enabled
- Certs: `files/linphone-certs/` (auto-generated)

### 3. Answer URL (webhook.site)
Returns XML with:
- `dialMusic="real"` - caller hears ringback
- `timeout="45"` - 45s ring timeout
- `callbackUrl` + `action` - dial event tracking

## Debugging Checklist

### Inbound Call Not Ringing
1. Check WSS relay: `adb logcat -s WssRelay` → "REGISTER ok", "WSS open"
2. Check INVITE delivery: `adb logcat -s VobizLP` → "platform -> linphone: INVITE"
3. Check SDP munging: `adb logcat -s WssRelay` → "rewrote inbound SDP m-line to DTLS profile"
4. Check DTLS init: `adb logcat -s VobizLP` → "Incoming call media encryption initialized to LinphoneMediaEncryptionDTLS"
5. Check ringing: `adb logcat -s VobizSip` → "IncomingReceived", "180 Ringing"

### Call Hangs Up at 10s (Cause 2020)
1. Check webhook.site for `DialHangup` with `DialBLegHangupCauseCode=2020`
2. Check endpoint API: `sip_registered` is `"false"`
3. This is platform quirk - WSS registration doesn't update flag

### No Audio After Answer
1. Check SDP answer: `a=fingerprint:sha-256 ...`, `a=setup:active`, ICE candidates
2. Check certs: `ls files/linphone-certs/`
3. Try ICE disabled: `isIceEnabled = false` in NatPolicy
4. Check media anchoring: emulator egress IP must be Indian

### Emulator No Ringtone
1. Extended Controls → Microphone → "Virtual microphone uses host audio input"
2. Or launch with `-allow-host-audio`
3. Check `IncomingCallRinger.kt` uses `USAGE_NOTIFICATION_RINGTONE`

## CDR Analysis

### Hangup Cause Codes
| Code | Name | Meaning |
|------|------|---------|
| 2020 | Endpoint Not Registered | `sip_registered: "false"` - platform quirk |
| 2070 | Media Anchoring Violation | Media left India |
| 6010 | Ring Timeout | Increase `<Dial timeout>` |
| 4000 | Normal Clearing | Call connected successfully |

### Dial Events (callbackUrl)
| Event | DialAction | Meaning |
|-------|------------|---------|
| DialAnswer | answer | Destination answered |
| DialConnected | connected | B-leg bridged to A-leg |
| DialHangup | hangup | B-leg ended |
| DialAction | (final) | Final result |

## API Endpoints Used

| Purpose | Endpoint |
|---------|----------|
| Auth | `POST /auth/login`, `GET /auth/me` |
| Application | `GET/POST /Account/{authId}/Application/{appId}/` |
| Endpoint | `GET /Account/{authId}/Endpoint/{id}/` |
| Numbers | `GET /Account/{authId}/numbers` |
| CDR | `GET /Account/{authId}/Call/` |
| Provisioning | `POST /Endpoint/{id}/password` (via VobizProvisioner) |

## Test Commands

```bash
# Fire test call
cd C:\Users\DELL\AppData\Local\Temp\opencode\pt
$login = Invoke-RestMethod -Method Post -Uri "https://api.vobiz.ai/api/v1/auth/login" -ContentType "application/json" -Body (@{email="<your-email>";password="<your-password>"} | ConvertTo-Json)
$me = Invoke-RestMethod -Uri "https://api.vobiz.ai/api/v1/auth/me" -Headers @{ "Authorization" = "Bearer $($login.access_token)" }
$env:VOBIZ_TOKEN = $me.auth_secret
node inbound1.js

# Check CDR
$token = Get-Content token.txt -Raw
Invoke-RestMethod -Method Get -Uri "https://api.vobiz.ai/api/v1/Account/MA_4272LINL/Call/?limit=5&direction=inbound" -Headers @{ "X-Auth-ID" = "MA_4272LINL"; "X-Auth-Token" = $token }

# Check endpoint
Invoke-RestMethod -Method Get -Uri "https://api.vobiz.ai/api/v1/Account/MA_4272LINL/Endpoint/269321988178847/" -Headers @{ "X-Auth-ID" = "MA_4272LINL"; "X-Auth-Token" = $token }

# Check answer_url
Invoke-WebRequest -Method Post -Uri "https://webhook.site/aa07c1c4-8255-4a25-a027-58d8cf7518ec" -ContentType "application/x-www-form-urlencoded" -Body ""
```

## Logcat Filters

```bash
# Full inbound flow
adb logcat -s WssRelay,VobizSip,VobizLP,SipRelay

# Registration only
adb logcat -s WssRelay | grep -E "REGISTER|WSS|relay started"

# Media negotiation
adb logcat -s VobizLP | grep -E "MediaSession|encryption|fingerprint|setup|ICE|DTLS"

# SDP munging
adb logcat -s WssRelay | grep "rewrote"
```