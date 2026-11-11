package com.alice.quotarabbit.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.quota.QuotaWindowDisplayKind
import com.alice.quotarabbit.ui.quota.formatProviderBalance
import java.time.Instant
import kotlin.math.roundToInt

@Composable
internal fun DashboardQuotaMetrics(cards: List<HomeQuotaCardUi>, now: Instant, muted: Boolean) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 300.dp && LocalDensity.current.fontScale <= 1.2f) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            cards.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { card ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                stringResource(card.titleResId),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            when (card.displayKind) {
                                QuotaWindowDisplayKind.Percent -> DashboardPercent(card.percent, muted, card.tone)
                                QuotaWindowDisplayKind.Balance -> DashboardBalance(card)
                                QuotaWindowDisplayKind.UsageCount -> DashboardCounts(card)
                                QuotaWindowDisplayKind.MultiModelFraction -> {
                                    if (card.modelBuckets.isEmpty()) {
                                        Text(stringResource(R.string.home_quota_status_unavailable))
                                    }
                                    card.modelBuckets.forEach { model ->
                                        Text(model.displayName, style = MaterialTheme.typography.labelSmall)
                                        DashboardPercent(
                                            model.remainingFraction.takeIf { it.isFinite() }
                                                ?.let { (it.coerceIn(0.0, 1.0) * 100).roundToInt() },
                                            muted,
                                            card.tone,
                                        )
                                        DashboardResetTime(model.resetAt, now)
                                    }
                                }
                            }
                            if (card.displayKind != QuotaWindowDisplayKind.Balance &&
                                card.displayKind != QuotaWindowDisplayKind.MultiModelFraction
                            ) {
                                DashboardResetTime(card.resetAt, now)
                            }
                            // Model-only providers may supply every fraction without an aggregate.
                            // An absent aggregate must not label their known model values unavailable.
                            if (card.displayKind != QuotaWindowDisplayKind.MultiModelFraction ||
                                card.modelBuckets.isEmpty() || card.status != HomeQuotaStatus.Unavailable
                            ) {
                                Text(
                                    stringResource(card.statusLabelResId),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else toneColor(card.tone),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardPercent(percent: Int?, muted: Boolean, tone: HomeStatusTone) {
    val label = percent?.let { stringResource(R.string.dashboard_remaining_format, it) }
        ?: stringResource(R.string.home_quota_status_unavailable)
    val ink = if (muted || percent == null) MaterialTheme.colorScheme.onSurfaceVariant
        else if (tone == HomeStatusTone.Warning || tone == HomeStatusTone.Danger) toneColor(tone)
        else toneColor(tone)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        RemainingQuotaGauge(percent, label, ink)
        Column(Modifier.weight(1f)) {
            Text(
                text = percent?.let { stringResource(R.string.home_quota_percent_format, it) }
                    ?: stringResource(R.string.dashboard_unknown),
                style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.dashboard_remaining),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RemainingQuotaGauge(percent: Int?, label: String, ink: Color) {
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    Canvas(
        modifier = Modifier.size(38.dp).semantics {
            contentDescription = label
            percent?.let { progressBarRangeInfo = ProgressBarRangeInfo(it / 100f, 0f..1f) }
        },
    ) {
        val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        val inset = stroke.width / 2
        val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
        drawArc(track, 135f, 270f, false, Offset(inset, inset), arcSize, style = stroke)
        percent?.let {
            drawArc(ink, 135f, 270f * it.coerceIn(0, 100) / 100, false, Offset(inset, inset), arcSize, style = stroke)
        }
    }
}

@Composable
private fun DashboardBalance(card: HomeQuotaCardUi) {
    Text(
        text = formatProviderBalance(card.balanceAmount, card.balanceCurrency)
            ?: stringResource(R.string.dashboard_unknown),
        style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(stringResource(R.string.dashboard_balance_remaining), style = MaterialTheme.typography.labelSmall)
    formatProviderBalance(card.originalBalanceAmount, card.originalBalanceCurrency)?.let {
        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (card.grantedBalance != null || card.toppedUpBalance != null) {
        val currency = card.originalBalanceCurrency ?: card.balanceCurrency
        val unknown = stringResource(R.string.dashboard_unknown)
        Text(
            stringResource(
                R.string.quota_balance_breakdown_format,
                formatProviderBalance(card.grantedBalance, currency) ?: unknown,
                formatProviderBalance(card.toppedUpBalance, currency) ?: unknown,
            ),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun DashboardCounts(card: HomeQuotaCardUi) {
    val unknown = stringResource(R.string.dashboard_unknown)
    Text(
        stringResource(R.string.dashboard_used_count_format, card.usedCount?.toString() ?: unknown, card.limitCount?.toString() ?: unknown),
        style = MaterialTheme.typography.titleMedium,
    )
    if (card.limitCount != null && card.usedCount != null && card.limitCount > 0 && card.usedCount >= 0) {
        Text(
            stringResource(R.string.dashboard_remaining_count_format, (card.limitCount - card.usedCount).coerceAtLeast(0)),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
internal fun DashboardResetTime(resetAt: Instant?, now: Instant) {
    val minutes = quotaResetMinutes(resetAt, now)
    val label = when {
        minutes == null -> stringResource(R.string.home_reset_unavailable)
        minutes == 0L -> stringResource(R.string.dashboard_reset_passed)
        minutes >= 1440 -> stringResource(R.string.dashboard_reset_days, minutes / 1440, minutes % 1440 / 60)
        minutes >= 60 -> stringResource(R.string.dashboard_reset_hours, minutes / 60, minutes % 60)
        else -> stringResource(R.string.dashboard_reset_minutes, minutes)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
