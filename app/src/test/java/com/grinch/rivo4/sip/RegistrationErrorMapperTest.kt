package com.grinch.rivo4.sip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistrationErrorMapperTest {

    @Test
    fun `401 maps to credentials message`() {
        val msg = RegistrationErrorMapper.map("401 Unauthorized")
        assertTrue(msg.contains("Wrong username or password"))
    }

    @Test
    fun `403 maps to authorization message`() {
        val msg = RegistrationErrorMapper.map("403 Forbidden")
        assertTrue(msg.contains("not authorized"))
    }

    @Test
    fun `408 maps to unreachable registrar message`() {
        val msg = RegistrationErrorMapper.map("408 Request Timeout")
        assertTrue(msg.contains("Cannot reach registrar"))
    }

    @Test
    fun `503 maps to unavailable message`() {
        val msg = RegistrationErrorMapper.map("503 Service Unavailable")
        assertTrue(msg.contains("temporarily unavailable"))
    }

    @Test
    fun `timeout maps case-insensitively`() {
        val msg = RegistrationErrorMapper.map("Registration TIMEOUT after 45s")
        assertTrue(msg.contains("timed out"))
    }

    @Test
    fun `unknown reason falls back with raw reason included`() {
        val msg = RegistrationErrorMapper.map("CSharp transport error")
        assertTrue(msg.startsWith("Registration failed"))
        assertTrue(msg.contains("CSharp transport error"))
    }

    @Test
    fun `401 wins over other substrings when combined`() {
        val msg = RegistrationErrorMapper.map("timeout after 401 challenge")
        assertTrue(msg.contains("Wrong username or password"))
    }

    @Test
    fun `empty reason does not crash`() {
        val msg = RegistrationErrorMapper.map("")
        assertEquals("Registration failed: ", msg)
    }
}
