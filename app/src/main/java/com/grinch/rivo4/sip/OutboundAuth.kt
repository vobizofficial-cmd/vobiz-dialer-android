package com.grinch.rivo4.sip

import com.grinch.rivo4.auth.SipCredentials
import com.grinch.rivo4.auth.TrunkConfig

data class OutboundAuthPlan(
    val fromUser: String,
    val outboundDomain: String,
    val digestUser: String,
    val digestPassword: String,
    val registerOutboundAuth: Boolean,
)

/**
 * Single source of truth for how an outbound INVITE identifies itself.
 *
 * liblinphone matches auth info by the From-header user
 * (belle_sip_auth_event_create uses the From URI user), while the digest
 * response must carry the credential attached to the outbound trunk.
 * [fromUser] is therefore written into the From header and used as the auth
 * lookup key, while [digestUser]/[digestPassword] are sent in the digest.
 * [registerOutboundAuth] tells the caller whether a dedicated auth info
 * (fromUser -> digest credentials, scoped to [outboundDomain]) must be
 * registered in addition to the registration auth info; it is false when the
 * mapping would be an exact duplicate of the registration auth info
 * (direct SIP login, no caller ID override).
 */
object OutboundAuth {

    fun resolve(callerId: String?, sip: SipCredentials, trunk: TrunkConfig?): OutboundAuthPlan {
        val outboundDomain = trunk?.domain ?: sip.domain
        val digestUser = trunk?.username ?: sip.username
        val digestPassword = trunk?.password ?: sip.password
        val fromUser = callerId?.trim()?.takeIf { it.isNotEmpty() } ?: sip.username
        val needed = !(fromUser == sip.username &&
            digestUser == sip.username &&
            digestPassword == sip.password &&
            outboundDomain == sip.domain)
        return OutboundAuthPlan(
            fromUser = fromUser,
            outboundDomain = outboundDomain,
            digestUser = digestUser,
            digestPassword = digestPassword,
            registerOutboundAuth = needed,
        )
    }
}
