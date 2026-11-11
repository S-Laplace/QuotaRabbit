package com.alice.quotarabbit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alice.quotarabbit.core.i18n.AppLocaleController
import com.alice.quotarabbit.ui.navigation.QuotaRabbitNavHost
import com.alice.quotarabbit.ui.navigation.QuotaRabbitRoute
import com.alice.quotarabbit.ui.theme.QuotaRabbitSystemBars
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val quotaRabbitApp = application as QuotaRabbitApp
        val launchDestinationValue = intent?.getStringExtra(QuotaRabbitRoute.EXTRA_LAUNCH_DESTINATION)
        val startDestination = QuotaRabbitRoute.startRouteForLaunchDestination(launchDestinationValue)
        setContent {
            val themeMode by quotaRabbitApp.appearancePreferenceStore.themeMode
                .collectAsStateWithLifecycle(initialValue = quotaRabbitApp.initialThemeMode)
            val language by quotaRabbitApp.appearancePreferenceStore.languagePreference
                .collectAsStateWithLifecycle(initialValue = quotaRabbitApp.initialLanguagePreference)
            val localized = remember(language) {
                AppLocaleController.localizedContext(this@MainActivity, language)
            }
            // Never update LocaleManager from composition: its Activity recreation can race
            // with the DataStore value and repeatedly reset the language.
            SideEffect { AppLocaleController.applyProcessDefaultLocale(language) }
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides localized.resources.configuration,
            ) {
                QuotaRabbitTheme(themeMode = themeMode) {
                    QuotaRabbitSystemBars(window)
                    QuotaRabbitNavHost(
                        startDestination = startDestination,
                        launchDestinationValue = launchDestinationValue,
                        updatePreferenceStore = quotaRabbitApp.updatePreferenceStore,
                        deviceCodeLoginControllers = quotaRabbitApp.deviceCodeLoginControllers,
                        deviceCodeLoginNotifiers = quotaRabbitApp.deviceCodeLoginNotifiers,
                        deviceCodeLoginNotifier = quotaRabbitApp.deviceCodeLoginNotifier,
                        accountListUseCase = quotaRabbitApp.accountListUseCase,
                        accountDeleteUseCase = quotaRabbitApp.accountDeleteUseCase,
                        accountSwitchUseCase = quotaRabbitApp.accountSwitchUseCase,
                        accountRenameUseCase = quotaRabbitApp.accountRenameUseCase,
                        accountEditUseCase = quotaRabbitApp.accountEditUseCase,
                        accountQuotaAlertEvaluationRequester = quotaRabbitApp.accountQuotaAlertEvaluationRequester,
                        accountRefreshAllUseCase = quotaRabbitApp.accountRefreshAllUseCase,
                        homeCurrentQuotaStateLoader = quotaRabbitApp.homeCurrentQuotaStateLoader,
                        homeRefreshUseCase = quotaRabbitApp.homeRefreshUseCase,
                        homeTrendHistoryLoader = quotaRabbitApp.homeTrendHistoryLoader,
                        retentionPreferenceStore = quotaRabbitApp.retentionPreferenceStore,
                        notificationPreferenceStore = quotaRabbitApp.notificationPreferenceStore,
                        backgroundRefreshStatusReader = quotaRabbitApp.backgroundRefreshStatusReader,
                        settingsDiagnosticsReader = quotaRabbitApp.settingsDiagnosticsReader,
                        quotaHistoryClearUseCase = quotaRabbitApp.quotaHistoryClearUseCase,
                        appUpdateChecker = quotaRabbitApp.appUpdateChecker,
                        appUpdateDownloader = quotaRabbitApp.appUpdateDownloader,
                        apiKeyLoginUseCase = quotaRabbitApp.apiKeyLoginUseCase,
                        currencyPreferenceReader = quotaRabbitApp.currencyPreferenceReader,
                        exchangeRateReader = quotaRabbitApp.exchangeRateReader,
                        notificationWindowChoicesLoader = quotaRabbitApp.notificationWindowChoicesLoader,
                        currencyPreferenceStore = quotaRabbitApp.currencyPreferenceStore,
                        appearancePreferenceStore = quotaRabbitApp.appearancePreferenceStore,
                    )
                }
            }
        }
    }
}
