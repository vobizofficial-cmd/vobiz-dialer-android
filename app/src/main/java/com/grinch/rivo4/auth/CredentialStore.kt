package com.grinch.rivo4.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class SipCredentials(
    val username: String,
    val password: String,
    val domain: String,
    val transport: String,
)

data class ApiCredentials(
    val authId: String,
    val authToken: String,
)

data class TrunkConfig(
    val username: String,
    val password: String,
    val domain: String,
)

sealed class CallerIdMode {
    data class Did(val number: String) : CallerIdMode()
    data class Custom(val number: String) : CallerIdMode()
    object Default : CallerIdMode()
}

/**
 * Single source of truth for credentials on this device.
 *
 * Backed by Android Keystore (MasterKey AES256-GCM) with
 * EncryptedSharedPreferences key/value encryption. Must be initialized
 * once from [com.grinch.rivo4.RivoApp.onCreate] before first use.
 *
 * SIP credentials and Vobiz API credentials are different credentials:
 * - SIP credentials authenticate SIP registration/calling.
 * - API credentials are stored only for future backend forwarding;
 *   the app itself never calls the Vobiz API with them.
 */
object CredentialStore {

    enum class LoginMode {
        ACCOUNT,
        SIP
    }

    private const val PREFS_FILE = "vobiz_credentials"
    private const val KEY_SIP_USERNAME = "sip_username"
    private const val KEY_SIP_PASSWORD = "sip_password"
    private const val KEY_SIP_DOMAIN = "sip_domain"
    private const val KEY_SIP_TRANSPORT = "sip_transport"
    private const val KEY_API_AUTH_ID = "api_auth_id"
    private const val KEY_API_AUTH_TOKEN = "api_auth_token"
    private const val KEY_TRUNK_USERNAME = "trunk_username"
    private const val KEY_TRUNK_PASSWORD = "trunk_password"
    private const val KEY_TRUNK_DOMAIN = "trunk_domain"
    private const val KEY_DIDS = "vobiz_dids"
    private const val KEY_SELECTED_DID = "vobiz_selected_did"
    private const val KEY_APPLICATION_ID = "vobiz_application_id"
    private const val KEY_CALLER_ID_TYPE = "vobiz_caller_id_type"
    private const val KEY_CALLER_ID_NUMBER = "vobiz_caller_id_number"
    private const val KEY_LOGIN_MODE = "vobiz_login_mode"
    private const val KEY_FCM_TOKEN = "fcm_token"
    private const val KEY_DEVICE_ID = "device_id"

    private const val LEGACY_SIP_FILE = "vobiz_sip_credentials"
    private const val LEGACY_SESSION_FILE = "vobiz_session"

    const val DEFAULT_DOMAIN = "registrar.vobiz.ai"

    /**
     * Transport used for registration/calling.
     * UDP (native) is now the default so the FreeSWITCH location service sees
     * the endpoint as registered for PSTN→DID <Dial> routing. WSS bridge was
     * a workaround but broke Dial routing (FreeSWITCH doesn't share WSS
     * location store).
     */
    const val DEFAULT_TRANSPORT = "UDP"

    private val lock = Any()

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        synchronized(lock) {
            if (prefs != null) return
            val appContext = context.applicationContext
            prefs = createPrefs(appContext, PREFS_FILE)
            migrateLegacyData(appContext)
        }
    }

    private fun storage(): SharedPreferences =
        prefs ?: error("CredentialStore.init(context) must be called from Application.onCreate before use")

    fun saveSip(username: String, password: String, domain: String, transport: String) {
        storage().edit()
            .putString(KEY_SIP_USERNAME, username.trim())
            .putString(KEY_SIP_PASSWORD, password)
            .putString(KEY_SIP_DOMAIN, domain.trim().ifBlank { DEFAULT_DOMAIN })
            .putString(KEY_SIP_TRANSPORT, transport.trim().uppercase())
            .commit()
    }

    fun getSip(): SipCredentials? {
        val p = storage()
        val username = p.getString(KEY_SIP_USERNAME, "").orEmpty()
        val password = p.getString(KEY_SIP_PASSWORD, "").orEmpty()
        if (username.isEmpty() || password.isEmpty()) return null
        return SipCredentials(
            username = username,
            password = password,
            domain = p.getString(KEY_SIP_DOMAIN, DEFAULT_DOMAIN) ?: DEFAULT_DOMAIN,
            transport = p.getString(KEY_SIP_TRANSPORT, DEFAULT_TRANSPORT) ?: DEFAULT_TRANSPORT,
        )
    }

    fun saveApi(authId: String, authToken: String) {
        storage().edit()
            .putString(KEY_API_AUTH_ID, authId.trim())
            .putString(KEY_API_AUTH_TOKEN, authToken)
            .commit()
    }

    fun getApi(): ApiCredentials? {
        val p = storage()
        val authId = p.getString(KEY_API_AUTH_ID, "").orEmpty()
        val authToken = p.getString(KEY_API_AUTH_TOKEN, "").orEmpty()
        if (authId.isEmpty() || authToken.isEmpty()) return null
        return ApiCredentials(authId = authId, authToken = authToken)
    }

    fun saveTrunkConfig(username: String, password: String, domain: String) {
        val u = username.trim()
        val p = password
        val d = domain.trim()
        if (u.isEmpty() || p.isEmpty() || d.isEmpty()) {
            clearTrunkConfig()
            return
        }
        storage().edit()
            .putString(KEY_TRUNK_USERNAME, u)
            .putString(KEY_TRUNK_PASSWORD, p)
            .putString(KEY_TRUNK_DOMAIN, d)
            .commit()
    }

    fun getTrunkConfig(): TrunkConfig? {
        val p = storage()
        val username = p.getString(KEY_TRUNK_USERNAME, "").orEmpty()
        val password = p.getString(KEY_TRUNK_PASSWORD, "").orEmpty()
        val domain = p.getString(KEY_TRUNK_DOMAIN, "").orEmpty()
        if (username.isEmpty() || password.isEmpty() || domain.isEmpty()) return null
        return TrunkConfig(username = username, password = password, domain = domain)
    }

    fun clearTrunkConfig() {
        storage().edit()
            .remove(KEY_TRUNK_USERNAME)
            .remove(KEY_TRUNK_PASSWORD)
            .remove(KEY_TRUNK_DOMAIN)
            .commit()
    }

    fun hasSipCredentials(): Boolean = getSip() != null

    fun saveDids(dids: Set<String>) {
        storage().edit().putStringSet(KEY_DIDS, dids).commit()
    }

    fun getDids(): Set<String> =
        storage().getStringSet(KEY_DIDS, null)?.toSet() ?: emptySet()

    fun saveCallerIdMode(mode: CallerIdMode) {
        val editor = storage().edit()
        when (mode) {
            is CallerIdMode.Did -> {
                editor.putString(KEY_CALLER_ID_TYPE, "did")
                editor.putString(KEY_CALLER_ID_NUMBER, mode.number.trim())
                editor.putString(KEY_SELECTED_DID, mode.number.trim())
            }
            is CallerIdMode.Custom -> {
                editor.putString(KEY_CALLER_ID_TYPE, "custom")
                editor.putString(KEY_CALLER_ID_NUMBER, mode.number.trim())
                editor.putString(KEY_SELECTED_DID, mode.number.trim())
            }
            CallerIdMode.Default -> {
                editor.putString(KEY_CALLER_ID_TYPE, "default")
                editor.remove(KEY_CALLER_ID_NUMBER)
                editor.remove(KEY_SELECTED_DID)
            }
        }
        editor.commit()
    }

    fun getCallerIdMode(): CallerIdMode {
        val p = storage()
        val type = p.getString(KEY_CALLER_ID_TYPE, null)
        val num = p.getString(KEY_CALLER_ID_NUMBER, p.getString(KEY_SELECTED_DID, null)).orEmpty()
        return when (type) {
            "did" -> if (num.isNotEmpty()) CallerIdMode.Did(num) else CallerIdMode.Default
            "custom" -> if (num.isNotEmpty()) CallerIdMode.Custom(num) else CallerIdMode.Default
            "default" -> CallerIdMode.Default
            else -> if (num.isNotEmpty()) CallerIdMode.Did(num) else CallerIdMode.Default
        }
    }

    fun getEffectiveCallerId(): String? = when (val mode = getCallerIdMode()) {
        is CallerIdMode.Did -> mode.number.ifEmpty { null }
        is CallerIdMode.Custom -> mode.number.ifEmpty { null }
        CallerIdMode.Default -> null
    }

    fun selectDid(did: String?) {
        if (did.isNullOrBlank()) {
            saveCallerIdMode(CallerIdMode.Default)
        } else {
            saveCallerIdMode(CallerIdMode.Did(did))
        }
    }

    fun getSelectedDid(): String? = getEffectiveCallerId()

    fun saveApplicationId(applicationId: String) {
        storage().edit().putString(KEY_APPLICATION_ID, applicationId).commit()
    }

    fun getApplicationId(): String? {
        return storage().getString(KEY_APPLICATION_ID, null)
    }

    fun saveLoginMode(mode: LoginMode) {
        storage().edit()
            .putString(KEY_LOGIN_MODE, mode.name)
            .commit()
    }

    fun getLoginMode(): LoginMode {
        val p = storage()
        val modeStr = p.getString(KEY_LOGIN_MODE, LoginMode.SIP.name) ?: LoginMode.SIP.name
        return LoginMode.valueOf(modeStr)
    }

    fun clearSip() {
        storage().edit()
            .remove(KEY_SIP_USERNAME)
            .remove(KEY_SIP_PASSWORD)
            .remove(KEY_SIP_DOMAIN)
            .remove(KEY_SIP_TRANSPORT)
            .commit()
    }

    fun clearAll() {
        storage().edit().clear().commit()
    }

    fun saveFcmToken(token: String) {
        storage().edit().putString(KEY_FCM_TOKEN, token).commit()
    }

    fun getFcmToken(): String? {
        val p = storage()
        val token = p.getString(KEY_FCM_TOKEN, null)
        return if (token.isNullOrBlank()) null else token
    }

    fun getDeviceId(): String? {
        val p = storage()
        val id = p.getString(KEY_DEVICE_ID, null)
        if (id.isNullOrBlank()) {
            val newId = java.util.UUID.randomUUID().toString()
            storage().edit().putString(KEY_DEVICE_ID, newId).commit()
            return newId
        }
        return id
    }

    private fun createPrefs(context: Context, file: String): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            file,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * One-time copy of credentials written by earlier builds
     * (SipAccountConfig / VobizSession files). Runs only when the new
     * store has no SIP entry yet, so re-installs over old data keep working.
     */
    private fun migrateLegacyData(context: Context) {
        if (hasSipCredentials()) return
        runCatching {
            val legacySip = createPrefs(context, LEGACY_SIP_FILE)
            val username = legacySip.getString("sip_username", "").orEmpty()
            val password = legacySip.getString("sip_password", "").orEmpty()
            if (username.isNotEmpty() && password.isNotEmpty()) {
                saveSip(
                    username = username,
                    password = password,
                    domain = legacySip.getString("sip_domain", DEFAULT_DOMAIN) ?: DEFAULT_DOMAIN,
                    transport = DEFAULT_TRANSPORT,
                )
            }
        }
        runCatching {
            if (getApi() == null) {
                val legacySession = createPrefs(context, LEGACY_SESSION_FILE)
                val authId = legacySession.getString("auth_id", "").orEmpty()
                val authSecret = legacySession.getString("auth_secret", "").orEmpty()
                if (authId.isNotEmpty() && authSecret.isNotEmpty()) {
                    saveApi(authId, authSecret)
                }
            }
        }
    }
}
