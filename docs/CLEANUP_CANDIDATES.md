# Cleanup Candidates Audit Report

## 1. Removed & Orphaned Files (Pruned)
- `FakeCallActivity.kt`, `FakeCallManager.kt`, `FakeCallNotificationManager.kt`, `FakeCallReceiver.kt`, `FakeCallSchedulerScreen.kt`
- `MessageLauncher.kt`, `EmailLauncher.kt`, `VideoLauncher.kt`, `SocialUtils.kt`
- `CallScreenControls.kt`
- `ContributorsScreen.kt`

## 2. Unused Strings & Resources
- `fake_call_*` strings removed from `res/values/strings.xml` and `res/values-it/strings.xml`.
- Promo dialog strings removed from `MainActivity.kt`.

## 3. Manifest Permissions
- Kept core permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`, `BLUETOOTH`, `BLUETOOTH_CONNECT`, `VIBRATE`, `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `READ_CONTACTS`.
