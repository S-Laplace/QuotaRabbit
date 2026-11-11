package com.alice.quotarabbit.ui.account

import org.junit.Assert.assertEquals
import org.junit.Test

/** Both the 5h and weekly quota bars share this visible-fill calculation. */
class CodexQuotaBarWidthTest {
    @Test
    fun onePercentRemainsVisibleButFitsInsideTrack() {
        // 1% of a 200px track is only 2px; the visible pill is at least bar height.
        assertEquals(9f, quotaFillWidthPx(200f, 9f, 1), 0.001f)
        assertEquals(4f, quotaFillWidthPx(4f, 9f, 1), 0.001f)
    }

    @Test
    fun regularAndFullPercentagesStayProportional() {
        assertEquals(100f, quotaFillWidthPx(200f, 9f, 50), 0.001f)
        assertEquals(200f, quotaFillWidthPx(200f, 9f, 100), 0.001f)
        assertEquals(200f, quotaFillWidthPx(200f, 9f, 150), 0.001f)
    }

    @Test
    fun zeroMissingAndInvalidSizesNeverCreateOverflow() {
        assertEquals(0f, quotaFillWidthPx(200f, 9f, 0), 0.001f)
        assertEquals(0f, quotaFillWidthPx(200f, 9f, null), 0.001f)
        assertEquals(0f, quotaFillWidthPx(200f, 9f, -1), 0.001f)
        assertEquals(0f, quotaFillWidthPx(0f, 9f, 1), 0.001f)
        assertEquals(0f, quotaFillWidthPx(200f, 0f, 1), 0.001f)
    }
}
