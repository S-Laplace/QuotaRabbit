package com.alice.quotarabbit.ui.auth

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.auth.DeviceCodeLoginController
import com.alice.quotarabbit.domain.auth.DeviceCodeLoginNotifier
import com.alice.quotarabbit.domain.auth.NoopDeviceCodeLoginController
import com.alice.quotarabbit.domain.auth.NoopDeviceCodeLoginNotifier
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import com.alice.quotarabbit.ui.theme.QuotaRabbitDialogSystemBars
import com.alice.quotarabbit.ui.theme.QuotaRabbitShapes
import com.alice.quotarabbit.ui.theme.QuotaRabbitSpacing

@Composable
fun AddAccountRoute(
    modifier: Modifier = Modifier,
    entryMode: AddAccountEntryMode = AddAccountEntryMode.Choose,
    deviceCodeLoginController: DeviceCodeLoginController = NoopDeviceCodeLoginController,
    deviceCodeLoginNotifier: DeviceCodeLoginNotifier = NoopDeviceCodeLoginNotifier,
    providerDisplayName: String = "Codex",
    viewModel: DeviceCodeLoginViewModel = viewModel(
        factory = DeviceCodeLoginViewModel.factory(
            controller = deviceCodeLoginController,
            notifier = deviceCodeLoginNotifier,
        ),
    ),
    onBackClick: () -> Unit = {},
    onLoginSaved: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(entryMode, viewModel) {
        viewModel.applyEntryMode(entryMode)
    }
    LaunchedEffect(uiState.shouldNavigateHomeAfterSave, uiState.attemptId) {
        if (uiState.shouldNavigateHomeAfterSave) {
            onLoginSaved()
        }
    }

    AddAccountScreen(
        uiState = uiState,
        modifier = modifier,
        providerDisplayName = providerDisplayName,
        onBackClick = {
            viewModel.cancelLogin()
            onBackClick()
        },
        onStartLoginClick = viewModel::startDeviceCodeLogin,
        onCopyCodeClick = { code -> context.copyToClipboard(code) },
        onOpenVerificationClick = { uri -> uriHandler.openUri(uri) },
        onRetryValidationClick = viewModel::retryValidation,
        onCancelLoginClick = viewModel::cancelLogin,
        onConfirmAddMismatchClick = viewModel::confirmAddAccountFromMismatch,
        onCancelMismatchClick = {
            viewModel.cancelLogin()
            onBackClick()
        },
    )
}

@Composable
fun AddAccountScreen(
    uiState: DeviceCodeLoginUiState,
    modifier: Modifier = Modifier,
    providerDisplayName: String = "Codex",
    onBackClick: () -> Unit = {},
    onStartLoginClick: () -> Unit = {},
    onCopyCodeClick: (String) -> Unit = {},
    onOpenVerificationClick: (String) -> Unit = {},
    onRetryValidationClick: () -> Unit = {},
    onCancelLoginClick: () -> Unit = {},
    onConfirmAddMismatchClick: () -> Unit = {},
    onCancelMismatchClick: () -> Unit = {},
) {
    if (uiState.status == DeviceCodeLoginUiStatus.AccountMismatchDecision) {
        AccountMismatchDialog(
            onConfirm = onConfirmAddMismatchClick,
            onDismiss = onCancelMismatchClick,
        )
    }
    AuthScaffold(
        title = stringResource(R.string.add_account_title, providerDisplayName),
        onBack = onBackClick,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = QuotaRabbitSpacing.xl)
                .padding(top = QuotaRabbitSpacing.sm, bottom = QuotaRabbitSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.lg),
        ) {
            Text(
                text = stringResource(R.string.add_account_description, providerDisplayName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AddAccountDeviceCodeCard(
                uiState = uiState,
                providerDisplayName = providerDisplayName,
                onStartLoginClick = onStartLoginClick,
                onCopyCodeClick = onCopyCodeClick,
                onOpenVerificationClick = onOpenVerificationClick,
                onRetryValidationClick = onRetryValidationClick,
                onCancelLoginClick = onCancelLoginClick,
            )

            Spacer(modifier = Modifier.height(QuotaRabbitSpacing.bottomNavigationClearance))
        }
    }
}

@Composable
private fun AddAccountDeviceCodeCard(
    uiState: DeviceCodeLoginUiState,
    providerDisplayName: String,
    onStartLoginClick: () -> Unit,
    onCopyCodeClick: (String) -> Unit,
    onOpenVerificationClick: (String) -> Unit,
    onRetryValidationClick: () -> Unit,
    onCancelLoginClick: () -> Unit,
) {
    AddAccountSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md)) {
            Text(
                text = stringResource(R.string.add_account_codex_title, providerDisplayName),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            AddAccountStatusText(uiState = uiState)
            uiState.userCode?.let { code ->
                val codeDescription = stringResource(R.string.add_account_device_code_format, code)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = code,
                        modifier = Modifier.weight(1f)
                            .testTag("device-code-value")
                            .semantics { contentDescription = codeDescription },
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(
                        onClick = { onCopyCodeClick(code) },
                        modifier = Modifier.size(48.dp).testTag("device-code-copy"),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_action_copy),
                            contentDescription = stringResource(R.string.add_account_copy_code),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            uiState.verificationUri?.let { verificationUri ->
                Button(
                    onClick = { onOpenVerificationClick(verificationUri) },
                    modifier = Modifier.fillMaxWidth().testTag("device-code-open-browser"),
                    shape = QuotaRabbitShapes.md,
                ) {
                    Text(
                        text = stringResource(R.string.add_account_open_verification_page, providerDisplayName),
                        style = MaterialTheme.typography.labelLarge.copy(lineBreak = LineBreak.Heading),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            AddAccountActionRow(
                status = uiState.status,
                providerDisplayName = providerDisplayName,
                onStartLoginClick = onStartLoginClick,
                onRetryValidationClick = onRetryValidationClick,
                onCancelLoginClick = onCancelLoginClick,
            )
        }
    }
}

@Composable
private fun AddAccountStatusText(uiState: DeviceCodeLoginUiState) {
    val errorMessageResId = uiState.errorMessageResId
    val connectedAccount = uiState.connectedAccount
    val text = when {
        errorMessageResId != null -> stringResource(errorMessageResId)
        connectedAccount != null -> stringResource(R.string.add_account_device_code_connected, connectedAccount.displayName)
        else -> when (uiState.status) {
            DeviceCodeLoginUiStatus.Idle -> stringResource(R.string.add_account_device_code_idle)
            DeviceCodeLoginUiStatus.RequestingDeviceCode -> stringResource(R.string.add_account_device_code_requesting)
            DeviceCodeLoginUiStatus.AwaitingUserAuthorization -> stringResource(R.string.add_account_device_code_waiting)
            DeviceCodeLoginUiStatus.PollingAuthorization -> stringResource(R.string.add_account_device_code_polling)
            DeviceCodeLoginUiStatus.ExchangingToken -> stringResource(R.string.add_account_device_code_exchanging)
            DeviceCodeLoginUiStatus.ValidatingUsage -> stringResource(R.string.add_account_device_code_validating)
            DeviceCodeLoginUiStatus.ValidationFailed -> stringResource(R.string.add_account_device_code_validation_failed)
            DeviceCodeLoginUiStatus.Saved -> stringResource(R.string.add_account_device_code_saved)
            DeviceCodeLoginUiStatus.AccountMismatchDecision -> stringResource(R.string.add_account_device_code_account_mismatch)
            DeviceCodeLoginUiStatus.Expired -> stringResource(R.string.add_account_device_code_expired)
            DeviceCodeLoginUiStatus.Cancelled -> stringResource(R.string.add_account_device_code_cancelled)
            DeviceCodeLoginUiStatus.Failed -> stringResource(R.string.add_account_device_code_failed)
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (errorMessageResId != null) {
            MaterialTheme.colorScheme.error
        } else if (connectedAccount != null || uiState.status == DeviceCodeLoginUiStatus.Saved) {
            QuotaRabbitTheme.colors.success
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
private fun AddAccountActionRow(
    status: DeviceCodeLoginUiStatus,
    providerDisplayName: String,
    onStartLoginClick: () -> Unit,
    onRetryValidationClick: () -> Unit,
    onCancelLoginClick: () -> Unit,
) {
    when (status) {
        DeviceCodeLoginUiStatus.RequestingDeviceCode,
        DeviceCodeLoginUiStatus.ExchangingToken,
        DeviceCodeLoginUiStatus.ValidatingUsage -> Row(
            horizontalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
                text = stringResource(R.string.add_account_device_code_in_progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DeviceCodeLoginUiStatus.AwaitingUserAuthorization,
        DeviceCodeLoginUiStatus.PollingAuthorization -> OutlinedButton(
            onClick = onCancelLoginClick,
            modifier = Modifier.fillMaxWidth(),
            shape = QuotaRabbitShapes.md,
        ) {
            Text(text = stringResource(R.string.add_account_cancel_login))
        }
        DeviceCodeLoginUiStatus.ValidationFailed -> BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 300.dp && LocalDensity.current.fontScale <= 1.2f) 2 else 1
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm),
                maxItemsInEachRow = columns,
            ) {
                Button(
                    onClick = onRetryValidationClick,
                    shape = QuotaRabbitShapes.md,
                    modifier = Modifier.weight(1f).testTag("device-code-retry-validation"),
                ) {
                    Text(text = stringResource(R.string.add_account_retry_validation))
                }
                OutlinedButton(
                    onClick = onStartLoginClick,
                    shape = QuotaRabbitShapes.md,
                    modifier = Modifier.weight(1f).testTag("device-code-restart-login"),
                ) {
                    Text(text = stringResource(R.string.add_account_restart_login))
                }
            }
        }
        DeviceCodeLoginUiStatus.Idle,
        DeviceCodeLoginUiStatus.Expired,
        DeviceCodeLoginUiStatus.Cancelled,
        DeviceCodeLoginUiStatus.Failed,
        DeviceCodeLoginUiStatus.AccountMismatchDecision,
        DeviceCodeLoginUiStatus.Saved -> Button(
            onClick = onStartLoginClick,
            modifier = Modifier.fillMaxWidth(),
            shape = QuotaRabbitShapes.md,
        ) {
            Text(text = stringResource(R.string.add_account_start_device_code_login, providerDisplayName))
        }
    }
}

@Composable
private fun AccountMismatchDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("device-code-mismatch-dialog"),
        title = { Text(text = stringResource(R.string.add_account_mismatch_dialog_title)) },
        text = {
            Text(
                text = stringResource(R.string.add_account_mismatch_dialog_message),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            QuotaRabbitDialogSystemBars()
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.add_account_mismatch_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.add_account_mismatch_dialog_cancel))
            }
        },
    )
}

@Composable
private fun AddAccountSurfaceCard(content: @Composable () -> Unit) {
    Card(
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

private fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.add_account_copy_code), text))
}
