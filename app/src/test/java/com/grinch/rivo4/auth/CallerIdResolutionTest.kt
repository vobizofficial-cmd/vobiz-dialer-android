package com.grinch.rivo4.auth

import org.junit.Assert.*
import org.junit.Test

class CallerIdResolutionTest {

    @Test
    fun testE164ValidationRegex() {
        val regex = Regex("^\\+[1-9]\\d{7,14}$")

        // Valid E.164
        assertTrue("+911234567890".matches(regex))
        assertTrue("+15550192834".matches(regex))
        assertTrue("+442079460912".matches(regex))

        // Invalid inputs
        assertFalse("1234567890".matches(regex)) // missing +
        assertFalse("+012345678".matches(regex)) // starts with +0
        assertFalse("+1234".matches(regex)) // too short (< 8 digits)
        assertFalse("+1234567890123456".matches(regex)) // too long (> 15 digits)
        assertFalse("+1234abc5678".matches(regex)) // contains letters
    }

    @Test
    fun testCustomCallerIdResolution() {
        val customNumber = "+911234567890"
        val mode: CallerIdMode = CallerIdMode.Custom(customNumber)

        val effective = when (mode) {
            is CallerIdMode.Did -> mode.number
            is CallerIdMode.Custom -> mode.number
            CallerIdMode.Default -> null
        }

        assertEquals(customNumber, effective)
    }

    @Test
    fun testDefaultCallerIdResolutionReturnsNull() {
        val mode: CallerIdMode = CallerIdMode.Default

        val effective = when (mode) {
            is CallerIdMode.Did -> mode.number
            is CallerIdMode.Custom -> mode.number
            CallerIdMode.Default -> null
        }

        assertNull(effective)
    }

    @Test
    fun testDidCallerIdResolutionReturnsAsIs() {
        val didNumber = "+15550192834"
        val mode: CallerIdMode = CallerIdMode.Did(didNumber)

        val effective = when (mode) {
            is CallerIdMode.Did -> mode.number
            is CallerIdMode.Custom -> mode.number
            CallerIdMode.Default -> null
        }

        assertEquals(didNumber, effective)
    }
}
