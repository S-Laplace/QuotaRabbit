package com.alice.quotarabbit.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alice.quotarabbit.R
import com.alice.quotarabbit.ui.components.LiquidGlassSurfaceRole
import com.alice.quotarabbit.ui.components.QmLiquidGlassSurface
import com.alice.quotarabbit.ui.motion.QuotaRabbitMotion
import com.alice.quotarabbit.ui.motion.TrendUsagePoint
import com.alice.quotarabbit.ui.motion.rememberQuotaRabbitAnimatorsEnabled
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import com.alice.quotarabbit.ui.theme.QuotaRabbitShapes
import com.alice.quotarabbit.ui.theme.QuotaRabbitSpacing

@Composable
internal fun HomeTrendCard(trend: HomeTrendUi) {
    HomeSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.xs),
            ) {
                Text(
                    text = stringResource(trend.titleResId),
                    modifier = Modifier.testTag("home-trend-title"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(trend.metricLabelResId),
                    modifier = Modifier.testTag("home-trend-metric"),
                    style = MaterialTheme.typography.labelMedium,
                    color = QuotaRabbitTheme.colors.accent,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp)
                    .clip(QuotaRabbitShapes.lg)
                    .background(QuotaRabbitTheme.colors.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                if (trend.points.any { it.usageValue > 0.0 }) {
                    TrendBarChart(points = trend.points)
                } else {
                    Text(
                        text = stringResource(trend.descriptionResId),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AxisText(R.string.home_trend_axis_24h_ago)
                AxisText(R.string.home_trend_axis_12h)
                AxisText(R.string.home_trend_axis_now)
            }
        }
    }
}

@Composable
private fun TrendBarChart(points: List<HomeTrendPointUi>) {
    val barColor = QuotaRabbitTheme.colors.accent.copy(alpha = 0.76f)
    val animatorsEnabled = rememberQuotaRabbitAnimatorsEnabled()
    val revealFraction = remember { Animatable(QuotaRabbitMotion.initialTrendRevealTarget(animatorsEnabled)) }
    var hasRevealed by remember { mutableStateOf(false) }
    LaunchedEffect(points.size, animatorsEnabled) {
        if (!animatorsEnabled || hasRevealed || points.isEmpty()) {
            revealFraction.snapTo(1f)
        } else {
            hasRevealed = true
            revealFraction.snapTo(0f)
            revealFraction.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = QuotaRabbitMotion.TrendRedrawDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp)
            .padding(QuotaRabbitSpacing.md),
    ) {
        val sorted = points.sortedBy { it.capturedAt }
        val maxIndex = (sorted.size - 1).coerceAtLeast(1)
        val topInset = 6.dp.toPx()
        val bottomInset = 5.dp.toPx()
        val baselineY = size.height - bottomInset
        val bars = QuotaRabbitMotion.trendHourlyUsageBars(
            sorted.mapIndexed { index, point ->
                TrendUsagePoint(
                    x = (point.xPositionInWindow ?: (index.toFloat() / maxIndex)).coerceIn(0f, 1f),
                    value = point.usageValue.toFloat(),
                )
            },
        )
        val maxValue = bars.maxOfOrNull { it.value } ?: 0f
        val barWidth = QuotaRabbitMotion.trendBarWidth(
            availableWidth = size.width,
            barCount = QuotaRabbitMotion.TrendHourlyBucketCount,
            minWidth = 4.dp.toPx(),
            maxWidth = 10.dp.toPx(),
        )
        if (bars.isEmpty() || barWidth <= 0f || maxValue <= 0f) return@Canvas

        clipRect(right = size.width * revealFraction.value.coerceIn(0f, 1f)) {
            bars.forEach { bar ->
                val centerX = QuotaRabbitMotion.trendBarCenterX(
                    normalizedX = bar.x,
                    width = size.width,
                    barWidth = barWidth,
                )
                val topY = QuotaRabbitMotion.trendUsageBarY(
                    value = bar.value,
                    maxValue = maxValue,
                    height = size.height,
                    topInset = topInset,
                    bottomInset = bottomInset,
                )
                val barHeight = (baselineY - topY).coerceAtLeast(0f)
                val radius = QuotaRabbitMotion.trendBarTopCornerRadius(
                    barWidth = barWidth,
                    barHeight = barHeight,
                )
                drawTopRoundedTrendBar(
                    left = centerX - barWidth / 2f,
                    top = topY,
                    right = centerX + barWidth / 2f,
                    bottom = baselineY,
                    radius = radius,
                    color = barColor,
                )
            }
        }
    }
}

private fun DrawScope.drawTopRoundedTrendBar(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
    color: Color,
) {
    val boundedLeft = left.coerceAtMost(right)
    val boundedRight = right.coerceAtLeast(left)
    val boundedTop = top.coerceAtMost(bottom)
    val height = bottom - boundedTop
    val width = boundedRight - boundedLeft
    if (height <= 0f || width <= 0f) return

    val capRadius = radius.coerceIn(0f, minOf(width / 2f, height / 2f))
    val path = Path().apply {
        moveTo(boundedLeft, bottom)
        lineTo(boundedLeft, boundedTop + capRadius)
        quadraticTo(boundedLeft, boundedTop, boundedLeft + capRadius, boundedTop)
        lineTo(boundedRight - capRadius, boundedTop)
        quadraticTo(boundedRight, boundedTop, boundedRight, boundedTop + capRadius)
        lineTo(boundedRight, bottom)
        close()
    }
    drawPath(path = path, color = color)
}

@Composable
private fun AxisText(resId: Int) {
    Text(
        text = stringResource(resId),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun HomeLoadingCard(loading: HomeLoadingUi) {
    HomeSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = QuotaRabbitTheme.colors.accent,
            )
            Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.xs)) {
                Text(
                    text = stringResource(loading.titleResId),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(loading.descriptionResId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun HomeRefreshCard(uiState: HomeUiState) {
    HomeSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md)) {
            Text(
                text = stringResource(uiState.refresh.titleResId),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(uiState.refresh.descriptionResId),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            uiState.errorMessageResId?.let {
                Text(
                    text = stringResource(it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            RefreshTimeline(refresh = uiState.refresh)
        }
    }
}

@Composable
private fun RefreshTimeline(refresh: HomeRefreshUi) {
    Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.xs)) {
        refresh.lastSuccessfulRefreshAt?.let {
            Text(
                text = stringResource(R.string.home_last_refresh_format, formattedInstant(it)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        refresh.lastAttemptFinishedAt?.let {
            Text(
                text = stringResource(R.string.home_last_attempt_format, formattedInstant(it)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun HomeActionCard(
    uiState: HomeUiState,
    onLoginClick: () -> Unit,
) {
    QmLiquidGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        role = LiquidGlassSurfaceRole.Hero,
        cornerRadius = 28.dp,
        contentPadding = PaddingValues(QuotaRabbitSpacing.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md)) {
            Text(
                text = stringResource(uiState.statusTitleResId),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(uiState.statusDescriptionResId),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md)) {
                uiState.primaryAction?.let { action ->
                    Button(
                        onClick = { action.onClick(onLoginClick) },
                        modifier = Modifier.weight(1f),
                        shape = QuotaRabbitShapes.md,
                    ) {
                        Text(text = stringResource(action.labelResId))
                    }
                }
                uiState.secondaryAction?.let { action ->
                    OutlinedButton(
                        onClick = { action.onClick(onLoginClick) },
                        modifier = Modifier.weight(1f),
                        shape = QuotaRabbitShapes.md,
                    ) {
                        Text(text = stringResource(action.labelResId))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSurfaceCard(content: @Composable () -> Unit) {
    QmLiquidGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        role = LiquidGlassSurfaceRole.Card,
        cornerRadius = 28.dp,
        contentPadding = PaddingValues(QuotaRabbitSpacing.lg),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

private fun HomeActionUi.onClick(
    onLoginClick: () -> Unit,
) {
    when (kind) {
        HomeActionKind.LoginToCodex -> onLoginClick()
    }
}
