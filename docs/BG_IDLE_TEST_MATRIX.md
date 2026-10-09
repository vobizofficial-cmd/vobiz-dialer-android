# Background & Idle Test Matrix

| Test Row | Scenario | Steps | Expected Result | Log Tag to Watch | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Row 1** | App Open + Screen Unlocked | Open app -> Call Vobiz DID | Instant ringtone, vibration, and full-screen incoming UI | `VobizSip` | ✅ PASS |
| **Row 2** | App in Background | Minimize app -> Call Vobiz DID | Heads-up incoming call banner + ringtone + vibration | `VobizSip` | ✅ PASS |
| **Row 3** | Screen Locked | Lock phone screen -> Call Vobiz DID | Wakes screen, displays locked incoming UI (`setShowWhenLocked`) | `VobizSip` | ✅ PASS |
| **Row 4** | App Swiped Away | Swipe app from Recents -> Call Vobiz DID | `LinphoneService` FGS continues running & receives INVITE | `VobizSip` | ✅ PASS |
| **Row 5** | Device Idle (Doze Mode) | Leave device idle for 10+ min -> Call Vobiz DID | `WAKE_LOCK` + FGS process wakes CPU and triggers incoming alert | `VobizSip` | ✅ PASS |
| **Row 6** | Network Switch (Wi-Fi <-> Cellular) | Switch active connection while registered | `LinphoneService` detects network change & re-registers automatically | `VobizSip` | ✅ PASS |
| **Row 7** | Force Stop | Force stop app in Android Settings -> Call Vobiz DID | Android platform blocks stopped apps until user re-opens app | `VobizSip` | Documented OS Constraint |
