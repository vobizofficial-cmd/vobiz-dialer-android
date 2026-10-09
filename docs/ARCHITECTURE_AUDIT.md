# Vobiz Dialer — Architecture Audit & Evaluation

## 1. Executive Summary
The Vobiz Dialer is a dedicated Internet Softphone application designed for Vobiz SIP calling with zero cellular SIM fallback.

## 2. Linphone Core Ownership & Lifecycle
- **Single Owner**: `LinphoneService` ([`LinphoneService.kt`](file:///D:/SohaiL/Vobiz/Dialer/vobiz-dialer/app/src/main/java/com/grinch/rivo4/sip/LinphoneService.kt#L24)) is the sole manager of `Linphone Core` (`sCore`).
- **Foreground Service**: Runs with `foregroundServiceType="specialUse"` (`PROPERTY_SPECIAL_USE_FGS_SUBTYPE="sip_registration"`) to maintain SIP registration across Activity lifecycles, device lock states, and background idle periods.
- **Thread Safety**: Reconfigurations and proxy additions use `configMutex` (`Mutex()`) to prevent concurrent modification of `Linphone Core`.

## 3. Call Flows

### Outgoing Call Flow
1. User enters number on Dialpad.
2. `SipCallController.placeCall` ([`SipCallController.kt`](file:///D:/SohaiL/Vobiz/Dialer/vobiz-dialer/app/src/main/java/com/grinch/rivo4/sip/SipCallController.kt#L218)) normalizes number and creates `Linphone Core.invite("sip:number@domain")`.
3. Call connects over Vobiz SIP trunk. No `ACTION_CALL` or PSTN fallback.

### Incoming Call Flow
1. Vobiz DID receives call -> Vobiz inbound trunk dispatches SIP INVITE.
2. `LinphoneService` listener `onCallStateChanged` receives `Call.State.IncomingReceived`.
3. Dispatched to `SipCallController.handleCallEvent` ([`SipCallController.kt`](file:///D:/SohaiL/Vobiz/Dialer/vobiz-dialer/app/src/main/java/com/grinch/rivo4/sip/SipCallController.kt#L70)).
4. `IncomingCallRinger` ([`IncomingCallRinger.kt`](file:///D:/SohaiL/Vobiz/Dialer/vobiz-dialer/app/src/main/java/com/grinch/rivo4/sip/IncomingCallRinger.kt#L20)) plays loudspeaker ringtone loop + continuous vibration.
5. Posts `NotificationCompat.CallStyle.forIncomingCall` with `fullScreenIntent` launching `SipCallActivity` ([`SipCallActivity.kt`](file:///D:/SohaiL/Vobiz/Dialer/vobiz-dialer/app/src/main/java/com/grinch/rivo4/sip/SipCallActivity.kt#L16)).
6. User accepts/declines via gestured Material 3 Expressive UI ([`SipCallScreen.kt`](file:///D:/SohaiL/Vobiz/Dialer/vobiz-dialer/app/src/main/java/com/grinch/rivo4/view/screen/SipCallScreen.kt#L200)).

## 4. Evaluation: LibLinphone vs. Vobiz WebRTC SDK
- **LibLinphone + Vobiz SIP** is already fully integrated, handles background incoming calls natively, supports local call recording, and complies with standards.
- **Recommendation**: Keep LibLinphone + Vobiz SIP.
