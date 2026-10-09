# API Reference

## Vobiz REST API

Base URL: `https://api.vobiz.ai/api/v1`
Auth: `X-Auth-ID` + `X-Auth-Token` headers

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/Account/{auth_id}/Endpoint/` | GET | List endpoints |
| `/Account/{auth_id}/Endpoint/{id}/` | GET | Get endpoint details |
| `/Account/{auth_id}/Endpoint/{id}/` | POST | Update endpoint |
| `/Account/{auth_id}/numbers` | GET | List numbers |
| `/Account/{auth_id}/numbers/{number}/application` | POST | Attach number to app |
| `/Account/{auth_id}/trunks` | GET | List trunks |
| `/Account/{auth_id}/Call/` | POST | Make outbound call |
| `/Account/{auth_id}/Call/` | GET | List CDRs |
| `/Account/{auth_id}/Application/{id}/` | GET | Get application |

## Backend Endpoints (Node.js)

Base URL: `https://<backend-url>`

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/answer` | POST | Vobiz webhook — returns conference XML |
| `/hangup` | POST | Vobiz webhook — logs hangup |
| `/register-token` | POST | Register device FCM token |
| `/health` | GET | Health check |

## Android Internal APIs

| Component | Function | Purpose |
|-----------|----------|---------|
| `CredentialStore` | `saveSip()`, `getSip()` | SIP credentials |
| `CredentialStore` | `saveTrunkConfig()`, `getTrunkConfig()` | Trunk credentials |
| `CredentialStore` | `saveFcmToken()`, `getFcmToken()` | FCM token |
| `SipCallController` | `placeCall()` | Initiate outbound |
| `SipCallController` | `setPendingConference()` | Store conference from FCM |
| `SipCallController` | `accept()` | Join conference or accept call |