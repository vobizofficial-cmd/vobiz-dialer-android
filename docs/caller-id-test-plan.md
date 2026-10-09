# Real-Device Caller ID Verification & Test Plan

## Overview
This document specifies the manual test plan to verify Caller ID override functionality for **Vobiz Personal** on Android.

Caller ID selection lives **only in Settings → Outbound Caller ID**. The Dialpad no longer shows a `From: +...` chip; each outgoing call simply uses the saved selection.

---

## Prerequisites
1. Active Vobiz account logged in on Vobiz Personal app.
2. Vobiz Web Console access (`https://console.vobiz.ai`).
3. Active Vobiz SIP Trunk with outbound calling enabled.
4. Target recipient test phone (mobile or landline).

---

## Step-by-Step Test Execution Plan

### Step 1: Vobiz Console Trunk Verification
- Log in to `https://console.vobiz.ai`.
- Navigate to: **SIP Trunk → Outbound Trunks → [Your Active Trunk] → Settings**.
- Verify **"Allow Custom Caller ID"** (or **"Caller ID Override"**) is enabled.
- Verify that your bought DIDs are listed under **Verified Numbers**.

### Step 2: Open the Caller ID Card
1. Launch **Vobiz Personal** on the test device.
2. Open **Settings** (avatar on the main screen).
3. Scroll to **Outbound Caller ID**.
4. Verify the card description: *"Choose which number appears as the caller on outgoing calls. Changes apply only after Save."*
5. Verify the available options:
   - **Use default trunk number** — *Vobiz will use the trunk's configured caller ID*
   - Each account DID (e.g., `+917965850027`, `+919240953996`) — *Vobiz account number*
   - **Custom number** — *E.164, must be verified on Vobiz*

### Step 3: Test DID #1 Selection & Outbound Call
1. In **Settings → Outbound Caller ID**, select **DID #1** (e.g., `+1 555 019 2834`).
2. Tap **Save**. Verify the confirmation snackbar (*Caller ID saved - re-registering*) appears.
3. Place a call to the recipient test phone from the Dialpad.
4. **Expected Result**:
   - Recipient test phone displays caller ID `+1 555 019 2834`.
   - Logcat shows `Outbound auth registered: from=+15550192834 ...` after the save.

### Step 4: Test DID #2 Selection & Outbound Call
1. Return to **Settings → Outbound Caller ID**.
2. Select **DID #2** (e.g., `+1 555 019 2835`) and tap **Save**.
3. Place a call to the recipient test phone.
4. **Expected Result**:
   - Recipient test phone displays caller ID `+1 555 019 2835`.

### Step 5: Test Custom Verified Number Input
1. In **Settings → Outbound Caller ID**, select **Custom number**.
2. Enter a custom E.164 number verified on your Vobiz account (e.g., `+919876543210`).
3. Tap **Save**.
4. Place a call to the recipient test phone.
5. **Expected Result**:
   - Recipient test phone displays caller ID `+919876543210`.

### Step 6: Test Default Trunk Caller ID
1. In **Settings → Outbound Caller ID**, select **Use default trunk number**.
2. Tap **Save**.
3. Place a call to the recipient test phone.
4. **Expected Result**:
   - Recipient test phone displays the default trunk caller ID configured on Vobiz Console.

### Step 7: Save-Disabled Behavior
1. Open **Settings → Outbound Caller ID** without changing the selection.
2. **Expected Result**: **Save** is disabled until the selection differs from the stored value.
3. Change the selection and enter an invalid custom value (e.g., `123`).
4. **Expected Result**: **Save** stays disabled for non-E.164 input.

### Step 8: Direct SIP Login Mode
1. Log in with **SIP credentials** (Use SIP credentials instead) instead of the Vobiz account.
2. Open **Settings → Outbound Caller ID**.
3. **Expected Result**: the card is grayed out/disabled with the subtitle *"Fixed to the number assigned to this SIP account"*; no Save button.

### Step 9: Vobiz CDR Verification
1. Log in to `https://console.vobiz.ai` -> **Reports → CDR**.
2. Check the `from_number` column for the test calls placed above.
3. **Expected Result**: The `from_number` recorded in each Vobiz CDR entry matches the caller ID selected in the app for that call.

---

## Verified (2026-10-01, emulator E2E)
- Step 2/3/7 flow: selecting `+919240953996` + **Save** → logcat `Outbound auth registered: from=+919240953996 digest=dialer_trunk_auth domain=08ecd76e.sip.vobiz.ai`, re-registration `Registration state: Ok`, and the subsequent outbound call presented the selected DID as caller ID (CDR `from_number=+917965850027` for the default-trunk call, `+919240953996` when selected).
- Outbound calling also verified by the user on both emulator and a physical device.
