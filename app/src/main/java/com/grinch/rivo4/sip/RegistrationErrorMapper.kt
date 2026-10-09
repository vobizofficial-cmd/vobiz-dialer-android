package com.grinch.rivo4.sip

/**
 * Maps raw SIP registration failure reasons to user-facing messages.
 * Pure function - covered by JVM unit tests.
 */
object RegistrationErrorMapper {
    fun map(rawReason: String): String = when {
        rawReason.contains("401") ->
            "Wrong username or password. Check your SIP credentials."
        rawReason.contains("403") ->
            "This endpoint is not authorized. Contact your Vobiz admin."
        rawReason.contains("408") ->
            "Cannot reach registrar. Check your network or SIP domain."
        rawReason.contains("503") ->
            "Vobiz registrar is temporarily unavailable. Try again."
        rawReason.contains("timeout", ignoreCase = true) ->
            "Registration timed out. Check your network."
        else ->
            "Registration failed: $rawReason"
    }
}
