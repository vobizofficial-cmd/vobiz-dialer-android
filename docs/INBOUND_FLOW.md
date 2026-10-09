# Inbound Call Flow (Conference + FCM)

## Why This Architecture

The Voice Application `<Dial><User>` path **fails** for WSS-registered endpoints because:
- Vobiz API's `sip_registered` flag is always `"false"` for WSS registrations
- The Dial node checks this flag before routing, and rejects when false
- Fallback goes to TTS "user unavailable"

Inbound Trunks cannot help either because Origination URIs only support UDP/TCP/TLS — not WSS.

**Solution:** Park the call in a Vobiz conference, wake the app via FCM, and have the app join the conference.

## Complete Flow

```
PSTN caller dials +917965850027
↓
Vobiz receives call, hits Application's Answer URL
POST https://backend.vercel.app/answer
Body: CallUUID=..., From=+919123151351, To=+917965850027
↓
Backend responds with conference XML:
<Response>
    <Conference startConferenceOnEnter="false"
                endConferenceOnExit="true"
                waitSound="https://actions.google.com/sounds/v1/alarms/beep_short.ogg"
                maxMembers="2">
        vobiz-dialer-{CallUUID}-{timestamp}
    </Conference>
</Response>
↓
Caller is parked in conference (hears hold music)
↓
Backend sends FCM to all registered devices:
{
    type: "incoming_call",
    conference_name: "vobiz-dialer-...",
    caller_number: "+919123151351",
    call_uuid: "..."
}
↓
Device receives FCM (even if app was killed)
↓
App wakes LinphoneService, shows incoming call UI
↓
User taps Answer
↓
App sends INVITE to sip:<conference>@registrar.vobiz.ai
↓
App joins conference as second participant
↓
Two-way audio through Vobiz conference bridge
↓
User hangs up → call.terminate() → conference ends
```

## Key Files

| File | Role |
|------|------|
| `backend/index.js` | `/answer` returns conference XML, sends FCM |
| `fcm/VobizMessagingService.kt` | Receives FCM, stores pending conference |
| `sip/SipCallController.kt` | Joins conference on user answer |
| `view/screen/SipCallScreen.kt` | Shows incoming call UI |

## Timing Requirements

| Step | Max Time | Note |
|------|----------|------|
| Backend responds to webhook | 2 seconds | Vobiz retries on timeout |
| FCM delivery | ~2-5 seconds | High-priority data message |
| App joins conference | 5 seconds | SIP INVITE + media negotiation |
| Total user-visible delay | < 10 seconds | Caller hears hold music meanwhile |