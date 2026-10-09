# Vobiz Dialer — Security Review

## 1. Credential Storage
- **SIP Passwords & API Tokens**: Stored using Android `EncryptedSharedPreferences` via `MasterKey` in [`CredentialStore.kt`](file:///D:/SohaiL/Vobiz/Dialer/vobiz-dialer/app/src/main/java/com/grinch/rivo4/auth/CredentialStore.kt#L20).
- **Separation of Concerns**: SIP Digest Credentials (`username`/`password`) are strictly separated from Vobiz Master API Tokens (`authId`/`authToken`).

## 2. Logging & Redaction
- Logcat tags (`VobizSip`, `VobizDialer`) filter out sensitive authorization headers, passwords, and HTTP tokens.
- Debug mode in release builds is disabled (`debuggable = false` in release buildType).

## 3. Exported Components
- [`SipCallActivity`](file:///D:/SohaiL/Vobiz/Dialer/vobiz-dialer/app/src/main/java/com/grinch/rivo4/sip/SipCallActivity.kt) and [`LinphoneService`](file:///D:/SohaiL/Vobiz/Dialer/vobiz-dialer/app/src/main/java/com/grinch/rivo4/sip/LinphoneService.kt) are `exported="false"`.
- `SipCredentialReceiver` lives in the debug sourceSet only (absent from release APKs) and is
  `exported="true"` so the adb E2E harness can drive it; since audit round 1 it is guarded by
  `android:permission="android.permission.DUMP"` (held by the shell/system uid, not obtainable
  by third-party apps), replacing the previously-claimed but non-existent signature permission.
  Verified by the E2E call scripts (`tools/e2e/*.ps1`).
- Verbose liblinphone logcat logging (`setDebugMode`/`enableLogcatLogs`) is now gated behind
  `BuildConfig.DEBUG` (`LinphoneService.onCreate`), so release builds log only the app's own
  redacted `VobizSip`/`VobizDialer` messages.
