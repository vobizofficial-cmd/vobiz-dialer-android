package com.grinch.rivo4.sip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import com.grinch.rivo4.auth.CredentialStore

/**
 * Debug-only helper (src/debug - never packaged in release builds).
 *
 * Credentials:
 *   am broadcast -n "it.stivy.rivo.personal.debug/com.grinch.rivo4.sip.SipCredentialReceiver" \
 *     --es username_b64 <b64> --es password_b64 <b64> [--es domain registrar.vobiz.ai]
 *
 * Call actions (for deterministic E2E tests):
 *   --es call_action answer|decline|end|hold|record
 *   --es call_action place --es number 15001         (place outbound SIP call)
 *   --es call_action dtmf --es digits 580
 *   --es call_action transfer --es target +15551234567
 *   --es call_action state          (dumps ui state to logcat)
 */
class SipCredentialReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val callAction = intent.getStringExtra("call_action")
        if (callAction != null) {
            handleCallAction(
                context, callAction,
                intent.getStringExtra("digits") ?: "",
                intent.getStringExtra("target") ?: "",
                intent.getStringExtra("number") ?: "",
            )
            return
        }

        val userB64 = intent.getStringExtra("username_b64") ?: return
        val passB64 = intent.getStringExtra("password_b64") ?: return
        val domain = intent.getStringExtra("domain") ?: CredentialStore.DEFAULT_DOMAIN
        val user = String(Base64.decode(userB64, Base64.DEFAULT), Charsets.UTF_8)
        val pass = String(Base64.decode(passB64, Base64.DEFAULT), Charsets.UTF_8)
        Log.i("VobizSip", "Debug receiver: SIP credentials received for user=$user domain=$domain")
        CredentialStore.init(context)
        CredentialStore.saveSip(
            username = user,
            password = pass,
            domain = domain,
            transport = CredentialStore.DEFAULT_TRANSPORT,
        )
        context.stopService(Intent(context, LinphoneService::class.java))
        LinphoneService.start(context)
    }

    private fun handleCallAction(context: Context, action: String, digits: String, target: String, number: String) {
        val result: Any? = when (action) {
            "answer" -> { SipCallController.accept(context); "accepted" }
            "decline" -> { SipCallController.decline(); "declined" }
            "end" -> { SipCallController.endCall(); "ended" }
            "hold" -> SipCallController.toggleHold()
            "record" -> SipCallController.toggleRecording()
            "transfer" -> SipCallController.transferCall(target)
            "dtmf" -> digits.map { c -> SipCallController.sendDtmf(c) to c }
            "place" -> if (SipCallController.placeCall(context, number)) "placed" else "place-failed"
            "state" -> SipCallController.uiState.value
            else -> "unknown action: $action"
        }
        Log.i("VobizSip", "Debug call_action=$action digits='$digits' target='$target' number='$number' -> $result")
    }
}
