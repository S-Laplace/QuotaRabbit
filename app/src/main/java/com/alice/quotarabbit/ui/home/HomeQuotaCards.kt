package com.alice.quotarabbit.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.quota.QuotaWindowDisplayKind
import com.alice.quotarabbit.ui.components.LiquidGlassSurfaceRole
import com.alice.quotarabbit.ui.components.QmLiquidGlassSurface
import com.alice.quotarabbit.ui.motion.AnimatedQuotaPercent
import com.alice.quotarabbit.ui.motion.QuotaRabbitMotion
import com.alice.quotarabbit.ui.motion.MotionQuotaRail
import com.alice.quotarabbit.ui.motion.rememberQuotaRabbitAnimatorsEnabled
import com.alice.quotarabbit.ui.theme.QuotaRabbitSpacing
import com.alice.quotarabbit.ui.theme.QuotaRabbitTypography
import java.time.Instant
import kotlinx.coroutines.delay

@Composable
internal fun HomeQuotaCards(quotaCards: List<HomeQuotaCardUi>) {
    if (quotaCards.isEmpty()) return
    val now by produceState(initialValue = Instant.now()) {
        while (true) {
            delay(60_000)
            value = Instant.now()
        }
    }
    // Two cards per row by default; an odd trailing card stretches to a full row.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md),
    ) {
        quotaCards.chunked(2).forEach { rowCards ->
            if (rowCards.size == 2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md),
                ) {
                    QuotaCard(
                        card = rowCards[0],
                        now = now,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    QuotaCard(
                        card = rowCards[1],
                        now = now,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            } else {
                QuotaCard(card = rowCards[0], now = now, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun QuotaCard(
    card: HomeQuotaCardUi,
    now: Instant,
    modifier: Modifier = Modifier,
) {
    QmLiquidGlassSurface(
        modifier = modifier,
        role = LiquidGlassSurfaceRole.Card,
        cornerRadius = 28.dp,
        contentPadding = PaddingValues(QuotaRabbitSpacing.lg),
    ) {
        when (card.displayKind) {
            QuotaWindowDisplayKind.Percent -> PercentQuotaCard(card, now)
            QuotaWindowDisplayKind.Balance -> BalanceQuotaCard(card)
            QuotaWindowDisplayKind.UsageCount -> UsageCountQuotaCard(card, now)
            QuotaWindowDisplayKind.MultiModelFraction -> PercentQuotaCard(card, now) // fallback to percent rendering
        }
    }
}

@Composable
private fun PercentQuotaCard(card: HomeQuotaCardUi, now: Instant) {
    val tone = card.tone
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(card.titleResId),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatusDot(tone = tone)
        }
        AnimatedQuotaPercent(
            percent = card.percent,
            style = QuotaRabbitTypography.current.number,
            color = MaterialTheme.colorScheme.onSurface,
        )
        MotionQuotaRail(
            percent = card.percent,
            color = toneColor(tone),
        )
        DashboardResetTime(card.resetAt, now)
        Text(
            text = stringResource(card.statusLabelResId),
            style = MaterialTheme.typography.labelMedium,
            color = toneColor(tone),
            maxLines = 1,
        )
    }
}

@Composable
private fun BalanceQuotaCard(card: HomeQuotaCardUi) {
    val tone = card.tone
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(card.titleResId),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatusDot(tone = tone)
        }
        Text(
            text = buildString {
                append(
                    com.alice.quotarabbit.ui.quota.formatProviderBalance(
                        card.balanceAmount,
                        card.balanceCurrency,
                    ).orEmpty(),
                )
            }.trim(),
            style = QuotaRabbitTypography.current.number,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val originalBalance = com.alice.quotarabbit.ui.quota.formatProviderBalance(
            card.originalBalanceAmount, card.originalBalanceCurrency,
        )
        if (originalBalance != null) {
            Text(
                text = originalBalance,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (card.grantedBalance != null || card.toppedUpBalance != null) {
            val nativeCurrency = card.originalBalanceCurrency ?: card.balanceCurrency
            val granted = com.alice.quotarabbit.ui.quota.formatProviderBalance(card.grantedBalance, nativeCurrency).orEmpty()
            val toppedUp = com.alice.quotarabbit.ui.quota.formatProviderBalance(card.toppedUpBalance, nativeCurrency).orEmpty()
            Text(
                text = stringResource(R.string.quota_balance_breakdown_format, granted, toppedUp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        card.subLabel?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            text = stringResource(card.statusLabelResId),
            style = MaterialTheme.typography.labelMedium,
            color = toneColor(tone),
            maxLines = 1,
        )
    }
}

@Composable
private fun UsageCountQuotaCard(card: HomeQuotaCardUi, now: Instant) {
    val tone = card.tone
    val percent = card.percent // derived from usedPercent
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(card.titleResId),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatusDot(tone = tone)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${card.usedCount ?: "–"} / ${card.limitCount ?: "–"}",
                style = QuotaRabbitTypography.current.number,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        MotionQuotaRail(
            percent = percent,
            color = toneColor(tone),
        )
        DashboardResetTime(card.resetAt, now)
        Text(
            text = stringResource(card.statusLabelResId),
            style = MaterialTheme.typography.labelMedium,
            color = toneColor(tone),
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusDot(tone: HomeStatusTone) {
    val animatorsEnabled = rememberQuotaRabbitAnimatorsEnabled()
    val shouldBreathe = QuotaRabbitMotion.statusDotBreathEnabled(
        isSemanticTone = tone != HomeStatusTone.Neutral,
        animatorsEnabled = animatorsEnabled,
    )
    val color = toneColor(tone)
    val haloScale: Float
    val haloAlpha: Float
    if (shouldBreathe) {
        val transition = rememberInfiniteTransition(label = "quota_status_dot_breath")
        val animatedScale by transition.animateFloat(
            initialValue = 1f,
            targetValue = QuotaRabbitMotion.StatusDotBreathMaxScale,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = QuotaRabbitMotion.StatusDotBreathDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "quota_status_dot_halo_scale",
        )
        val animatedAlpha by transition.animateFloat(
            initialValue = QuotaRabbitMotion.StatusDotBreathMinAlpha,
            targetValue = 0.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = QuotaRabbitMotion.StatusDotBreathDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "quota_status_dot_halo_alpha",
        )
        haloScale = animatedScale
        haloAlpha = animatedAlpha
    } else {
        haloScale = 1f
        haloAlpha = 0f
    }

    Box(
        modifier = Modifier
            .size(15.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (haloAlpha > 0f) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .graphicsLayer {
                        scaleX = haloScale
                        scaleY = haloScale
                        alpha = haloAlpha
                    }
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}
