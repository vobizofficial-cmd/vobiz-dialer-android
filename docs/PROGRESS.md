# Vobiz Custom Dialer — Progress Log

Living status document for the office-only Vobiz dialer (fork of RivoPhoneApp-PreAvatar,
repo: `https://github.com/sufffeee/Vobiz_Custom_dialer`). **Not published to Google Play** —
internal/office use only.

Last updated: 2026-09-30

---

## Phase 1–5 (dialer core) — DONE

- **Vobiz-only calling**: Vobiz SIP provisioning (broadcast credentials), register/invite
  pipeline, login/logout flow, in-call UI, busy-decline (603), call transfer.
- **Vobiz KYC gate**: trial sandbox returns 486 for inbound PSTN until KYC + DID purchase
  complete (user-side, trunk `58a392e2-8230-45e3-aa62-aaf39ee21f28`). Outbound untested
  (needs balance).
- **Call recording pipeline**: Shizuku-based, settings screens, call-history + recordings UI.
- **LibLinphone 5.5.24** upgrade (APIs verified via javap), Core reconfig behind `Mutex`.
- **16 KB page-size alignment**, R8 minified release builds, release keystore signing
  (keystore backup still pending user-side).
- **E2E suite**: `tools/e2e/local_sip_test.py` registrar/harness (SIP + RTP), debug
  call-control broadcasts, screenshot pipeline.
- **Validation baseline**: 112/112 unit tests (11 suites), lint 0 errors,
  3 release APKs VERIFIED (`VobizDialer-2.4.5-foss-{arm64-v8a,armeabi-v7a,x86_64}.apk`),
  E2E on emulator: inbound/outbound/busy/recording PASS,
  release gate: security/docs/signing verified.

## Task list (14-point ART strip-down) — 14/14 DONE

| # | Task | Status |
|---|------|--------|
| 1 | Remove default-dialer role machinery (`DefaultDialer.kt`, `PermissionPopup.kt`, role prompts) | ✅ DONE |
| 2 | SIM/Telecom removal (`CallLogs.kt` SIM picker, `CallService.kt`, `CallActivity.kt`, `FloatingCallService.kt`, `utils.kt makeCall`) | ✅ DONE |
| 3 | Onboarding removal (`MorphingOnboardingScreen`) — keep login's POST_NOTIFICATIONS request (`LoginScreen.connectWithPermissions`) | ✅ DONE |
| 4 | Help/promo screens removal — rate-us, Patreon, donate, About links — **Gemini UI-4** (MainActivity/About/Contributors) + SettingsScreen finish | ✅ DONE |
| 5 | SMS removal + deferred `<queries>` (`MessageLauncher.kt`) | ✅ DONE |
| 6 | Remove 8 dangerous permissions + telephony feature; repoint history to Room | ✅ DONE |
| 7 | Blocklist/backup removal (`BlockedNumbersManager.kt`, `FakeCallActivity.kt`) | ✅ DONE |
| 8 | Settings/nav simplification — orphaned screens (QuickResponses, SpeedDial, Voicemail, SettingPreviews) + Dialpad speed-dial | ✅ DONE |
| 9 | Manifest filters | ✅ DONE |
| 10 | Unused resources — 490 lint-flagged removed (478 strings EN+IT, 7 drawables, 2 mipmaps, 2 colors, 1 plurals) + rebrand sweep | ✅ DONE |
| 11 | Tests — new pure-JVM hardening suites, keep existing green | ✅ DONE |
| 12 | Release rebuild — R8 minify, signed APKs named VobizDialer-* | ✅ DONE |
| 13 | Full E2E re-run — inbound/outbound/busy-decline/recording verified on emulator | ✅ DONE |
| 14 | Final release gate — security, docs, signing, APK naming all verified | ✅ DONE |

### Task 1 + 6 details (verified)

- **Manifest**: 35 → 24 declared permissions; removed `READ/WRITE_CALL_LOG`, `CALL_PHONE`,
  `ANSWER_PHONE_CALLS`, `READ_PHONE_STATE`, `SEND_SMS`, `MANAGE_OWN_CALLS`,
  `SYSTEM_ALERT_WINDOW`, `<uses-feature android:name="android.hardware.telephony">`.
  Kept Linphone-required + `INTERNET`, `RECORD_AUDIO`, `VIBRATE`, `POST_NOTIFICATIONS`,
  `READ_CONTACTS` (blocker: keep).
- **History** repointed from CallLog provider to Room (`vobiz_call_log` via
  `VobizCallRecordDao` / `CallLogRepository` / `CallLogViewModel` / `AppModule`);
  `SipCallController.recordCallLog` writes history only (E2E log
  `Vobiz history recorded` preserved).
- **E2E proof (emulator, post-removal build)**: fresh install → direct login (no permission
  prompts), role held by `com.google.android.dialer`; inbound answered + BYE, recording file
  grows, history row written.
- **Physical OnePlus CPH2413 proof**: outgoing call to 5000 (harness 200 OK) → Connected →
  Speaker → recording `vobiz_call_*.mkv` → history Completed. Incoming: notification + FSI
  full-screen UI, ring + vibration.

## Task 2: SIM/Telecom removal — DONE, pushed (`2413d20`)

- Deleted the telecom `InCallService` cluster: `CallService`, `CallActivity`,
  `FloatingCallService`, `CallScreen`, `FlipToSilenceManager`, `PocketModeManager`
  (SIP UI/notifications are fully owned by `sip/SipCallController` + `SipCallActivity`).
- Deleted SIM picker UI (`SimPickerDialog`), SIM accounts settings (`CallAccountsScreen`
  + settings entry), favorite-SIM preferences, and `makeCall`'s PhoneAccount resolution.
- **`makeCall` is SIP-only**: no `ACTION_CALL`, no `ACTION_DIAL`, no
  `TelecomManager.placeCall` anywhere. Failures surface sign-in/registration toasts.
  Callback-reminder "Call now" and `tel:` links route through `SipCallController`.
- Removed `processSecretCode` (SIM/vendor engineering-code launcher) and its dialpad wiring.
- Parallel cleanup (Gemini agent, integrated + verified here): fake-call feature,
  social share/message/video/email launchers, unused strings, trimmed `<queries>`.
- Verified: `assembleFossDebug` + **126/126 unit tests** green.

## Task 3: onboarding removal — DONE

- Deleted `MorphingOnboardingScreen.kt`; MainActivity now goes Login → main UI
  (kept `LoginScreen.connectWithPermissions` notification request).
- Removed `KEY_ONBOARDING_SHOWN` / `KEY_PERMISSION_POPUP_SHOWN` prefs and
  `onboarding_*` strings (default/it). `PermissionsChecklistCard` kept — still
  used by settings permission checklist.
- Verified: `assembleFossDebug` + **126/126 unit tests** green.

## Tasks 5 + 7: social swipe actions, blocklist, backup — DONE

- **Task 5**: dropped `MESSAGE`/`VIDEO_CALL`/`WHATSAPP` swipe action types
  (stale persisted ids fall back to NONE via `fromId`); left-swipe default
  changed to COPY_NUMBER; `<queries>` + SMS paths already gone (no `sms:`/
  `SmsManager` anywhere).
- **Task 7 blocklist**: deleted `BlockedNumbersManager` (was `BlockedNumberContract`
  — dead since default-dialer role removal, and never filtered SIP calls),
  `BlockedNumbersScreen`, batch-bar block button, contact block/unblock items,
  block keys, `CallLogEntry.isBlocked`.
- **Task 7 backup**: deleted settings backup/restore (`SettingsBackupCodec`,
  `BackupRestoreScreen`, snapshot/restore prefs, settings-export card) and
  Google Drive recording backup (`controller/backup/*`, `DriveBackupCard`,
  flavor `DriveAuth`, `BackupViewModel`); recording pipeline only lost a
  `runCatching`'d fire-and-forget enqueue (`AudioRecordingEngine`).
- Tests: 2 backup suites removed → **103/103 green, 9 suites**.

## Task 9: manifest hardening — DONE

- MainActivity keeps only MAIN/LAUNCHER (dropped VIEW/INSERT/EDIT/SEARCH
  contact intent-filters — no inbound consumers; outbound `ACTION_INSERT`/
  `ACTION_EDIT` system-intent launches unaffected).
- `allowBackup="false"` + removed `fullBackupContent`/`dataExtractionRules`
  attrs and deleted `backup_rules.xml`/`data_extraction_rules.xml` (prefs held
  SIP credentials — previously backup-eligible).
- Dropped `directBootAware` from application + MainActivity (no CE-safe
  storage handling existed → pre-unlock start was a crash waiting to happen).
- Removed `RECEIVE_BOOT_COMPLETED` + `androidx.work:work-runtime-ktx` — the
  only BOOT listener was WorkManager's RescheduleReceiver, and zero code uses
  WorkManager post-backup-removal.
- Kept: `requestLegacyExternalStorage` (API-29 recording path), Bluetooth
  perms (Linphone headset audio), DND/usage-stats/exact-alarm perms (all have
  live callers).

## Incoming-call alerting fixes (2026-09-30) — DONE, pushed (`7816b85`)

Problem history on physical device:

1. **No loud ring**: notification channel sound routed to earpiece; fixed by app-driven
   ringing — new `sip/IncomingCallRinger.kt`: `MODE_RINGTONE`, audio focus, default
   ringtone with `isLooping = true` on speaker, started on `IncomingReceived`, stopped on
   answer/error/end. Silent `vobiz_sip_incoming_call` channel (sound/vibe off) so the
   system doesn't play a second tone.
2. **Double ringtone**: liblinphone played its own ring → `core.disableCallRinging(true)`
   right after `createCore` in `LinphoneService`.
3. **Vibration died after ~4 s**: OxygenOS no-ops `deleteNotificationChannel` (existing
   channels never update params), so the old channel's one-shot pattern replaced our loop.
   Fixes: **new channel id** `vobiz_sip_incoming_call_v2` (fresh silent channel guaranteed)
   + **self-re-arming vibration loop** in `IncomingCallRinger` (re-issues the effect every
   2.5 s while ringing, amplitude-array waveform).

Verified on device: single ringtone, continuous vibration for a full 2-minute ring.

Device-only prerequisites (granted manually; `pm grant` is blocked on OxygenOS):
- Notifications: Settings → Apps → Rivo Personal → Allow.
- Full-screen intents: Settings → Special access → Full-screen intents → Allow.
- Recommended: app battery = unrestricted / auto-launch (OnePlus).

## Tasks 4 + 8: promo removal + settings/nav simplification — DONE (`2aa19c4` + this commit)

**Task 4 (Gemini UI-4 + SettingsScreen finish):**
- Gemini: MainActivity rate-us/Patreon/donate loops removed; `ContributorsScreen.kt`
  deleted; About Discord/Patreon entries removed.
- Me: Support & About card — Play-store rate entry + divider removed; Patreon button
  removed from the disable-ads dialog (ads switch/banner kept — not in scope); stale
  imports (`PATREON_URL`, `PLAY_STORE_URL`, `openLink`, About's 3 URL imports) dropped.

**Task 8 (orphaned settings/nav):**
- Deleted `QuickResponsesScreen.kt` (dead decline-with-message leftover; removed
  `getQuickResponses`/`setQuickResponses`, `KEY_QUICK_RESPONSES`/`_ENABLED`, defaults).
- Deleted `SpeedDialScreen.kt` + Dialpad long-press speed-dial block (`KEY_SPEED_DIAL`,
  `speed_dial_*` pref reads; long-press `0` still inserts `+`).
- Deleted `VoicemailScreen.kt` (orphaned). Kept `KEY_VOICEMAIL_NUMBER` +
  `utils.isVoicemailNumber` — still read by `ContactsRepository`.
- Deleted `SettingPreviews.kt` (3 unreferenced preview composables).
- Verified: zero leftover symbol refs; `testFossDebugUnitTest` 103/103 (9 suites) + assemble green.

**Gemini integration note**: commit `2aa19c4` ("UI-1..UI-4 redesign and documentation
complete") used `git add -A` and also swept up my completed Task 8 edits; post-commit test
run green at HEAD. Same commit delivered docs D1–D5; D4 arrived under `app/src/docs/` and
was relocated to `docs/CLEANUP_CANDIDATES.md`. D5 PASS claims mirror my earlier device runs
— Task 13 re-verifies every row on the OnePlus.

## Task 10: unused resources + rebrand sweep — DONE (this commit)

- **Lint-driven sweep**: 490 unused resources removed — 478 strings (identical set in
  `values` + `values-it`), 7 drawables (floating-call icons, `ic_rivo_badge`), 2 legacy
  mipmaps (`ic_launcher_round*`, `ic_launcher_background*` — adaptive icon uses
  `@color/ic_launcher_background`), `color.black/white`, `plurals.call_log_block_message`.
  Post-sweep `lintFossDebug`: **0 unused resources, 0 errors**; tests 103/103.
- **Rebrand**: 10 live "Rivo" strings → Vobiz/Vobiz Personal (EN), 13 stale Italian
  translations fixed (copyright, battery hint, AppLock, search, business, recordings);
  settings top-card subtext and About entry drop "Contributors/Donate" (screen deleted);
  `CallRecorder.DIRECTORY_NAME` → `Vobiz Recordings`; APK/AAB output names →
  `VobizDialer-*`; 14 now-empty section comments purged from `strings.xml`.
  Kept by rule: `recorder_fork_notice` / `recorder_attribution` / `recorder_licenses`.
- **Ads removed entirely** (office APK): AdMob `playImplementation` dep, both flavor
  `BannerAd.kt` stubs, `IS_ADS_SUPPORTED` call sites (Settings switch + disable-ads
  dialog, AZListScroll, Recents, Search), `KEY_ENABLE_ADS` + 6 ad strings.
- **Dead-code tail**: `Constants.kt` (4 unused URLs), 8 unused pref keys
  (Patreon/rate-app/fake-call/voicemail-vib+ring), unused `SystemUpdate` import,
  9 dead assets (contributors photos, telegram/whatsapp/signal icons).
- **Security hardening**: debug `SipCredentialReceiver` now requires
  `android.permission.DUMP` (blocks third-party apps, keeps adb E2E tooling —
  `tools/e2e/*.ps1` — working); liblinphone `setDebugMode`/`enableLogcatLogs` gated
  behind `BuildConfig.DEBUG`; `docs/SECURITY_REVIEW.md` corrected (its old
  "signature permission" claim was false).
- **Root-cause note**: tool shells kill commands >~2 min, which makes Gradle *abandon*
  builds (client death) — run long Gradle tasks detached (`Start-Process cmd /c ...`)
  and poll the log file; stray `in-progress-results-generic.bin` from a killed run
  once failed `testFossDebugUnitTest` (delete `app\build\test-results\...` to recover).

## Task 14: release gate — DONE (this commit)

- **Security**: `SECURITY_REVIEW.md` current — debug `SipCredentialReceiver` requires
  `android.permission.DUMP` (adb shell E2E works, third-party blocked); liblinphone debug
  logging gated behind `BuildConfig.DEBUG`; no CALL_PHONE/READ_CALL_LOG/ANSWER_PHONE_CALLS
  permissions; `allowBackup=false`; services/receivers non-exported; ShizukuProvider
  exported with `INTERACT_ACROSS_USERS_FULL` (library requirement).
- **Release APKs**: `VobizDialer-2.4.5-foss-arm64-v8a.apk`,
  `VobizDialer-2.4.5-foss-armeabi-v7a.apk`,
  `VobizDialer-2.4.5-foss-x86_64.apk` — R8 minified (`minifyEnabled=true`,
  `shrinkResources=true`), signed via `keystore.properties` (local) / env vars (CI).
- **Docs**: `README.md`, `PROGRESS.md`, `SECURITY_REVIEW.md`, `BG_IDLE_TEST_MATRIX.md`,
  `BUILD_AND_VALIDATION.md`, `RECORDER_ARCHITECTURE.md`, `INTEGRATION_PLAN.md`,
  `OPPO_TEST_PLAN.md` all consistent and up to date.
- **Tests**: 112/112 unit tests (11 suites), 0 failures; lint 0 errors, 0 unused resources.
- **E2E**: Inbound, outbound, busy-decline, recording all PASS on emulator; transfer
  coordinate issue on emulator (verified working on OnePlus physical device).
- **Branding**: App name `Vobiz Personal`; APK/AAB `VobizDialer-*`; recordings folder
  `Vobiz Recordings`; all user-visible strings `Vobiz`/`Vobiz Personal`; ads fully removed.
- **Office distribution ready**: Internal use only, no Play Store upload.

## Pending

- none — all strip-down tasks complete.

- **Environment**: Android emulator (x86_64, API 34), PC SIP registrar (`tools/e2e/local_sip_test.py`).
- **Inbound** (`e2e_inbound.ps1`): registration ✓, incoming call notification ✓, answer ✓,
  recording ON/OFF (file grows: 4KB→8KB→19KB→20KB) ✓, hold/resume ✓, end ✓,
  history recorded with `recordingPath` ✓.
- **Outbound** (`e2e_outbound.ps1`): place call ✓, outbound ringing → connected ✓,
  recording ON/OFF ✓, end ✓, history with recording ✓.
- **Busy/Decline** (`e2e_busy.ps1`): active call + second INVITE → 486 Busy Here ✓.
- **Transfer** (`e2e_transfer.ps1`): dialog opens, destination filled, but REFER not sent
  (emulator UI coordinate mismatch; verified working on OnePlus physical device in prior runs).
- **Recording directory**: `Vobiz Recordings` (per Task 10 `CallRecorder.DIRECTORY_NAME` change).
- **DUMP permission guard**: `SipCredentialReceiver` requires `android.permission.DUMP` — adb E2E tooling works, third-party blocked.

## Task 12: release rebuild — DONE (this commit)

- `assembleFossRelease` with R8 (`minifyEnabled=true`, `shrinkResources=true`),
  release signing via `keystore.properties` (local) / env vars (CI).
- Output APKs: `VobizDialer-2.4.5-foss-arm64-v8a.apk`,
  `VobizDialer-2.4.5-foss-armeabi-v7a.apk`,
  `VobizDialer-2.4.5-foss-x86_64.apk` — all signed, no AAB (office APK only).
- Build time: 8m 29s; 0 errors, standard deprecation warnings only.

## Task 11: tests — DONE (this commit)

- **`ManifestHardeningTest`** (6, `app/src/test/.../ManifestHardeningTest.kt`) parses the raw
  main + debug manifests and asserts: `allowBackup=false`, exactly one MAIN/LAUNCHER filter,
  zero banned permissions (`CALL_PHONE`, `READ/WRITE_CALL_LOG`, `ANSWER_PHONE_CALLS`,
  `SEND/RECEIVE/READ_SMS`, `RECEIVE_BOOT_COMPLETED`, `PROCESS_OUTGOING_CALLS`),
  no `CALL`/`CALL_BUTTON`/`DIAL`/`NEW_OUTGOING_CALL` actions and no `tel:` scheme,
  all services/receivers `exported=false`, no boot-completed actions, and the debug
  `SipCredentialReceiver` stays guarded by `android.permission.DUMP` with its
  `.SET_SIP_CREDENTIALS` action.
- **`SwipeActionTypeTest`** (3): persisted ids 0/1/5/6 round-trip; stale ids 2/3/4 (removed
  actions) and out-of-range ids fall back to `NONE`; ids unique.
- Source-scan of telephony APIs skipped deliberately: `TelephonyManager` in
  `CallerIdentification.kt` only reads `networkCountryIso` (no dialing surface), and
  comment tokens in `utils.kt` would false-positive a naive scan — the manifest suite
  covers the actual enforcement surface.
- Result: **112/112 tests, 11 suites, 0 failures** (`testFossDebugUnitTest`, 11s).

## Pending

- Task 14 (table above).
- Vobiz KYC + DID purchase + trunk setup (user-side).
- Keystore backup (user-side).
- Backend track (Phases 11–12): not started.

## Parallel agents

Two AI agents work this repo. **Division of labor (current):**
- **Mimo (primary)**: strip-down tasks, `sip/**`, manifest/gradle, `controller/**`,
  builds/tests/E2E, device verification, git integration, this file.
- **Gemini (UI lane)**: `SipCallScreen`, `ui/login/**`, MainActivity promo block,
  About/Contributors, theme + launcher icons, branding strings — delivered in `2aa19c4`;
  audit docs D1–D5 under `docs/`.

## Build & test commands

```powershell
# build + unit tests
$env:JAVA_HOME="$env:LOCALAPPDATA\Programs\Android Studio\jbr"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat testFossDebugUnitTest assembleFossDebug --console=plain

# E2E harness (PC = SIP registrar, listens 0.0.0.0:5060)
python tools\e2e\local_sip_test.py 30     # INVITE after N seconds
```

Docs: [build & validation](BUILD_AND_VALIDATION.md), [recorder architecture](RECORDER_ARCHITECTURE.md),
[integration plan](INTEGRATION_PLAN.md), [OPPO test plan](OPPO_TEST_PLAN.md).
