package com.alice.quotarabbit

import android.app.Application
import androidx.work.Configuration
import com.alice.quotarabbit.BuildConfig
import com.alice.quotarabbit.app.AppContainer
import com.alice.quotarabbit.core.i18n.AppLocaleController
import com.alice.quotarabbit.domain.account.AccountDeleteUseCase
import com.alice.quotarabbit.domain.account.AccountListUseCase
import com.alice.quotarabbit.domain.account.AccountRenameUseCase
import com.alice.quotarabbit.domain.account.AccountEditUseCase
import com.alice.quotarabbit.domain.account.AccountSwitchUseCase
import com.alice.quotarabbit.data.currency.ExchangeRateReader
import com.alice.quotarabbit.domain.auth.ApiKeyLoginUseCase
import com.alice.quotarabbit.domain.auth.SessionLoginUseCase
import com.alice.quotarabbit.domain.currency.CurrencyPreferenceReader
import com.alice.quotarabbit.domain.currency.CurrencyPreferenceStore
import com.alice.quotarabbit.domain.auth.DeviceCodeLoginController
import com.alice.quotarabbit.domain.auth.DeviceCodeLoginNotifier
import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.settings.DEFAULT_REFRESH_INTERVAL_MINUTES
import com.alice.quotarabbit.domain.settings.NotificationPreferenceStore
import com.alice.quotarabbit.domain.settings.PrimaryQuotaWindowPreferenceStore
import com.alice.quotarabbit.domain.settings.QuotaHistoryClearUseCase
import com.alice.quotarabbit.domain.settings.RetentionPreferenceStore
import com.alice.quotarabbit.domain.theme.AppearancePreferenceStore
import com.alice.quotarabbit.domain.settings.LanguagePreference
import com.alice.quotarabbit.domain.theme.ThemeMode
import com.alice.quotarabbit.domain.update.AppUpdateCheckUseCase
import com.alice.quotarabbit.domain.update.AppUpdateDownloadUseCase
import com.alice.quotarabbit.domain.update.AppUpdateNotifier
import com.alice.quotarabbit.domain.update.UpdatePreferenceStore
import com.alice.quotarabbit.refresh.ExchangeRateRefresher
import com.alice.quotarabbit.refresh.QuotaRefreshDependenciesProvider
import com.alice.quotarabbit.refresh.RefreshCoordinator
import com.alice.quotarabbit.ui.home.HomeCurrentQuotaStateLoader
import com.alice.quotarabbit.ui.home.HomeRefreshUseCase
import com.alice.quotarabbit.ui.home.HomeTrendHistoryLoader
import com.alice.quotarabbit.refresh.RefreshWorkScheduler
import com.alice.quotarabbit.app.NotificationWindowChoicesLoader
import com.alice.quotarabbit.ui.account.AccountQuotaAlertEvaluationRequester
import com.alice.quotarabbit.ui.settings.SettingsBackgroundRefreshStatusReader
import com.alice.quotarabbit.ui.settings.SettingsDiagnosticsReader
import com.alice.quotarabbit.update.UpdateCheckDependenciesProvider
import com.alice.quotarabbit.update.UpdateCheckWorkScheduler
import com.alice.quotarabbit.widget.WidgetQuotaStateLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class QuotaRabbitApp :
    Application(),
    Configuration.Provider,
    QuotaRefreshDependenciesProvider,
    UpdateCheckDependenciesProvider {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val appContainer: AppContainer by lazy {
        AppContainer.create(this)
    }

    override val workManagerConfiguration: Configuration =
        Configuration.Builder().build()

    override val refreshCoordinator: RefreshCoordinator
        get() = appContainer.refreshCoordinator

    override val exchangeRateRefresher: ExchangeRateRefresher
        get() = appContainer.exchangeRateRefresher

    val accountListUseCase: AccountListUseCase
        get() = appContainer.accountListUseCase

    val accountDeleteUseCase: AccountDeleteUseCase
        get() = appContainer.accountDeleteUseCase

    val accountSwitchUseCase: AccountSwitchUseCase
        get() = appContainer.accountSwitchUseCase

    val accountRenameUseCase: AccountRenameUseCase
        get() = appContainer.accountRenameUseCase

    val accountEditUseCase: AccountEditUseCase
        get() = appContainer.accountEditUseCase

    val accountRefreshAllUseCase: com.alice.quotarabbit.ui.account.AccountRefreshAllUseCase
        get() = appContainer.accountRefreshAllUseCase

    val accountQuotaAlertEvaluationRequester: AccountQuotaAlertEvaluationRequester
        get() = appContainer.accountQuotaAlertEvaluationRequester

    val homeCurrentQuotaStateLoader: HomeCurrentQuotaStateLoader
        get() = appContainer.homeCurrentQuotaStateLoader

    val homeRefreshUseCase: HomeRefreshUseCase
        get() = appContainer.homeRefreshUseCase

    val deviceCodeLoginController: DeviceCodeLoginController
        get() = appContainer.deviceCodeLoginController

    val deviceCodeLoginControllers: Map<ProviderId, DeviceCodeLoginController>
        get() = appContainer.deviceCodeLoginControllers

    val deviceCodeLoginNotifiers: Map<ProviderId, DeviceCodeLoginNotifier>
        get() = appContainer.deviceCodeLoginNotifiers

    val deviceCodeLoginNotifier: DeviceCodeLoginNotifier
        get() = appContainer.deviceCodeLoginNotifier

    val retentionPreferenceStore: RetentionPreferenceStore
        get() = appContainer.retentionPreferences

    val notificationPreferenceStore: NotificationPreferenceStore
        get() = appContainer.notificationPreferences

    val primaryQuotaWindowPreferenceStore: PrimaryQuotaWindowPreferenceStore
        get() = appContainer.primaryQuotaWindowPreferences

    val quotaHistoryClearUseCase: QuotaHistoryClearUseCase
        get() = appContainer.quotaHistoryClearUseCase

    val homeTrendHistoryLoader: HomeTrendHistoryLoader
        get() = appContainer.homeTrendHistoryLoader

    val backgroundRefreshStatusReader: SettingsBackgroundRefreshStatusReader
        get() = appContainer.backgroundRefreshStatusReader

    val settingsDiagnosticsReader: SettingsDiagnosticsReader
        get() = appContainer.settingsDiagnosticsReader

    val appUpdateChecker: AppUpdateCheckUseCase
        get() = appContainer.appUpdateChecker

    val appUpdateDownloader: AppUpdateDownloadUseCase
        get() = appContainer.appUpdateDownloader

    override val appUpdateCheck: AppUpdateCheckUseCase
        get() = appContainer.appUpdateChecker

    override val updatePreferenceStore: UpdatePreferenceStore
        get() = appContainer.updatePreferenceStore

    override val appUpdateNotifier: AppUpdateNotifier
        get() = appContainer.appUpdateNotifier

    override val currentVersionName: String
        get() = BuildConfig.VERSION_NAME

    val widgetQuotaStateLoader: WidgetQuotaStateLoader
        get() = appContainer.widgetQuotaStateLoader

    val apiKeyLoginUseCase: SessionLoginUseCase
        get() = appContainer.apiKeyLoginUseCase

    val currencyPreferenceReader: CurrencyPreferenceReader
        get() = appContainer.currencyPreferences

    val currencyPreferenceStore: CurrencyPreferenceStore
        get() = appContainer.currencyPreferences

    val exchangeRateReader: ExchangeRateReader
        get() = appContainer.exchangeRateRepository

    val notificationWindowChoicesLoader: NotificationWindowChoicesLoader
        get() = appContainer.notificationWindowChoicesLoader

    val appearancePreferenceStore: AppearancePreferenceStore
        get() = appContainer.appearancePreferences

    val initialThemeMode: ThemeMode
        get() = appContainer.initialThemeMode

    val initialLanguagePreference: LanguagePreference
        get() = appContainer.initialLanguagePreference

    override fun onCreate() {
        super.onCreate()
        AppLocaleController.apply(this, initialLanguagePreference)
        appContainer.startupMaintenance.start(applicationScope)
        val scheduler = RefreshWorkScheduler.from(this)
        registerRefreshWork(scheduler)
        // Re-apply the user's configured cadence (15/30 min, or Manual = cancel) once prefs are read.
        applicationScope.launch {
            val minutes = runCatching {
                appContainer.notificationPreferences.notificationPreferences().backgroundRefreshIntervalMinutes
            }.getOrDefault(DEFAULT_REFRESH_INTERVAL_MINUTES)
            scheduler.applyIntervalMinutes(minutes)
        }
        val updateScheduler = UpdateCheckWorkScheduler.from(this)
        applicationScope.launch {
            if (!com.alice.quotarabbit.domain.update.QuotaRabbitRepository.isConfigured) {
                // Clear cached release links for unconfigured repositories without resetting the user's toggle choices.
                runCatching { appContainer.updatePreferenceStore.setAvailableUpdate(null) }
                runCatching { appContainer.updatePreferenceStore.setLastNotifiedVersion(null) }
            }
            val autoCheck = runCatching {
                appContainer.updatePreferenceStore.preferences().autoCheckEnabled
            }.getOrDefault(true)
            updateScheduler.setAutoCheckEnabled(autoCheck)
        }
    }

    override suspend fun activeQuotaRefreshAccounts(): List<ProviderAccount> =
        appContainer.activeQuotaRefreshAccounts()

    companion object {
        fun registerRefreshWork(scheduler: RefreshWorkScheduler) {
            scheduler.schedulePeriodicRefresh()
        }
    }
}
