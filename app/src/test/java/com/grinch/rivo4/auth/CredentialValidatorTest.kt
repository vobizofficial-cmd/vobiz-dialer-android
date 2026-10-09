package com.grinch.rivo4.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialValidatorTest {

    private fun sipError(
        username: String = "play2042343509327874733",
        password: String = "VobizTest123",
        domain: String = "registrar.vobiz.ai",
        transport: String = "UDP",
    ): ValidationResult = CredentialValidator.validateSip(username, password, domain, transport)

    private fun assertInvalid(expectedField: String, result: ValidationResult) {
        assertTrue("expected Invalid but was $result", result is ValidationResult.Invalid)
        assertEquals(expectedField, (result as ValidationResult.Invalid).field)
    }

    @Test
    fun validSipCredentialsPass() {
        assertEquals(ValidationResult.Valid, sipError())
    }

    @Test
    fun lowercaseTransportIsNormalizedAndValid() {
        assertEquals(ValidationResult.Valid, sipError(transport = "udp"))
    }

    @Test
    fun emptyUsernameIsInvalid() {
        assertInvalid("username", sipError(username = ""))
    }

    @Test
    fun shortUsernameIsInvalid() {
        assertInvalid("username", sipError(username = "ab"))
    }

    @Test
    fun tooLongUsernameIsInvalid() {
        assertInvalid("username", sipError(username = "a".repeat(65)))
    }

    @Test
    fun usernameWithDashIsInvalid() {
        assertInvalid("username", sipError(username = "my-user.name"))
    }

    @Test
    fun emptyPasswordIsInvalid() {
        assertInvalid("password", sipError(password = ""))
    }

    @Test
    fun shortPasswordIsInvalid() {
        assertInvalid("password", sipError(password = "short1"))
    }

    @Test
    fun emptyDomainIsInvalid() {
        assertInvalid("domain", sipError(domain = ""))
    }

    @Test
    fun domainWithSpacesIsInvalid() {
        assertInvalid("domain", sipError(domain = "registrar vobiz ai"))
    }

    @Test
    fun domainWithInvalidCharactersIsInvalid() {
        assertInvalid("domain", sipError(domain = "registrar.vobiz!.ai"))
    }

    @Test
    fun ipStyleDomainIsValid() {
        assertEquals(ValidationResult.Valid, sipError(domain = "10.0.2.2"))
    }

    @Test
    fun wssTransportIsValid() {
        assertEquals(ValidationResult.Valid, sipError(transport = "WSS"))
    }

    @Test
    fun unknownTransportIsInvalid() {
        assertInvalid("transport", sipError(transport = "SCTP"))
    }

    @Test
    fun validApiCredentialsPass() {
        assertEquals(
            ValidationResult.Valid,
            CredentialValidator.validateApi("MA_KVG9SWIM", "8rsHZmxEfdlaYxQQDQQ6PkEpHopP5O1XLrUqq3YMiW"),
        )
    }

    @Test
    fun authIdWithoutPrefixIsInvalid() {
        assertInvalid(
            "authId",
            CredentialValidator.validateApi("KVG9SWIMXXXXXXXX", "8rsHZmxEfdlaYxQQDQQ6PkEpHopP5O1XLrUqq3YMiW"),
        )
    }

    @Test
    fun lowercaseAuthIdIsInvalid() {
        assertInvalid(
            "authId",
            CredentialValidator.validateApi("ma_kvg9swim", "8rsHZmxEfdlaYxQQDQQ6PkEpHopP5O1XLrUqq3YMiW"),
        )
    }

    @Test
    fun shortAuthIdIsInvalid() {
        assertInvalid(
            "authId",
            CredentialValidator.validateApi("MA_AB", "8rsHZmxEfdlaYxQQDQQ6PkEpHopP5O1XLrUqq3YMiW"),
        )
    }

    @Test
    fun emptyAuthTokenIsInvalid() {
        assertInvalid("authToken", CredentialValidator.validateApi("MA_KVG9SWIM", ""))
    }

    @Test
    fun shortAuthTokenIsInvalid() {
        assertInvalid("authToken", CredentialValidator.validateApi("MA_KVG9SWIM", "tooshorttoken"))
    }
}
