package com.alice.quotarabbit.ui.analytics

import com.alice.quotarabbit.providers.codex.network.AnalyticsReport
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelSevenDayChartTest {
    private val lastDay = LocalDate.of(2026, 10, 29)
    private val daily = mapOf(
        "2026-10-22" to mapOf("excluded" to 2000.0),
        "2026-10-23" to mapOf("gpt-a" to 75.0, "gpt-b" to 25.0),
        "2026-10-28" to mapOf("gpt-a" to 45.0, "gpt-b" to 55.0),
        "2026-10-29" to mapOf("gpt-a" to 60.0, "gpt-b" to 40.0),
        "2026-10-30" to mapOf("excluded-future" to 9000.0),
    )
    private val report = AnalyticsReport(
        days = emptyList(), models = emptyList(), clients = emptyList(),
        features = emptyList(), pluginMetricsPresent = false, partial = false,
        modelCreditsDays = daily,
    )
    @Test fun sevenDailyBarsIncludeSevenExactDatesAndExcludeOutOfWindowModels() {
        val dates = lastSevenFeatureDates(lastDay)
        assertEquals(7, dates.size)
        assertEquals(LocalDate.of(2026, 10, 23), dates.first())
        assertEquals(lastDay, dates.last())
        assertEquals(listOf("gpt-a", "gpt-b"), windowModelNames(report, dates))
        assertEquals(emptyMap<String, Double>(), modelCreditsOnDate(report, dates[1]))
        assertEquals("2026.10.23. ~ 2026.10.29", formatSevenDayFeatureRange(lastDay))
    }

    @Test fun eachDailyStackUsesItsOwnTotalAndTheSameLegendColorOrder() {
        val dates = lastSevenFeatureDates(lastDay)
        val names = windowModelNames(report, dates)
        val last = modelCreditsOnDate(report, lastDay)
        val sum = positiveUsageTotal(last)
        assertEquals(1.0, names.sumOf {
            featureUsageBarFraction(last[it] ?: 0.0, sum).toDouble()
        }, 0.00001)
        assertEquals("60.0%", formatFeatureUsageShare(last.getValue("gpt-a"), sum, Locale.US))
        assertEquals("40.0%", formatFeatureUsageShare(last.getValue("gpt-b"), sum, Locale.US))
    }
    @Test fun finalDayShareDifferenceIsNotTheSevenDayAverage() {
        val today = modelCreditsOnDate(report, lastDay)
        val yesterday = modelCreditsOnDate(report, lastDay.minusDays(1))
        assertEquals("▲ 15.0%p", formatDailyFeatureShareChange(
            dailyFeatureShareChange("gpt-a", today, yesterday), Locale.US))
        assertEquals("▼ 15.0%p", formatDailyFeatureShareChange(
            dailyFeatureShareChange("gpt-b", today, yesterday), Locale.US))
    }

    @Test fun invalidAndZeroModelsDoNotAppearInLegend() {
        val report = report.copy(modelCreditsDays = mapOf(
            "2026-10-29" to mapOf("valid" to 2.0, "zero" to 0.0,
                "invalid" to Double.NaN, "negative" to -5.0),
        ))
        assertEquals(listOf("valid"), windowModelNames(report, lastSevenFeatureDates(lastDay)))
    }
}
