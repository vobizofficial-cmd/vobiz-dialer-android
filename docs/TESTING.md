# Testing Guide

## Unit Tests

```bash
./gradlew :app:testFossDebugUnitTest
```

Current count: ~144 tests

## E2E Tests

```bash
cd tools/e2e/
./run-inbound.sh
./run-outbound.sh
./run-busy.sh
./run-transfer.sh
```

## Manual Test Matrix

| Test | Setup | Expected |
|------|-------|----------|
| Outbound | App open, registered | Call connects |
| Inbound (app open) | App in foreground | Ring + UI + answer |
| Inbound (app killed) | Force-stop app | FCM wakes, ring + UI |
| Inbound (screen locked) | Lock device | Full-screen UI on lock screen |
| Conference join | Answer inbound | Two-way audio |
| Missed call | Don't answer | History shows missed |
| Recording | Answer call | File written to cacheDir |
| Battery optimization | Doze mode | Still rings |

## Backend Tests

```bash
# Health check
curl https://<backend-url>/health

# Simulate inbound webhook
curl -X POST https://<backend-url>/answer \
  -d "CallUUID=test&From=%2B919123151351&To=%2B917965850027"

# Register token
curl -X POST https://<backend-url>/register-token \
  -H "Content-Type: application/json" \
  -d '{"token":"<fcm-token>","deviceId":"test-device"}'
```