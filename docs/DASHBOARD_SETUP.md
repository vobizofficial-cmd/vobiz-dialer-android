# Vobiz Dashboard Setup

## Application Configuration

**Voice → Applications → Vobiz WebRTC Playground**

| Field | Value |
|-------|-------|
| Application ID | `85076776948601220` |
| Answer URL | `https://<backend-url>/answer` |
| Answer Method | POST |
| Hangup URL | `https://<backend-url>/hangup` |
| Public URI | Enabled |

**Attached Numbers:**
- `+917965850027` (must be attached here)

**Endpoints:**
- `sairam8391265128911238046` (must be linked)

## Outbound Trunk Configuration

**SIP Trunk → Outbound Trunks**

| Field | Value |
|-------|-------|
| Trunk Name | `Dialer Outbound` |
| SIP Domain | `<trunk-id>.sip.vobiz.ai` |
| Auth Credential | `dialer_trunk_auth` |
| Call Recording | ON |

## Inbound Trunk Configuration

**Not used** — inbound routed via Application instead. Inbound Trunks don't support WSS.

## Credentials Reference

- Auth ID: `MA_4272LINL`
- Auth Token: (from Console → API Credentials)
- Endpoint Password: (set at creation)

## Cleanup Checklist

- [ ] Delete unused applications (e.g., `WebDialer-App` if unused)
- [ ] Delete unused endpoints (keep only `sairam8391265128911238046`)
- [ ] Verify DID attached to exactly one Application
- [ ] Verify Endpoint linked to same Application