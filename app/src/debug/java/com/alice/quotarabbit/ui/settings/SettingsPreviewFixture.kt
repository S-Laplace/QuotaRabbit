package com.alice.quotarabbit.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.model.QuotaWindowId
import com.alice.quotarabbit.domain.theme.ThemeMode
import com.alice.quotarabbit.domain.update.AppUpdateInfo

/** Screen callbacks only: no repositories, credentials, clipboard, downloads, or data deletion. */
class SettingsPreviewFixture(initialMode: ThemeMode) {
    val events = mutableListOf<String>()
    var state by mutableStateOf(
        SettingsUiState(
            appearance = SettingsAppearanceUi(initialMode),
            persistentNotification = SettingsPersistentNotificationUi(
                accountChoices = listOf(
                    SettingsNotificationAccountChoice(SettingsNotificationAccountSelection.FollowCurrent),
                ) + accounts.map(::SettingsNotificationAccountChoice),
                windowChoices = listOf(
                    SettingsNotificationWindowChoice(QuotaWindowId("five_hour"), R.string.home_quota_window_five_hour),
                    SettingsNotificationWindowChoice(QuotaWindowId("weekly"), R.string.home_quota_window_weekly),
                ),
            ),
            alerts = SettingsAlertsUi(balanceCaution = 10.0, balanceWarning = 3.0),
            currency = SettingsCurrencyUi(targetCurrency = "USD", supportedCurrencies = listOf("USD", "CNY", "EUR", "KRW")),
            refresh = SettingsRefreshUi(lastResultLabelResId = R.string.settings_refresh_last_result_failed),
            diagnostics = SettingsDiagnosticsUi(copyModel = SettingsDiagnosticsCopyModel(DIAGNOSTICS)),
            about = SettingsAboutUi(versionName = "0.0.0", buildName = "offline fixture"),
        ),
    )
        private set

    private fun change(event: String, transform: (SettingsUiState) -> SettingsUiState) {
        events += event
        state = transform(state)
    }

    /** Test-controlled completion makes the checking/disabled state observable without a timer. */
    fun completeUpdateCheck() {
        state = state.copy(update = state.update.copy(
            isChecking = false,
            statusLabelResId = R.string.settings_update_status_update_available,
            availableUpdate = update,
            pendingUpdate = update,
        ))
    }

    @Composable
    fun Content() {
        SettingsScreen(
            uiState = state,
            onChoiceDialogRequested = { choice -> change("choice:$choice") { it.copy(pendingChoiceDialog = choice) } },
            onChoiceDialogDismiss = { change("choice_cancel") { it.copy(pendingChoiceDialog = null) } },
            onPersistentNotificationAccountSelected = { account ->
                change("notification_account") { it.copy(
                    persistentNotification = it.persistentNotification.copy(accountSelection = account),
                    pendingChoiceDialog = null,
                ) }
            },
            onPersistentNotificationWindowSelected = { window ->
                change("notification_window:${window.value}") { it.copy(
                    persistentNotification = it.persistentNotification.copy(selectedWindowId = window),
                    pendingChoiceDialog = null,
                ) }
            },
            onStatusNotificationChanged = { enabled -> change("status:$enabled") { it.copy(
                persistentNotification = it.persistentNotification.copy(enabled = enabled),
            ) } },
            onAccountErrorsChanged = { enabled -> change("errors:$enabled") { it.copy(
                alerts = it.alerts.copy(accountErrorsEnabled = enabled),
            ) } },
            onCautionThresholdChanged = { value -> change("caution:$value") { it.copy(
                alerts = it.alerts.copy(thresholds = it.alerts.thresholds.copy(caution = value)),
            ) } },
            onWarningThresholdChanged = { value -> change("warning:$value") { it.copy(
                alerts = it.alerts.copy(thresholds = it.alerts.thresholds.copy(warning = value)),
            ) } },
            onBalanceCautionChanged = { value -> change("balance_caution:$value") { it.copy(
                alerts = it.alerts.copy(balanceCaution = value),
            ) } },
            onBalanceWarningChanged = { value -> change("balance_warning:$value") { it.copy(
                alerts = it.alerts.copy(balanceWarning = value),
            ) } },
            onBackgroundRefreshChanged = { enabled -> change("background:$enabled") { it.copy(
                refresh = it.refresh.copy(backgroundRefreshEnabled = enabled),
            ) } },
            onRefreshIntervalSelected = { interval -> change("interval:$interval") { it.copy(
                refresh = it.refresh.copy(interval = interval), pendingChoiceDialog = null,
            ) } },
            onCurrencyTargetSelected = { currency -> change("currency:$currency") { it.copy(
                currency = it.currency.copy(targetCurrency = currency), pendingChoiceDialog = null,
            ) } },
            onThemeModeSelected = { mode -> change("theme:$mode") { it.copy(appearance = SettingsAppearanceUi(mode)) } },
            onRetentionSelected = { retention -> change("retention:$retention") { it.copy(
                data = it.data.copy(retention = retention), pendingChoiceDialog = null,
            ) } },
            onClearCurrentHistoryClick = { requestClear(SettingsPendingDataAction.ClearCurrentHistory) },
            onClearAllHistoryClick = { requestClear(SettingsPendingDataAction.ClearAllHistory) },
            onDataActionConfirm = {
                val action = requireNotNull(state.data.pendingAction)
                change("clear_confirm:$action") { it.copy(data = it.data.copy(pendingAction = null, confirmedAction = action)) }
            },
            onDataActionDismiss = { change("clear_cancel") { it.copy(data = it.data.copy(pendingAction = null)) } },
            onDiagnosticsToggle = { change("diagnostics_toggle") { it.copy(
                diagnostics = it.diagnostics.copy(isExpanded = !it.diagnostics.isExpanded),
            ) } },
            onDiagnosticsCopyClick = { events += "diagnostics_copy" },
            onDiagnosticsRecheckClick = { change("diagnostics_recheck") { it.copy(
                diagnostics = it.diagnostics.copy(recheckRequestCount = it.diagnostics.recheckRequestCount + 1),
            ) } },
            onAboutRepositoryClick = { events += "repository" },
            onAppUpdateCheckClick = { change("update_check") { it.copy(
                update = it.update.copy(isChecking = true, statusLabelResId = R.string.settings_update_status_checking),
            ) } },
            onAppUpdateDismiss = { change("update_cancel") { it.copy(update = it.update.copy(pendingUpdate = null)) } },
            onAppUpdateDownloadClick = { change("update_download") { it.copy(update = it.update.copy(
                pendingUpdate = null, statusLabelResId = R.string.settings_update_status_downloading,
            )) } },
            onAppUpdateAvailableClick = { change("update_available") { it.copy(update = it.update.copy(pendingUpdate = update)) } },
            onAppUpdateAutoCheckChanged = { enabled -> change("update_auto:$enabled") { it.copy(
                update = it.update.copy(autoCheckEnabled = enabled),
            ) } },
            onAppUpdateNotifyChanged = { enabled -> change("update_notify:$enabled") { it.copy(
                update = it.update.copy(notifyOnUpdateEnabled = enabled),
            ) } },
            showBatteryOptimizationHint = state.refresh.backgroundRefreshEnabled,
            onBatteryOptimizationClick = { events += "battery" },
        )
    }

    private fun requestClear(action: SettingsPendingDataAction) {
        change("clear_request:$action") { it.copy(data = it.data.copy(pendingAction = action)) }
    }

    companion object {
        val accounts = listOf(
            SettingsNotificationAccountSelection.Account(ProviderId("codex"), LocalAccountId("settings-personal"), "Personal development workspace"),
            SettingsNotificationAccountSelection.Account(ProviderId("codex"), LocalAccountId("settings-korean"), "개발 프로젝트 개인 계정"),
            SettingsNotificationAccountSelection.Account(ProviderId("deepseek"), LocalAccountId("settings-team"), "团队研发工作账号"),
        )
        const val DIAGNOSTICS = "## ENVIRONMENT\nOffline specimen\n## CONFIG\nHistory: 30 days\n## ACCOUNTS\nCodex: local fixture"
        private val update = AppUpdateInfo(
            versionName = "99.0.0",
            releasePageUrl = "https://example.invalid/offline-release",
            apkDownloadUrl = "https://example.invalid/offline.apk",
            apkFileName = "offline.apk",
            releaseNotes = "Offline release notes for checking wrapped text and cancellable actions.\n".repeat(6),
        )
    }
}
