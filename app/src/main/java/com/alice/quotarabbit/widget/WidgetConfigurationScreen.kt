package com.alice.quotarabbit.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.ui.theme.QuotaRabbitShapes
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import com.alice.quotarabbit.ui.theme.QuotaRabbitSpacing

/** 多选切换：按 available（天然顺序）重排，已满 WIDGET_MAX_FIELDS 时忽略新增。 */
internal fun toggleWindowSelection(
    current: List<String>,
    windowId: String,
    available: List<String>,
): List<String> {
    val next = if (current.contains(windowId)) {
        current - windowId
    } else {
        if (current.size >= WIDGET_MAX_FIELDS) return current
        current + windowId
    }
    return available.filter { next.contains(it) }
}

/** 切换账号后，仅保留新账号可用的窗口，按 available 顺序。 */
internal fun retainAvailableWindowIds(current: List<String>, available: List<String>): List<String> =
    available.filter { current.contains(it) }

internal data class WidgetWindowOption(
    val windowId: String,
    @get:androidx.annotation.StringRes val labelResId: Int,
)

internal data class WidgetConfigurationScreenState(
    val accounts: List<ProviderAccount> = emptyList(),
    val selectedProviderId: String? = null,
    val selectedLocalAccountId: String? = null,
    val selectedWindowIds: List<String> = emptyList(),
    val defaultWindowId: String = FIVE_HOUR_WINDOW_ID,
    val currentAccountId: String? = null,
    val windowOptionsByAccount: Map<String, List<WidgetWindowOption>> = emptyMap(),
    val selectedAccounts: List<WidgetAccountSelection> = emptyList(),
) {
    val effectiveSelectedAccounts: List<WidgetAccountSelection>
        get() = selectedAccounts.ifEmpty {
            selectedAccount?.let { listOf(WidgetAccountSelection(it.providerId, it.localAccountId)) }.orEmpty()
        }
    val selectedAccount: WidgetConfigurationAccountSelection?
        get() = if (selectedProviderId.isNullOrBlank() || selectedLocalAccountId.isNullOrBlank()) {
            null
        } else {
            WidgetConfigurationAccountSelection(selectedProviderId, selectedLocalAccountId)
        }

    /** Only offer windows present in the selected account's saved provider snapshot. */
    val windowOptions: List<WidgetWindowOption>
        get() = selectedLocalAccountId
            ?.let { windowOptionsByAccount[it] }
            .orEmpty()
}

internal data class WidgetConfigurationAccountSelection(
    val providerId: String,
    val localAccountId: String,
)

@Composable
internal fun WidgetConfigurationRoute(
    appWidgetId: Int,
    loadState: suspend () -> WidgetConfigurationScreenState,
    onCancel: () -> Unit,
    onSave: (List<WidgetAccountSelection>, List<String>) -> Unit,
) {
    var state by remember(appWidgetId) { mutableStateOf<WidgetConfigurationScreenState?>(null) }
    var isSaving by remember(appWidgetId) { mutableStateOf(false) }
    var expandedSection by remember(appWidgetId) {
        mutableStateOf<WidgetConfigurationSection?>(WidgetConfigurationSection.Account)
    }

    LaunchedEffect(appWidgetId) {
        state = loadState()
    }

    val currentState = state
    if (currentState == null) {
        LoadingConfigurationScreen()
    } else {
        WidgetConfigurationScreen(
            state = currentState,
            expandedSection = expandedSection,
            isSaving = isSaving,
            onSectionToggle = { section ->
                expandedSection = if (expandedSection == section) null else section
            },
            onAccountSelected = { account ->
                val selected = toggleWidgetAccountSelection(
                    currentState.effectiveSelectedAccounts,
                    WidgetAccountSelection(account.providerId.value, account.localAccountId.value),
                )
                val first = selected.firstOrNull()
                val options = currentState.windowOptionsByAccount[first?.localAccountId]
                    .orEmpty()
                val available = options.map { it.windowId }
                val retained = retainAvailableWindowIds(currentState.selectedWindowIds, available)
                val nextSelection = retained.ifEmpty {
                    listOfNotNull(available.firstOrNull { it == currentState.defaultWindowId } ?: available.firstOrNull())
                }
                state = currentState.copy(
                    selectedProviderId = first?.providerId,
                    selectedLocalAccountId = first?.localAccountId,
                    selectedAccounts = selected,
                    selectedWindowIds = nextSelection,
                )
            },
            onWindowToggled = { windowId ->
                val available = currentState.windowOptions.map { it.windowId }
                state = currentState.copy(
                    selectedWindowIds = toggleWindowSelection(currentState.selectedWindowIds, windowId, available),
                )
            },
            onCancel = onCancel,
            onSave = {
                isSaving = true
                onSave(currentState.effectiveSelectedAccounts, currentState.selectedWindowIds)
            },
        )
    }
}

@Composable
private fun LoadingConfigurationScreen() {
    WidgetConfigurationDialogContainer {
        Column(
            verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(text = stringResource(R.string.widget_config_loading))
        }
    }
}

@Composable
internal fun WidgetConfigurationScreen(
    state: WidgetConfigurationScreenState,
    expandedSection: WidgetConfigurationSection?,
    isSaving: Boolean,
    onSectionToggle: (WidgetConfigurationSection) -> Unit,
    onAccountSelected: (ProviderAccount) -> Unit,
    onWindowToggled: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    WidgetConfigurationDialogContainer {
        Column(
            modifier = Modifier
                .heightIn(max = WIDGET_CONFIG_DIALOG_MAX_HEIGHT)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.xs)) {
                Text(
                    text = stringResource(R.string.widget_config_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.widget_multi_config_description, WIDGET_MAX_ACCOUNTS),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            WidgetConfigurationExpandableOption(
                title = stringResource(R.string.widget_config_account_section),
                summary = state.selectedAccountLabel(),
                expanded = expandedSection == WidgetConfigurationSection.Account,
                onToggle = { onSectionToggle(WidgetConfigurationSection.Account) },
            ) {
                if (state.accounts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.widget_config_no_accounts),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.xs)) {
                        state.accounts.forEach { account ->
                            AccountOptionRow(
                                account = account,
                                selected = WidgetAccountSelection(account.providerId.value, account.localAccountId.value)
                                    in state.effectiveSelectedAccounts,
                                enabled = WidgetAccountSelection(account.providerId.value, account.localAccountId.value)
                                    in state.effectiveSelectedAccounts || state.effectiveSelectedAccounts.size < WIDGET_MAX_ACCOUNTS,
                                current = account.localAccountId.value == state.currentAccountId,
                                onClick = { onAccountSelected(account) },
                            )
                        }
                    }
                }
            }
            WidgetConfigurationExpandableOption(
                title = stringResource(R.string.widget_config_fields_section),
                summary = state.fieldsSummary(),
                expanded = expandedSection == WidgetConfigurationSection.DisplayFields,
                onToggle = { onSectionToggle(WidgetConfigurationSection.DisplayFields) },
            ) {
                if (state.effectiveSelectedAccounts.size > 1) {
                    Text(
                        text = stringResource(R.string.widget_multi_config_default_fields),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.windowOptions.forEach { option ->
                        val checked = state.selectedWindowIds.contains(option.windowId)
                        val enabled = checked || state.selectedWindowIds.size < WIDGET_MAX_FIELDS
                        FieldOptionRow(
                            label = stringResource(option.labelResId),
                            checked = checked,
                            enabled = enabled,
                            onClick = { onWindowToggled(option.windowId) },
                        )
                    }
                    Text(
                        text = stringResource(R.string.widget_config_fields_limit, WIDGET_MAX_FIELDS),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = if (maxWidth >= 300.dp && LocalDensity.current.fontScale <= 1.2f) 2 else 1
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm),
                    maxItemsInEachRow = columns,
                ) {
                    val actionModifier = if (columns == 1) Modifier.fillMaxWidth() else Modifier
                    TextButton(
                        onClick = onCancel,
                        enabled = !isSaving,
                        shape = QuotaRabbitShapes.md,
                        modifier = actionModifier.heightIn(min = 48.dp).testTag("widget-config-cancel"),
                    ) {
                        Text(text = stringResource(R.string.widget_config_cancel))
                    }
                    Button(
                        onClick = onSave,
                        enabled = !isSaving && state.effectiveSelectedAccounts.isNotEmpty(),
                        shape = QuotaRabbitShapes.md,
                        modifier = actionModifier.heightIn(min = 48.dp).testTag("widget-config-save"),
                    ) {
                        Text(
                            text = stringResource(
                                if (isSaving) R.string.widget_config_saving else R.string.widget_config_save,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetConfigurationDialogContainer(content: @Composable () -> Unit) {
    Surface(color = androidx.compose.ui.graphics.Color.Transparent) {
        Box(
            modifier = Modifier
                .widthIn(min = WIDGET_CONFIG_DIALOG_MIN_WIDTH, max = WIDGET_CONFIG_DIALOG_MAX_WIDTH)
                .padding(WIDGET_CONFIG_DIALOG_OUTER_PADDING),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = QuotaRabbitShapes.xl,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 16.dp,
            ) {
                Box(modifier = Modifier.padding(QuotaRabbitSpacing.lg)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun WidgetConfigurationExpandableOption(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = QuotaRabbitShapes.lg,
        color = QuotaRabbitTheme.colors.surfaceSoft,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(QuotaRabbitSpacing.md),
            verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
            .clickable(onClick = onToggle)
            .heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = if (expanded) "⌃" else "⌄",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Column(content = content)
            }
        }
    }
}

@Composable
private fun WidgetConfigurationScreenState.selectedAccountLabel(): String =
    if (effectiveSelectedAccounts.isEmpty()) stringResource(R.string.widget_config_account_none)
    else accounts.filter {
        WidgetAccountSelection(it.providerId.value, it.localAccountId.value) in effectiveSelectedAccounts
    }.joinToString(" · ") { it.displayName }

@Composable
private fun WidgetConfigurationScreenState.fieldsSummary(): String {
    if (effectiveSelectedAccounts.size > 1) return stringResource(R.string.widget_multi_config_provider_fields)
    val labels = selectedWindowIds.mapNotNull { id ->
        windowOptions.firstOrNull { it.windowId == id }?.let { stringResource(it.labelResId) }
    }
    return if (labels.isEmpty()) {
        stringResource(R.string.widget_config_account_none)
    } else {
        labels.joinToString(separator = " · ")
    }
}

@Composable
private fun AccountOptionRow(
    account: ProviderAccount,
    selected: Boolean,
    enabled: Boolean,
    current: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, enabled = enabled, onCheckedChange = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (current) {
                    stringResource(R.string.widget_config_account_current)
                } else {
                    account.providerId.value
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FieldOptionRow(label: String, checked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, enabled = enabled, onCheckedChange = { onClick() })
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val WIDGET_CONFIG_DIALOG_MIN_WIDTH = 300.dp
private val WIDGET_CONFIG_DIALOG_MAX_WIDTH = 420.dp
private val WIDGET_CONFIG_DIALOG_MAX_HEIGHT = 560.dp
private val WIDGET_CONFIG_DIALOG_OUTER_PADDING = 12.dp
private const val FIVE_HOUR_WINDOW_ID = "five_hour"
