package com.grinch.rivo4.sip

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class VobizSession(
    val email: String,
    val password: String,
    val accessToken: String,
    val refreshToken: String,
    val authId: String,
    val authSecret: String,
    val accountName: String
) {
    val isLoggedIn: Boolean
        get() = authId.isNotBlank() && authSecret.isNotBlank()

    companion object {
        private const val PREFS_FILE = "vobiz_session"
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_AUTH_ID = "auth_id"
        private const val KEY_AUTH_SECRET = "auth_secret"
        private const val KEY_NAME = "account_name"

        private fun prefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        fun load(context: Context): VobizSession {
            val p = prefs(context)
            return VobizSession(
                email = p.getString(KEY_EMAIL, "").orEmpty(),
                password = p.getString(KEY_PASSWORD, "").orEmpty(),
                accessToken = p.getString(KEY_ACCESS, "").orEmpty(),
                refreshToken = p.getString(KEY_REFRESH, "").orEmpty(),
                authId = p.getString(KEY_AUTH_ID, "").orEmpty(),
                authSecret = p.getString(KEY_AUTH_SECRET, "").orEmpty(),
                accountName = p.getString(KEY_NAME, "").orEmpty()
            )
        }

        fun save(context: Context, session: VobizSession) {
            prefs(context).edit()
                .putString(KEY_EMAIL, session.email)
                .putString(KEY_PASSWORD, session.password)
                .putString(KEY_ACCESS, session.accessToken)
                .putString(KEY_REFRESH, session.refreshToken)
                .putString(KEY_AUTH_ID, session.authId)
                .putString(KEY_AUTH_SECRET, session.authSecret)
                .putString(KEY_NAME, session.accountName)
                .apply()
        }

        fun clear(context: Context) {
            prefs(context).edit().clear().apply()
        }
    }
}
