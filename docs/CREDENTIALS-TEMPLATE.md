# Credentials Template (NEVER COMMIT REAL VALUES)

## Vobiz Console

- Auth ID: `MA_XXXXXXX`
- Auth Token: `<from Console → API Credentials>`
- Account Email: `<your email>`
- Account Password: `<your password>`

## SIP Endpoint

- Username: `<from Console → Endpoints>`
- Password: `<set at creation>`
- SIP URI: `sip:<username>@registrar.vobiz.ai`
- Registrar: `registrar.vobiz.ai`
- Transport: WSS (via relay)

## Outbound Trunk

- Trunk Name: `Dialer Outbound`
- Trunk Domain: `<trunk-id>.sip.vobiz.ai`
- Credential Username: `dialer_trunk_auth`
- Credential Password: `<set at creation>`

## Backend (Environment Variables)

- `FIREBASE_SERVICE_ACCOUNT`: JSON string of service account
- `VOBIZ_AUTH_ID`: `MA_XXXXXXX`
- `VOBIZ_AUTH_TOKEN`: `<token>`

## Android

- `google-services.json`: placed in `app/` (gitignored)
- FCM Token: auto-generated, registered with backend on launch

## Numbers

- DID 1: `+91XXXXXXXXXX`
- DID 2: `+91XXXXXXXXXX`