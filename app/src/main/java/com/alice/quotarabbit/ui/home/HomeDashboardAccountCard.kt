package com.alice.quotarabbit.ui.home

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alice.quotarabbit.R
import com.alice.quotarabbit.ui.compactHeaderPlanName
import com.alice.quotarabbit.ui.components.AccountProfileAvatar
import com.alice.quotarabbit.ui.motion.QuotaRabbitMotion
import com.alice.quotarabbit.ui.motion.rememberQuotaRabbitAnimatorsEnabled
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import java.time.Instant
import kotlinx.coroutines.delay

@Composable
internal fun HomeDashboardAccountCard(
    card: HomeDashboardAccountUi,
    onManageAccountsClick: () -> Unit,
) {
    val account = card.state.account ?: return
    var expanded by rememberSaveable(card.providerId.value, card.localAccountId.value) { mutableStateOf(false) }
    val animatorsEnabled = rememberQuotaRabbitAnimatorsEnabled()
    val activityStateHolder = rememberSaveableStateHolder()
    // A local display clock updates countdowns; it never schedules provider polling.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = Instant.now()
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth()
            .testTag("dashboard-account:${card.providerId.value}:${card.localAccountId.value}"),
        shape = RoundedCornerShape(22.dp),
        color = QuotaRabbitTheme.colors.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, QuotaRabbitTheme.colors.border),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DashboardAccountHeader(card, account, expanded) { expanded = !expanded }
            DashboardQuotaMetrics(card.state.quotaCards, now, muted = card.isDisconnected || card.state.contentStatus != HomeContentStatus.Fresh)
            if (card.state.quotaCards.isEmpty()) {
                Text(
                    text = stringResource(card.state.statusDescriptionResId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (card.providerId.value == "codex") {
                Text(
                    text = account.bankedResetCount?.let {
                        stringResource(R.string.dashboard_banked_resets_count, it)
                    } ?: stringResource(R.string.dashboard_banked_resets_unavailable),
                    modifier = Modifier.fillMaxWidth()
                        .testTag("dashboard-banked:${card.providerId.value}:${card.localAccountId.value}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                modifier = Modifier.clipToBounds(),
                enter = if (animatorsEnabled) expandVertically(animationSpec = tween(
                    QuotaRabbitMotion.AccountDrawerDurationMillis, easing = FastOutSlowInEasing,
                )) + fadeIn(animationSpec = tween(QuotaRabbitMotion.AccountDrawerDurationMillis))
                else EnterTransition.None,
                exit = if (animatorsEnabled) shrinkVertically(animationSpec = tween(
                    QuotaRabbitMotion.AccountDrawerDurationMillis, easing = FastOutSlowInEasing,
                )) + fadeOut(animationSpec = tween(QuotaRabbitMotion.AccountDrawerDurationMillis))
                else ExitTransition.None,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    activityStateHolder.SaveableStateProvider("heatmap:${card.providerId.value}:${card.localAccountId.value}") {
                        AccountActivityHeatmap(
                            providerId = card.providerId.value,
                            accountId = card.localAccountId.value,
                            accentColorId = account.accentColorId,
                            points = card.activityPoints,
                            now = now,
                        )
                    }
                    DashboardAccountExpandedMetadata(card)
                }
            }
            DashboardAccountStatus(card)
            if (card.isDisconnected || card.state.contentStatus == HomeContentStatus.AuthRequired) {
                TextButton(onClick = onManageAccountsClick) {
                    Text(stringResource(R.string.dashboard_manage_accounts))
                }
            }
        }
    }
}

@Composable
private fun DashboardAccountHeader(
    card: HomeDashboardAccountUi,
    account: HomeAccountUi,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val label = stringResource(if (expanded) R.string.account_collapse else R.string.account_expand)
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (rememberQuotaRabbitAnimatorsEnabled())
            tween(QuotaRabbitMotion.AccountDrawerDurationMillis) else snap(),
        label = "dashboard_accordion_chevron",
    )
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onToggle)
            .semantics { stateDescription = label }
            .testTag("dashboard-accordion-toggle:${card.providerId.value}:${card.localAccountId.value}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AccountProfileAvatar(
            initial = account.avatarInitial,
            avatarColorKey = account.avatarColorKey,
            accentColorId = account.accentColorId,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = account.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                account.providerIconResId?.let {
                    Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(14.dp))
                }
                Text(
                    text = listOfNotNull(
                        card.providerDisplayName,
                        compactHeaderPlanName(card.providerDisplayName, account.planType),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            if (card.isCurrent) {
                Text(
                    text = stringResource(R.string.home_current_account),
                    style = MaterialTheme.typography.labelSmall,
                    color = QuotaRabbitTheme.colors.accent,
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_down),
            contentDescription = null,
            tint = QuotaRabbitTheme.colors.accent,
            modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = rotation },
        )
    }
}

@Composable
private fun DashboardAccountStatus(card: HomeDashboardAccountUi) {
    val state = card.state
    val status = when {
        card.isDisconnected -> R.string.dashboard_disconnected
        state.isRefreshing -> R.string.home_state_loading_title
        else -> state.statusTitleResId
    }
    val statusColor = when {
        state.isRefreshing -> QuotaRabbitTheme.colors.accent
        card.isDisconnected -> MaterialTheme.colorScheme.onSurfaceVariant
        state.contentStatus == HomeContentStatus.AuthRequired -> QuotaRabbitTheme.colors.danger
        state.contentStatus == HomeContentStatus.ErrorWithLastKnownGood -> QuotaRabbitTheme.colors.warning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    if (!card.isDisconnected && !state.isRefreshing && state.contentStatus == HomeContentStatus.Fresh &&
        state.errorMessageResId == null && state.account?.credits !is HomeCreditsUi.Balance &&
        state.account?.credits != HomeCreditsUi.Unlimited
    ) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Freshness is still visible in Account; Home omits its redundant "Data fresh" row.
        if (card.isDisconnected || state.isRefreshing || state.contentStatus != HomeContentStatus.Fresh) {
            Text(
                text = stringResource(status),
                color = statusColor,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        state.errorMessageResId?.let {
            Text(stringResource(it), style = MaterialTheme.typography.labelSmall, color = statusColor)
        }
        when (val credits = state.account?.credits) {
            is HomeCreditsUi.Balance -> Text(
                text = stringResource(R.string.home_account_credits_balance_format, credits.amount.toString()),
                style = MaterialTheme.typography.labelSmall,
            )
            HomeCreditsUi.Unlimited -> Text(
                text = stringResource(R.string.home_account_credits_unlimited),
                style = MaterialTheme.typography.labelSmall,
            )
            else -> Unit
        }
    }
}

@Composable
private fun DashboardAccountExpandedMetadata(card: HomeDashboardAccountUi) {
    val account = card.state.account
    val subscription = account?.manualRenewalDate?.let {
        stringResource(R.string.account_renewal_manual, formatManualRenewalDate(it))
    } ?: account?.providerRenewalAt?.let {
        stringResource(R.string.account_renewal_provider, formattedInstant(it))
    } ?: account?.subscriptionActiveUntil?.let {
        stringResource(
            if (account.activeUntilFromToken) R.string.account_subscription_coverage_token
            else R.string.account_subscription_coverage_provider,
            formattedInstant(it),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        subscription?.let {
            Text(
                text = it,
                modifier = Modifier.fillMaxWidth()
                    .testTag("dashboard-renewal:${card.providerId.value}:${card.localAccountId.value}"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = card.state.refresh.lastSuccessfulRefreshAt?.let {
                stringResource(R.string.home_last_refresh_format, formattedInstant(it))
            } ?: stringResource(R.string.account_last_refresh_unavailable),
            modifier = Modifier.fillMaxWidth()
                .testTag("dashboard-last-success:${card.providerId.value}:${card.localAccountId.value}"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
