package com.alice.quotarabbit.ui.analytics

import com.alice.quotarabbit.providers.codex.network.AnalyticsDay
import com.alice.quotarabbit.providers.codex.network.AnalyticsReport
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsWindowAnchorTest {
    private val today = LocalDate.of(2026, 10, 30)
    private fun report(
        features: Map<String, Map<String, Double>> = emptyMap(),
        models: Map<String, Map<String, Double>> = emptyMap(),
        todayTurns: Long? = null,
    ) = AnalyticsReport(
        days = listOf(AnalyticsDay("2026-10-30", null, null, null, null, todayTurns)),
        models = emptyList(), clients = emptyList(), features = emptyList(),
        pluginMetricsPresent = false, partial = false,
        featureDays = features, modelCreditsDays = models,
    )
    @Test fun everyModeExcludesUnreportedTodayAndUsesFullPreviousWindow() {
        val data = report()
        for (mode in listOf("feature", "models", "turns", "messages")) {
            val end = analyticsWindowEnd(data, mode, today)
            assertEquals(LocalDate.of(2026, 10, 29), end)
            val week = analyticsWindowDates(end, 7)
            val month = analyticsWindowDates(end, 30)
            assertEquals(LocalDate.of(2026, 10, 23), week.first())
            assertEquals(LocalDate.of(2026, 10, 29), week.last())
            assertEquals(7, week.size)
            assertEquals(LocalDate.of(2026, 9, 30), month.first())
            assertEquals(LocalDate.of(2026, 10, 29), month.last())
            assertEquals(30, month.size)
        }
    }
    @Test fun eachModeChecksItsOwnActualDailyValue() {
        val data = report(
            features = mapOf("2026-10-30" to mapOf("chat" to 65.0)),
            models = mapOf("2026-10-30" to mapOf("gpt-a" to 0.0)),
            todayTurns = 0L,
        )
        assertEquals(today, analyticsWindowEnd(data, "feature", today))
        for (mode in listOf("models", "turns", "messages")) {
            assertEquals(today.minusDays(1), analyticsWindowEnd(data, mode, today))
        }
        val positive = report(
            models = mapOf("2026-10-30" to mapOf("gpt-a" to 1.0)),
            todayTurns = 3L,
        )
        for (mode in listOf("models", "turns", "messages")) {
            assertEquals(today, analyticsWindowEnd(positive, mode, today))
        }
        assertEquals(today.minusDays(1), analyticsWindowEnd(positive, "feature", today))
    }
    @Test fun windowFeatureSharesDoNotUseExcludedTodayOrExtraStartDay() {
        val report = report(features = mapOf(
            "2026-10-22" to mapOf("chat" to 900.0),
            "2026-10-23" to mapOf("chat" to 20.0, "subagent" to 80.0),
            "2026-10-29" to mapOf("chat" to 60.0, "subagent" to 40.0),
            "2026-10-30" to emptyMap(),
        ))
        val dates = analyticsWindowDates(analyticsWindowEnd(report, "feature", today), 7)
        val features = windowFeatures(report, dates)
        assertEquals(2, features.size)
        assertEquals(80.0, features.first { it.label == "chat" }.usage, 0.00001)
        assertEquals(120.0, features.first { it.label == "subagent" }.usage, 0.00001)
        assertEquals("2026.10.23. ~ 2026.10.29", formatSevenDayFeatureRange(dates.last()))
        assertEquals("2026.09.30. ~ 2026.10.29",
            formatThirtyDayAnalyticsRange(analyticsWindowDates(dates.last(), 30).last()))
    }
    @Test fun invalidValuesOrTomorrowNeverCreateToday() {
        val data = report(
            features = mapOf("2026-10-30" to mapOf("chat" to Double.NaN, "other" to -8.0),
                "2026-10-31" to mapOf("chat" to 80.0)),
            models = mapOf("2026-10-30" to mapOf("gpt-a" to 0.0)),
            todayTurns = null,
        )
        for (mode in listOf("feature", "models", "turns", "messages")) {
            assertEquals(today.minusDays(1), analyticsWindowEnd(data, mode, today))
        }
        val dates = analyticsWindowDates(LocalDate.of(2027, 1, 1), 7)
        assertEquals(LocalDate.of(2026, 12, 26), dates.first())
        assertEquals(LocalDate.of(2027, 1, 1), dates.last())
    }
}
