package com.alice.quotarabbit.ui.account

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.alice.quotarabbit.domain.settings.NotificationPreferences
import com.alice.quotarabbit.ui.home.HomeQuotaStatus
import com.alice.quotarabbit.ui.home.remainingQuotaStatus
import com.alice.quotarabbit.ui.theme.QuotaRabbitColorPalette
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alice.quotarabbit.R

/** A single semantic fill color; user thresholds match the Home quota status policy. */
internal fun quotaSolidColor(
    percent: Int?,
    quotaPreferences: NotificationPreferences,
    palette: QuotaRabbitColorPalette,
): Color = when (remainingQuotaStatus(percent?.toDouble(), quotaPreferences)) {
    HomeQuotaStatus.Normal -> palette.success
    HomeQuotaStatus.Caution -> palette.warning
    HomeQuotaStatus.Warning, HomeQuotaStatus.Exhausted -> palette.danger
    HomeQuotaStatus.Unavailable -> palette.secondary
}

@Composable
internal fun CodexQuotaRing(
    remainingPercent: Int?, accountId: String, size: Dp = 76.dp, strokeWidth: Dp = 8.dp,
    compact: Boolean = true,
    quotaPreferences: NotificationPreferences = NotificationPreferences(),
) {
    val value = remainingPercent?.coerceIn(0, 100)
    val description = value?.let { stringResource(R.string.dashboard_remaining_format, it) }
        ?: stringResource(R.string.account_plan_unavailable)
    val fillColor = quotaSolidColor(value, quotaPreferences, QuotaRabbitTheme.colors)
    val track = MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size).testTag(
            if (compact) "account-weekly-gauge:$accountId" else "account-detail-gauge:$accountId"
        ).semantics {
            contentDescription = description
            if (value != null) progressBarRangeInfo = ProgressBarRangeInfo(value / 100f, 0f..1f)
        }) {
            val stroke = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2f
            val arc = Size(this.size.width - stroke.width, this.size.height - stroke.width)
            drawArc(track, 135f, 270f, false, Offset(inset, inset), arc, style = stroke)
            if (value != null && value > 0) drawArc(
                color = fillColor,
                startAngle = 135f, sweepAngle = value * 2.7f, useCenter = false,
                topLeft = Offset(inset, inset), size = arc, style = stroke,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(value?.let { "$it%" } ?: "--%", style = if (compact)
                MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                else MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold))
            Text(
                stringResource(if (compact) R.string.account_ring_compact_remaining
                    else R.string.dashboard_remaining),
                style = if (compact) MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
                    else MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * Keeps small positive values visible as a capsule while clipping every fill
 * to the track boundary. Used for both the 5h and 7-day quotas.
 */
internal fun quotaFillWidthPx(trackWidth: Float, trackHeight: Float, percent: Int?): Float {
    if (percent == null || percent <= 0 || trackWidth <= 0f || trackHeight <= 0f) return 0f
    return (trackWidth * (percent.coerceIn(0, 100) / 100f))
        .coerceAtLeast(trackHeight).coerceAtMost(trackWidth)
}

@Composable
internal fun CodexSolidQuotaBar(
    percent: Int?,
    modifier: Modifier = Modifier,
    height: Dp = 9.dp,
    quotaPreferences: NotificationPreferences = NotificationPreferences(),
) {
    val value = percent?.coerceIn(0, 100)
    val description = value?.let { "$it% left" } ?: "Unavailable"
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val fillColor = quotaSolidColor(value, quotaPreferences, QuotaRabbitTheme.colors)
    Canvas(modifier.fillMaxWidth().height(height)
        .clip(RoundedCornerShape(50))
        .semantics {
            contentDescription = description
            if (value != null) progressBarRangeInfo = ProgressBarRangeInfo(value / 100f, 0f..1f)
        }) {
        drawRect(trackColor)
        val fillWidth = quotaFillWidthPx(size.width, size.height, value)
        if (fillWidth > 0f) {
            drawRoundRect(
                color = fillColor,
                size = Size(fillWidth, size.height),
                cornerRadius = CornerRadius(size.height / 2f),
            )
        }
    }
}
