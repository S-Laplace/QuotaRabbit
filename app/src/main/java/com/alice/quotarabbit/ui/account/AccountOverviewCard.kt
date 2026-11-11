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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import com.alice.quotarabbit.ui.home.formatManualRenewalDate
import com.alice.quotarabbit.ui.home.DashboardResetTime
import com.alice.quotarabbit.ui.components.AccountProfileAvatar
import com.alice.quotarabbit.ui.theme.accountAccentGradient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alice.quotarabbit.R
import com.alice.quotarabbit.ui.compactHeaderPlanName
import com.alice.quotarabbit.domain.settings.NotificationPreferences
import com.alice.quotarabbit.ui.theme.QuotaRabbitSpacing
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import com.alice.quotarabbit.ui.motion.QuotaRabbitMotion
import com.alice.quotarabbit.ui.motion.rememberQuotaRabbitAnimatorsEnabled
import java.time.Instant
import kotlinx.coroutines.delay

/** Display-only overview. Percentages originate in the snapshot's remainingPercent. */
@Composable
internal fun AccountOverviewCard(
    account: AccountItemUi,
    quotaPreferences: NotificationPreferences = NotificationPreferences(),
    onOpenDetails: (() -> Unit)? = null,
    onToggleExpanded: (() -> Unit)? = null,
) {
    val codex = account.providerId.value == "codex"
    val animatorsEnabled = rememberQuotaRabbitAnimatorsEnabled()
    val toggleLabel = stringResource(if (account.isExpanded) R.string.account_collapse else R.string.account_expand)
    val rotation by animateFloatAsState(
        targetValue = if (account.isExpanded) 180f else 0f,
        animationSpec = if (animatorsEnabled)
            tween(QuotaRabbitMotion.AccountDrawerDurationMillis) else snap(),
        label = "account_overview_chevron",
    )
    AccountSurfaceCard(modifier = Modifier.testTag("account-overview:${account.id.value}")) {
        Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).then(
                    if (onToggleExpanded != null) Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClickLabel = toggleLabel, onClick = onToggleExpanded)
                        .semantics { stateDescription = toggleLabel }
                        .testTag("account-accordion-toggle:${account.id.value}")
                    else Modifier
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm),
            ) {
                AccountProfileAvatar(
                    initial = account.avatarInitial,
                    avatarColorKey = account.avatarColorKey,
                    accentColorId = account.accentColorId,
                    modifier = Modifier.testTag("account-profile-avatar:${account.id.value}"),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(account.displayName, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        account.providerIconResId?.let { iconResId ->
                            Icon(
                                painter = painterResource(iconResId),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                                    .testTag("account-provider-logo:${account.id.value}"),
                            )
                        }
                        val providerName = account.providerDisplayName.ifBlank { account.providerId.value }
                        Text(
                            text = listOfNotNull(
                                providerName,
                                compactHeaderPlanName(providerName, account.planType),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
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
            AnimatedVisibility(
                visible = account.isExpanded,
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
              Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md)) {
            if (account.isCurrent) {
                Text(stringResource(R.string.account_badge_current),
                    style = MaterialTheme.typography.labelSmall,
                    color = QuotaRabbitTheme.colors.accent)
            }
            account.accentColorId?.let { accent ->
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(4.dp))
                    .background(accountAccentGradient(accent, QuotaRabbitTheme.colors.accent)))
            }
            if (codex) {
                val weekly = account.quotaSummaries.firstOrNull { it.windowId == "weekly" }
                val fiveHour = account.quotaSummaries.firstOrNull { it.windowId == "five_hour" }
                CodexQuotaOverview(weekly, fiveHour, account.id.value, quotaPreferences)
            } else {
                OtherProviderOverview(account)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (codex) {
                Text(account.bankedResetCount?.let {
                    stringResource(R.string.dashboard_banked_resets_count, it)
                } ?: stringResource(R.string.dashboard_banked_resets_unavailable),
                    modifier = Modifier.testTag("account-banked:${account.id.value}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val subscription = account.manualRenewalDate?.let {
                stringResource(R.string.account_renewal_manual, formatManualRenewalDate(it))
            } ?: account.providerRenewalAt?.let {
                stringResource(R.string.account_renewal_provider, formattedInstant(it))
            } ?: account.subscriptionActiveUntil?.let {
                stringResource(
                    if (account.activeUntilFromToken) R.string.account_subscription_coverage_token
                    else R.string.account_subscription_coverage_provider,
                    formattedInstant(it),
                )
            }
            subscription?.let {
                Text(it,
                    modifier = Modifier.testTag("account-renewal:${account.id.value}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                account.lastSuccessfulRefreshAt?.let {
                    stringResource(R.string.account_last_refresh_format, formattedInstant(it))
                } ?: stringResource(R.string.account_last_refresh_unavailable),
                modifier = Modifier.testTag("account-success:${account.id.value}"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(account.statusLabelResId),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onOpenDetails != null) {
                OutlinedButton(
                    onClick = onOpenDetails,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("account-open-details:${account.id.value}"),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (codex) {
                        Icon(
                            painter = painterResource(R.drawable.ic_usage_analytics),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        text = stringResource(
                            if (codex) R.string.account_open_analytics else R.string.account_open_details_short
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_down),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = 270f },
                    )
                }
            }
              }
            }
        }
    }
}

@Composable
private fun CodexQuotaOverview(
    weekly: AccountQuotaSummaryUi?,
    fiveHour: AccountQuotaSummaryUi?,
    accountId: String,
    quotaPreferences: NotificationPreferences,
) {
    // A display-only timer keeps relative reset text current without polling the provider.
    val now by produceState(initialValue = Instant.now()) {
        while (true) {
            delay(60_000)
            value = Instant.now()
        }
    }
    // Codex Quota dashboard at normal fonts. Large accessibility text needs a
    // stacked variant so both tracks stay full-width instead of wrapping each digit.
    if (LocalDensity.current.fontScale >= 1.5f) {
        Column(Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CodexQuotaRing(weekly?.percent, accountId, size = 94.dp, strokeWidth = 9.dp,
                quotaPreferences = quotaPreferences)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QuotaLine(R.string.account_quota_weekly_label, weekly, accountId, "weekly", quotaPreferences, now)
                QuotaLine(R.string.account_quota_five_hour_label, fiveHour, accountId, "five_hour", quotaPreferences, now)
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CodexQuotaRing(weekly?.percent, accountId, quotaPreferences = quotaPreferences)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                QuotaLine(R.string.account_quota_weekly_label, weekly, accountId, "weekly", quotaPreferences, now)
                QuotaLine(R.string.account_quota_five_hour_label, fiveHour, accountId, "five_hour", quotaPreferences, now)
            }
        }
    }
}

@Composable
private fun QuotaLine(
    labelRes: Int, summary: AccountQuotaSummaryUi?, accountId: String, key: String,
    quotaPreferences: NotificationPreferences,
    now: Instant,
) {
    val value = summary?.percent?.coerceIn(0, 100)
    val description = value?.let { stringResource(R.string.dashboard_remaining_format, it) }
        ?: stringResource(R.string.account_plan_unavailable)
    Column(Modifier.fillMaxWidth().testTag("account-window:$key:$accountId"),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(2.dp), maxItemsInEachRow = 2) {
            Text(stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface)
            val metric = value?.let { "$it%" }
            val index = metric?.let(description::indexOf) ?: -1
            val metricSize = MaterialTheme.typography.bodySmall.fontSize
            Text(
                text = buildAnnotatedString {
                    if (metric != null && index >= 0) {
                        append(description.substring(0, index))
                        withStyle(SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = metricSize,
                        )) { append(metric) }
                        append(description.substring(index + metric.length))
                    } else append(description)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CodexSolidQuotaBar(value, modifier = Modifier.testTag("account-progress:$key:$accountId"),
            quotaPreferences = quotaPreferences)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            DashboardResetTime(summary?.resetAt, now)
            summary?.resetAt?.let { ResetLabel(it) }
        }
    }
}

@Composable
private fun ResetLabel(resetAt: Instant?) {
    Text(
        resetAt?.let { stringResource(R.string.home_reset_at_format, formattedInstant(it)) }
            ?: stringResource(R.string.home_reset_unavailable),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun OtherProviderOverview(account: AccountItemUi) {
    if (account.quotaSummaries.isEmpty() && account.modelFamilies.isEmpty()) {
        Text(stringResource(R.string.account_quota_placeholder_no_data),
            style = MaterialTheme.typography.bodyMedium)
    }
    account.quotaSummaries.forEach { summary ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.testTag("account-window:${summary.windowId}:${account.id.value}")) {
            Text(stringResource(summary.labelResId), style = MaterialTheme.typography.labelMedium)
            Text(
                summary.valueText ?: summary.percent?.let {
                    stringResource(R.string.dashboard_remaining_format, it)
                } ?: stringResource(R.string.account_plan_unavailable),
                style = MaterialTheme.typography.titleMedium,
            )
            summary.originalValueText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (summary.grantedText != null || summary.toppedUpText != null) {
                Text(stringResource(R.string.quota_balance_breakdown_format,
                    summary.grantedText.orEmpty(), summary.toppedUpText.orEmpty()),
                    style = MaterialTheme.typography.bodySmall)
            }
            if (summary.resetAt != null) ResetLabel(summary.resetAt)
        }
    }
    if (account.modelFamilies.isNotEmpty()) {
        Text(stringResource(R.string.account_model_details_title),
            style = MaterialTheme.typography.labelMedium)
        account.modelFamilies.forEach { family ->
            Text(family.name, style = MaterialTheme.typography.labelSmall)
            family.models.forEach { model ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(model.displayName, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.dashboard_remaining_format, model.remainingPercent),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
