package com.alice.quotarabbit.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alice.quotarabbit.R
import com.alice.quotarabbit.providers.codex.network.*
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Seven daily 100% stacked bars; the percentage legend reflects today only. */
@Composable
internal fun FeatureUsageSevenDayChart(report: AnalyticsReport, today: LocalDate) {
    val palette = QuotaRabbitTheme.colors
    val dates = lastSevenFeatureDates(today)
    val features = windowFeatures(report, dates)
    val heldDate = remember(report, today) { mutableStateOf<LocalDate?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().analyticsPressHoldPlot(
                dates, heldDate, AnalyticsHoldPlotAxis.VERTICAL_SEVEN_DAYS)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        dates.forEach { date ->
            val daily = usageOnDate(report.featureDays, date)
            val total = positiveUsageTotal(daily)
            Row(Modifier.fillMaxWidth().testTag("analytics-feature-day:$date"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(date.dayOfMonth.toString().padStart(2, '0'),
                    color = palette.secondary, style = MaterialTheme.typography.labelSmall)
                Row(Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(8.dp))
                    .background(palette.surfaceSoft)) {
                    if (total > 0.0 && total.isFinite()) {
                        features.forEachIndexed { index, feature ->
                            val fraction = featureUsageBarFraction(daily[feature.label] ?: 0.0, total)
                            if (fraction > 0f) Box(Modifier.weight(fraction).fillMaxHeight()
                                .background(featureBarColors[index % featureBarColors.size]))
                        }
                    }
                }
            }
        }
            }
            heldDate.value?.let { day ->
                AnalyticsHoldDetail(day, featureHoldRows(usageOnDate(report.featureDays, day)),
                    Modifier.align(Alignment.TopCenter))
            }
        }
        Text(stringResource(R.string.analytics_feature_share_on_date,
            today.format(DateTimeFormatter.ofPattern("MM.dd", Locale.ROOT))),
            color = palette.secondary, style = MaterialTheme.typography.labelSmall)
        val todayUsage = usageOnDate(report.featureDays, today)
        val yesterdayUsage = usageOnDate(report.featureDays, today.minusDays(1))
        val todayTotal = positiveUsageTotal(todayUsage)
        if (features.isEmpty()) {
            Text(stringResource(R.string.analytics_no_feature_analytics_for_the_selected_period),
                color = palette.secondary, style = MaterialTheme.typography.bodySmall)
        } else {
            features.forEachIndexed { index, feature ->
                Row(Modifier.fillMaxWidth().testTag("analytics-feature-legend:$index"),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(featureBarColors[index % featureBarColors.size],
                        RoundedCornerShape(4.dp)))
                    Spacer(Modifier.width(6.dp))
                    Text(localizedFeatureLabel(feature.label), modifier = Modifier.weight(1f),
                        color = palette.secondary, style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatFeatureUsageShare(todayUsage[feature.label] ?: 0.0, todayTotal),
                        color = palette.secondary, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("analytics-feature-percentage:$index"))
                    Spacer(Modifier.width(8.dp))
                    val change = dailyFeatureShareChange(
                        feature.label, todayUsage, yesterdayUsage)
                    Text(
                        formatDailyFeatureShareChange(change),
                        color = when {
                            change == null || abs(change) < 1e-9 -> palette.secondary
                            change > 0.0 -> Color(0xFFDC3545)
                            else -> Color(0xFF3071CF)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(83.dp)
                            .testTag("analytics-feature-change:$index"),
                    )
                }
            }
        }
    }
}

/** Seven daily 100% stacked model-credit bars with the same layout as the feature view.
 *  Model ratios are relative shares of provider-reported credits, not token/turn counts.
 */
@Composable
internal fun ModelUsageSevenDayChart(report: AnalyticsReport, lastDay: LocalDate) {
    val palette = QuotaRabbitTheme.colors
    val dates = lastSevenFeatureDates(lastDay)
    val names = windowModelNames(report, dates)
    val heldDate = remember(report, lastDay) { mutableStateOf<LocalDate?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().analyticsPressHoldPlot(
                dates, heldDate, AnalyticsHoldPlotAxis.VERTICAL_SEVEN_DAYS)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        dates.forEach { date ->
            val daily = modelCreditsOnDate(report, date)
            val total = positiveUsageTotal(daily)
            Row(Modifier.fillMaxWidth().testTag("analytics-model-7d-day:$date"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(date.dayOfMonth.toString().padStart(2, '0'),
                    color = palette.secondary, style = MaterialTheme.typography.labelSmall)
                Row(Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(8.dp))
                    .background(palette.surfaceSoft)) {
                    if (total > 0.0 && total.isFinite()) {
                        names.forEachIndexed { index, name ->
                            val fraction = featureUsageBarFraction(daily[name] ?: 0.0, total)
                            if (fraction > 0f) Box(Modifier.weight(fraction).fillMaxHeight()
                                .background(barColors[index % barColors.size]))
                        }
                    }
                }
            }
        }
            }
            heldDate.value?.let { day ->
                AnalyticsHoldDetail(day, modelHoldRows(modelCreditsOnDate(report, day)),
                    Modifier.align(Alignment.TopCenter))
            }
        }
        Text(stringResource(R.string.analytics_model_share_on_date,
            lastDay.format(DateTimeFormatter.ofPattern("MM.dd", Locale.ROOT))),
            color = palette.secondary, style = MaterialTheme.typography.labelSmall)
        val selectedDay = modelCreditsOnDate(report, lastDay)
        val previousDay = modelCreditsOnDate(report, lastDay.minusDays(1))
        val selectedTotal = positiveUsageTotal(selectedDay)
        if (names.isEmpty()) {
            Text(stringResource(R.string.analytics_no_model_data_available),
                color = palette.secondary, style = MaterialTheme.typography.bodySmall)
        } else {
            names.forEachIndexed { index, name ->
                Row(Modifier.fillMaxWidth().testTag("analytics-model-7d-legend:$index"),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(
                        barColors[index % barColors.size], RoundedCornerShape(4.dp)))
                    Spacer(Modifier.width(6.dp))
                    Text(name, modifier = Modifier.weight(1f), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, color = palette.secondary,
                        style = MaterialTheme.typography.bodySmall)
                    Text(formatFeatureUsageShare(selectedDay[name] ?: 0.0, selectedTotal),
                        color = palette.secondary, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("analytics-model-7d-percentage:$index"))
                    Spacer(Modifier.width(8.dp))
                    val change = dailyFeatureShareChange(name, selectedDay, previousDay)
                    Text(formatDailyFeatureShareChange(change),
                        color = when {
                            change == null || abs(change) < 1e-9 -> palette.secondary
                            change > 0.0 -> Color(0xFFDC3545)
                            else -> Color(0xFF3071CF)
                        }, style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.End, modifier = Modifier.width(83.dp)
                            .testTag("analytics-model-7d-change:$index"))
                }
            }
        }
    }
}

/** Model credits come from the model breakdown, including days absent from daily counts.
 *  The model breakdown does not provide turn counts or exact model token counts.
 */
@Composable
internal fun ModelUsagePeriodChart(report: AnalyticsReport, today: LocalDate, period: Int) {
    val palette = QuotaRabbitTheme.colors
    val dates = if (period == 30) lastThirtyFeatureDates(today) else lastSevenFeatureDates(today)
    val totals = dates.map { date -> positiveUsageTotal(modelCreditsOnDate(report, date)) }
    val maximum = totals.maxOrNull()?.takeIf { it > 0.0 && it.isFinite() } ?: 0.0
    val modelNames = windowModelNames(report, dates)
    Text(stringResource(R.string.analytics_measured_in_credits),
        color = palette.secondary, style = MaterialTheme.typography.bodySmall)
    if (maximum <= 0.0 || modelNames.isEmpty()) {
        Text(stringResource(R.string.analytics_no_model_data_available),
            color = palette.secondary, style = MaterialTheme.typography.bodySmall)
    }
    val heldDate = remember(report, today, period) { mutableStateOf<LocalDate?>(null) }
    val plotScroll = rememberScrollState()
    Box(Modifier.fillMaxWidth().analyticsPressHoldPlot(
        dates, heldDate, AnalyticsHoldPlotAxis.HORIZONTAL_SCROLLED,
        scrollOffsetPx = { plotScroll.value })) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(plotScroll)
            .testTag("analytics-model-${period}d-scroll"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        dates.forEachIndexed { index, date ->
            val modelCredits = modelCreditsOnDate(report, date)
            val total = totals[index]
            Column(Modifier.width(22.dp)
                .testTag("analytics-model-${period}d-day:$date"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.width(22.dp).height(166.dp),
                    contentAlignment = Alignment.BottomCenter) {
                    if (total > 0.0 && maximum > 0.0) {
                        val height = (featureUsageBarFraction(total, maximum) * 160f)
                            .coerceAtLeast(2f).dp
                        Column(Modifier.width(22.dp).height(height)
                            .clip(thirtyDayVerticalBarShape),
                            verticalArrangement = Arrangement.Bottom) {
                            modelNames.forEachIndexed { modelIndex, name ->
                                val share = featureUsageBarFraction(
                                    modelCredits[name] ?: 0.0, total)
                                if (share > 0f) Box(Modifier.fillMaxWidth()
                                    .weight(share)
                                    .background(barColors[modelIndex % barColors.size]))
                            }
                        }
                    }
                }
                Text(
                    if (period == 30) thirtyDayFeatureTick(date, index, dates.size)
                    else date.dayOfMonth.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.secondary,
                    maxLines = 1,
                    modifier = Modifier.height(17.dp)
                        .testTag("analytics-model-${period}d-tick:$index"),
                )
            }
        }
    }
    heldDate.value?.let { day ->
        AnalyticsHoldDetail(day, modelHoldRows(modelCreditsOnDate(report, day)),
            Modifier.align(Alignment.TopCenter))
    }
    }
    val totalCredits = totals.sum()
    modelNames.forEachIndexed { index, name ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(
                barColors[index % barColors.size], RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(6.dp))
            Text(name, modifier = Modifier.weight(1f), maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = palette.secondary)
            val credits = dates.sumOf { date ->
                modelCreditsOnDate(report, date)[name] ?: 0.0
            }
            Text(formatFeatureUsageShare(credits, totalCredits),
                style = MaterialTheme.typography.bodySmall, color = palette.secondary)
        }
    }
}

/** Thirty vertically stacked daily feature bars. The model and feature charts
 *  share this 22dp × 160dp rounded, horizontally scrollable layout.
 */
@Composable
internal fun FeatureUsageThirtyDayChart(report: AnalyticsReport, today: LocalDate) {
    val palette = QuotaRabbitTheme.colors
    val dates = lastThirtyFeatureDates(today)
    val features = windowFeatures(report, dates)
    val totals = dates.map { date ->
        positiveUsageTotal(usageOnDate(report.featureDays, date))
    }
    val maximum = totals.maxOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
    if (maximum == 0.0) {
        Text(stringResource(R.string.analytics_no_feature_analytics_for_the_selected_period),
            color = palette.secondary, style = MaterialTheme.typography.bodySmall)
    }
    val heldDate = remember(report, today) { mutableStateOf<LocalDate?>(null) }
    val plotScroll = rememberScrollState()
    Box(Modifier.fillMaxWidth().analyticsPressHoldPlot(
        dates, heldDate, AnalyticsHoldPlotAxis.HORIZONTAL_SCROLLED,
        scrollOffsetPx = { plotScroll.value })) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(plotScroll)
            .testTag("analytics-feature-30d-scroll"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        dates.forEachIndexed { index, date ->
            val daily = usageOnDate(report.featureDays, date)
            val total = totals[index]
            Column(Modifier.width(22.dp)
                .testTag("analytics-feature-30d-day:$date"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.width(22.dp).height(166.dp),
                    contentAlignment = Alignment.BottomCenter) {
                    if (total > 0.0 && maximum > 0.0) {
                        val height = (featureUsageBarFraction(total, maximum) * 160f)
                            .coerceAtLeast(2f).dp
                        Column(Modifier.width(22.dp).height(height)
                            .clip(thirtyDayVerticalBarShape),
                            verticalArrangement = Arrangement.Bottom) {
                            features.forEachIndexed { featureIndex, feature ->
                                val share = featureUsageBarFraction(
                                    daily[feature.label] ?: 0.0, total)
                                if (share > 0f) Box(Modifier.fillMaxWidth()
                                    .weight(share)
                                    .background(featureBarColors[featureIndex % featureBarColors.size]))
                            }
                        }
                    }
                }
                Text(
                    thirtyDayFeatureTick(date, index, dates.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.secondary,
                    maxLines = 1,
                    modifier = Modifier.height(17.dp).testTag("analytics-feature-30d-tick:$index"),
                )
            }
        }
    }
    heldDate.value?.let { day ->
        AnalyticsHoldDetail(day, featureHoldRows(usageOnDate(report.featureDays, day)),
            Modifier.align(Alignment.TopCenter))
    }
    }
    features.forEachIndexed { index, feature ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(
                featureBarColors[index % featureBarColors.size], RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(6.dp))
            Text(localizedFeatureLabel(feature.label), modifier = Modifier.weight(1f),
                color = palette.secondary, style = MaterialTheme.typography.bodySmall)
            val total = features.sumOf { it.usage }
            Text(formatFeatureUsageShare(feature.usage, total),
                style = MaterialTheme.typography.bodySmall, color = palette.secondary)
        }
    }
}

/** One rounded 100%-width bar, segmented in proportion to each feature's selected-period usage. */
@Composable
internal fun FeatureUsageHorizontalBars(features: List<AnalyticsFeature>) {
    val palette = QuotaRabbitTheme.colors
    val visibleFeatures = features.filter { it.usage.isFinite() && it.usage > 0.0 }
    val total = visibleFeatures.sumOf { it.usage }
    if (visibleFeatures.isEmpty() || !total.isFinite() || total <= 0.0) return

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.surfaceSoft)
                .testTag("analytics-feature-bar:stacked"),
        ) {
            visibleFeatures.forEachIndexed { index, feature ->
                val fraction = featureUsageBarFraction(feature.usage, total)
                if (fraction > 0f) {
                    Box(Modifier.weight(fraction).fillMaxHeight()
                        .background(featureBarColors[index % featureBarColors.size]))
                }
            }
        }
        visibleFeatures.forEachIndexed { index, feature ->
            Row(
                Modifier.fillMaxWidth().testTag("analytics-feature-legend:$index"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).background(featureBarColors[index % featureBarColors.size],
                    RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(6.dp))
                Text(localizedFeatureLabel(feature.label), modifier = Modifier.weight(1f),
                    color = palette.secondary, style = MaterialTheme.typography.bodySmall)
                Text(formatFeatureUsageShare(feature.usage, total),
                    color = palette.secondary, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("analytics-feature-percentage:$index"))
            }
        }
    }
}

@Composable internal fun localizedFeatureLabel(label: String): String = when {
    label == "채팅" -> stringResource(R.string.analytics_feature_chat)
    label == "서브에이전트" -> stringResource(R.string.analytics_feature_subagent)
    label == "메모리 업데이트" -> stringResource(R.string.analytics_feature_memory)
    label == "채팅 제목" -> stringResource(R.string.analytics_feature_title)
    label == "기타 기능" -> stringResource(R.string.analytics_feature_other)
    label.startsWith("기타 기능 · ") -> stringResource(
        R.string.analytics_feature_other_with_source,
        label.removePrefix("기타 기능 · "),
    )
    else -> label
}
