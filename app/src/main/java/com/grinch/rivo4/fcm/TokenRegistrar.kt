package com.grinch.rivo4.fcm

import android.content.Context
import android.util.Log
import com.grinch.rivo4.BuildConfig
import com.grinch.rivo4.auth.CredentialStore
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL

object TokenRegistrar {
    private const val TAG = "VobizFCM"
    private val BACKEND_URL = "${BuildConfig.VOBIZ_BACKEND_URL}/register-token"

    fun registerAsync(context: Context, token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val deviceId = CredentialStore.getDeviceId() ?: android.provider.Settings.Secure.getString(
                    context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
                )
                val url = URL(BACKEND_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                val body = """{"token":"$token","deviceId":"$deviceId"}"""
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                Log.i(TAG, "Token registered: HTTP $code")
            } catch (e: Exception) {
                Log.e(TAG, "Token registration failed", e)
            }
        }
    }
}