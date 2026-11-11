package com.alice.quotarabbit.domain.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountEditValidationTest {
    @Test fun dateIsCalendarDateNotAnInstant() {
        val input = AccountEditInput(" Work ", "teal", "2026-03-01").validated()
        assertEquals("Work", input.nickname)
        assertEquals("2026-03-01", input.manualRenewalDate)
    }
    @Test fun clearingDateAndDefaultColorAreAllowed() {
        val input = AccountEditInput("Work", null, null).validated()
        assertNull(input.manualRenewalDate)
        assertNull(input.accentColorId)
    }
    @Test fun accentPaletteExcludesBlueAndIncludesThreeDistinctOptions() {
        assertEquals(listOf("teal", "violet", "amber", "rose", "slate", "green", "red", "brown"), ACCOUNT_ACCENT_COLORS)
        listOf("green", "red", "brown").forEach { key ->
            assertEquals(key, AccountEditInput("Work", key, null).validated().accentColorId)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AccountEditInput("Work", "blue", null).validated()
        }
    }
    @Test fun invalidDatesAndColorsAreRejectedBeforeWrite() {
        assertThrows(IllegalArgumentException::class.java) {
            AccountEditInput("Work", "teal", "2026-02-30").validated()
        }
        assertThrows(IllegalArgumentException::class.java) {
            AccountEditInput("Work", "invalid-color", null).validated()
        }
        assertThrows(IllegalArgumentException::class.java) {
            AccountEditInput("  ", "teal", null).validated()
        }
    }
}
