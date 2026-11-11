package com.alice.quotarabbit.ui.analytics

import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.providers.codex.network.AnalyticsDay
import com.alice.quotarabbit.providers.codex.network.AnalyticsFeature
import com.alice.quotarabbit.providers.codex.network.AnalyticsReport
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Share of all features over the selected period, shared by the chart legend and breakdown. */
internal fun formatFeatureUsageShare(
    usage: Double,
    total: Double,
    locale: Locale = Locale.getDefault(),
): String {
    if (!usage.isFinite() || !total.isFinite() || usage < 0.0 || total <= 0.0) return "—"
    val share = usage / total * 100.0
    return if (share > 0.0 && share < 0.1) "<0.1%"
    else String.format(locale, "%.1f%%", share)
}

/** Bounded fraction used by the horizontal feature bars for the selected account/period. */
internal fun featureUsageBarFraction(usage: Double, total: Double): Float {
    if (!usage.isFinite() || !total.isFinite() || usage <= 0.0 || total <= 0.0) return 0f
    return (usage / total).coerceIn(0.0, 1.0).toFloat()
}

/** Include a day in a mode only if the provider supplied positive usage for that mode. */
internal fun analyticsWindowEnd(report: AnalyticsReport, mode: String, today: LocalDate): LocalDate {
    val available = when (mode) {
        "feature" -> positiveUsageTotal(report.featureDays[today.toString()].orEmpty()) > 0.0
        "models" -> positiveUsageTotal(report.modelCreditsDays[today.toString()].orEmpty()) > 0.0
        "turns", "messages" -> (dailyTurnsOnDate(report.days, today) ?: 0L) > 0L
        else -> false
    }
    return if (available) today else today.minusDays(1)
}

internal fun analyticsWindowDates(lastDay: LocalDate, period: Int): List<LocalDate> =
    ((period - 1).toLong() downTo 0L).map { lastDay.minusDays(it) }

/** Provider-reported daily turns; null means no usable turn count for that day. */
internal fun dailyTurnsOnDate(days: List<AnalyticsDay>, date: LocalDate): Long? =
    days.firstOrNull { it.date == date.toString() }?.turns?.takeIf { it >= 0L }

/** Both turn-start and message tabs use the API's daily turns metric, not message counts. */
internal fun turnMessagePlotValues(report: AnalyticsReport, dates: List<LocalDate>): List<Double> =
    dates.map { (dailyTurnsOnDate(report.days, it) ?: 0L).toDouble() }

internal fun windowFeatures(report: AnalyticsReport, dates: List<LocalDate>): List<AnalyticsFeature> {
    val totals = linkedMapOf<String, Double>()
    for (date in dates) {
        for ((name, amount) in report.featureDays[date.toString()].orEmpty()) {
            if (amount.isFinite() && amount > 0.0) totals[name] = (totals[name] ?: 0.0) + amount
        }
    }
    return totals.map { AnalyticsFeature(it.key, it.value) }.sortedByDescending { it.usage }
}

/** A complete inclusive period ending on the selected actual-data day. */
internal fun lastSevenFeatureDates(today: LocalDate): List<LocalDate> =
    analyticsWindowDates(today, 7)

internal fun formatSevenDayFeatureRange(today: LocalDate): String {
    val pattern = DateTimeFormatter.ofPattern("yyyy.MM.dd.", Locale.ROOT)
    return "${today.minusDays(6).format(pattern)} ~ ${today.format(pattern).removeSuffix(".")}"
}

internal fun usageOnDate(
    featureDays: Map<String, Map<String, Double>>,
    date: LocalDate,
): Map<String, Double> = featureDays[date.toString()].orEmpty()

/** Inclusive rolling month. The axis has a fixed 5-day stride from its first day. */
internal fun lastThirtyFeatureDates(today: LocalDate): List<LocalDate> =
    analyticsWindowDates(today, 30)

internal fun formatThirtyDayAnalyticsRange(today: LocalDate): String {
    val pattern = DateTimeFormatter.ofPattern("yyyy.MM.dd.", Locale.ROOT)
    return "${today.minusDays(29).format(pattern)} ~ ${today.format(pattern).removeSuffix(".")}"
}

internal fun thirtyDayFeatureTick(date: LocalDate, index: Int, count: Int = 30): String =
    if (index == 0 || index == count - 1 || index % 5 == 0)
        date.dayOfMonth.toString().padStart(2, '0') else ""

internal fun modelTurnsOnDate(days: List<AnalyticsDay>, date: LocalDate): Map<String, Long> =
    days.firstOrNull { it.date == date.toString() }?.modelTurns.orEmpty()

internal fun modelTurnTotal(values: Map<String, Long>): Double =
    values.values.sumOf { it.coerceAtLeast(0L).toDouble() }

internal fun modelCreditsOnDate(report: AnalyticsReport, date: LocalDate): Map<String, Double> =
    report.modelCreditsDays[date.toString()].orEmpty()

internal fun positiveUsageTotal(values: Map<String, Double>): Double =
    values.values.filter { it.isFinite() && it > 0.0 }.sum()

/** Stable model order and colors for daily segments and legend, limited to selected dates. */
internal fun windowModelNames(report: AnalyticsReport, dates: List<LocalDate>): List<String> {
    val totalByModel = linkedMapOf<String, Double>()
    for (date in dates) {
        for ((name, credits) in modelCreditsOnDate(report, date)) {
            if (credits.isFinite() && credits > 0.0) {
                totalByModel[name] = (totalByModel[name] ?: 0.0) + credits
            }
        }
    }
    return totalByModel.keys.sortedByDescending { totalByModel[it] ?: 0.0 }
}

/** Today minus yesterday in percentage points of each day's total feature usage. */
internal fun dailyFeatureShareChange(
    feature: String,
    todayUsage: Map<String, Double>,
    yesterdayUsage: Map<String, Double>,
): Double? {
    val todayTotal = positiveUsageTotal(todayUsage)
    val yesterdayTotal = positiveUsageTotal(yesterdayUsage)
    if (todayTotal <= 0.0 || yesterdayTotal <= 0.0 ||
        !todayTotal.isFinite() || !yesterdayTotal.isFinite()) return null
    val todayValue = todayUsage[feature] ?: 0.0
    val yesterdayValue = yesterdayUsage[feature] ?: 0.0
    if (!todayValue.isFinite() || !yesterdayValue.isFinite() ||
        todayValue < 0.0 || yesterdayValue < 0.0) return null
    return (todayValue / todayTotal - yesterdayValue / yesterdayTotal) * 100.0
}

internal fun formatDailyFeatureShareChange(
    change: Double?,
    locale: Locale = Locale.getDefault(),
): String {
    if (change == null || !change.isFinite()) return "—"
    if (abs(change) < 1e-9) return "0.0%p"
    val magnitude = abs(change)
    val amount = if (magnitude < 0.1) "<0.1%p"
        else String.format(locale, "%.1f%%p", magnitude)
    return if (change > 0.0) "▲ $amount" else "▼ $amount"
}

/** Analytics is scoped to one Account detail, never the current or first account. */
internal fun selectCodexAnalyticsAccount(
    accounts: List<ProviderAccount>,
    accountId: LocalAccountId,
): ProviderAccount? = accounts.firstOrNull {
    it.localAccountId == accountId && it.providerId.value == "codex"
}
