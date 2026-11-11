package com.hidsquid.refreshpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ButtonActionTest {

    @Test
    fun `fromId round-trips every entry`() {
        for (action in ButtonAction.entries) {
            assertEquals(action, ButtonAction.fromId(action.id))
        }
    }

    @Test
    fun `fromId returns null for unknown ids`() {
        assertNull(ButtonAction.fromId(-1))
        assertNull(ButtonAction.fromId(8))
        assertNull(ButtonAction.fromId(Int.MAX_VALUE))
    }

    @Test
    fun `ids are unique`() {
        val ids = ButtonAction.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `ids match the stored prefs contract`() {
        val expected = mapOf(
            ButtonAction.BACK to 0,
            ButtonAction.SCREENSHOT to 1,
            ButtonAction.QUICK_SETTINGS to 2,
            ButtonAction.BRIGHTNESS to 3,
            ButtonAction.MANUAL_REFRESH to 4,
            ButtonAction.HOME_LAUNCHER to 5,
            ButtonAction.NONE to 6,
            ButtonAction.PAGE_KEY_SWAP to 7
        )
        assertEquals(expected.keys, ButtonAction.entries.toSet())
        for ((action, id) in expected) {
            assertEquals("id of $action", id, action.id)
        }
    }
}
