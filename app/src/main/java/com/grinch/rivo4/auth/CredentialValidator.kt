package com.grinch.rivo4.auth

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val field: String, val message: String) : ValidationResult()
}

/**
 * Pure input validation for credentials before they are persisted.
 * No Android dependencies - safe for JVM unit tests.
 */
object CredentialValidator {

    private val USERNAME_REGEX = Regex("^[A-Za-z0-9_]{4,64}$")
    private val HOSTNAME_REGEX =
        Regex("^[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)*$")
    private val AUTH_ID_REGEX = Regex("^MA_[A-Z0-9]{6,}$")
    private val SUPPORTED_TRANSPORTS = setOf("TCP", "TLS", "UDP", "WSS")
    private const val MIN_PASSWORD_LENGTH = 8
    private const val MIN_TOKEN_LENGTH = 16

    fun validateSip(
        username: String,
        password: String,
        domain: String,
        transport: String,
    ): ValidationResult {
        if (username.isEmpty()) {
            return ValidationResult.Invalid("username", "SIP username is required")
        }
        if (!USERNAME_REGEX.matches(username)) {
            return ValidationResult.Invalid(
                "username",
                "Use 4-64 letters, digits or underscore (e.g. play2042343509327874733)",
            )
        }
        if (password.isEmpty()) {
            return ValidationResult.Invalid("password", "SIP password is required")
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            return ValidationResult.Invalid("password", "Password must be at least $MIN_PASSWORD_LENGTH characters")
        }
        if (domain.isEmpty()) {
            return ValidationResult.Invalid("domain", "SIP domain is required")
        }
        if (!HOSTNAME_REGEX.matches(domain)) {
            return ValidationResult.Invalid("domain", "Enter a valid host name (e.g. registrar.vobiz.ai)")
        }
        if (transport.uppercase() !in SUPPORTED_TRANSPORTS) {
            return ValidationResult.Invalid("transport", "Transport must be one of WSS, TCP, TLS or UDP")
        }
        return ValidationResult.Valid
    }

    fun validateApi(authId: String, authToken: String): ValidationResult {
        if (!AUTH_ID_REGEX.matches(authId)) {
            return ValidationResult.Invalid("authId", "Auth ID must start with MA_ (e.g. MA_KVG9SWIM)")
        }
        if (authToken.length < MIN_TOKEN_LENGTH) {
            return ValidationResult.Invalid("authToken", "Auth token must be at least $MIN_TOKEN_LENGTH characters")
        }
        return ValidationResult.Valid
    }
}
