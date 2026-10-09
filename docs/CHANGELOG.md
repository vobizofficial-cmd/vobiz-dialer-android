# Changelog

## [Unreleased]
### Added
- Conference + FCM inbound architecture
- Node.js backend for webhook + FCM
- Documentation folder for AI continuity

### Fixed
- `sip_registered: "false"` for WSS → bypassed via conference
- Emulator ringtone silent → host audio enabled
- Malformed caller ID `+9240953996` → E.164 validation

## [Previous]
- WSS relay implementation
- SDP munging for DTLS
- Caller ID selection in Settings
- Recording pipeline