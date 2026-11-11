package com.alice.quotarabbit.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alice.quotarabbit.BuildConfig
import com.alice.quotarabbit.R
import com.alice.quotarabbit.app.NotificationWindowChoice
import com.alice.quotarabbit.app.NotificationWindowChoicesLoader
import com.alice.quotarabbit.domain.account.AccountListUseCase
import com.alice.quotarabbit.domain.account.NoopAccountListUseCase
import com.alice.quotarabbit.domain.currency.CurrencyPreferenceStore
import com.alice.quotarabbit.domain.currency.CurrencyPreferences
import com.alice.quotarabbit.domain.diagnostics.DiagnosticsRedactor
import com.alice.quotarabbit.domain.diagnostics.DiagnosticsTimeFormatter
import com.alice.quotarabbit.domain.model.AccountStatus
import com.alice.quotarabbit.domain.model.QuotaWindowId
import com.alice.quotarabbit.domain.settings.LanguagePreference
import com.alice.quotarabbit.domain.settings.NoopQuotaHistoryClearUseCase
import com.alice.quotarabbit.domain.settings.NotificationAccountSelection
import com.alice.quotarabbit.domain.settings.NotificationPreferenceStore
import com.alice.quotarabbit.domain.settings.NotificationPreferences
import com.alice.quotarabbit.domain.settings.QuotaHistoryClearUseCase
import com.alice.quotarabbit.domain.settings.RetentionPreference
import com.alice.quotarabbit.domain.settings.RetentionPreferenceStore
import com.alice.quotarabbit.domain.theme.AppearancePreferenceStore
import com.alice.quotarabbit.domain.theme.ThemeMode
import com.alice.quotarabbit.domain.update.AppUpdateCheckResult
import com.alice.quotarabbit.domain.update.AppUpdateCheckUseCase
import com.alice.quotarabbit.domain.update.AppUpdateDownloadResult
import com.alice.quotarabbit.domain.update.AppUpdateDownloadUseCase
import com.alice.quotarabbit.domain.update.AppVersionComparator
import com.alice.quotarabbit.domain.update.NoopAppUpdateCheckUseCase
import com.alice.quotarabbit.domain.update.NoopAppUpdateDownloadUseCase
import com.alice.quotarabbit.domain.update.NoopUpdatePreferenceStore
import com.alice.quotarabbit.domain.update.UpdatePreferenceStore
import com.alice.quotarabbit.ui.quota.quotaWindowLabelRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SettingsViewModel(
    private val accountListUseCase: AccountListUseCase = NoopAccountListUseCase,
    private val retentionPreferenceStore: RetentionPreferenceStore = InMemoryRetentionPreferenceStore(),
    private val notificationPreferenceStore: NotificationPreferenceStore = InMemoryNotificationPreferenceStore(),
    private val backgroundRefreshStatusReader: SettingsBackgroundRefreshStatusReader =
        InMemoryBackgroundRefreshStatusReader(),
    private val diagnosticsReader: SettingsDiagnosticsReader = DefaultSettingsDiagnosticsReader,
    private val quotaHistoryClearUseCase: QuotaHistoryClearUseCase = NoopQuotaHistoryClearUseCase,
    private val appUpdateChecker: AppUpdateCheckUseCase = NoopAppUpdateCheckUseCase,
    private val appUpdateDownloader: AppUpdateDownloadUseCase = NoopAppUpdateDownloadUseCase,
    private val backgroundRefreshScheduler: BackgroundRefreshScheduler = NoopBackgroundRefreshScheduler,
    private val currentVersionName: String = BuildConfig.VERSION_NAME,
    private val notificationWindowChoicesLoader: NotificationWindowChoicesLoader =
        NotificationWindowChoicesLoader { _, _ -> emptyList() },
    private val currencyPreferenceStore: CurrencyPreferenceStore = NoopCurrencyPreferenceStore,
    private val appearancePreferenceStore: AppearancePreferenceStore = NoopAppearancePreferenceStore,
    private val updatePreferenceStore: UpdatePreferenceStore = NoopUpdatePreferenceStore,
    private val updateCheckScheduler: UpdateCheckScheduler = NoopUpdateCheckScheduler,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SettingsUiState(
            about = SettingsAboutUi(
                versionName = currentVersionName,
                buildName = BuildConfig.BUILD_TYPE,
            ),
        ),
    )
    private val notificationPreferenceWriteMutex = Mutex()
    private var notificationPreferencesEditedLocally = false
    private var latestNotificationPreferences = NotificationPreferences()

    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setStatusNotificationEnabled(enabled: Boolean) {
        val nextAlerts = _uiState.value.alerts.copy(statusNotificationEnabled = enabled)
        notificationPreferencesEditedLocally = true
        update { state ->
            state.copy(
                alerts = nextAlerts,
                persistentNotification = state.persistentNotification.copy(enabled = enabled),
            )
        }
        viewModelScope.launch {
            persistNotificationPreferences { it.copy(statusNotificationEnabled = enabled) }
        }
    }

    fun setAccountErrorsEnabled(enabled: Boolean) {
        val nextAlerts = _uiState.value.alerts.copy(accountErrorsEnabled = enabled)
        notificationPreferencesEditedLocally = true
        update { state ->
            state.copy(alerts = nextAlerts)
        }
        viewModelScope.launch {
            persistNotificationPreferences { it.copy(accountErrorsEnabled = enabled) }
        }
    }

    fun updateAlertThresholds(caution: Int, warning: Int) {
        val nextAlerts = _uiState.value.alerts.copy(thresholds = normalizedThresholds(caution, warning))
        notificationPreferencesEditedLocally = true
        update { state ->
            state.copy(alerts = nextAlerts)
        }
        viewModelScope.launch {
            persistNotificationPreferences {
                it.copy(
                    cautionThreshold = nextAlerts.thresholds.caution,
                    warningThreshold = nextAlerts.thresholds.warning,
                    limitThreshold = nextAlerts.thresholds.limit,
                )
            }
        }
    }

    fun updateBalanceThresholds(caution: Double, warning: Double) {
        val (safeCaution, safeWarning) = normalizedBalanceThresholds(caution, warning)
        val nextAlerts = _uiState.value.alerts.copy(balanceCaution = safeCaution, balanceWarning = safeWarning)
        notificationPreferencesEditedLocally = true
        update { state -> state.copy(alerts = nextAlerts) }
        viewModelScope.launch {
            persistNotificationPreferences {
                it.copy(balanceCautionThreshold = safeCaution, balanceWarningThreshold = safeWarning)
            }
        }
    }

    fun updatePersistentNotificationAccount(selection: SettingsNotificationAccountSelection) {
        notificationPreferencesEditedLocally = true
        update { state ->
            state.copy(
                persistentNotification = state.persistentNotification.copy(accountSelection = selection),
                pendingChoiceDialog = null,
            )
        }
        viewModelScope.launch {
            persistNotificationPreferences {
                it.copy(persistentNotificationAccount = selection.toNotificationAccountSelection())
            }
            loadAndApplyWindowChoices(selection)
        }
    }

    fun updatePersistentNotificationWindow(windowId: QuotaWindowId) {
        notificationPreferencesEditedLocally = true
        update { state ->
            state.copy(
                persistentNotification = state.persistentNotification.copy(selectedWindowId = windowId),
                pendingChoiceDialog = null,
            )
        }
        viewModelScope.launch {
            persistNotificationPreferences { it.copy(persistentNotificationWindowId = windowId) }
        }
    }

    private suspend fun loadAndApplyWindowChoices(selection: SettingsNotificationAccountSelection) {
        val (providerId, localAccountId) = when (selection) {
            SettingsNotificationAccountSelection.FollowCurrent -> null to null
            is SettingsNotificationAccountSelection.Account ->
                selection.providerId to selection.localAccountId
        }
        val choices = notificationWindowChoicesLoader.windowChoices(providerId, localAccountId)
            .map { it.toSettingsNotificationWindowChoice() }
        var needsPersist = false
        var resolvedWindowId: QuotaWindowId? = null
        update { state ->
            val currentId = state.persistentNotification.selectedWindowId
            val resolvedId = if (choices.isNotEmpty() && choices.none { it.windowId == currentId }) {
                choices.first().windowId
            } else {
                currentId
            }
            if (choices.isNotEmpty() && resolvedId != currentId) {
                needsPersist = true
                resolvedWindowId = resolvedId
            }
            state.copy(
                persistentNotification = state.persistentNotification.copy(
                    windowChoices = choices,
                    selectedWindowId = resolvedId,
                ),
            )
        }
        val finalWindowId = resolvedWindowId
        if (needsPersist && finalWindowId != null) {
            persistNotificationPreferences { it.copy(persistentNotificationWindowId = finalWindowId) }
        }
    }

    fun setBackgroundRefreshEnabled(enabled: Boolean) {
        update { state ->
            state.copy(refresh = state.refresh.copy(backgroundRefreshEnabled = enabled))
        }
    }

    fun updateRefreshInterval(interval: SettingsRefreshInterval) {
        notificationPreferencesEditedLocally = true
        update { state ->
            state.copy(
                refresh = state.refresh.copy(interval = interval),
                pendingChoiceDialog = null,
            )
        }
        backgroundRefreshScheduler.applyIntervalMinutes(interval.minutes)
        viewModelScope.launch {
            persistNotificationPreferences { it.copy(backgroundRefreshIntervalMinutes = interval.minutes) }
        }
    }

    fun updateCurrencyTarget(currency: String) {
        update { it.copy(currency = it.currency.copy(targetCurrency = currency), pendingChoiceDialog = null) }
        viewModelScope.launch {
            currencyPreferenceStore.updateCurrencyPreferences(CurrencyPreferences(currency))
        }
    }

    fun updateLanguage(preference: LanguagePreference) {
        update { state ->
            state.copy(
                appearance = state.appearance.copy(language = preference),
                pendingChoiceDialog = null,
            )
        }
        viewModelScope.launch {
            appearancePreferenceStore.setLanguagePreference(preference)
        }
    }

    fun updateThemeMode(mode: ThemeMode) {
        update { it.copy(appearance = it.appearance.copy(themeMode = mode)) }
        viewModelScope.launch {
            appearancePreferenceStore.setThemeMode(mode)
        }
    }

    fun loadSettings() {
        viewModelScope.launch {
            runCatching {
                retentionPreferenceStore.retentionPreference().toSettingsRetentionOption()
            }.onSuccess { retention ->
                update { state ->
                    state.copy(
                        data = state.data.copy(
                            retention = retention,
                        ),
                    )
                }
            }
            runCatching {
                accountListUseCase.loadAccounts()
            }.onSuccess { accountList ->
                update { state ->
                    val accountChoices = accountList.accounts
                        .filterNot { it.status == AccountStatus.Deleted }
                        .map { account ->
                            SettingsNotificationAccountChoice(
                                SettingsNotificationAccountSelection.Account(
                                    providerId = account.providerId,
                                    localAccountId = account.localAccountId,
                                    displayName = account.displayName,
                                ),
                            )
                        }
                    val allChoices = listOf(
                        SettingsNotificationAccountChoice(SettingsNotificationAccountSelection.FollowCurrent),
                    ) + accountChoices
                    state.copy(
                        persistentNotification = state.persistentNotification.copy(
                            accountChoices = allChoices,
                            accountSelection = state.persistentNotification.accountSelection
                                .withDisplayNameFrom(allChoices),
                        ),
                    )
                }
            }
            runCatching {
                backgroundRefreshStatusReader.latestBackgroundRefreshStatus()
            }.onSuccess { status ->
                update { state ->
                    state.copy(
                        refresh = state.refresh.copy(lastResultLabelResId = status.labelResId),
                    )
                }
            }
            runCatching {
                currencyPreferenceStore.currencyPreferences()
            }.onSuccess { currencyPreferences ->
                update { state ->
                    state.copy(
                        currency = SettingsCurrencyUi(targetCurrency = currencyPreferences.targetCurrency),
                    )
                }
            }
            runCatching {
                appearancePreferenceStore.languagePreference.first()
            }.onSuccess { language ->
                update { state ->
                    state.copy(appearance = state.appearance.copy(language = language))
                }
            }
            runCatching {
                appearancePreferenceStore.themeMode.first()
            }.onSuccess { themeMode ->
                update { state ->
                    state.copy(appearance = state.appearance.copy(themeMode = themeMode))
                }
            }
            runCatching {
                notificationPreferenceStore.notificationPreferences()
            }.onSuccess { preferences ->
                latestNotificationPreferences = preferences
                if (!notificationPreferencesEditedLocally) {
                    update { state ->
                        val resolvedAccountSelection = preferences.persistentNotificationAccount
                            .toSettingsNotificationAccountSelection(
                                state.persistentNotification.accountChoices,
                            )
                        state.copy(
                            persistentNotification = state.persistentNotification.copy(
                                enabled = preferences.statusNotificationEnabled,
                                accountSelection = resolvedAccountSelection,
                                selectedWindowId = preferences.persistentNotificationWindowId,
                            ),
                            alerts = preferences.toSettingsAlertsUi(),
                            refresh = state.refresh.copy(
                                interval = SettingsRefreshInterval.fromMinutes(
                                    preferences.backgroundRefreshIntervalMinutes,
                                ),
                            ),
                        )
                    }
                    // Load window choices for the resolved account selection.
                    val resolvedAccountSelection = _uiState.value.persistentNotification.accountSelection
                    loadAndApplyWindowChoices(resolvedAccountSelection)
                }
            }
            runCatching {
                updatePreferenceStore.preferences()
            }.onSuccess { prefs ->
                val available = prefs.availableUpdate?.takeIf {
                    AppVersionComparator.isRemoteVersionNewer(currentVersionName, it.versionName)
                }
                if (available == null && prefs.availableUpdate != null) {
                    runCatching { updatePreferenceStore.setAvailableUpdate(null) }
                }
                update { state ->
                    state.copy(
                        update = state.update.copy(
                            autoCheckEnabled = prefs.autoCheckEnabled,
                            notifyOnUpdateEnabled = prefs.notifyOnUpdateEnabled,
                            availableUpdate = available,
                        ),
                    )
                }
            }
        }
    }

    fun updateRetention(retention: SettingsRetentionOption) {
        viewModelScope.launch {
            runCatching {
                retentionPreferenceStore.updateRetentionPreference(retention.toRetentionPreference())
            }.onSuccess {
                update { state ->
                    state.copy(
                        data = state.data.copy(retention = retention),
                        pendingChoiceDialog = null,
                    )
                }
            }
        }
    }

    fun requestChoiceDialog(dialog: SettingsChoiceDialog) {
        update { state ->
            state.copy(pendingChoiceDialog = dialog)
        }
    }

    fun dismissChoiceDialog() {
        update { state ->
            state.copy(pendingChoiceDialog = null)
        }
    }

    fun requestClearCurrentHistory() {
        update { state ->
            state.copy(data = state.data.copy(pendingAction = SettingsPendingDataAction.ClearCurrentHistory))
        }
    }

    fun requestClearAllHistory() {
        update { state ->
            state.copy(data = state.data.copy(pendingAction = SettingsPendingDataAction.ClearAllHistory))
        }
    }

    fun cancelDataAction() {
        update { state ->
            state.copy(data = state.data.copy(pendingAction = null))
        }
    }

    fun confirmDataAction() {
        val action = _uiState.value.data.pendingAction ?: return
        viewModelScope.launch {
            val result = runCatching {
                when (action) {
                    SettingsPendingDataAction.ClearCurrentHistory ->
                        quotaHistoryClearUseCase.clearCurrentAccountHistory()
                    SettingsPendingDataAction.ClearAllHistory ->
                        quotaHistoryClearUseCase.clearAllHistory()
                }
            }
            if (result.isSuccess) {
                update { state ->
                    state.copy(data = state.data.copy(pendingAction = null, confirmedAction = action))
                }
            }
        }
    }

    fun toggleDiagnosticsExpanded() {
        update { state ->
            state.copy(diagnostics = state.diagnostics.copy(isExpanded = !state.diagnostics.isExpanded))
        }
    }

    suspend fun copyDiagnostics(): SettingsDiagnosticsCopyModel =
        copyDiagnostics(diagnosticsReader.diagnosticsInput())

    fun copyDiagnostics(input: SettingsDiagnosticsInput): SettingsDiagnosticsCopyModel {
        val copyModel = buildDiagnosticsCopyModel(input)
        update { state ->
            state.copy(diagnostics = state.diagnostics.copy(copyModel = copyModel))
        }
        return copyModel
    }

    fun requestDiagnosticsRecheck() {
        viewModelScope.launch {
            val copyModel = copyDiagnostics()
            update { state ->
                state.copy(
                    diagnostics = state.diagnostics.copy(
                        copyModel = copyModel,
                        recheckRequestCount = state.diagnostics.recheckRequestCount + 1,
                    ),
                )
            }
        }
    }

    fun checkForUpdates() {
        if (_uiState.value.update.isChecking) return
        update { state ->
            state.copy(
                update = state.update.copy(
                    isChecking = true,
                    statusLabelResId = R.string.settings_update_status_checking,
                    pendingUpdate = null,
                ),
            )
        }
        viewModelScope.launch {
            val result = runCatching {
                appUpdateChecker.checkForUpdate(currentVersionName)
            }.getOrElse {
                AppUpdateCheckResult.Failure("app_update_check_failed")
            }
            update { state -> state.copy(update = result.toSettingsUpdateUi(state.update)) }
            when (result) {
                is AppUpdateCheckResult.UpdateAvailable ->
                    runCatching { updatePreferenceStore.setAvailableUpdate(result.update) }
                AppUpdateCheckResult.UpToDate ->
                    runCatching { updatePreferenceStore.setAvailableUpdate(null) }
                else -> Unit
            }
        }
    }

    fun dismissPendingUpdate() {
        update { state ->
            state.copy(update = state.update.copy(pendingUpdate = null))
        }
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        update { state -> state.copy(update = state.update.copy(autoCheckEnabled = enabled)) }
        updateCheckScheduler.setAutoCheckEnabled(enabled)
        viewModelScope.launch {
            runCatching { updatePreferenceStore.setAutoCheckEnabled(enabled) }
        }
    }

    fun setNotifyOnUpdate(enabled: Boolean) {
        update { state -> state.copy(update = state.update.copy(notifyOnUpdateEnabled = enabled)) }
        viewModelScope.launch {
            runCatching { updatePreferenceStore.setNotifyOnUpdateEnabled(enabled) }
        }
    }

    fun showAvailableUpdateDialog() {
        viewModelScope.launch {
            val available = runCatching { updatePreferenceStore.preferences().availableUpdate }
                .getOrNull() ?: _uiState.value.update.availableUpdate ?: return@launch
            update { state ->
                state.copy(update = state.update.copy(pendingUpdate = available))
            }
        }
    }

    fun downloadPendingUpdate() {
        val pendingUpdate = _uiState.value.update.pendingUpdate ?: return
        viewModelScope.launch {
            val result = runCatching {
                appUpdateDownloader.download(pendingUpdate)
            }.getOrElse {
                AppUpdateDownloadResult.Failure("app_update_download_failed")
            }
            update { state ->
                state.copy(update = result.toSettingsUpdateUi(state.update))
            }
        }
    }

    fun buildDiagnosticsCopyModel(input: SettingsDiagnosticsInput): SettingsDiagnosticsCopyModel {
        val now = input.generatedAtMillis
        fun ts(value: String?): String? = DiagnosticsTimeFormatter.render(value, now)
        fun age(value: String?): String? = DiagnosticsTimeFormatter.renderAgeOnly(value, now)

        val rawDiagnostics = buildString {
            appendLine("## GENERATED")
            appendLine("generatedAt=${DiagnosticsTimeFormatter.render(now?.toString(), null) ?: "unavailable"}")

            appendLine("## ENVIRONMENT")
            appendLine("appVersion=${input.appVersion}")
            appendLine("buildType=${input.buildType}")
            input.androidSdk?.let { appendLine("androidSdk=$it") }
            input.androidRelease?.let { appendLine("androidRelease=$it") }
            input.deviceModel?.let { appendLine("deviceModel=$it") }
            input.locale?.let { appendLine("locale=$it") }
            input.batteryOptimizationIgnored?.let { appendLine("batteryOptimizationIgnored=$it") }
            input.backgroundRestricted?.let { appendLine("backgroundRestricted=$it") }
            input.networkType?.let { appendLine("networkType=$it") }
            input.dataSaver?.let { appendLine("dataSaver=$it") }
            ts(input.appFirstInstallAt)?.let { appendLine("appFirstInstallAt=$it") }
            ts(input.appLastUpdateAt)?.let { appendLine("appLastUpdateAt=$it") }

            appendLine("## WORKMANAGER")
            appendLine("workManagerStatus=${input.workManagerStatus}")
            input.workManagerRunAttemptCount?.let { appendLine("workManagerRunAttemptCount=$it") }
            ts(input.workManagerNextScheduleAt)?.let { appendLine("workManagerNextScheduleAt=$it") }
            input.workManagerStopReason?.let { appendLine("workManagerStopReason=$it") }
            input.onceWorkManagerStatus?.let { appendLine("onceWorkManagerStatus=$it") }
            appendLine("notificationPermissionStatus=${input.notificationPermissionStatus}")

            appendLine("## CONFIG")
            input.roomSchemaVersion?.let { appendLine("roomSchemaVersion=$it") }
            input.retentionDays?.let { appendLine("retentionDays=$it") }
            input.refreshIntervalMinutes?.let { appendLine("refreshIntervalMinutes=$it") }
            input.statusNotificationEnabled?.let { appendLine("statusNotificationEnabled=$it") }
            input.quotaAlertsEnabled?.let { appendLine("quotaAlertsEnabled=$it") }
            input.alertThresholds?.let { appendLine("alertThresholds=$it") }
            if (input.accountAlertConfigs.isNotEmpty()) {
                appendLine("accountAlertConfigs:")
                input.accountAlertConfigs.forEach { config ->
                    appendLine(
                        "  ${config.providerId}/${config.accountIdHash}=" +
                            "[${config.enabledWindowIds.joinToString(",")}]",
                    )
                }
            }
            input.widgetCount?.let { appendLine("widgetCount=$it") }

            appendLine("## ACCOUNTS")
            input.accountSummaries.forEach { summary ->
                val attempt = if (summary.lastAttemptStatus == "none") {
                    "none"
                } else {
                    val ageText = age(summary.lastAttemptAt)
                    val parts = listOfNotNull(summary.lastAttemptErrorCode, ageText).joinToString(", ")
                    "${summary.lastAttemptStatus}($parts)"
                }
                appendLine(
                    "${summary.providerId}/${summary.accountIdHash} status=${summary.status} " +
                        "lastAttempt=$attempt lastSuccess=${age(summary.lastSuccessfulRefreshAt) ?: "unavailable"} " +
                        "snapshot=${age(summary.latestSnapshotAt) ?: "missing"}",
                )
            }

            appendLine("## CURRENT ACCOUNT")
            appendLine("providerId=${input.providerId}")
            appendLine("accountDiagnosticId=${input.accountDiagnosticId}")
            appendLine("currentAccountSelectionStatus=${input.currentAccountSelectionStatus}")
            input.accountStatus?.let { appendLine("accountStatus=$it") }
            input.accountCount?.let { appendLine("accountCount=$it") }
            input.consecutiveFailures?.let { appendLine("consecutiveFailures=$it") }
            input.sessionEnvelopeStatus?.let { appendLine("sessionEnvelopeStatus=$it") }
            input.sessionProviderAccountIdStatus?.let { appendLine("sessionProviderAccountIdStatus=$it") }
            appendLine("currentState=${input.currentState}")
            input.latestSnapshotStatus?.let { appendLine("latestSnapshotStatus=$it") }
            input.latestSnapshotSource?.let { appendLine("latestSnapshotSource=$it") }
            ts(input.latestSnapshotFetchedAt)?.let { appendLine("latestSnapshotFetchedAt=$it") }
            input.latestSnapshotDigestStatus?.let { appendLine("latestSnapshotDigestStatus=$it") }
            appendLine("lastSuccessfulRefreshAt=${ts(input.lastSuccessfulRefreshAt) ?: input.lastSuccessfulRefreshAt}")
            appendLine("lastAttemptStatus=${input.lastAttemptStatus}")
            input.lastAttemptTrigger?.let { appendLine("lastAttemptTrigger=$it") }
            ts(input.lastAttemptStartedAt)?.let { appendLine("lastAttemptStartedAt=$it") }
            ts(input.lastAttemptFinishedAt)?.let { appendLine("lastAttemptFinishedAt=$it") }
            input.safeErrorCode?.let { appendLine("safeErrorCode=$it") }
            input.httpStatus?.let { appendLine("httpStatus=$it") }
            input.retryable?.let { appendLine("retryable=$it") }
            input.userActionRequired?.let { appendLine("userActionRequired=$it") }
            input.diagnosticsDigest?.let { appendLine("diagnosticsDigest=$it") }
            input.deviceCodeLoginStatus?.let { appendLine("deviceCodeLoginStatus=$it") }
            input.deviceCodeLoginAttemptId?.let { appendLine("deviceCodeLoginAttemptId=$it") }
            input.deviceCodeLoginSafeErrorCode?.let { appendLine("deviceCodeLoginSafeErrorCode=$it") }
            input.deviceCodeLoginVerificationUriStatus?.let { appendLine("deviceCodeLoginVerificationUriStatus=$it") }
            input.deviceCodeLoginPollIntervalSeconds?.let { appendLine("deviceCodeLoginPollIntervalSeconds=$it") }
            input.deviceCodeLoginExpiresAt?.let { appendLine("deviceCodeLoginExpiresAt=$it") }
            input.unsafeDetails?.takeIf { it.isNotBlank() }?.let { appendLine("details=[REDACTED]") }
        }.trim()

        return SettingsDiagnosticsCopyModel(text = DiagnosticsRedactor.redact(rawDiagnostics))
    }

    companion object {
        fun factory(
            accountListUseCase: AccountListUseCase = NoopAccountListUseCase,
            retentionPreferenceStore: RetentionPreferenceStore,
            notificationPreferenceStore: NotificationPreferenceStore = InMemoryNotificationPreferenceStore(),
            backgroundRefreshStatusReader: SettingsBackgroundRefreshStatusReader =
                InMemoryBackgroundRefreshStatusReader(),
            diagnosticsReader: SettingsDiagnosticsReader = DefaultSettingsDiagnosticsReader,
            quotaHistoryClearUseCase: QuotaHistoryClearUseCase = NoopQuotaHistoryClearUseCase,
            appUpdateChecker: AppUpdateCheckUseCase = NoopAppUpdateCheckUseCase,
            appUpdateDownloader: AppUpdateDownloadUseCase = NoopAppUpdateDownloadUseCase,
            backgroundRefreshScheduler: BackgroundRefreshScheduler = NoopBackgroundRefreshScheduler,
            currentVersionName: String = BuildConfig.VERSION_NAME,
            notificationWindowChoicesLoader: NotificationWindowChoicesLoader =
                NotificationWindowChoicesLoader { _, _ -> emptyList() },
            currencyPreferenceStore: CurrencyPreferenceStore = NoopCurrencyPreferenceStore,
            appearancePreferenceStore: AppearancePreferenceStore = NoopAppearancePreferenceStore,
            updatePreferenceStore: UpdatePreferenceStore = NoopUpdatePreferenceStore,
            updateCheckScheduler: UpdateCheckScheduler = NoopUpdateCheckScheduler,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SettingsViewModel(
                        accountListUseCase = accountListUseCase,
                        retentionPreferenceStore = retentionPreferenceStore,
                        notificationPreferenceStore = notificationPreferenceStore,
                        backgroundRefreshStatusReader = backgroundRefreshStatusReader,
                        diagnosticsReader = diagnosticsReader,
                        quotaHistoryClearUseCase = quotaHistoryClearUseCase,
                        appUpdateChecker = appUpdateChecker,
                        appUpdateDownloader = appUpdateDownloader,
                        backgroundRefreshScheduler = backgroundRefreshScheduler,
                        currentVersionName = currentVersionName,
                        notificationWindowChoicesLoader = notificationWindowChoicesLoader,
                        currencyPreferenceStore = currencyPreferenceStore,
                        appearancePreferenceStore = appearancePreferenceStore,
                        updatePreferenceStore = updatePreferenceStore,
                        updateCheckScheduler = updateCheckScheduler,
                    ) as T
            }
    }

    private fun update(transform: (SettingsUiState) -> SettingsUiState) {
        _uiState.value = transform(_uiState.value)
    }

    private fun normalizedThresholds(caution: Int, warning: Int): SettingsAlertThresholdsUi {
        val safeCaution = caution.coerceIn(CAUTION_MIN, CAUTION_MAX)
        val safeWarning = warning.coerceIn(WARNING_MIN, minOf(WARNING_MAX, safeCaution - 1))
        return SettingsAlertThresholdsUi(caution = safeCaution, warning = safeWarning)
    }

    private fun normalizedBalanceThresholds(caution: Double, warning: Double): Pair<Double, Double> {
        val safeCaution = caution.coerceIn(BALANCE_MIN, BALANCE_MAX)
        // Warning (urgent) must stay at or below caution.
        val safeWarning = warning.coerceIn(BALANCE_MIN, safeCaution)
        return safeCaution to safeWarning
    }

    private suspend fun persistNotificationPreferences(
        transform: (NotificationPreferences) -> NotificationPreferences,
    ) {
        notificationPreferenceWriteMutex.withLock {
            val nextPreferences = transform(latestNotificationPreferences)
            runCatching {
                notificationPreferenceStore.updateNotificationPreferences(nextPreferences)
            }.onSuccess {
                latestNotificationPreferences = nextPreferences
            }
        }
    }
}

private fun AppUpdateCheckResult.toSettingsUpdateUi(previous: SettingsUpdateUi): SettingsUpdateUi =
    when (this) {
        is AppUpdateCheckResult.UpdateAvailable -> previous.copy(
            isChecking = false,
            statusLabelResId = R.string.settings_update_status_update_available,
            pendingUpdate = update,
            availableUpdate = update,
        )
        AppUpdateCheckResult.UpToDate -> previous.copy(
            isChecking = false,
            statusLabelResId = R.string.settings_update_status_latest,
            pendingUpdate = null,
            availableUpdate = null,
        )
        AppUpdateCheckResult.NoRelease -> previous.copy(
            isChecking = false,
            statusLabelResId = R.string.settings_update_status_no_release,
        )
        AppUpdateCheckResult.NoApkAsset -> previous.copy(
            isChecking = false,
            statusLabelResId = R.string.settings_update_status_no_apk,
        )
        is AppUpdateCheckResult.Failure -> previous.copy(
            isChecking = false,
            statusLabelResId = R.string.settings_update_status_failed,
        )
    }

private fun AppUpdateDownloadResult.toSettingsUpdateUi(previous: SettingsUpdateUi): SettingsUpdateUi =
    when (this) {
        is AppUpdateDownloadResult.Enqueued -> previous.copy(
            isChecking = false,
            statusLabelResId = R.string.settings_update_status_downloading,
            pendingUpdate = null,
        )
        is AppUpdateDownloadResult.Failure -> previous.copy(
            isChecking = false,
            statusLabelResId = R.string.settings_update_status_download_failed,
            pendingUpdate = null,
        )
    }

private fun RetentionPreference.toSettingsRetentionOption(): SettingsRetentionOption =
    when (this) {
        RetentionPreference.SevenDays -> SettingsRetentionOption.SevenDays
        RetentionPreference.ThirtyDays -> SettingsRetentionOption.ThirtyDays
        RetentionPreference.NinetyDays -> SettingsRetentionOption.NinetyDays
        RetentionPreference.Forever -> SettingsRetentionOption.Forever
    }

private fun SettingsRetentionOption.toRetentionPreference(): RetentionPreference =
    when (this) {
        SettingsRetentionOption.SevenDays -> RetentionPreference.SevenDays
        SettingsRetentionOption.ThirtyDays -> RetentionPreference.ThirtyDays
        SettingsRetentionOption.NinetyDays -> RetentionPreference.NinetyDays
        SettingsRetentionOption.Forever -> RetentionPreference.Forever
    }

private fun NotificationWindowChoice.toSettingsNotificationWindowChoice(): SettingsNotificationWindowChoice =
    SettingsNotificationWindowChoice(
        windowId = windowId,
        labelResId = quotaWindowLabelRes(windowId.value, limitWindowSeconds),
    )

private fun SettingsNotificationAccountSelection.toNotificationAccountSelection(): NotificationAccountSelection? =
    when (this) {
        SettingsNotificationAccountSelection.FollowCurrent -> null
        is SettingsNotificationAccountSelection.Account -> NotificationAccountSelection(
            providerId = providerId,
            localAccountId = localAccountId,
        )
    }

private fun NotificationAccountSelection?.toSettingsNotificationAccountSelection(
    choices: List<SettingsNotificationAccountChoice>,
): SettingsNotificationAccountSelection {
    this ?: return SettingsNotificationAccountSelection.FollowCurrent
    return choices.map { it.selection }
        .filterIsInstance<SettingsNotificationAccountSelection.Account>()
        .firstOrNull { it.providerId == providerId && it.localAccountId == localAccountId }
        ?: SettingsNotificationAccountSelection.FollowCurrent
}

private fun SettingsNotificationAccountSelection.withDisplayNameFrom(
    choices: List<SettingsNotificationAccountChoice>,
): SettingsNotificationAccountSelection =
    when (this) {
        SettingsNotificationAccountSelection.FollowCurrent -> this
        is SettingsNotificationAccountSelection.Account ->
            choices.map { it.selection }
                .filterIsInstance<SettingsNotificationAccountSelection.Account>()
                .firstOrNull { it.providerId == providerId && it.localAccountId == localAccountId }
                ?: SettingsNotificationAccountSelection.FollowCurrent
    }

private fun NotificationPreferences.toSettingsAlertsUi(): SettingsAlertsUi =
    SettingsAlertsUi(
        statusNotificationEnabled = statusNotificationEnabled,
        quotaAlertsEnabled = quotaAlertsEnabled,
        accountErrorsEnabled = accountErrorsEnabled,
        thresholds = SettingsAlertThresholdsUi(
            caution = cautionThreshold,
            warning = warningThreshold,
            limit = limitThreshold,
        ),
        balanceCaution = balanceCautionThreshold,
        balanceWarning = balanceWarningThreshold,
    )

internal class InMemoryRetentionPreferenceStore : RetentionPreferenceStore {
    private var preference = RetentionPreference.ThirtyDays

    override suspend fun retentionPreference(): RetentionPreference = preference

    override suspend fun updateRetentionPreference(preference: RetentionPreference) {
        this.preference = preference
    }
}

internal class InMemoryNotificationPreferenceStore : NotificationPreferenceStore {
    private var preferences = NotificationPreferences()

    override suspend fun notificationPreferences(): NotificationPreferences = preferences

    override suspend fun updateNotificationPreferences(preferences: NotificationPreferences) {
        this.preferences = preferences
    }
}

internal class InMemoryBackgroundRefreshStatusReader : SettingsBackgroundRefreshStatusReader {
    override suspend fun latestBackgroundRefreshStatus(): SettingsBackgroundRefreshStatus =
        SettingsBackgroundRefreshStatus.NoAttempts
}

private const val CAUTION_MIN = 2
private const val CAUTION_MAX = 99
private const val WARNING_MIN = 1
private const val WARNING_MAX = 98
private const val BALANCE_MIN = 0.0
private const val BALANCE_MAX = 1000.0
