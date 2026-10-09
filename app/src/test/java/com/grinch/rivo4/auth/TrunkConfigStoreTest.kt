package com.grinch.rivo4.auth

import org.junit.Assert.*
import org.junit.Test

class TrunkConfigStoreTest {

    @Test
    fun testTrunkConfigDataClass() {
        val trunk = TrunkConfig("dialer_trunk_auth", "pass123", "45b8afc2.sip.vobiz.ai")
        assertEquals("dialer_trunk_auth", trunk.username)
        assertEquals("pass123", trunk.password)
        assertEquals("45b8afc2.sip.vobiz.ai", trunk.domain)
    }

    @Test
    fun testTrunkConfigValidation() {
        val valid = TrunkConfig("user", "pass", "domain.com")
        assertTrue(valid.username.isNotEmpty() && valid.password.isNotEmpty() && valid.domain.isNotEmpty())

        val invalidUser = TrunkConfig("", "pass", "domain.com")
        assertFalse(invalidUser.username.isNotEmpty() && invalidUser.password.isNotEmpty() && invalidUser.domain.isNotEmpty())

        val invalidPass = TrunkConfig("user", "", "domain.com")
        assertFalse(invalidPass.username.isNotEmpty() && invalidPass.password.isNotEmpty() && invalidPass.domain.isNotEmpty())

        val invalidDomain = TrunkConfig("user", "pass", "")
        assertFalse(invalidDomain.username.isNotEmpty() && invalidDomain.password.isNotEmpty() && invalidDomain.domain.isNotEmpty())
    }
}
