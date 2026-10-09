# Troubleshooting

## Inbound Calls Don't Ring

1. **Check backend is deployed and reachable:**
   `curl https://<backend-url>/health`

2. **Check webhook received:**
   Look at backend logs for `[ANSWER] CallUUID=...`

3. **Check FCM sent:**
   Look at backend logs for `[ANSWER] FCM sent: N success`

4. **Check device received FCM:**
   Logcat filter: `VobizFCM`

5. **Check conference join:**
   Logcat: `Joining conference: vobiz-dialer-...`

6. **Check caller hears hold music:**
   If yes → caller is parked. If no → backend XML is wrong.

## Outbound Calls Fail

1. Verify trunk domain in `CredentialStore` matches Console
2. Verify caller ID is set in Settings
3. Check Logcat for `SIP invite placed:` — the domain must match

## Registration Fails

1. Try TCP transport instead of UDP
2. Verify endpoint password matches Console
3. Check firewall allows WSS on port 5063

## App Doesn't Wake on FCM

1. Check Firebase project has Cloud Messaging enabled
2. Check `google-services.json` is in `app/`
3. Check battery optimization is disabled for the app
4. Check `POST_NOTIFICATIONS` permission is granted

## `sip_registered: "false"` Even When Registered

This is expected for WSS endpoints. See `KNOWN_ISSUES.md`.

## See Also

- `HANGUP_CODES.md` — decode Vobiz CDR hangup reasons
- `KNOWN_ISSUES.md` — platform limitations