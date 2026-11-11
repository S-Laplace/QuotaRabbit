package com.alice.quotarabbit.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.alice.quotarabbit.ui.components.GlassPullToRefreshIndicator
import com.alice.quotarabbit.ui.components.AccountProfileAvatar
import com.alice.quotarabbit.ui.components.ProviderFilterChips
import com.alice.quotarabbit.ui.components.ProviderFilterOption
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.account.AccountDeleteUseCase
import com.alice.quotarabbit.domain.account.AccountListUseCase
import com.alice.quotarabbit.domain.account.AccountRenameUseCase
import com.alice.quotarabbit.domain.account.AccountEditUseCase
import com.alice.quotarabbit.domain.account.AccountEditInput
import com.alice.quotarabbit.domain.account.AccountSwitchUseCase
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.QuotaWindowId
import com.alice.quotarabbit.domain.settings.NotificationPreferenceStore
import com.alice.quotarabbit.data.currency.ExchangeRateReader
import com.alice.quotarabbit.domain.currency.CurrencyPreferenceReader
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import com.alice.quotarabbit.ui.theme.QuotaRabbitDialogSystemBars
import com.alice.quotarabbit.ui.theme.QuotaRabbitShapes
import com.alice.quotarabbit.ui.theme.QuotaRabbitSpacing
import com.alice.quotarabbit.ui.analytics.AccountUsageAnalytics
import kotlinx.coroutines.launch

@Composable
fun AccountRoute(
    accountListUseCase: AccountListUseCase,
    accountDeleteUseCase: AccountDeleteUseCase,
    accountSwitchUseCase: AccountSwitchUseCase,
    accountRenameUseCase: AccountRenameUseCase,
    accountEditUseCase: AccountEditUseCase = AccountEditUseCase { _, _, _ -> null },
    notificationPreferenceStore: NotificationPreferenceStore = AccountInMemoryNotificationPreferenceStore(),
    quotaAlertEvaluationRequester: AccountQuotaAlertEvaluationRequester =
        NoopAccountQuotaAlertEvaluationRequester,
    refreshAllUseCase: AccountRefreshAllUseCase = NoopAccountRefreshAllUseCase,
    currencyPreferenceReader: CurrencyPreferenceReader = NoopAccountCurrencyPreferenceReader,
    exchangeRateReader: ExchangeRateReader = NoopAccountExchangeRateReader,
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = viewModel(
        factory = AccountViewModel.factory(
            deleteUseCase = accountDeleteUseCase,
            accountListUseCase = accountListUseCase,
            switchUseCase = accountSwitchUseCase,
            renameUseCase = accountRenameUseCase,
            editUseCase = accountEditUseCase,
            notificationPreferenceStore = notificationPreferenceStore,
            quotaAlertEvaluationRequester = quotaAlertEvaluationRequester,
            refreshAllUseCase = refreshAllUseCase,
            currencyPreferenceReader = currencyPreferenceReader,
            exchangeRateReader = exchangeRateReader,
        ),
    ),
    onLoginToCodexClick: () -> Unit = {},
    onAddAccountClick: () -> Unit = viewModel::requestAddAccount,
    onReloginAccount: (com.alice.quotarabbit.domain.model.ProviderId, LocalAccountId, String?) -> Unit = { _, _, _ -> },
    detailAccountId: LocalAccountId? = null,
    onBackClick: () -> Unit = {},
    onOpenDetails: (LocalAccountId) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.loadAccounts()
    }
    LaunchedEffect(uiState.pendingReloginRequest) {
        val request = uiState.pendingReloginRequest ?: return@LaunchedEffect
        onReloginAccount(request.providerId, request.localAccountId, request.providerAccountId)
        viewModel.consumeReloginRequest()
    }
    AccountScreen(
        uiState = uiState,
        modifier = modifier,
        detailAccountId = detailAccountId,
        onBackClick = onBackClick,
        onOpenDetails = onOpenDetails,
        analyticsContent = { account ->
            AccountUsageAnalytics(account.id, accountListUseCase, account.displayName)
        },
        onRefresh = viewModel::refreshAllAccounts,
        onAddAccountClick = onAddAccountClick,
        onLoginToCodexClick = {
            viewModel.selectAddAccountLogin()
            onLoginToCodexClick()
        },
        onAddAccountDismiss = viewModel::dismissAddAccount,
        onSetCurrentClick = { id ->
            coroutineScope.launch {
                viewModel.switchCurrentAccount(id)
            }
        },
        onRenameClick = viewModel::requestEditAccount,
        onEditSave = { id, input -> coroutineScope.launch { viewModel.saveEdit(id, input) } },
        onEditDismiss = viewModel::cancelEdit,
        onRenameConfirm = { id, name ->
            coroutineScope.launch {
                viewModel.renameAccount(id, name)
            }
        },
        onRenameDismiss = viewModel::cancelRename,
        onReloginClick = { id -> viewModel.selectRelogin(id) },
        onQuotaAlertToggle = { id, windowId, enabled ->
            coroutineScope.launch {
                viewModel.setQuotaAlertEnabled(id, windowId, enabled)
            }
        },
        onDeleteClick = viewModel::requestDeleteAccount,
        onToggleExpanded = viewModel::toggleAccountExpanded,
        onDeleteConfirm = {
            coroutineScope.launch {
                viewModel.confirmDelete()
            }
        },
        onDeleteDismiss = viewModel::cancelDelete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    uiState: AccountUiState,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {},
    onAddAccountClick: () -> Unit = {},
    onLoginToCodexClick: () -> Unit = {},
    onAddAccountDismiss: () -> Unit = {},
    onSetCurrentClick: (LocalAccountId) -> Unit = {},
    onRenameClick: (LocalAccountId) -> Unit = {},
    onEditSave: (LocalAccountId, AccountEditInput) -> Unit = { _, _ -> },
    onEditDismiss: () -> Unit = {},
    onRenameConfirm: (LocalAccountId, String) -> Unit = { _, _ -> },
    onRenameDismiss: () -> Unit = {},
    onReloginClick: (LocalAccountId) -> Unit = {},
    onQuotaAlertToggle: (LocalAccountId, QuotaWindowId, Boolean) -> Unit = { _, _, _ -> },
    onDeleteClick: (LocalAccountId) -> Unit = {},
    onToggleExpanded: (LocalAccountId) -> Unit = {},
    onDeleteConfirm: () -> Unit = {},
    onDeleteDismiss: () -> Unit = {},
    detailAccountId: LocalAccountId? = null,
    onBackClick: () -> Unit = {},
    onOpenDetails: (LocalAccountId) -> Unit = {},
    analyticsContent: @Composable (AccountItemUi) -> Unit = {},
) {
    var selectedProviderId by rememberSaveable { mutableStateOf<String?>(null) }
    val providers = uiState.accounts.distinctBy { it.providerId }
        .map { ProviderFilterOption(it.providerId.value, it.providerDisplayName.ifBlank { it.providerId.value }) }
    val effectiveProviderId = selectedProviderId?.takeIf { id -> providers.any { it.id == id } }
    val visibleAccounts = uiState.accounts.filter { effectiveProviderId == null || it.providerId.value == effectiveProviderId }
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
        state = pullState,
        indicator = {
            GlassPullToRefreshIndicator(
                isRefreshing = uiState.isRefreshing,
                state = pullState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("account-screen")
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QuotaRabbitSpacing.xl, vertical = QuotaRabbitSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.lg),
    ) {
        if (detailAccountId == null) {
            AccountHeader(onAddAccountClick = onAddAccountClick)
            if (providers.isNotEmpty()) {
                ProviderFilterChips(providers, effectiveProviderId) { selectedProviderId = it }
            }
            SectionLabel(R.string.account_saved_section)
            if (uiState.contentStatus == AccountContentStatus.Unauthenticated) {
                EmptyAccountsCard(onAddAccountClick = onAddAccountClick)
            } else {
                visibleAccounts.forEach { account ->
                    AccountOverviewCard(
                        account = account,
                        quotaPreferences = uiState.quotaColorPreferences,
                        onOpenDetails = { onOpenDetails(account.id) },
                        onToggleExpanded = { onToggleExpanded(account.id) },
                    )
                }
            }
        } else {
            TextButton(
                onClick = onBackClick,
                modifier = Modifier.testTag("account-details-back"),
            ) {
                Text(stringResource(R.string.account_details_back))
            }
            val selected = uiState.accounts.firstOrNull { it.id == detailAccountId }
            if (selected == null) {
                Text(stringResource(R.string.account_details_missing))
            } else {
                Column(
                    modifier = Modifier.testTag("account-details:${selected.id.value}"),
                    verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.lg),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm),
                    ) {
                        AccountProfileAvatar(
                            initial = selected.avatarInitial,
                            avatarColorKey = selected.avatarColorKey,
                            accentColorId = selected.accentColorId,
                            modifier = Modifier.testTag("account-profile-avatar-detail:${selected.id.value}"),
                        )
                        Text(
                            selected.displayName,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (selected.providerId.value == "codex") {
                        CodexAccountDetailSummary(selected, uiState.quotaColorPreferences)
                        analyticsContent(selected)
                        CodexAccountDetailHero(selected, uiState.quotaColorPreferences)
                    } else {
                        Text(stringResource(R.string.analytics_usage_analytics),
                            style = MaterialTheme.typography.titleLarge)
                        AccountSurfaceCard(modifier = Modifier.testTag("analytics-unavailable:${selected.id.value}")) {
                            Text(
                                stringResource(R.string.account_analytics_unsupported),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    AccountDetailsPanel(
                        account = selected,
                        onSetCurrentClick = onSetCurrentClick,
                        onRenameClick = onRenameClick,
                        onReloginClick = onReloginClick,
                        onQuotaAlertToggle = onQuotaAlertToggle,
                        onDeleteClick = onDeleteClick,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(QuotaRabbitSpacing.bottomNavigationClearance))
    }
    }

    if (uiState.showAddAccountSheet) {
        AddAccountDialog(
            onLoginToCodexClick = onLoginToCodexClick,
            onDismiss = onAddAccountDismiss,
        )
    }

    uiState.pendingDeleteAccount?.let { pending ->
        DeleteAccountDialog(
            pending = pending,
            onConfirm = onDeleteConfirm,
            onDismiss = onDeleteDismiss,
        )
    }

    uiState.pendingEditAccount?.let { pending ->
        AccountEditDialog(
            account = pending,
            isSaving = uiState.isEditSaving,
            failed = uiState.editFailed,
            onSave = { input -> onEditSave(pending.id, input) },
            onDismiss = onEditDismiss,
        )
    }

    uiState.pendingRenameAccount?.let { pending ->
        RenameAccountDialog(
            pending = pending,
            onConfirm = onRenameConfirm,
            onDismiss = onRenameDismiss,
        )
    }
}

@Composable
private fun AddAccountDialog(
    onLoginToCodexClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("account-add-dialog"),
        title = {
            Text(
                text = stringResource(R.string.account_add_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md),
            ) {
                Text(
                    text = stringResource(R.string.account_add_dialog_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = onLoginToCodexClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = QuotaRabbitShapes.md,
                ) {
                    Text(text = stringResource(R.string.account_add_login_to_codex), textAlign = TextAlign.Center)
                }
            }
        },
        confirmButton = {
            QuotaRabbitDialogSystemBars()
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.account_cancel))
            }
        },
    )
}

@Composable
private fun AccountHeader(onAddAccountClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md),
    ) {
        Text(
            text = stringResource(R.string.account_title),
            style = if (LocalDensity.current.fontScale > 1.25f)
                MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f).testTag("account-title"),
        )
        FilledIconButton(
            onClick = onAddAccountClick,
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = QuotaRabbitTheme.colors.accent,
                contentColor = QuotaRabbitTheme.colors.surface,
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = stringResource(R.string.account_add_account),
            )
        }
    }
}

@Composable
private fun RenameAccountDialog(
    pending: AccountRenameUi,
    onConfirm: (LocalAccountId, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(pending.accountId) { mutableStateOf(pending.displayName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("account-rename-dialog"),
        title = {
            Text(
                text = stringResource(R.string.account_rename_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("account-rename-field"),
                textStyle = MaterialTheme.typography.bodyLarge,
                label = { Text(text = stringResource(R.string.account_rename_field_label)) },
            )
        },
        confirmButton = {
            QuotaRabbitDialogSystemBars()
            TextButton(onClick = { onConfirm(pending.accountId, name) }) {
                Text(text = stringResource(R.string.account_rename_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.account_cancel))
            }
        },
    )
}

@Composable
private fun DeleteAccountDialog(
    pending: AccountDeleteConfirmationUi,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("account-delete-dialog"),
        title = {
            Text(
                text = stringResource(R.string.account_delete_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.account_delete_dialog_message, pending.displayName),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            QuotaRabbitDialogSystemBars()
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.account_delete_dialog_confirm),
                    color = QuotaRabbitTheme.colors.danger,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.account_delete_dialog_cancel))
            }
        },
    )
}
