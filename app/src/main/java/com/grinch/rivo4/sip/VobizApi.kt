package com.grinch.rivo4.sip

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class VobizApiException(val code: Int, message: String) : Exception(message)

object VobizApi {
    const val BASE = "https://api.vobiz.ai/api/v1"

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class LoginResult(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Long
    )

    data class AccountInfo(
        val authId: String,
        val authSecret: String,
        val name: String,
        val isTrial: Boolean,
        val isVerified: Boolean
    )

    data class EndpointInfo(
        val endpointId: String,
        val alias: String,
        val username: String,
        /** Full SIP URI from the platform, e.g. `sip:user@registrar.vobiz.ai` (may be synthesized when absent). */
        val sipUri: String,
        /** Resource URI of the linked application, e.g. `/v1/Account/MA_x/Application/123/` (empty if none). */
        val application: String
    )

    data class TrunkInfo(
        val trunkId: String,
        val name: String,
        val trunkDomain: String,
        /** `inbound`, `outbound` or `both`. */
        val trunkDirection: String,
        /** UUID of the SIP digest credential attached to this trunk (empty when none). */
        val credentialUuid: String
    )

    /** SIP digest credential as stored by the platform (username/password are returned by the API). */
    data class TrunkCredential(
        val id: String,
        val username: String,
        val password: String
    )

    data class NumberInfo(
        val e164: String,
        val voiceEnabled: Boolean,
        val trunkGroupId: String?,
        val applicationId: String?
    )

    data class ApplicationInfo(
        val appId: String,
        val appName: String,
        val answerUrl: String
    )

    fun login(email: String, password: String): LoginResult {
        val body = JSONObject().put("email", email).put("password", password).toString()
        val (code, text) = request("POST", "$BASE/auth/login", null, body)
        if (code != 200) throw VobizApiException(code, "Login failed (HTTP $code) ${text.take(160)}")
        val j = JSONObject(text)
        return LoginResult(
            accessToken = j.getString("access_token"),
            refreshToken = j.getString("refresh_token"),
            expiresIn = j.optLong("expires_in", 900)
        )
    }

    fun me(accessToken: String): AccountInfo {
        val headers = mapOf("Authorization" to "Bearer $accessToken")
        val (code, text) = request("GET", "$BASE/auth/me", headers)
        if (code != 200) throw VobizApiException(code, "Account fetch failed (HTTP $code)")
        val j = JSONObject(text)
        return AccountInfo(
            authId = j.getString("auth_id"),
            authSecret = j.getString("auth_secret"),
            name = j.optString("name", j.optString("email", "")),
            isTrial = j.optBoolean("is_trial_account"),
            isVerified = j.optBoolean("is_verified")
        )
    }

    fun listEndpoints(authId: String, token: String): JSONArray {
        val (code, text) = request("GET", "$BASE/Account/$authId/Endpoint/", authHeaders(authId, token))
        check2xx(code, "List endpoints", text)
        return JSONObject(text).optJSONArray("objects") ?: JSONArray()
    }

    fun listEndpointsFull(authId: String, token: String): List<EndpointInfo> {
        val objects = listEndpoints(authId, token)
        return buildList {
            for (i in 0 until objects.length()) {
                val o = objects.getJSONObject(i)
                val username = o.optString("username")
                add(
                    EndpointInfo(
                        endpointId = o.optString("endpoint_id"),
                        alias = o.optString("alias"),
                        username = username,
                        sipUri = o.optString("sip_uri").ifEmpty { "sip:$username@registrar.vobiz.ai" },
                        application = o.optString("application", "")
                    )
                )
            }
        }
    }

    fun listTrunks(authId: String, token: String): List<TrunkInfo> {
        val (code, text) = request("GET", "$BASE/Account/$authId/trunks", authHeaders(authId, token))
        check2xx(code, "List trunks", text)
        val objects = JSONObject(text).optJSONArray("objects") ?: JSONArray()
        return buildList {
            for (i in 0 until objects.length()) {
                val t = objects.getJSONObject(i)
                add(
                    TrunkInfo(
                        trunkId = t.optString("trunk_id"),
                        name = t.optString("name"),
                        trunkDomain = t.optString("trunk_domain"),
                        trunkDirection = t.optString("trunk_direction"),
                        credentialUuid = t.optString("credential_uuid", "")
                    )
                )
            }
        }
    }

    /**
     * Creates an OUTBOUND trunk carrying [name] attached to [credentialId].
     * The digest credential itself is created/adopted via [ensureCredential] first,
     * so the caller always knows the password that ends up on the platform.
     */
    fun createOutboundTrunk(
        authId: String,
        token: String,
        name: String,
        credentialId: String
    ): TrunkInfo {
        val body = JSONObject()
            .put("name", name)
            .put("trunk_direction", "outbound")
            .put("credential_uuid", credentialId)
            .toString()
        val (code, text) = request("POST", "$BASE/Account/$authId/trunks", authHeaders(authId, token), body)
        check2xx(code, "Create outbound trunk", text)
        val j = JSONObject(text)
        return TrunkInfo(
            trunkId = j.getString("trunk_id"),
            name = j.getString("name"),
            trunkDomain = j.getString("trunk_domain"),
            trunkDirection = j.optString("trunk_direction", "outbound"),
            credentialUuid = j.optString("credential_uuid", credentialId)
        )
    }

    /** Lists SIP digest credentials. GET /Account/{auth_id}/trunks/credentials - returns username and password. */
    fun listCredentials(authId: String, token: String): List<TrunkCredential> {
        val (code, text) = request("GET", "$BASE/Account/$authId/trunks/credentials", authHeaders(authId, token))
        check2xx(code, "List credentials", text)
        val objects = JSONObject(text).optJSONArray("objects") ?: JSONArray()
        return buildList {
            for (i in 0 until objects.length()) {
                val c = objects.getJSONObject(i)
                add(
                    TrunkCredential(
                        id = c.optString("id"),
                        username = c.optString("username"),
                        password = c.optString("password")
                    )
                )
            }
        }
    }

    /** Finds a credential by UUID (via [listCredentials]); null when not present. */
    fun getCredential(authId: String, token: String, credentialId: String): TrunkCredential? =
        listCredentials(authId, token).find { it.id == credentialId }

    /**
     * Ensures a SIP digest credential with [username] exists and returns it.
     * POST /Account/{auth_id}/credentials; a 409 (already exists) ADOPTS the
     * credential as the platform stores it - the server-side password wins, so
     * the app never has to know or rotate a dashboard-configured secret.
     */
    fun ensureCredential(authId: String, token: String, username: String, password: String): TrunkCredential {
        val createBody = JSONObject()
            .put("username", username)
            .put("password", password)
            .toString()
        val (code, text) = request("POST", "$BASE/Account/$authId/credentials", authHeaders(authId, token), createBody)
        if (code in 200..299) {
            val id = JSONObject(text).optString("id")
            if (id.isNotEmpty()) return TrunkCredential(id, username, password)
        } else if (code != 409) {
            throw VobizApiException(code, "Create credential failed (HTTP $code) ${text.take(160)}")
        }
        return listCredentials(authId, token).find { it.username == username }
            ?: throw VobizApiException(code, "Credential $username exists but could not be listed")
    }

    /**
     * Lists all voice applications. GET /Account/{auth_id}/Application/ - response field is `objects`.
     */
    fun listApplications(authId: String, token: String): List<ApplicationInfo> {
        val (code, text) = request("GET", "$BASE/Account/$authId/Application/", authHeaders(authId, token))
        check2xx(code, "List applications", text)
        val objects = JSONObject(text).optJSONArray("objects") ?: JSONArray()
        return buildList {
            for (i in 0 until objects.length()) {
                val a = objects.getJSONObject(i)
                add(
                    ApplicationInfo(
                        appId = a.optString("app_id"),
                        appName = a.optString("app_name"),
                        answerUrl = a.optString("answer_url")
                    )
                )
            }
        }
    }

    /** Creates a voice application. POST /Account/{auth_id}/Application/ */
    fun createApplication(
        authId: String,
        token: String,
        appName: String,
        answerUrl: String,
        answerMethod: String
    ): ApplicationInfo {
        val body = JSONObject()
            .put("app_name", appName)
            .put("answer_url", answerUrl)
            .put("answer_method", answerMethod)
            .toString()
        val (code, text) = request("POST", "$BASE/Account/$authId/Application/", authHeaders(authId, token), body)
        check2xx(code, "Create application", text)
        val j = JSONObject(text)
        return ApplicationInfo(
            appId = j.optString("app_id"),
            appName = j.optString("app_name", appName),
            answerUrl = j.optString("answer_url", answerUrl)
        )
    }

    /** Fetches a single voice application. GET /Account/{auth_id}/Application/{app_id}/ */
    fun getApplication(authId: String, token: String, appId: String): ApplicationInfo {
        val (code, text) = request("GET", "$BASE/Account/$authId/Application/$appId/", authHeaders(authId, token))
        check2xx(code, "Fetch application", text)
        val j = JSONObject(text)
        return ApplicationInfo(
            appId = j.optString("app_id", appId),
            appName = j.optString("app_name"),
            answerUrl = j.optString("answer_url")
        )
    }

    /** Updates a voice application's Answer URL. POST /Account/{auth_id}/Application/{app_id}/ */
    fun updateApplicationAnswerUrl(authId: String, token: String, appId: String, answerUrl: String) {
        val body = JSONObject().put("answer_url", answerUrl).toString()
        val (code, text) = request(
            "POST", "$BASE/Account/$authId/Application/$appId/", authHeaders(authId, token), body
        )
        check2xx(code, "Update application answer URL", text)
    }

    /**
     * Routes a purchased number to a voice application so inbound calls hit its
     * Answer URL. POST /Account/{auth_id}/numbers/{e164}/application
     * (number URL-encoded, `+` -> `%2B`). Re-attaching overwrites a previous
     * application binding; a trunk binding must be removed first via
     * [detachNumberFromTrunk] (attach returns HTTP 400 otherwise).
     */
    fun attachNumberToApplication(authId: String, token: String, e164: String, applicationId: String) {
        val encoded = java.net.URLEncoder.encode(e164, "UTF-8")
        val body = JSONObject().put("application_id", applicationId).toString()
        val (code, text) = request(
            "POST", "$BASE/Account/$authId/numbers/$encoded/application", authHeaders(authId, token), body
        )
        check2xx(code, "Attach number to application", text)
    }

    /** Removes a number's trunk binding. DELETE /Account/{auth_id}/numbers/{e164}/assign */
    fun detachNumberFromTrunk(authId: String, token: String, e164: String) {
        val encoded = java.net.URLEncoder.encode(e164, "UTF-8")
        val (code, text) = request(
            "DELETE", "$BASE/Account/$authId/numbers/$encoded/assign", authHeaders(authId, token)
        )
        check2xx(code, "Unassign number from trunk", text)
    }

    /**
     * Links a SIP endpoint to a voice application - calls placed to the endpoint
     * (and calls the application dials back to the endpoint) hit this app's
     * Answer URL. POST /Account/{auth_id}/Endpoint/{endpoint_id}/
     * with `application` as the numeric app id (docs: integer).
     */
    fun updateEndpointApplication(authId: String, token: String, endpointId: String, applicationId: String) {
        val applicationValue: Any = applicationId.toLongOrNull() ?: applicationId
        val body = JSONObject().put("application", applicationValue).toString()
        val (code, text) = request(
            "POST", "$BASE/Account/$authId/Endpoint/$endpointId/", authHeaders(authId, token), body
        )
        check2xx(code, "Update endpoint application", text)
    }

    /** Lists purchased numbers. GET /Account/{auth_id}/numbers - response field is `items`. */
    fun listNumbers(authId: String, token: String): List<NumberInfo> {
        return runCatching {
            val (code, text) = request("GET", "$BASE/Account/$authId/numbers", authHeaders(authId, token))
            if (code !in 200..299) return emptyList()
            val items = JSONObject(text).optJSONArray("items") ?: JSONArray()
            val list = mutableListOf<NumberInfo>()
            for (i in 0 until items.length()) {
                val n = items.getJSONObject(i)
                val num = n.optString("e164", n.optString("number", n.optString("phone_number", "")))
                if (num.isEmpty()) continue
                list.add(
                    NumberInfo(
                        e164 = num,
                        voiceEnabled = n.optBoolean("voice_enabled", true),
                        trunkGroupId = n.optString("trunk_group_id", "").ifEmpty { null },
                        applicationId = n.optString("application_id", "").ifEmpty { null }
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    fun createEndpoint(
        authId: String,
        token: String,
        username: String,
        password: String,
        alias: String
    ): JSONObject {
        val body = JSONObject()
            .put("username", username)
            .put("password", password)
            .put("alias", alias)
            .put("allow_voice", true)
        val (code, text) = request("POST", "$BASE/Account/$authId/Endpoint/", authHeaders(authId, token), body.toString())
        check2xx(code, "Create endpoint", text)
        return JSONObject(text)
    }

    fun updateEndpointPassword(authId: String, token: String, endpointId: String, password: String) {
        val body = JSONObject().put("password", password)
        val (code, text) = request("POST", "$BASE/Account/$authId/Endpoint/$endpointId/", authHeaders(authId, token), body.toString())
        check2xx(code, "Update endpoint", text)
    }

    private fun authHeaders(authId: String, token: String) =
        mapOf("X-Auth-ID" to authId, "X-Auth-Token" to token)

    private fun check2xx(code: Int, op: String, text: String) {
        if (code !in 200..299) throw VobizApiException(code, "$op failed (HTTP $code) ${text.take(160)}")
    }

    private fun request(
        method: String,
        url: String,
        headers: Map<String, String>?,
        body: String? = null
    ): Pair<Int, String> {
        val builder = Request.Builder().url(url)
        headers?.forEach { (k, v) -> builder.header(k, v) }
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post((body ?: "{}").toRequestBody(JSON_TYPE))
            "PUT" -> builder.put((body ?: "{}").toRequestBody(JSON_TYPE))
            else -> builder.method(method, null)
        }
        client.newCall(builder.build()).execute().use { resp ->
            return resp.code to (resp.body?.string() ?: "")
        }
    }
}
