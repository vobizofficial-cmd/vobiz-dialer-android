# Vobiz Hangup Codes Reference

| Code | Name | Meaning | Fix |
|------|------|---------|-----|
| 2020 | Endpoint Not Registered | Dial node can't find endpoint | Use Conference + FCM |
| 2070 | Violates Media Anchoring | Media left India | Move backend to India |
| 4000 | Normal Hangup | Call completed | — |
| 4010 | USER_BUSY | User busy in another call | Check concurrency |
| 6010 | Ring Timeout Reached | No answer within timeout | Increase timeout in XML |
| 6030 | Call Rejected | Endpoint declined | Check app state |

## How to Check

Console → Voice → Logs → Calls → click a row → look for `hangup_cause_code`.

Or via API:

```bash
curl "https://api.vobiz.ai/api/v1/Account/MA_4272LINL/Call/?limit=10" \
  -H "X-Auth-ID: MA_4272LINL" \
  -H "X-Auth-Token: <token>"
```