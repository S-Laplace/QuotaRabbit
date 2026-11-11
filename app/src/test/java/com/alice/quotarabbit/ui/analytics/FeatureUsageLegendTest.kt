package com.alice.quotarabbit.ui.analytics

import com.alice.quotarabbit.providers.codex.network.AnalyticsFeature
import java.util.Locale
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureUsageLegendTest {
    @Test
    fun chartLegendAndFeatureBreakdownUseTheSamePeriodShare() {
        val features = listOf(
            AnalyticsFeature("채팅", 56.1),
            AnalyticsFeature("서브에이전트", 40.3),
            AnalyticsFeature("메모리 업데이트", 3.5),
            AnalyticsFeature("채팅 제목", 0.1),
        )
        val total = features.sumOf { it.usage }
        assertEquals("56.1%", formatFeatureUsageShare(features[0].usage, total, Locale.US))
        assertEquals("40.3%", formatFeatureUsageShare(features[1].usage, total, Locale.US))
        assertEquals("3.5%", formatFeatureUsageShare(features[2].usage, total, Locale.US))
        assertEquals("0.1%", formatFeatureUsageShare(features[3].usage, total, Locale.US))
    }

    @Test
    fun verySmallShareIsNotRoundedDownToZero() {
        assertEquals("<0.1%", formatFeatureUsageShare(0.04, 100.0, Locale.US))
        assertEquals("0.0%", formatFeatureUsageShare(0.0, 100.0, Locale.US))
    }

    @Test
    fun unavailableTotalDoesNotInventPercentage() {
        assertEquals("—", formatFeatureUsageShare(5.0, 0.0, Locale.US))
        assertEquals("—", formatFeatureUsageShare(5.0, Double.NaN, Locale.US))
    }

    @Test
    fun stackedBarSegmentsMatchTheSameShareAsTheLabel() {
        assertEquals(0.6f, featureUsageBarFraction(60.0, 100.0), 0.00001f)
        assertEquals(0.4f, featureUsageBarFraction(40.0, 100.0), 0.00001f)
        assertEquals(1.0f, featureUsageBarFraction(180.0, 120.0), 0.00001f)
    }

    @Test
    fun multipleStackedSegmentsFillExactlyOneBar() {
        val features = listOf(
            AnalyticsFeature("채팅", 56.1),
            AnalyticsFeature("서브에이전트", 40.3),
            AnalyticsFeature("메모리 업데이트", 3.5),
            AnalyticsFeature("채팅 제목", 0.1),
        )
        val total = features.sumOf { it.usage }
        val fractions = features.map { featureUsageBarFraction(it.usage, total) }
        assertEquals(1f, fractions.sum(), 0.00001f)
        assertEquals(4, fractions.size)
    }

    @Test
    fun sevenDatesAreInclusiveOfTodayAndHeadingUsesTheRequestedFormat() {
        val today = LocalDate.of(2026, 10, 29)
        val dates = lastSevenFeatureDates(today)
        assertEquals(7, dates.size)
        assertEquals(LocalDate.of(2026, 10, 23), dates.first())
        assertEquals(today, dates.last())
        assertEquals("2026.10.23. ~ 2026.10.29", formatSevenDayFeatureRange(today))
        assertEquals("2026.12.27. ~ 2027.01.02",
            formatSevenDayFeatureRange(LocalDate.of(2027, 1, 2)))
    }

    @Test
    fun todayPercentagesAreNotTheSevenDayAverage() {
        val today = LocalDate.of(2026, 10, 29)
        val daily = mapOf(
            "2026-10-23" to mapOf("채팅" to 95.0, "서브에이전트" to 5.0),
            "2026-10-29" to mapOf("채팅" to 20.0, "서브에이전트" to 80.0),
        )
        val todayUsage = usageOnDate(daily, today)
        val total = todayUsage.values.sum()
        assertEquals("20.0%", formatFeatureUsageShare(todayUsage.getValue("채팅"), total, Locale.US))
        assertEquals("80.0%", formatFeatureUsageShare(todayUsage.getValue("서브에이전트"), total, Locale.US))
        assertEquals(emptyMap<String, Double>(),
            usageOnDate(daily, LocalDate.of(2026, 10, 28)))
        assertEquals(1f, todayUsage.values.sumOf {
            featureUsageBarFraction(it, total).toDouble()
        }.toFloat(), 0.00001f)
    }

    @Test
    fun todayShareChangeIsComparedToYesterdaysShareNotWeekAverage() {
        val yesterday = mapOf("채팅" to 57.0, "서브에이전트" to 43.0)
        val today = mapOf("채팅" to 60.0, "서브에이전트" to 40.0)
        val chat = dailyFeatureShareChange("채팅", today, yesterday)
        val subagent = dailyFeatureShareChange("서브에이전트", today, yesterday)
        assertEquals(3.0, chat!!, 0.000001)
        assertEquals(-3.0, subagent!!, 0.000001)
        assertEquals("▲ 3.0%p", formatDailyFeatureShareChange(chat, Locale.US))
        assertEquals("▼ 3.0%p", formatDailyFeatureShareChange(subagent, Locale.US))
    }

    @Test
    fun yesterdayMissingAndTodayMissingNeverInventChange() {
        val today = mapOf("채팅" to 60.0, "서브에이전트" to 40.0)
        assertEquals(null, dailyFeatureShareChange("채팅", today, emptyMap()))
        assertEquals(null, dailyFeatureShareChange("채팅", emptyMap(), today))
        assertEquals(null, dailyFeatureShareChange("채팅", today, mapOf("채팅" to 0.0)))
        assertEquals("—", formatDailyFeatureShareChange(null))
        assertEquals(null, dailyFeatureShareChange(
            "채팅", mapOf("채팅" to Double.NaN), today))
    }

    @Test
    fun newFeatureZeroChangeAndTinyChangesAreCorrect() {
        val yesterday = mapOf("서브에이전트" to 100.0)
        val today = mapOf("채팅" to 25.0, "서브에이전트" to 75.0)
        assertEquals(25.0, dailyFeatureShareChange("채팅", today, yesterday)!!, 0.000001)
        assertEquals("0.0%p", formatDailyFeatureShareChange(
            dailyFeatureShareChange("채팅", today, today), Locale.US))
        assertEquals("▲ <0.1%p", formatDailyFeatureShareChange(0.04, Locale.US))
        assertEquals("▼ <0.1%p", formatDailyFeatureShareChange(-0.04, Locale.US))
    }

    @Test
    fun thirtyDaysIncludeTodayWithFiveDayAxisTicksAndLastDate() {
        val today = LocalDate.of(2026, 10, 29)
        val dates = lastThirtyFeatureDates(today)
        assertEquals(30, dates.size)
        assertEquals(LocalDate.of(2026, 9, 30), dates.first())
        assertEquals(today, dates.last())
        val ticks = dates.mapIndexed { index, date ->
            index to thirtyDayFeatureTick(date, index, dates.size)
        }.filter { it.second.isNotBlank() }
        assertEquals(listOf(0, 5, 10, 15, 20, 25, 29), ticks.map { it.first })
        assertEquals(listOf("30", "05", "10", "15", "20", "25", "29"),
            ticks.map { it.second })
    }

    @Test
    fun thirtyDayXAxisWorksAcrossYearAndWithoutDailyData() {
        val dates = lastThirtyFeatureDates(LocalDate.of(2027, 1, 2))
        assertEquals(LocalDate.of(2026, 12, 4), dates.first())
        assertEquals(LocalDate.of(2027, 1, 2), dates.last())
        assertEquals("04", thirtyDayFeatureTick(dates.first(), 0))
        assertEquals("02", thirtyDayFeatureTick(dates.last(), 29))
        assertEquals("", thirtyDayFeatureTick(dates[1], 1))
        assertEquals(emptyMap<String, Double>(), usageOnDate(
            mapOf("2027-01-02" to mapOf("채팅" to 70.0)), dates[0]))
    }

    @Test
    fun modelThirtyDayDatesAndTicksMatchFeatureThirtyDayChart() {
        val dates = lastThirtyFeatureDates(LocalDate.of(2026, 10, 29))
        assertEquals(30, dates.size)
        assertEquals("30", thirtyDayFeatureTick(dates[0], 0))
        assertEquals("05", thirtyDayFeatureTick(dates[5], 5))
        assertEquals("10", thirtyDayFeatureTick(dates[10], 10))
        assertEquals("25", thirtyDayFeatureTick(dates[25], 25))
        assertEquals("29", thirtyDayFeatureTick(dates[29], 29))
        assertEquals("", thirtyDayFeatureTick(dates[28], 28))
        assertEquals("2026.09.30. ~ 2026.10.29",
            formatThirtyDayAnalyticsRange(LocalDate.of(2026, 10, 29)))
        assertEquals("2026.12.04. ~ 2027.01.02",
            formatThirtyDayAnalyticsRange(LocalDate.of(2027, 1, 2)))
    }

    @Test
    fun modelThirtyDayTotalsIgnoreMissingDatesAndNegativeTurnCounts() {
        val days = listOf(
            com.alice.quotarabbit.providers.codex.network.AnalyticsDay(
                "2026-10-28", null, null, null, null, null,
                mapOf("gpt-a" to 8L, "gpt-b" to 2L)),
            com.alice.quotarabbit.providers.codex.network.AnalyticsDay(
                "2026-10-29", null, null, null, null, null,
                mapOf("gpt-a" to 3L, "gpt-b" to 7L, "invalid" to -1L)),
        )
        assertEquals(10.0, modelTurnTotal(
            modelTurnsOnDate(days, LocalDate.of(2026, 10, 29))), 0.000001)
        assertEquals(0.3f, featureUsageBarFraction(3.0, 10.0), 0.000001f)
        assertEquals(0.7f, featureUsageBarFraction(7.0, 10.0), 0.000001f)
        assertEquals(emptyMap<String, Long>(),
            modelTurnsOnDate(days, LocalDate.of(2026, 10, 27)))
    }

    @Test
    fun zeroInvalidOrMissingUsageNeverProducesAFalseBar() {
        assertEquals(0f, featureUsageBarFraction(0.0, 100.0), 0f)
        assertEquals(0f, featureUsageBarFraction(-1.0, 100.0), 0f)
        assertEquals(0f, featureUsageBarFraction(Double.NaN, 100.0), 0f)
        assertEquals(0f, featureUsageBarFraction(3.0, 0.0), 0f)
    }
}
