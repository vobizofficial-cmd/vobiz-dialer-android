package com.grinch.rivo4.sip

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Incoming-call alert: a loud looping ringtone on the main speaker plus continuous
 * vibration, started when an INVITE arrives and stopped when the call is answered
 * or ends. Follows Android's ringing contract: MODE_RINGTONE while the call is
 * being signaled, ring-stream volume, ringer-mode aware vibration.
 */
class IncomingCallRinger(context: Context) {

    private val app = context.applicationContext
    private val audioManager = app.getSystemService(AudioManager::class.java)
    private val vibrator: Vibrator? = app.getSystemService(VibratorManager::class.java)?.defaultVibrator

    private var ringtone: Ringtone? = null
    private var focusRequest: AudioFocusRequest? = null
    private var ringing = false
    private var previousMode = AudioManager.MODE_NORMAL
    private val rearmHandler = Handler(Looper.getMainLooper())
    private var rearmRunnable: Runnable? = null

    @Synchronized
    fun start() {
        if (ringing) return
        ringing = true
        previousMode = runCatching { audioManager.mode }.getOrDefault(AudioManager.MODE_NORMAL)
        runCatching { audioManager.mode = AudioManager.MODE_RINGTONE }
            .onFailure { Log.w(TAG, "Setting MODE_RINGTONE failed", it) }
        requestFocus()
        startRingtoneLoop()
        startVibrationLoop()
        Log.i(TAG, "Incoming ring started (previousMode=$previousMode)")
    }

    @Synchronized
    fun stop() {
        if (!ringing) return
        ringing = false
        runCatching { ringtone?.stop() }
        ringtone = null
        rearmRunnable?.let { rearmHandler.removeCallbacks(it) }
        rearmRunnable = null
        runCatching { vibrator?.cancel() }
        focusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        focusRequest = null
        runCatching { audioManager.mode = previousMode }
            .onFailure { Log.w(TAG, "Restoring audio mode failed", it) }
        Log.i(TAG, "Incoming ring stopped")
    }

    private fun requestFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        runCatching {
            if (audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                focusRequest = request
            }
        }.onFailure { Log.w(TAG, "Audio focus request failed", it) }
    }

    private fun startRingtoneLoop() {
        var uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        if (uri == null) {
            // Emulator may have no default ringtone; fall back to a bundled resource.
            uri = RingtoneManager.getActualDefaultRingtoneUri(app, RingtoneManager.TYPE_RINGTONE)
        }
        if (uri == null) {
            Log.w(TAG, "No default ringtone uri - using silent fallback")
            return
        }
        val tone = runCatching { RingtoneManager.getRingtone(app, uri) }.getOrNull()
        if (tone == null) {
            Log.w(TAG, "Ringtone lookup failed for $uri")
            return
        }
        runCatching {
            tone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            tone.isLooping = true
            tone.play()
            ringtone = tone
        }.onFailure { Log.w(TAG, "Ringtone playback failed", it) }
    }

    private fun startVibrationLoop() {
        val ringerMode = runCatching { audioManager.ringerMode }
            .getOrDefault(AudioManager.RINGER_MODE_NORMAL)
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) return
        val v = vibrator ?: return
        if (!runCatching { v.hasVibrator() }.getOrDefault(false)) return
        if (!vibrateEffect(v)) return
        // OEMs (seen on OxygenOS) cancel/replaces repeating vibrations after a few
        // seconds. Re-issue the effect while the call rings so vibration stays continuous.
        val runnable = object : Runnable {
            override fun run() {
                if (!ringing) return
                vibrateEffect(v)
                rearmHandler.postDelayed(this, VIBE_REARM_INTERVAL_MS)
            }
        }
        rearmRunnable = runnable
        rearmHandler.postDelayed(runnable, VIBE_REARM_INTERVAL_MS)
    }

    private fun vibrateEffect(v: Vibrator): Boolean {
        val effect = runCatching {
            VibrationEffect.createWaveform(VIBE_TIMINGS_MS, VIBE_AMPLITUDES, -1)
        }.getOrNull() ?: return false
        val ok = runCatching { v.vibrate(effect); true }
            .onFailure { Log.w(TAG, "Vibration failed", it) }
            .getOrDefault(false)
        if (!ok) Log.w(TAG, "Vibration effect was rejected")
        return ok
    }

    companion object {
        private const val TAG = "VobizSip"
        private const val VIBE_REARM_INTERVAL_MS = 2500L
        private val VIBE_TIMINGS_MS = longArrayOf(0, 1000, 500, 1000, 500, 1000)
        private val VIBE_AMPLITUDES = intArrayOf(0, 255, 0, 255, 0, 255)
    }
}
