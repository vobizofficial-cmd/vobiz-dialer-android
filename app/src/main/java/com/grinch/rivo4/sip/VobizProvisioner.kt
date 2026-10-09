package com.grinch.rivo4.sip

import android.content.Context
import android.util.Log
import com.grinch.rivo4.auth.CredentialStore
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.random.Random

/**
 * Signs in to the Vobiz REST API with the account email/password and makes sure a
 * SIP endpoint exists for this app, then persists the resulting SIP credentials in
 * [CredentialStore]. Registration itself is handled by [LinphoneService] and observed
 * by the login ViewModel - this class only provisions.
 *
 * Nothing account-specific is hardcoded: numbers, the voice application, the
 * endpoint and the outbound trunk are all discovered from the logged-in account
 * (i.e. whatever the Vobiz dashboard configured), and every secret is either
 * adopted from the platform (`trunk` digest credentials, returned by the API)
 * or freshly generated at provision time (`endpoint` password, which the API
 * never returns, so the app sets it and uses the value it just wrote).
 *
 * Returns [ProvisionResult] with all credentials needed for SIP registration and calling.
 */
object VobizProvisioner {
    const val ENDPOINT_ALIAS = "VobizDialer"
    const val OUTBOUND_TRUNK_NAME = "VobizDialer-Outbound"
    const val PLAYGROUND_APP_NAME = "Vobiz WebRTC Playground"

    /**
     * Endpoint-aware hosted Answer-URL service, used ONLY when the account has no
     * configured voice application yet (or only a browser-only one such as
     * rtc-demo.vobiz.ai that cannot ring a SIP endpoint). The callerId/endpoint
     * parameters are filled from the logged-in account at runtime.
     */
    private const val ANSWER_SERVICE_URL = "https://vobiz-browser-buddy.lovable.app/api/public/vobiz/answer"

    /** Trunk digest credential username convention for credentials the app must create. */
    private const val TRUNK_CREDENTIAL_USERNAME = "dialer_trunk_auth"

    data class ProvisionResult(
        val endpointUsername: String,
        val endpointPassword: String,
        val endpointDomain: String,
        val trunkUsername: String,
        val trunkPassword: String,
        val trunkDomain: String,
        val status: String
    )

    private const val TAG = "VobizProvisioner"

    /**
     * Full auto-provisioning flow:
     * 1. Login to Vobiz API
     * 2. Fetch purchased numbers (they carry the dashboard's application binding)
     * 3. Discover the inbound voice application (number binding -> by name -> create)
     * 4. Discover this login's SIP endpoint (from the Answer URL -> by alias -> create)
     *    and set a freshly generated password (the only way to know it)
     * 5. Make sure the Answer URL dials this endpoint
     * 6. Route all purchased numbers to that application (detaching any trunk binding)
     * 7. Link this endpoint to the application
     * 8. Discover the outbound trunk and ADOPT its platform-stored digest credentials
     * 9. Save all credentials to CredentialStore
     * 10. Return ProvisionResult
     */
    fun ensureReady(
        context: Context,
        email: String,
        password: String,
        onStep: (String) -> Unit
    ): ProvisionResult {
        return try {
            // Step 1: Authenticate with Vobiz
            onStep("Signing in to Vobiz...")
            val login = VobizApi.login(email, password)
            onStep("Fetching account...")
            val account = VobizApi.me(login.accessToken)
            val auth = account.authId
            val tok = account.authSecret

            // Save API credentials for future use
            runCatching { CredentialStore.saveApi(auth, tok) }
            onStep("Account: ${account.name} ($auth)")
            if (account.isTrial) onStep("Note: trial account - outbound calling only")

            // Step 2: Purchased numbers - the dashboard binds them to an application
            onStep("Fetching phone numbers...")
            val numbers = runCatching { VobizApi.listNumbers(auth, tok) }.getOrDefault(emptyList())
            val did = numbers.firstOrNull { it.voiceEnabled }?.e164 ?: numbers.firstOrNull()?.e164

            // Step 3: Discover the inbound application (dashboard binding first)
            onStep("Provisioning inbound application...")
            val applications = VobizApi.listApplications(auth, tok)
            val boundAppId = numbers.firstNotNullOfOrNull { n ->
                n.applicationId?.takeIf { it.isNotEmpty() }
            }
            val application = applications.find { it.appId == boundAppId }
                ?: boundAppId?.let { id -> runCatching { VobizApi.getApplication(auth, tok, id) }.getOrNull() }
                ?: applications.find { it.appName.equals(PLAYGROUND_APP_NAME, ignoreCase = true) }

            // Step 4: Discover the endpoint the platform will ring for this login.
            // The Answer URL's endpoint parameter is authoritative: it names the
            // endpoint the platform actually dials. Alias match is the fallback;
            // a brand-new endpoint is created as a last resort.
            onStep("Provisioning SIP endpoint...")
            val endpoints = VobizApi.listEndpointsFull(auth, tok)
            val answerUrlTarget = application?.let { endpointUserFromAnswerUrl(it.answerUrl) }
            val endpoint = answerUrlTarget?.let { user -> endpoints.find { it.username == user } }
                ?: endpoints.find { it.alias == ENDPOINT_ALIAS }

            // The API never returns endpoint passwords, so the app writes one it
            // then registers with - freshly generated per provisioning run, never
            // hardcoded, so app and platform cannot drift apart.
            val endpointPassword = randomPassword()
            val endpointUsername = if (endpoint != null) {
                VobizApi.updateEndpointPassword(auth, tok, endpoint.endpointId, endpointPassword)
                onStep("Endpoint reused: ${endpoint.username}")
                endpoint.username
            } else {
                val username = "vobizdialer" + Random.nextLong(100_000_000_000L, 999_999_999_999L)
                VobizApi.createEndpoint(auth, tok, username, endpointPassword, ENDPOINT_ALIAS)
                onStep("Endpoint created: $username")
                username
            }
            val endpointSipUri = endpoints.find { it.username == endpointUsername }?.sipUri
                ?: "sip:$endpointUsername@${CredentialStore.DEFAULT_DOMAIN}"

            // Step 5: Make sure the Answer URL dials this endpoint (dashboard config
            // wins; only a wrong/missing endpoint target is corrected).
            val selectedApplication = if (application == null) {
                onStep("Creating inbound application...")
                val created = VobizApi.createApplication(
                    auth, tok, PLAYGROUND_APP_NAME,
                    buildAnswerUrl(did, endpointSipUri), "POST"
                )
                onStep("Inbound application created: ${created.appId}")
                created
            } else {
                val repointed = repointedAnswerUrl(application.answerUrl, did, endpointSipUri)
                if (repointed != null) {
                    VobizApi.updateApplicationAnswerUrl(auth, tok, application.appId, repointed)
                    onStep("Answer URL updated to reach this device")
                } else {
                    onStep("Inbound application found: ${application.appName} (${application.appId})")
                }
                application
            }
            val applicationId = selectedApplication.appId

            // Step 6: Route all purchased numbers to the application
            onStep("Routing phone numbers to application...")
            linkNumbersToApplication(auth, tok, applicationId, onStep)

            // Step 7: Link this endpoint to the application so calls placed to/from
            // it are handled by the application flow
            ensureEndpointApplication(auth, tok, endpointUsername, applicationId, onStep)

            // Step 8: Outbound trunk - discovered from the account; its digest
            // credentials are adopted exactly as the platform stores them.
            onStep("Provisioning outbound trunk...")
            val trunkResult = findOrCreateOutboundTrunk(auth, tok, onStep)

            // Step 9: Save all credentials to CredentialStore
            CredentialStore.saveSip(
                username = endpointUsername,
                password = endpointPassword,
                domain = CredentialStore.DEFAULT_DOMAIN,
                transport = CredentialStore.DEFAULT_TRANSPORT
            )
            CredentialStore.saveTrunkConfig(
                trunkResult.trunkUsername,
                trunkResult.trunkPassword,
                trunkResult.trunkDomain
            )

            // Save DIDs
            runCatching {
                if (numbers.isNotEmpty()) {
                    val dids = numbers.map { it.e164 }.toSet()
                    CredentialStore.saveDids(dids)
                    if (CredentialStore.getSelectedDid() == null) {
                        CredentialStore.selectDid(numbers.first().e164)
                    }
                    onStep("Account numbers loaded (${numbers.size} DIDs)")
                }
            }.onFailure {
                onStep("Numbers fetch skipped: ${it.message}")
            }

            // Set login mode to ACCOUNT for auto-provisioned
            CredentialStore.saveLoginMode(CredentialStore.LoginMode.ACCOUNT)

            Log.i(
                TAG,
                "Provisioning complete: endpoint=$endpointUsername, " +
                    "trunk=${trunkResult.trunkDomain}, app=$applicationId"
            )

            ProvisionResult(
                endpointUsername = endpointUsername,
                endpointPassword = endpointPassword,
                endpointDomain = CredentialStore.DEFAULT_DOMAIN,
                trunkUsername = trunkResult.trunkUsername,
                trunkPassword = trunkResult.trunkPassword,
                trunkDomain = trunkResult.trunkDomain,
                status = "OK"
            )

        } catch (e: VobizApiException) {
            Log.e(TAG, "Provisioning failed: ${e.message}")
            if (e.code == 401) {
                ProvisionResult("", "", "", "", "", "", "Invalid Vobiz account credentials")
            } else {
                ProvisionResult("", "", "", "", "", "", "Provisioning failed (HTTP ${e.code}): ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Provisioning error", e)
            ProvisionResult("", "", "", "", "", "", "Provisioning error: ${e.message}")
        }
    }

    private data class TrunkResult(
        val trunkUsername: String,
        val trunkPassword: String,
        val trunkDomain: String
    )

    /**
     * Finds the account's outbound-capable trunk (name match preferred, then any
     * credential-bearing trunk) and ADOPTS its platform-stored digest credentials
     * - no password is invented or rotated for dashboard-configured trunks.
     * Only when the account has no trunk/credential at all is one created with a
     * freshly generated password.
     */
    private fun findOrCreateOutboundTrunk(authId: String, token: String, onStep: (String) -> Unit): TrunkResult {
        val candidates = VobizApi.listTrunks(authId, token)
            .filter { it.trunkDirection.equals("outbound", true) || it.trunkDirection.equals("both", true) }

        val trunk = candidates.find { it.name == OUTBOUND_TRUNK_NAME && it.credentialUuid.isNotEmpty() }
            ?: candidates.find { it.credentialUuid.isNotEmpty() }
            ?: candidates.find { it.name == OUTBOUND_TRUNK_NAME }
            ?: candidates.firstOrNull()

        if (trunk != null) {
            onStep("Outbound trunk found: ${trunk.trunkId} (${trunk.trunkDomain})")
            val credential = trunk.credentialUuid.takeIf { it.isNotEmpty() }
                ?.let { id -> VobizApi.getCredential(authId, token, id) }
                ?: VobizApi.listCredentials(authId, token).firstOrNull()
            if (credential != null) {
                onStep("Outbound trunk credential adopted: ${credential.username}")
                return TrunkResult(credential.username, credential.password, trunk.trunkDomain)
            }
            val created = VobizApi.ensureCredential(authId, token, TRUNK_CREDENTIAL_USERNAME, randomPassword())
            onStep("Outbound trunk credential created: ${created.username}")
            return TrunkResult(created.username, created.password, trunk.trunkDomain)
        }

        // Nothing configured yet: create the credential (generated password,
        // adopted from the platform if the username already exists) and the trunk.
        val credential = VobizApi.ensureCredential(authId, token, TRUNK_CREDENTIAL_USERNAME, randomPassword())
        val created = VobizApi.createOutboundTrunk(authId, token, OUTBOUND_TRUNK_NAME, credential.id)
        onStep("Outbound trunk created: ${created.trunkId} (${created.trunkDomain})")
        return TrunkResult(credential.username, credential.password, created.trunkDomain)
    }

    /**
     * Routes all purchased numbers to [applicationId] so inbound calls hit the
     * application's Answer URL. A number bound to a trunk is detached first -
     * attach returns HTTP 400 while a trunk binding exists - and an existing
     * application binding is overwritten by the attach.
     */
    private fun linkNumbersToApplication(authId: String, token: String, applicationId: String, onStep: (String) -> Unit) {
        val numbers = VobizApi.listNumbers(authId, token)
        var linked = 0
        var skipped = 0

        for (number in numbers) {
            if (!number.voiceEnabled) {
                skipped++
                onStep("Skipped ${number.e164}: voice not enabled")
                continue
            }

            val onApplication = number.applicationId == applicationId
            val onTrunk = number.trunkGroupId?.isNotEmpty() == true
            if (onApplication && !onTrunk) {
                skipped++
                onStep("Already routed: ${number.e164}")
                continue
            }

            try {
                if (onTrunk) {
                    VobizApi.detachNumberFromTrunk(authId, token, number.e164)
                    onStep("Unlinked ${number.e164} from trunk")
                }
                if (!onApplication) {
                    VobizApi.attachNumberToApplication(authId, token, number.e164, applicationId)
                }
                linked++
                onStep("Routed ${number.e164} to application")
            } catch (e: Exception) {
                skipped++
                onStep("Skipped ${number.e164}: ${e.message}")
            }
        }

        onStep("Numbers routed: $linked, skipped: $skipped")
    }

    /**
     * Links [endpointUsername]'s endpoint to [applicationId] when not already
     * linked, so calls placed to the endpoint hit the application's Answer URL.
     */
    private fun ensureEndpointApplication(
        authId: String,
        token: String,
        endpointUsername: String,
        applicationId: String,
        onStep: (String) -> Unit
    ) {
        val endpoint = VobizApi.listEndpointsFull(authId, token)
            .find { it.username == endpointUsername }
        if (endpoint == null) {
            onStep("Endpoint link skipped: $endpointUsername not found")
            return
        }
        val linked = endpoint.application
        if (linked == applicationId ||
            linked.endsWith("/Application/$applicationId/") ||
            linked.endsWith("/$applicationId/")
        ) {
            onStep("Endpoint already linked to application")
            return
        }

        VobizApi.updateEndpointApplication(authId, token, endpoint.endpointId, applicationId)
        onStep("Endpoint linked to application ($applicationId)")
    }

    /** Extracts the endpoint username an Answer URL will dial (`endpoint=sip:user@host`), or null. */
    private fun endpointUserFromAnswerUrl(url: String): String? {
        val raw = queryParams(url)["endpoint"] ?: return null
        val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        return decoded.removePrefix("sip:").substringBefore('@').takeIf { it.isNotEmpty() }
    }

    /**
     * Returns the Answer URL rewritten to dial [sipUri] (with [did] as callerId),
     * or null when the current URL is already correct / belongs to a custom
     * service the dashboard owns and must not be touched.
     */
    private fun repointedAnswerUrl(current: String, did: String?, sipUri: String): String? {
        if (current.isEmpty()) return null
        val base = current.substringBefore('?')
        val params = current.substringAfter('?', "")
            .split('&')
            .filter { it.isNotEmpty() }
            .map { it.substringBefore('=') to it.substringAfter('=', "") }

        if (params.any { it.first == "endpoint" }) {
            val theirs = URLDecoder.decode(
                params.first { it.first == "endpoint" }.second, "UTF-8"
            )
            if (theirs == sipUri) return null // already dials this login's endpoint
            var query = params.joinToString("&") { (k, v) ->
                if (k == "endpoint") "$k=${URLEncoder.encode(sipUri, "UTF-8")}" else "$k=$v"
            }
            if (did != null && params.none { it.first == "callerId" }) {
                query += "&callerId=${URLEncoder.encode(did, "UTF-8")}"
            }
            return "$base?$query"
        }

        // No endpoint parameter: Vobiz's hosted browser playground (rtc-demo) only
        // rings browser sessions - it cannot reach a SIP endpoint, so point it at
        // the endpoint-aware service. Any other host is left to the dashboard.
        val host = base.substringAfter("://", "").substringBefore('/')
        if (host.equals("rtc-demo.vobiz.ai", ignoreCase = true)) {
            return buildAnswerUrl(did, sipUri)
        }
        return null
    }

    private fun buildAnswerUrl(did: String?, sipUri: String): String {
        val sb = StringBuilder(ANSWER_SERVICE_URL)
            .append("?endpoint=").append(URLEncoder.encode(sipUri, "UTF-8"))
        if (did != null) {
            sb.append("&callerId=").append(URLEncoder.encode(did, "UTF-8"))
        }
        return sb.toString()
    }

    private fun queryParams(url: String): Map<String, String> {
        val query = url.substringAfter('?', "")
        if (query.isEmpty()) return emptyMap()
        return query.split('&')
            .filter { it.isNotEmpty() }
            .map { it.substringBefore('=') to it.substringAfter('=', "") }
            .toMap()
    }

    /**
     * Random secret for credentials the app must set itself (the API never
     * returns endpoint passwords, so freshly generating one at provision time is
     * the only way to know it). Alphanumeric only: no platform password policy
     * surprises, and never logged or hardcoded.
     */
    private fun randomPassword(length: Int = 24): String {
        val chars = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..length).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}
