package com.alice.quotarabbit.ui.analytics

import com.alice.quotarabbit.providers.codex.network.AnalyticsDay
import com.alice.quotarabbit.providers.codex.network.AnalyticsReport
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TurnMessageDailyWindowTest {
    private val today = LocalDate.of(2026, 10, 30)
    private fun day(date: String, turns: Long?) =
        AnalyticsDay(date, null, null, null, null, turns)
    private fun report(vararg days: AnalyticsDay) = AnalyticsReport(
        days = days.toList(), models = emptyList(), clients = emptyList(),
        features = emptyList(), pluginMetricsPresent = false, partial = false,
    )

    @Test fun missingTodayShiftsBothModesBackWithoutAddingBlankCurrentDay() {
        val data = report(day("2026-10-22", 8L), day("2026-10-29", 20L))
        for (mode in listOf("turns", "messages")) {
            val lastDay = analyticsWindowEnd(data, mode, today)
            assertEquals(today.minusDays(1), lastDay)
            val seven = analyticsWindowDates(lastDay, 7)
            assertEquals(LocalDate.of(2026, 10, 23), seven.first())
            assertEquals(today.minusDays(1), seven.last())
            assertEquals(7, seven.size)
            assertEquals(20.0, turnMessagePlotValues(data, seven).last(), 0.00001)
            assertEquals(0.0, turnMessagePlotValues(data, seven).first(), 0.00001)
            assertEquals(20.0, turnMessagePlotValues(data, seven).sum(), 0.00001)
            val thirty = analyticsWindowDates(lastDay, 30)
            assertEquals(LocalDate.of(2026, 9, 30), thirty.first())
            assertEquals(today.minusDays(1), thirty.last())
            assertEquals(30, thirty.size)
            assertEquals("2026.10.23. ~ 2026.10.29", formatSevenDayFeatureRange(lastDay))
            assertEquals("2026.09.30. ~ 2026.10.29", formatThirtyDayAnalyticsRange(lastDay))
        }
    }

    @Test fun positiveTodayIsIncludedInBothSevenAndThirtyDayPlots() {
        val data = report(day("2026-10-29", 7L), day("2026-10-30", 12L))
        for (mode in listOf("turns", "messages")) {
            val lastDay = analyticsWindowEnd(data, mode, today)
            assertEquals(today, lastDay)
            val week = analyticsWindowDates(lastDay, 7)
            assertEquals(LocalDate.of(2026, 10, 24), week.first())
            assertEquals(7, week.size)
            assertEquals(12.0, turnMessagePlotValues(data, week).last(), 0.00001)
            val month = analyticsWindowDates(lastDay, 30)
            assertEquals(LocalDate.of(2026, 10, 1), month.first())
            assertEquals(30, month.size)
            assertEquals(12.0, turnMessagePlotValues(data, month).last(), 0.00001)
            assertEquals("2026.10.24. ~ 2026.10.30", formatSevenDayFeatureRange(lastDay))
            assertEquals("2026.10.01. ~ 2026.10.30", formatThirtyDayAnalyticsRange(lastDay))
        }
    }

    @Test fun zeroNullNegativeAndOtherMetricsCannotCreateTodaysTurnCount() {
        val missing = report(day("2026-10-29", 4L), day("2026-10-30", null))
        val zero = report(day("2026-10-30", 0L))
        val invalid = report(day("2026-10-30", -5L))
        for (data in listOf(missing, zero, invalid)) {
            for (mode in listOf("turns", "messages"))
                assertEquals(today.minusDays(1), analyticsWindowEnd(data, mode, today))
            assertEquals(null, dailyTurnsOnDate(data.days, LocalDate.of(2026, 10, 28)))
        }
        assertEquals(null, dailyTurnsOnDate(invalid.days, today))
        assertEquals(0.0, turnMessagePlotValues(zero, listOf(today)).single(), 0.00001)
        assertEquals(0.0, turnMessagePlotValues(invalid, listOf(today)).single(), 0.00001)
    }
}
