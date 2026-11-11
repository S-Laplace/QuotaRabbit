package com.alice.quotarabbit.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.account.ACCOUNT_ACCENT_COLORS
import com.alice.quotarabbit.domain.account.AccountEditInput
import com.alice.quotarabbit.ui.theme.accountAccentColor
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import com.alice.quotarabbit.ui.theme.QuotaRabbitDialogSystemBars

@Composable
internal fun AccountEditDialog(
    account: AccountItemUi,
    isSaving: Boolean,
    failed: Boolean,
    onSave: (AccountEditInput) -> Unit,
    onDismiss: () -> Unit,
) {
    var nickname by remember(account.id) { mutableStateOf(account.displayName) }
    var color by remember(account.id) {
        mutableStateOf(account.accentColorId.takeUnless { it == "blue" })
    }
    var renewal by remember(account.id) { mutableStateOf(account.manualRenewalDate.orEmpty()) }
    var invalidAttempt by remember(account.id) { mutableStateOf(false) }
    val candidate = AccountEditInput(nickname, color, renewal.trim().ifEmpty { null })
    val valid = runCatching { candidate.validated() }.isSuccess
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        modifier = Modifier.testTag("account-edit-dialog"),
        title = { Text(stringResource(R.string.account_edit_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(account.providerDisplayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it; invalidAttempt = false },
                    label = { Text(stringResource(R.string.account_edit_nickname)) },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth().testTag("account-edit-nickname"),
                )
                Text(stringResource(R.string.account_edit_color),
                    style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf<String?>(null).plus(ACCOUNT_ACCENT_COLORS).forEach { option ->
                        val selected = option == color
                        Row(
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                .semantics { this.selected = selected }
                                .clickable(enabled = !isSaving, role = Role.RadioButton) {
                                    color = option
                                }
                                .testTag("account-edit-color:${option ?: "automatic"}")
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(24.dp).background(
                                accountAccentColor(option) ?: QuotaRabbitTheme.colors.accent, CircleShape))
                            Text(
                                colorOptionLabel(option),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) QuotaRabbitTheme.colors.accent
                                    else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Text(stringResource(R.string.account_edit_preview),
                    style = MaterialTheme.typography.labelSmall)
                Box(Modifier.size(26.dp)
                    .background(accountAccentColor(color) ?: QuotaRabbitTheme.colors.accent,
                        CircleShape))
                OutlinedTextField(
                    value = renewal,
                    onValueChange = { renewal = it; invalidAttempt = false },
                    label = { Text(stringResource(R.string.account_edit_renewal)) },
                    placeholder = { Text("YYYY-MM-DD") },
                    supportingText = { Text(stringResource(R.string.account_edit_renewal_hint)) },
                    singleLine = true,
                    enabled = !isSaving,
                    isError = invalidAttempt || (!valid && renewal.isNotBlank()),
                    modifier = Modifier.fillMaxWidth().testTag("account-edit-renewal"),
                )
                TextButton(onClick = { renewal = ""; invalidAttempt = false },
                    enabled = !isSaving,
                    modifier = Modifier.testTag("account-edit-clear-renewal")) {
                    Text(stringResource(R.string.account_edit_clear_renewal))
                }
                if (invalidAttempt || failed) {
                    Text(stringResource(if (failed) R.string.account_edit_failed
                        else R.string.account_edit_invalid),
                        color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            QuotaRabbitDialogSystemBars()
            TextButton(
                enabled = !isSaving,
                onClick = {
                    if (valid) onSave(candidate.validated()) else invalidAttempt = true
                },
                modifier = Modifier.testTag("account-edit-save"),
            ) { Text(stringResource(R.string.account_edit_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving,
                modifier = Modifier.testTag("account-edit-cancel")) {
                Text(stringResource(R.string.account_cancel))
            }
        },
    )
}

@Composable
private fun colorOptionLabel(id: String?): String = stringResource(when (id) {
    null -> R.string.account_color_automatic
    "teal" -> R.string.account_color_teal
    "violet" -> R.string.account_color_violet
    "amber" -> R.string.account_color_amber
    "rose" -> R.string.account_color_rose
    "slate" -> R.string.account_color_slate
    "green" -> R.string.account_color_green
    "red" -> R.string.account_color_red
    else -> R.string.account_color_brown
})
