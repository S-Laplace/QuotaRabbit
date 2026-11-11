package com.alice.quotarabbit.ui.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.QuotaWindowId
import com.alice.quotarabbit.ui.theme.QuotaRabbitShapes
import com.alice.quotarabbit.ui.theme.QuotaRabbitSpacing
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import java.util.Locale

@Composable
internal fun AccountDetailsPanel(
    account: AccountItemUi,
    onSetCurrentClick: (LocalAccountId) -> Unit,
    onRenameClick: (LocalAccountId) -> Unit,
    onReloginClick: (LocalAccountId) -> Unit,
    onQuotaAlertToggle: (LocalAccountId, QuotaWindowId, Boolean) -> Unit,
    onDeleteClick: (LocalAccountId) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md)) {
        // Separate semantic sections mirror the Codex Quota detail navigation without
        // duplicating the large hero's weekly and five-hour percentages.
        AccountSurfaceCard {
            Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md)) {
                SectionLabel(R.string.account_detail_section_info)
                StatusBadge(account)
                AccountMetadataTile(label = stringResource(R.string.account_detail_nickname),
                    value = account.displayName)
                PlanCreditsRow(account)
                Text(account.lastSuccessfulRefreshAt?.let {
                    stringResource(R.string.account_last_refresh_format, formattedInstant(it))
                } ?: stringResource(R.string.account_last_refresh_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AccountSurfaceCard {
            Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md)) {
                SectionLabel(R.string.account_detail_section_limits)
                if (account.providerId.value != "codex") QuotaSummaryRow(account.quotaSummaries)
                if (account.modelFamilies.isNotEmpty()) ModelDetailsSection(account.modelFamilies)
                QuotaAlertSwitches(account, onQuotaAlertToggle)
            }
        }
        AccountSurfaceCard {
            Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md)) {
                SectionLabel(R.string.account_detail_section_actions)
                AccountActions(account, onSetCurrentClick, onRenameClick, onReloginClick, onDeleteClick)
            }
        }
    }
}

@Composable
internal fun SectionLabel(resId: Int) {
    Text(
        text = stringResource(resId),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun AccountSurfaceCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        shape = QuotaRabbitShapes.xl,
        colors = CardDefaults.cardColors(containerColor = QuotaRabbitTheme.colors.surface),
        border = BorderStroke(1.dp, QuotaRabbitTheme.colors.border),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(QuotaRabbitSpacing.lg),
        ) {
            content()
        }
    }
}

@Composable
internal fun PlanCreditsRow(account: AccountItemUi) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    // Providers expose different fields: show only the tiles that actually have data so that, e.g.,
    // Kimi (no plan/credits) or Antigravity (plan only) don't render "Unavailable" placeholders.
    val planType = account.planType
    val hasCredits = account.credits !is AccountCreditsUi.Unavailable
    if (planType == null && !hasCredits) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 300.dp && LocalDensity.current.fontScale <= 1.2f) 2 else 1
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md),
            verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md),
            maxItemsInEachRow = columns,
        ) {
            if (planType != null) {
                AccountMetadataTile(
                    label = stringResource(R.string.account_plan_label),
                    value = planType,
                    modifier = Modifier.weight(1f),
                )
            }
            if (hasCredits) {
                AccountMetadataTile(
                    label = stringResource(R.string.account_credits_label),
                    value = account.credits.displayText(locale),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AccountMetadataTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(QuotaRabbitShapes.md)
            .background(QuotaRabbitTheme.colors.surfaceSoft)
            .padding(QuotaRabbitSpacing.md),
        verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun QuotaSummaryRow(summaries: List<AccountQuotaSummaryUi>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 300.dp && LocalDensity.current.fontScale <= 1.2f) 2 else 1
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md),
            verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md),
            maxItemsInEachRow = columns,
        ) {
            summaries.forEach { summary ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(QuotaRabbitShapes.md)
                        .background(QuotaRabbitTheme.colors.surfaceSoft)
                        .padding(QuotaRabbitSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.xs),
                ) {
                    Text(
                        text = stringResource(summary.labelResId),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = summary.valueText
                            ?: summary.percent?.let {
                                stringResource(R.string.account_quota_percent_format, it)
                            }
                            ?: stringResource(R.string.account_quota_placeholder_no_data),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    // Original native balance (shown when conversion occurred)
                    summary.originalValueText?.let { original ->
                        Text(
                            text = original,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // DeepSeek-style granted/topped-up breakdown (native currency)
                    if (summary.grantedText != null || summary.toppedUpText != null) {
                        Text(
                            text = stringResource(
                                R.string.quota_balance_breakdown_format,
                                summary.grantedText.orEmpty(),
                                summary.toppedUpText.orEmpty(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ModelDetailsSection(families: List<AccountModelFamilyUi>) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(QuotaRabbitShapes.md)
            .background(QuotaRabbitTheme.colors.surfaceSoft)
            .padding(QuotaRabbitSpacing.md),
        verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.account_model_details_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (expanded) "▾" else "▸",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            families.forEach { family ->
                Text(
                    text = family.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                family.models.forEach { model ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = QuotaRabbitSpacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = model.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f).padding(end = QuotaRabbitSpacing.sm),
                        )
                        Text(
                            text = stringResource(R.string.account_quota_percent_format, model.remainingPercent),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun QuotaAlertSwitches(
    account: AccountItemUi,
    onQuotaAlertToggle: (LocalAccountId, QuotaWindowId, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(QuotaRabbitShapes.md)
            .background(QuotaRabbitTheme.colors.surfaceSoft)
            .padding(QuotaRabbitSpacing.md),
        verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm),
    ) {
        Text(
            text = stringResource(R.string.account_quota_alerts_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        account.alertToggles.forEach { toggle ->
            QuotaAlertSwitchLine(
                label = stringResource(toggle.labelResId),
                checked = toggle.enabled,
                testTag = "account-alert:${account.id.value}:${toggle.windowId}",
                onCheckedChange = { enabled ->
                    onQuotaAlertToggle(account.id, QuotaWindowId(toggle.windowId), enabled)
                },
            )
        }
    }
}

@Composable
private fun QuotaAlertSwitchLine(
    label: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
        )
    }
}

@Composable
internal fun AccountActions(
    account: AccountItemUi,
    onSetCurrentClick: (LocalAccountId) -> Unit,
    onRenameClick: (LocalAccountId) -> Unit,
    onReloginClick: (LocalAccountId) -> Unit,
    onDeleteClick: (LocalAccountId) -> Unit,
) {
    val actionButtonBorder = BorderStroke(1.dp, QuotaRabbitTheme.colors.border)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 300.dp && LocalDensity.current.fontScale <= 1.2f) 2 else 1
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm),
            maxItemsInEachRow = columns,
        ) {
            OutlinedButton(
                onClick = { onSetCurrentClick(account.id) },
                modifier = Modifier.weight(1f).testTag("account-action:current:${account.id.value}"),
                // Re-selecting an already pinned provider account moves it to the top of All.
                enabled = true,
                shape = QuotaRabbitShapes.md,
                border = actionButtonBorder,
            ) {
                Text(text = stringResource(R.string.account_set_current), textAlign = TextAlign.Center)
            }
            OutlinedButton(
                onClick = { onRenameClick(account.id) },
                modifier = Modifier.weight(1f).testTag("account-action:rename:${account.id.value}"),
                shape = QuotaRabbitShapes.md,
            ) {
                Text(text = stringResource(R.string.account_edit_title), textAlign = TextAlign.Center)
            }
            OutlinedButton(
                onClick = { onReloginClick(account.id) },
                modifier = Modifier.weight(1f).testTag("account-action:relogin:${account.id.value}"),
                shape = QuotaRabbitShapes.md,
            ) {
                Text(text = stringResource(R.string.account_relogin), textAlign = TextAlign.Center)
            }
            OutlinedButton(
                onClick = { onDeleteClick(account.id) },
                modifier = Modifier.weight(1f).testTag("account-action:delete:${account.id.value}"),
                shape = QuotaRabbitShapes.md,
                border = actionButtonBorder,
            ) {
                Text(
                    text = stringResource(R.string.account_delete),
                    color = QuotaRabbitTheme.colors.danger,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
