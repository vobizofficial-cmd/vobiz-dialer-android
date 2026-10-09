# Vobiz Configuration Checklist

| Requirement | Setting / Implementation | File Location | Status |
| :--- | :--- | :--- | :--- |
| **SIP Username** | Stored in `CredentialStore` | [`CredentialStore.kt`](file:///D:/SohaiL/Vobiz/Dialer/vobiz-dialer/app/src/main/java/com/grinch/rivo4/auth/CredentialStore.kt#L10) | ✅ OK |
| **SIP Password** | Encrypted in EncryptedSharedPreferences | [`CredentialStore.kt`](file:///D:/SohaiL/Vobiz/Dialer/vobiz-dialer/app/src/main/java/com/grinch/rivo4/auth/CredentialStore.kt#L25) | ✅ OK |
| **SIP Domain** | Configurable, defaults to Vobiz domain | [`LinphoneService.kt`](file:///D:/SohaiL/Vobiz/Dialer/vobiz-dialer/app/src/main/java/com/grinch/rivo4/sip/LinphoneService.kt#L180) | ✅ OK |
| **Transport** | UDP (5060), TCP (5060), TLS (5061) | [`LinphoneService.kt`](file:///D:/SohaiL/Vobiz/Dialer/vobiz-dialer/app/src/main/java/com/grinch/rivo4/sip/LinphoneService.kt#L190) | ✅ OK |
| **Outbound Trunk** | Standard Vobiz SIP Trunking | [`SipCallController.kt`](file:///D:/SohaiL/Vobiz/Dialer/vobiz-dialer/app/src/main/java/com/grinch/rivo4/sip/SipCallController.kt#L225) | ✅ OK |
| **Inbound Origination** | Vobiz Inbound Trunk -> SIP INVITE to endpoint | [`LinphoneService.kt`](file:///D:/SohaiL/Vobiz/Dialer/vobiz-dialer/app/src/main/java/com/grinch/rivo4/sip/LinphoneService.kt#L140) | ✅ OK |
| **Call Duration & Ring Timeout** | Ring timeout = 120s; Call duration unlimited (0) | [`LinphoneService.kt`](file:///D:/SohaiL/Vobiz/Dialer/vobiz-dialer/app/src/main/java/com/grinch/rivo4/sip/LinphoneService.kt#L205) | ✅ OK |

## Inbound E2E Status (2026-10-01)

App-side inbound prerequisites are all confirmed on the emulator:

- REGISTER `sip:vobizdialer2962331181284600730@registrar.vobiz.ai` completes with `200 OK` (`Expires: 3600`, Contact rewritten to the public NAT mapping), digest accepted, `Registration state: Ok`, keepalives flowing every 30s.
- Inbound trunk `2cb0c686` has `inbound_destination = sip:vobizdialer2962331181284600730@registrar.vobiz.ai` (verified via API).
- Provisioning fresh at login (`Provisioning complete: endpoint=..., trunk=08ecd76e.sip.vobiz.ai`).

However, inbound calls are **refused by the platform before reaching the client** — open platform-side issue:

| Evidence | Result |
| :--- | :--- |
| `POST /api/v1/Account/MA_4272LINL/Call/` (`from=+919240953996`, `to=+917965850027`) | CDR leg 2 (`sip-trunking`, trunk `2cb0c686`): `ring=0 dur=0 send_refuse` — instant refusal; app logs contain **no incoming INVITE** |
| Manual inbound test from `+919631465841` (user) | Same instant `send_refuse`, `ring=0` |
| `GET .../Endpoint/148989357092714/` after successful registration | `sip_registered: false` |
| Raw `INVITE` probe to `registrar.vobiz.ai` for the AOR | `100 trying` → `480 Temporarily Unavailable` (`Reason: Q.850;cause=96`) |

Interpretation: the registrar answers the app's REGISTER with `200 OK`, but the binding is not visible to the platform's routing/location lookup (`sip_registered=false`, `480`), so trunk-leg calls are refused in 0 seconds without any delivery attempt. Client reachability is not yet disproven — delivery must be retested once the platform reports `sip_registered=true`; the remaining risk is NAT (contact `185.132.132.77:<mapped-port>`) if the platform does not latch inbound INVITEs onto the existing keepalive flow.
