package com.alice.quotarabbit.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.settings.NotificationPreferences
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import com.alice.quotarabbit.ui.home.formatDisplayDateOnly
import com.alice.quotarabbit.ui.home.formatManualRenewalDate
import java.time.ZoneId

/** Hero follows Codex Quota's account detail hierarchy without inventing API dimensions. */
@Composable
internal fun CodexAccountDetailHero(
    account: AccountItemUi,
    quotaPreferences: NotificationPreferences = NotificationPreferences(),
) {
    val weekly = account.quotaSummaries.firstOrNull { it.windowId == "weekly" }
    val fiveHour = account.quotaSummaries.firstOrNull { it.windowId == "five_hour" }
    AccountSurfaceCard(modifier = Modifier.testTag("account-detail-hero:${account.id.value}")) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(account.badgeLabelResId),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = QuotaRabbitTheme.colors.accent)
            CodexQuotaRing(weekly?.percent, account.id.value, size = 156.dp,
                strokeWidth = 13.dp, compact = false, quotaPreferences = quotaPreferences)
            val leftLabel = stringResource(R.string.account_detail_weekly_left)
            val usedLabel = stringResource(R.string.account_detail_weekly_used)
            val resetLabel = stringResource(R.string.account_detail_weekly_reset)
            val left = weekly?.percent?.let { "$it%" } ?: "--%"
            val used = weekly?.percent?.let { "${100 - it}%" } ?: "--%"
            // Exact reset time remains displayed under the full-width quota bar.
            val reset = weekly?.resetAt?.atZone(ZoneId.systemDefault())
                ?.toLocalDate()?.let(::formatDisplayDateOnly) ?: "--"
            if (LocalDensity.current.fontScale >= 1.5f) {
                Column(Modifier.fillMaxWidth()
                    .background(QuotaRabbitTheme.colors.surfaceSoft, RoundedCornerShape(16.dp))
                    .padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailHeroStatRow(leftLabel, left, emphasize = true)
                    DetailHeroStatRow(usedLabel, used)
                    DetailHeroStatRow(resetLabel, reset)
                }
            } else {
                Row(Modifier.fillMaxWidth()
                    .background(QuotaRabbitTheme.colors.surfaceSoft, RoundedCornerShape(16.dp))
                    .padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    DetailHeroStat(leftLabel, left, Modifier.weight(1f), emphasize = true)
                    DetailHeroStat(usedLabel, used, Modifier.weight(1f))
                    DetailHeroStat(resetLabel, reset, Modifier.weight(1f))
                }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DetailQuotaLine(R.string.account_quota_weekly_label, weekly, account.id.value, "weekly", quotaPreferences)
                DetailQuotaLine(R.string.account_quota_five_hour_label, fiveHour, account.id.value, "five_hour", quotaPreferences)
                Text(account.bankedResetCount?.let {
                    stringResource(R.string.dashboard_banked_resets_count, it)
                } ?: stringResource(R.string.dashboard_banked_resets_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            }
        }
    }
}

/** Compact glance shown above account-scoped analytics; full quota detail stays below. */
@Composable
internal fun CodexAccountDetailSummary(
    account: AccountItemUi,
    quotaPreferences: NotificationPreferences = NotificationPreferences(),
) {
    val weekly = account.quotaSummaries.firstOrNull { it.windowId == "weekly" }
    val fiveHour = account.quotaSummaries.firstOrNull { it.windowId == "five_hour" }
    AccountSurfaceCard(modifier = Modifier.testTag("account-detail-summary:${account.id.value}")) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(account.badgeLabelResId),
                style = MaterialTheme.typography.labelMedium,
                color = QuotaRabbitTheme.colors.accent,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(R.string.account_quota_weekly_label),
                        style = MaterialTheme.typography.labelSmall)
                    Text(weekly?.percent?.let { "$it%" } ?: "--%",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    CodexSolidQuotaBar(weekly?.percent, quotaPreferences = quotaPreferences)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(R.string.account_quota_five_hour_label),
                        style = MaterialTheme.typography.labelSmall)
                    Text(fiveHour?.percent?.let { "$it%" } ?: "--%",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    CodexSolidQuotaBar(fiveHour?.percent, quotaPreferences = quotaPreferences)
                }
            }
            weekly?.resetAt?.let {
                Text(stringResource(R.string.home_reset_at_format, formattedInstant(it)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DetailHeroStatRow(label: String, value: String, emphasize: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,
            style = if (emphasize)
                MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            else MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1)
    }
}

@Composable
private fun DetailHeroStat(label: String, value: String, modifier: Modifier = Modifier, emphasize: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Text(value, style = if (emphasize)
            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            else MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center, maxLines = 2,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun DetailQuotaLine(
    labelRes: Int,
    quota: AccountQuotaSummaryUi?,
    id: String,
    windowId: String,
    quotaPreferences: NotificationPreferences,
) {
    val value = quota?.percent
    Column(verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.fillMaxWidth().testTag("account-detail-window:$windowId:$id")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(labelRes),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(value?.let { "$it%" } ?: "--%",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
        }
        CodexSolidQuotaBar(value, quotaPreferences = quotaPreferences)
        quota?.resetAt?.let {
            Text(stringResource(R.string.home_reset_at_format, formattedInstant(it)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
