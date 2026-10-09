# Vobiz Dialer

Vobiz SIP dialer for internal use — PSTN → DID inbound with WebRTC media via WSS relay.
**Internal use only — not published to Google Play.**

## Status

- **Inbound breakthrough**: WSS relay + SDP munging delivers INVITE, app rings, DTLS initialized ✅
- **Caller hears ringback**: `dialMusic="real"` works ✅
- **Registration quirk**: `sip_registered` stays `"false"` for WSS (platform limitation) → Dial logic hangs up with 2020
- **Escalation**: See [docs/VOBIZ_SUPPORT_TICKET.md](docs/VOBIZ_SUPPORT_TICKET.md)

## Build

```powershell
$env:JAVA_HOME="$env:LOCALAPPDATA\Programs\Android Studio\jbr"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat testFossDebugUnitTest assembleFossDebug --console=plain
```

Package: `it.stivy.rivo.personal.debug` (installs beside any existing Vobiz build)

## Docs

- [Inbound Debugging](docs/INBOUND_DEBUG.md)
- [Troubleshooting](docs/TROUBLESHOOTING.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Support Ticket](docs/VOBIZ_SUPPORT_TICKET.md)

## Key Components

| File | Purpose |
|------|---------|
| `WssRelay.kt` | WSS bridge (port 5063) → loopback UDP (5099), SDP munging for DTLS |
| `LinphoneService.kt` | Dual proxy (WSS + UDP), DTLS media, AVPF |
| `SipRelayCore.kt` | SIP parsing, REGISTER Contact fix |
| `VobizProvisioner.kt` | Auth, password rotation, endpoint attach |

## Debugging

```bash
# Fire test call
cd C:\Users\DELL\AppData\Local\Temp\opencode\pt
$login = Invoke-RestMethod -Method Post -Uri "https://api.vobiz.ai/api/v1/auth/login" -ContentType "application/json" -Body (@{email="<your-email>";password="<your-password>"} | ConvertTo-Json)
$me = Invoke-RestMethod -Uri "https://api.vobiz.ai/api/v1/auth/me" -Headers @{ "Authorization" = "Bearer $($login.access_token)" }
$env:VOBIZ_TOKEN = $me.auth_secret
node inbound1.js

# Logcat filters
adb logcat -s WssRelay,VobizSip,VobizLP | grep -E "REGISTER|INVITE|180|DTLS|rewrote"
```

## License

GPL v3.0 (forked from RivoPhoneApp-PreAvatar, ShizuCallRecorder 1.3.3)