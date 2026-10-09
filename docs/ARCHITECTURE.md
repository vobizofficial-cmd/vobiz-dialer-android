# System Architecture

## High-Level Diagram

```
┌──────────────────────────────────────────────────────────────────┐
│ ANDROID APP (Kotlin)                                             │
│ ┌─────────────┐ ┌─────────────┐ ┌──────────────────────────┐    │
│ │ Dialpad     │ │ Call Log  │ │ Settings                   │    │
│ └─────────────┘ └─────────────┘ └──────────────────────────┘    │
│                                                                  │
│ ┌─────────────────────────────────────────────────────────────┐  │
│ │ LibLinphone Core (SIP engine)                                │  │
│ │ - WSS relay via 127.0.0.1:5099                                │  │
│ │ - DTLS + ICE + STUN                                           │  │
│ │ - Call recording to cacheDir                                  │  │
│ └─────────────────────────────────────────────────────────────┘  │
│                                                                  │
│ ┌─────────────────────────────────────────────────────────────┐  │
│ │ FCM Receiver (VobizMessagingService)                         │  │
│ │ - Handles "incoming_call" data messages                      │  │
│ │ - Wakes LinphoneService                                       │  │
│ │ - Shows full-screen incoming call UI                         │  │
│ └─────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
│                                                                  │
┌───────────────┴───────────────┐                                   │
│                               │                                   │
▼                               ▼                                   │
┌──────────────────────────────┐ ┌──────────────────────────────┐  │
│ WSS Registrar                │ │ FCM Push                       │
│ wss://registrar.vobiz.ai     │ │ Google Firebase              │
│ :5063                        │ │                               │
└──────────────────────────────┘ └──────────────────────────────┘  │
│                                                                   │
▼                                                                   │
┌──────────────────────────────────────────────────────────────────┐  │
│ VOBIZ PLATFORM                                                    │  │
│ - SIP Registrar                                                   │  │
│ - Voice Applications                                              │  │
│ - Conference Bridges                                              │  │
│ - Outbound Trunks                                                 │  │
└──────────────────────────────────────────────────────────────────┘  │
│                                                                   │
▼                                                                   │
┌──────────────────────────────────────────────────────────────────┐  │
│ BACKEND (Node.js on Vercel)                                       │  │
│ - /answer → returns <Conference> XML                              │  │
│ - /register-token → stores device FCM tokens                     │  │
│ - /hangup → logs call end                                         │  │
│ - Sends FCM to all registered devices                             │  │
└──────────────────────────────────────────────────────────────────┘  │
```

## Components

| Component | File / Location | Purpose |
|-----------|----------------|---------|
| **LinphoneService** | `sip/LinphoneService.kt` | Foreground SIP service, owns LibLinphone Core |
| **SipCallController** | `sip/SipCallController.kt` | Call state machine, outbound/inbound routing |
| **WssRelay** | `sip/WssRelay.kt` | WSS bridge + SDP munging |
| **SipRelayCore** | `sip/SipRelayCore.kt` | SIP message rewriting for the relay |
| **VobizProvisioner** | `sip/VobizProvisioner.kt` | Auto-provisions endpoint, trunks, application links |
| **VobizApi** | `sip/VobizApi.kt` | REST client for Vobiz |
| **CredentialStore** | `auth/CredentialStore.kt` | Encrypted credential persistence |
| **VobizMessagingService** | `fcm/VobizMessagingService.kt` | FCM receiver |
| **TokenRegistrar** | `fcm/TokenRegistrar.kt` | Registers FCM token with backend |
| **Backend** | `backend/index.js` | Express server for webhook + FCM |