package com.grinch.rivo4.fcm

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.grinch.rivo4.sip.LinphoneService
import com.grinch.rivo4.sip.SipCallController

class VobizMessagingService : FirebaseMessagingService() {
    private val TAG = "VobizFCM"

    override fun onMessageReceived(msg: RemoteMessage) {
        Log.i(TAG, "FCM received: ${msg.data}")
        if (msg.data["type"] == "incoming_call") {
            val conference = msg.data["conference_name"] ?: return
            val caller = msg.data["caller_number"] ?: "Unknown"

            SipCallController.setPendingConference(conference, caller)
            LinphoneService.start(applicationContext)
            SipCallController.showIncomingCallFromFcm(applicationContext, caller, conference)
        }
    }

    override fun onNewToken(token: String) {
        Log.i(TAG, "New FCM token: $token")
        com.grinch.rivo4.auth.CredentialStore.saveFcmToken(token)
        // Send token to backend
        com.grinch.rivo4.fcm.TokenRegistrar.registerAsync(applicationContext, token)
    }
}