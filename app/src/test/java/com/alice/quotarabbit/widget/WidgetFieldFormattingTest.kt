package com.alice.quotarabbit.widget

import com.alice.quotarabbit.domain.quota.QuotaWindowDisplayKind
import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
class WidgetFieldFormattingTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `quota and request values explicitly distinguish remaining and consumed`() {
        assertEquals("65% remaining", field().widgetValue(context))
        assertEquals("40 / 100 used", field().copy(
            displayKind = QuotaWindowDisplayKind.UsageCount, usedCount = 40, limitCount = 100,
        ).widgetValue(context))
    }

    @Test
    fun `missing counts do not borrow quota percentage`() {
        assertEquals(context.getString(com.alice.quotarabbit.R.string.widget_percent_unavailable),
            field().copy(displayKind = QuotaWindowDisplayKind.UsageCount).widgetValue(context))
    }

    @Test
    fun `malformed or nonfinite persisted balances never render as money`() {
        val unavailable = context.getString(com.alice.quotarabbit.R.string.widget_percent_unavailable)
        for (amount in listOf("N/A", "NaN", "Infinity", "-Infinity", "1e9999")) {
            assertEquals(unavailable, field().copy(
                isBalance = true, displayKind = QuotaWindowDisplayKind.Balance,
                balanceAmount = amount, balanceCurrency = "USD",
            ).widgetValue(context))
        }
    }

    @Test
    fun `widget reset timestamps use device-local 24-hour time without GMT`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val late = Instant.parse("2026-01-02T23:05:00Z").formatShortDateTime(context)
            val midnight = Instant.parse("2026-01-03T00:07:00Z").formatShortDateTime(context)
            assertEquals("26.01.02. 23:05", late)
            assertEquals("26.01.03. 00:07", midnight)
            for (value in listOf(late, midnight)) {
                assertFalse(value, Regex("\\b(AM|PM|GMT|UTC)\\b").containsMatchIn(value))
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    private fun field() = WidgetField("five_hour", false, 65, null, null, null, WidgetQuotaTone.Success)
}
