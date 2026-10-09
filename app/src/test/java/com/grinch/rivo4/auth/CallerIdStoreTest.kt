package com.grinch.rivo4.auth

import org.junit.Assert.*
import org.junit.Test

class CallerIdStoreTest {

    @Test
    fun testCallerIdModeTypes() {
        val didMode = CallerIdMode.Did("+911234567890")
        val customMode = CallerIdMode.Custom("+919876543210")
        val defaultMode = CallerIdMode.Default

        assertTrue(didMode is CallerIdMode.Did)
        assertEquals("+911234567890", didMode.number)

        assertTrue(customMode is CallerIdMode.Custom)
        assertEquals("+919876543210", customMode.number)

        assertTrue(defaultMode is CallerIdMode.Default)
    }

    @Test
    fun testE164Validation() {
        assertTrue(isValidE164("+911234567890"))
        assertTrue(isValidE164("+15550192834"))
        assertFalse(isValidE164("12345")) // No +
        assertFalse(isValidE164("+123")) // Too short
        assertFalse(isValidE164("+12345678901234567")) // Too long (>15 digits)
        assertFalse(isValidE164("+123abc456")) // Letters
    }

    private fun isValidE164(number: String): Boolean {
        val cleaned = number.trim()
        return cleaned.matches(Regex("^\\+[1-9]\\d{7,14}$"))
    }
}
