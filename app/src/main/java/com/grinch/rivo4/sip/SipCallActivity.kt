package com.grinch.rivo4.sip

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.grinch.rivo4.view.screen.SipCallScreen
import com.grinch.rivo4.view.theme.Rivo4Theme
import kotlinx.coroutines.delay

class SipCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "SipCallActivity created")
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handleIntent(intent)

        setContent {
            Rivo4Theme {
                val state by SipCallController.uiState.collectAsState()

                LaunchedEffect(state.active) {
                    if (!state.active) {
                        delay(1200)
                        finish()
                    }
                }

                SipCallScreen(
                    state = state,
                    onAccept = { SipCallController.accept(this@SipCallActivity) },
                    onDecline = { SipCallController.decline() },
                    onEnd = { SipCallController.endCall() },
                    onToggleMute = { SipCallController.toggleMute() },
                    onToggleSpeaker = { SipCallController.toggleSpeaker() },
                    onToggleHold = { SipCallController.toggleHold() },
                    onToggleBluetooth = { SipCallController.toggleBluetooth() },
                    onToggleRecord = { SipCallController.toggleRecording() },
                    onTransfer = { target -> SipCallController.transferCall(target) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.i(TAG, "SipCallActivity onNewIntent action=${intent.action} extra=${intent.getStringExtra(EXTRA_ACTION)}")
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.getStringExtra(EXTRA_ACTION)) {
            ACTION_ACCEPT -> SipCallController.accept(this)
            ACTION_DECLINE -> SipCallController.decline()
            ACTION_END -> SipCallController.endCall()
        }
        intent?.removeExtra(EXTRA_ACTION)
    }

    companion object {
        private const val TAG = "VobizSip"
        const val EXTRA_ACTION = "vobiz.sip.action"
        const val ACTION_ACCEPT = "accept"
        const val ACTION_DECLINE = "decline"
        const val ACTION_END = "end"
    }
}
