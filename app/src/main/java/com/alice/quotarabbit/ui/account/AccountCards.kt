package com.alice.quotarabbit.ui.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.QuotaWindowId
import com.alice.quotarabbit.ui.components.AccountProfileAvatar
import com.alice.quotarabbit.ui.motion.QuotaRabbitMotion
import com.alice.quotarabbit.ui.motion.rememberQuotaRabbitAnimatorsEnabled
import com.alice.quotarabbit.ui.theme.QuotaRabbitShapes
import com.alice.quotarabbit.ui.theme.QuotaRabbitSpacing
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import java.text.NumberFormat
import java.time.Instant
import java.util.Locale

@Composable
internal fun CurrentAccountCard(account: AccountItemUi) {
    AccountSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccountAvatar(account = account, size = 48.dp)
                Text(
                    text = account.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(R.string.account_current_card_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusBadge(account = account)
        }
    }
}

@Composable
internal fun EmptyAccountsCard(onAddAccountClick: () -> Unit) {
    AccountSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md)) {
            Text(
                text = stringResource(R.string.account_no_accounts_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.account_no_accounts_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onAddAccountClick, shape = QuotaRabbitShapes.md) {
                Text(text = stringResource(R.string.account_add_account))
            }
        }
    }
}

@Composable
internal fun AccountCard(
    account: AccountItemUi,
    onSetCurrentClick: (LocalAccountId) -> Unit,
    onRenameClick: (LocalAccountId) -> Unit,
    onReloginClick: (LocalAccountId) -> Unit,
    onQuotaAlertToggle: (LocalAccountId, QuotaWindowId, Boolean) -> Unit,
    onDeleteClick: (LocalAccountId) -> Unit,
    onToggleExpanded: (LocalAccountId) -> Unit,
) {
    AccountSurfaceCard(modifier = Modifier.testTag("account-card:${account.id.value}")) {
        val animatorsEnabled = rememberQuotaRabbitAnimatorsEnabled()
        val largeText = LocalDensity.current.fontScale > 1.2f
        Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md)) {
            val toggleLabel = stringResource(
                if (account.isExpanded) R.string.account_collapse else R.string.account_expand,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = toggleLabel) { onToggleExpanded(account.id) },
                verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AccountAvatar(account = account, size = 44.dp)
                    if (largeText) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        Text(
                            text = account.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    AccountExpandIconButton(
                        isExpanded = account.isExpanded,
                        contentDescription = toggleLabel,
                        animatorsEnabled = animatorsEnabled,
                        onClick = { onToggleExpanded(account.id) },
                        modifier = Modifier.testTag("account-expand:${account.id.value}"),
                    )
                }
                if (largeText) {
                    Text(
                        text = account.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = stringResource(account.statusLabelResId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = account.lastSuccessfulRefreshAt?.let {
                        stringResource(account.refreshSummaryResId, formattedInstant(it))
                    } ?: stringResource(account.refreshSummaryResId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusBadge(account = account)
            }
            AccountDetailsDrawer(
                account = account,
                animatorsEnabled = animatorsEnabled,
                onSetCurrentClick = onSetCurrentClick,
                onRenameClick = onRenameClick,
                onReloginClick = onReloginClick,
                onQuotaAlertToggle = onQuotaAlertToggle,
                onDeleteClick = onDeleteClick,
            )
        }
    }
}

@Composable
private fun AccountExpandIconButton(
    isExpanded: Boolean,
    contentDescription: String,
    animatorsEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = if (animatorsEnabled) {
            tween(
                durationMillis = QuotaRabbitMotion.AccountDrawerDurationMillis,
                easing = FastOutSlowInEasing,
            )
        } else {
            snap()
        },
        label = "account_expand_icon_rotation",
    )
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(R.drawable.ic_chevron_down),
            contentDescription = contentDescription,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { rotationZ = rotation },
            tint = QuotaRabbitTheme.colors.accent,
        )
    }
}

@Composable
private fun AccountDetailsDrawer(
    account: AccountItemUi,
    animatorsEnabled: Boolean,
    onSetCurrentClick: (LocalAccountId) -> Unit,
    onRenameClick: (LocalAccountId) -> Unit,
    onReloginClick: (LocalAccountId) -> Unit,
    onQuotaAlertToggle: (LocalAccountId, QuotaWindowId, Boolean) -> Unit,
    onDeleteClick: (LocalAccountId) -> Unit,
) {
    AnimatedVisibility(
        visible = account.isExpanded,
        modifier = Modifier.clipToBounds(),
        enter = if (animatorsEnabled) {
            expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(
                    durationMillis = QuotaRabbitMotion.AccountDrawerDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = QuotaRabbitMotion.AccountDrawerDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
            )
        } else {
            EnterTransition.None
        },
        exit = if (animatorsEnabled) {
            shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(
                    durationMillis = QuotaRabbitMotion.AccountDrawerDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
            ) + fadeOut(
                animationSpec = tween(
                    durationMillis = QuotaRabbitMotion.AccountDrawerDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
            )
        } else {
            ExitTransition.None
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md)) {
            PlanCreditsRow(account = account)
            QuotaSummaryRow(account.quotaSummaries)
            if (account.modelFamilies.isNotEmpty()) {
                ModelDetailsSection(account.modelFamilies)
            }
            QuotaAlertSwitches(
                account = account,
                onQuotaAlertToggle = onQuotaAlertToggle,
            )
            AccountActions(
                account = account,
                onSetCurrentClick = onSetCurrentClick,
                onRenameClick = onRenameClick,
                onReloginClick = onReloginClick,
                onDeleteClick = onDeleteClick,
            )
        }
    }
}

@Composable
internal fun StatusBadge(account: AccountItemUi) {
    Box(
        modifier = Modifier
            .clip(QuotaRabbitShapes.pill)
            .background(account.tone.softColor())
            .padding(horizontal = QuotaRabbitSpacing.md, vertical = QuotaRabbitSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(account.badgeLabelResId),
            style = MaterialTheme.typography.labelSmall,
            color = account.tone.color(),
        )
    }
}

@Composable
private fun AccountAvatar(account: AccountItemUi, size: Dp) {
    AccountProfileAvatar(
        initial = account.avatarInitial,
        avatarColorKey = account.avatarColorKey,
        accentColorId = account.accentColorId,
        minimumSize = size,
    )
}

// Share the home time presentation and timezone-change handling across account screens.
@Composable
internal fun formattedInstant(instant: Instant): String =
    com.alice.quotarabbit.ui.home.formattedInstant(instant)

@Composable
internal fun AccountCreditsUi.displayText(locale: Locale): String =
    when (this) {
        is AccountCreditsUi.Balance -> stringResource(
            R.string.account_credits_balance_format,
            formatCreditBalance(amount, locale),
        )
        AccountCreditsUi.Unlimited -> stringResource(R.string.account_credits_unlimited)
        AccountCreditsUi.Unavailable -> stringResource(R.string.account_credits_unavailable)
    }

private fun formatCreditBalance(amount: Double, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }.format(amount)

@Composable
@ReadOnlyComposable
private fun AccountStatusTone.color(): Color =
    when (this) {
        AccountStatusTone.Neutral -> QuotaRabbitTheme.colors.secondary
        AccountStatusTone.Success -> QuotaRabbitTheme.colors.success
        AccountStatusTone.Warning -> QuotaRabbitTheme.colors.warning
        AccountStatusTone.Danger -> QuotaRabbitTheme.colors.danger
    }

@Composable
@ReadOnlyComposable
private fun AccountStatusTone.softColor(): Color =
    when (this) {
        AccountStatusTone.Neutral -> QuotaRabbitTheme.colors.neutralAlt
        AccountStatusTone.Success -> QuotaRabbitTheme.colors.successSoft
        AccountStatusTone.Warning -> QuotaRabbitTheme.colors.warningSoft
        AccountStatusTone.Danger -> QuotaRabbitTheme.colors.dangerSoft
    }
