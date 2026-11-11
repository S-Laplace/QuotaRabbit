package com.alice.quotarabbit.ui.home

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuotaResetCountdownTest {
    private val now = Instant.parse("2026-11-08T07:00:00Z")

    @Test fun `missing reset remains unknown`() {
        assertNull(quotaResetMinutes(null, now))
    }

    @Test fun `expired reset never implies restored quota`() {
        assertEquals(0L, quotaResetMinutes(now.minusSeconds(1), now))
        assertEquals(0L, quotaResetMinutes(now, now))
    }

    @Test fun `partial minute is rounded up for display`() {
        assertEquals(1L, quotaResetMinutes(now.plusSeconds(1), now))
        assertEquals(61L, quotaResetMinutes(now.plusSeconds(3_601), now))
    }

    @Test fun `five hour and weekly resets retain minute precision`() {
        assertEquals(300L, quotaResetMinutes(now.plusSeconds(5 * 3_600L), now))
        assertEquals(10_080L, quotaResetMinutes(now.plusSeconds(7 * 86_400L), now))
    }
}
