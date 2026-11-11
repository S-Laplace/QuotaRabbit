package com.alice.quotarabbit.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alice.quotarabbit.R
import com.alice.quotarabbit.ui.components.LiquidGlassSurfaceRole
import com.alice.quotarabbit.ui.components.QmLiquidGlassSurface
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import com.alice.quotarabbit.ui.theme.QuotaRabbitSpacing
import com.alice.quotarabbit.ui.theme.avatarColor
import com.alice.quotarabbit.ui.theme.avatarInitialColor
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun HomeHeroGlassLayer(uiState: HomeUiState) {
    val account = uiState.account ?: return
    val primaryCard = uiState.fiveHourCard ?: uiState.weeklyCard
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    Box(modifier = Modifier.fillMaxWidth()) {
        QmLiquidGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            role = LiquidGlassSurfaceRole.Hero,
            cornerRadius = 28.dp,
            contentPadding = PaddingValues(QuotaRabbitSpacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.lg),
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md),
                ) {
                    HeroAccountAvatar(account)
                    Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.xs)) {
                        Text(
                            text = account.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = QuotaRabbitTheme.colors.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.home_current_account),
                            style = MaterialTheme.typography.labelSmall,
                            color = QuotaRabbitTheme.colors.secondary,
                            maxLines = 1,
                        )
                        Text(
                            text = stringResource(uiState.statusTitleResId),
                            style = MaterialTheme.typography.labelSmall,
                            color = toneColor(primaryCard?.tone ?: HomeStatusTone.Neutral),
                            maxLines = 1,
                        )
                    }
                }
                val planText = account.planType
                val hasCredits = account.credits !is HomeCreditsUi.Unavailable
                if (planText != null || hasCredits) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.xs),
                    ) {
                        if (planText != null) {
                            Text(
                                text = stringResource(R.string.home_account_plan_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = QuotaRabbitTheme.colors.secondary,
                                maxLines = 1,
                            )
                            Text(
                                text = planText,
                                style = MaterialTheme.typography.titleMedium,
                                color = QuotaRabbitTheme.colors.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (hasCredits) {
                            Text(
                                text = stringResource(R.string.home_account_credits_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = QuotaRabbitTheme.colors.secondary,
                                maxLines = 1,
                            )
                            Text(
                                text = account.credits.displayText(locale),
                                style = MaterialTheme.typography.titleMedium,
                                color = QuotaRabbitTheme.colors.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroAccountAvatar(account: HomeAccountUi) {
    val iconRes = account.providerIconResId
    if (iconRes != null) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(QuotaRabbitTheme.colors.surface)
                .border(
                    width = 1.dp,
                    color = QuotaRabbitTheme.colors.secondary.copy(alpha = 0.16f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Brand vectors are black-filled monochrome strokes (DESIGN §361): tint them with the
            // theme ink so they stay visible on dark surfaces instead of disappearing into them.
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = QuotaRabbitTheme.colors.primary,
                modifier = Modifier.size(26.dp),
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(avatarColor(account.avatarColorKey)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = account.avatarInitial,
                style = MaterialTheme.typography.labelLarge,
                // avatarColor() badges are light-toned in dark mode, so a dark ink reads; in light
                // mode the badge is saturated/dark and white reads. surface alone would vanish in dark.
                color = avatarInitialColor(),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HomeCreditsUi.displayText(locale: Locale): String =
    when (this) {
        is HomeCreditsUi.Balance -> stringResource(
            R.string.home_account_credits_balance_format,
            formatCreditBalance(amount, locale),
        )
        HomeCreditsUi.Unlimited -> stringResource(R.string.home_account_credits_unlimited)
        HomeCreditsUi.Unavailable -> stringResource(R.string.home_account_credits_unavailable)
    }

private fun formatCreditBalance(amount: Double, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }.format(amount)
