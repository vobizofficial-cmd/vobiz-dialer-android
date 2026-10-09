package com.grinch.rivo4.sip

import com.grinch.rivo4.auth.SipCredentials
import com.grinch.rivo4.auth.TrunkConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboundAuthTest {

    private val endpoint = SipCredentials(
        username = "vobizdialer123",
        password = "endpoint-pass",
        domain = "registrar.vobiz.ai",
        transport = "UDP",
    )

    private val trunk = TrunkConfig(
        username = "dialer_trunk_auth",
        password = "trunk-pass",
        domain = "08ecd76e.sip.vobiz.ai",
    )

    @Test
    fun `account login with caller id uses caller id as from and trunk digest`() {
        val plan = OutboundAuth.resolve("+917965850027", endpoint, trunk)
        assertEquals("+917965850027", plan.fromUser)
        assertEquals("08ecd76e.sip.vobiz.ai", plan.outboundDomain)
        assertEquals("dialer_trunk_auth", plan.digestUser)
        assertEquals("trunk-pass", plan.digestPassword)
        assertTrue(plan.registerOutboundAuth)
    }

    @Test
    fun `account login without caller id falls back to endpoint identity with trunk digest`() {
        val plan = OutboundAuth.resolve(null, endpoint, trunk)
        assertEquals("vobizdialer123", plan.fromUser)
        assertEquals("08ecd76e.sip.vobiz.ai", plan.outboundDomain)
        assertEquals("dialer_trunk_auth", plan.digestUser)
        assertTrue(plan.registerOutboundAuth)
    }

    @Test
    fun `blank caller id is treated as absent`() {
        val plan = OutboundAuth.resolve("   ", endpoint, trunk)
        assertEquals("vobizdialer123", plan.fromUser)
        assertTrue(plan.registerOutboundAuth)
    }

    @Test
    fun `direct sip without trunk and caller id skips duplicate auth info`() {
        val plan = OutboundAuth.resolve(null, endpoint, null)
        assertEquals("vobizdialer123", plan.fromUser)
        assertEquals("registrar.vobiz.ai", plan.outboundDomain)
        assertEquals("vobizdialer123", plan.digestUser)
        assertFalse(plan.registerOutboundAuth)
    }

    @Test
    fun `direct sip with caller id still registers lookup mapping`() {
        val plan = OutboundAuth.resolve("+919240953996", endpoint, null)
        assertEquals("+919240953996", plan.fromUser)
        assertEquals("registrar.vobiz.ai", plan.outboundDomain)
        assertEquals("vobizdialer123", plan.digestUser)
        assertEquals("endpoint-pass", plan.digestPassword)
        assertTrue(plan.registerOutboundAuth)
    }

    @Test
    fun `caller id equal to endpoint identity without trunk needs no extra auth info`() {
        val plan = OutboundAuth.resolve("vobizdialer123", endpoint, null)
        assertEquals("vobizdialer123", plan.fromUser)
        assertFalse(plan.registerOutboundAuth)
    }
}
