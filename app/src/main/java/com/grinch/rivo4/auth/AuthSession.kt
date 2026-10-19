package com.grinch.rivo4.auth

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.grinch.rivo4.sip.LinphoneService
import com.grinch.rivo4.sip.VobizSession

/** Session-level actions shared by Settings and the login flow. */
object AuthSession {

    private const val TAG = "AuthSession"

    /**
     * Clears every stored credential (SIP + API + Vobiz session + legacy files),
     * then tears down the SIP registration. After this the login gate re-appears on
     * the next activity (re)creation and the user can sign in with different credentials.
     */
    fun logout(context: Context) {
        Log.i(TAG, "logout() START - clearing all credentials")
        CredentialStore.clearAll()
        Log.i(TAG, "CredentialStore.clearAll() done")
        runCatching { VobizSession.clear(context) }
        Log.i(TAG, "VobizSession.clear() done")
        // Synchronously clear legacy preference files to prevent migration from restoring credentials
        try {
            val legacySipFile = "vobiz_sip_credentials"
            val legacySessionFile = "vobiz_session"
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            Log.i(TAG, "Clearing legacy SIP file: $legacySipFile")
            EncryptedSharedPreferences.create(
                context.applicationContext,
                legacySipFile,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ).edit().clear().commit()
            Log.i(TAG, "Legacy SIP file cleared")
            Log.i(TAG, "Clearing legacy Session file: $legacySessionFile")
            EncryptedSharedPreferences.create(
                context.applicationContext,
                legacySessionFile,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ).edit().clear().commit()
            Log.i(TAG, "Legacy Session file cleared")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear legacy files", e)
        }
        // Reset registration state FIRST to clear any stale "Registered" state
        Log.i(TAG, "Resetting LinphoneService registration state")
        LinphoneService.resetRegistrationState()
        Log.i(TAG, "Calling LinphoneService.reconfigureAndRegister()")
        LinphoneService.reconfigureAndRegister()
        // Verify state after logout
        val hasCreds = CredentialStore.hasSipCredentials()
        val regState = LinphoneService.registrationState.value
        Log.i(TAG, "Post-logout verification: hasSipCredentials=$hasCreds, registrationState=$regState")
        Log.i(TAG, "logout() COMPLETE")
    }
}