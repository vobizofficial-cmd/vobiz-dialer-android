# Known Issues

## Platform Limitations (Vobiz)

### ISSUE-001: `sip_registered` Never True for WSS

**Impact:** Voice Application `<Dial><User>` cannot route to WSS endpoints.

**Status:** Platform bug — awaiting Vobiz fix.

**Workaround:** Conference + FCM (see `INBOUND_FLOW.md`).

### ISSUE-002: Inbound Trunks Don't Support WSS

**Impact:** Cannot use Inbound Trunk's Primary URI for WSS endpoints.

**Status:** Platform limitation.

**Workaround:** Same — Conference + FCM.

### ISSUE-003: RTC Demo Fallback

**Impact:** When Voice App Dial fails, platform routes to RTC demo which answers with "user busy" (code 4010).

**Status:** Expected behavior of the fallback.

**Workaround:** Conference + FCM prevents fallback by always parking.

## App Limitations

### ISSUE-101: Emulator Ringtone Silent

**Impact:** No audible ringtone in emulator.

**Cause:** Emulator blocks host audio by default.

**Workaround:** Extended Controls → Microphone → "Virtual microphone uses host audio input".

### ISSUE-102: Battery Optimization Kills App

**Impact:** Some OEMs kill background services.

**Workaround:** Prompt user for battery exemption on first launch.

## See Also

- `HANGUP_CODES.md` — decode failures
- `TROUBLESHOOTING.md` — step-by-step fixes