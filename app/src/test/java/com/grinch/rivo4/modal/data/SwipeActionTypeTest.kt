package com.grinch.rivo4.modal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SwipeActionTypeTest {

    @Test
    fun fromIdMapsPersistedIdsToTypes() {
        assertEquals(SwipeActionType.NONE, SwipeActionType.fromId(0))
        assertEquals(SwipeActionType.CALL, SwipeActionType.fromId(1))
        assertEquals(SwipeActionType.COPY_NUMBER, SwipeActionType.fromId(5))
        assertEquals(SwipeActionType.DELETE, SwipeActionType.fromId(6))
    }

    @Test
    fun staleIdsFromRemovedActionsFallBackToNone() {
        for (stale in listOf(2, 3, 4)) {
            assertEquals("stale id $stale must map to NONE", SwipeActionType.NONE, SwipeActionType.fromId(stale))
        }
        assertEquals(SwipeActionType.NONE, SwipeActionType.fromId(-1))
        assertEquals(SwipeActionType.NONE, SwipeActionType.fromId(Int.MAX_VALUE))
    }

    @Test
    fun idsAreUniqueAndRoundTrip() {
        val ids = SwipeActionType.entries.map { it.id }
        assertEquals("ids must be unique", ids.size, ids.distinct().size)
        for (type in SwipeActionType.entries) {
            assertEquals(type, SwipeActionType.fromId(type.id))
        }
        assertNotEquals(SwipeActionType.CALL, SwipeActionType.NONE)
    }
}
