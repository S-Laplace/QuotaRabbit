package com.alice.quotarabbit.app

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.alice.quotarabbit.BuildConfig
import com.alice.quotarabbit.R
import com.alice.quotarabbit.core.network.ProviderHttpClient
import com.alice.quotarabbit.data.local.db.QuotaRabbitDatabase
import com.alice.quotarabbit.data.local.db.MIGRATION_1_2
import com.alice.quotarabbit.data.local.db.MIGRATION_2_3
import com.alice.quotarabbit.data.preferences.CurrentAccountPreferences
import com.alice.quotarabbit.data.preferences.CurrentAccountReader
import com.alice.quotarabbit.data.preferences.NotificationPreferencesDataStore
import com.alice.quotarabbit.data.preferences.PrimaryQuotaWindowPreferences
import com.alice.quotarabbit.data.preferences.RetentionPreferences
import com.alice.quotarabbit.data.preferences.UpdatePreferencesDataStore
import com.alice.quotarabbit.data.repository.AccountDeletionRepository
import com.alice.quotarabbit.data.repository.AccountListRepository
import com.alice.quotarabbit.data.repository.AccountMutationRepository
import com.alice.quotarabbit.data.repository.GitHubReleaseAppUpdateChecker
import com.alice.quotarabbit.data.repository.QuotaHistoryClearRepository
import com.alice.quotarabbit.data.repository.RetentionCleanupRepository
import com.alice.quotarabbit.data.repository.RoomNotificationAlertStateStore
import com.alice.quotarabbit.data.repository.RoomCodexSessionImportPersistence
import com.alice.quotarabbit.data.repository.RoomQuotaSnapshotStore
import com.alice.quotarabbit.data.repository.RoomRefreshAccountStatusStore
import com.alice.quotarabbit.data.repository.RoomRefreshAttemptStore
import com.alice.quotarabbit.data.secure.AesGcmPayloadCipher
import com.alice.quotarabbit.data.secure.AndroidKeystoreSecretKeyProvider
import com.alice.quotarabbit.data.secure.FileSecureSessionStore
import com.alice.quotarabbit.data.secure.PayloadCipher
import com.alice.quotarabbit.data.secure.ProviderSessionEnvelope
import com.alice.quotarabbit.domain.account.AccountDeleteUseCase
import com.alice.quotarabbit.domain.account.AccountListUseCase
import com.alice.quotarabbit.domain.account.AccountRenameUseCase
import com.alice.quotarabbit.domain.account.AccountEditUseCase
import com.alice.quotarabbit.domain.account.AccountSwitchUseCase
import com.alice.quotarabbit.domain.account.AccountSwitchRefreshRequester
import com.alice.quotarabbit.domain.auth.ApiKeyLoginUseCase
import com.alice.quotarabbit.domain.auth.SessionLoginUseCase
import com.alice.quotarabbit.domain.auth.DeviceCodeLoginController
import com.alice.quotarabbit.domain.auth.DeviceCodeLoginDiagnosticsReader
import com.alice.quotarabbit.domain.auth.DeviceCodeLoginNotifier
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.domain.model.ProviderAccountId
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.model.RefreshAttemptId
import com.alice.quotarabbit.domain.quota.CurrentQuotaStateFactory
import com.alice.quotarabbit.domain.refresh.RefreshTrigger
import com.alice.quotarabbit.domain.settings.NotificationPreferenceStore
import com.alice.quotarabbit.domain.settings.PrimaryQuotaWindowPreferenceStore
import com.alice.quotarabbit.domain.settings.QuotaHistoryClearUseCase
import com.alice.quotarabbit.domain.update.AppUpdateCheckUseCase
import com.alice.quotarabbit.domain.update.AppUpdateDownloadUseCase
import com.alice.quotarabbit.domain.update.AppUpdateNotifier
import com.alice.quotarabbit.domain.update.UpdatePreferenceStore
import com.alice.quotarabbit.notification.AndroidAppUpdateNotifier
import com.alice.quotarabbit.notification.AndroidNotificationRequestOptionsReader
import com.alice.quotarabbit.notification.AndroidDeviceCodeLoginNotifier
import com.alice.quotarabbit.notification.AndroidNotificationSink
import com.alice.quotarabbit.notification.AccountErrorEventReader
import com.alice.quotarabbit.notification.AccountErrorPolicy
import com.alice.quotarabbit.notification.CurrentQuotaNotificationPublisher
import com.alice.quotarabbit.notification.NotificationPreferenceAlertThresholdsReader
import com.alice.quotarabbit.notification.NotificationPreferenceQuotaAlertWindowReader
import com.alice.quotarabbit.providers.SessionImportRouter
import com.alice.quotarabbit.providers.ProviderRegistry
import com.alice.quotarabbit.providers.common.auth.OAuthTokenClient
import com.alice.quotarabbit.providers.codex.CodexRefreshProvider
import com.alice.quotarabbit.providers.codex.CodexSessionCipher
import com.alice.quotarabbit.providers.codex.CodexTokenRefresh
import com.alice.quotarabbit.providers.codex.CodexUsageFetcher
import com.alice.quotarabbit.providers.codex.CodexRenewalFetcher
import com.alice.quotarabbit.providers.codex.network.CodexSubscriptionRenewalClient
import com.alice.quotarabbit.providers.codex.auth.CodexSessionImporter
import com.alice.quotarabbit.providers.codex.auth.CodexDeviceCodeClient
import com.alice.quotarabbit.providers.codex.auth.CodexDeviceCodeLoginController
import com.alice.quotarabbit.providers.codex.auth.CodexDeviceCodeLoginUseCase
import com.alice.quotarabbit.providers.codex.auth.CodexOAuthTokenExchanger
import com.alice.quotarabbit.providers.codex.auth.CodexTokenRefresher
import com.alice.quotarabbit.providers.codex.auth.DeviceCodeChallenge
import com.alice.quotarabbit.providers.codex.auth.DeviceCodeLoginAttemptId
import com.alice.quotarabbit.providers.codex.mapper.CodexUsageMapper
import com.alice.quotarabbit.providers.codex.network.CodexUsageClient
import com.alice.quotarabbit.providers.codex.session.AndroidKeystoreCodexSessionCipher
import com.alice.quotarabbit.providers.codex.session.CodexSessionPayload
import com.alice.quotarabbit.providers.deepseek.DeepSeekRefreshProvider
import com.alice.quotarabbit.providers.deepseek.auth.DeepSeekSessionImporter
import com.alice.quotarabbit.providers.deepseek.network.DeepSeekBalanceClient
import com.alice.quotarabbit.providers.zai.ZaiRefreshProvider
import com.alice.quotarabbit.providers.zai.auth.ZaiSessionImporter
import com.alice.quotarabbit.providers.zai.network.ZaiQuotaClient
import com.alice.quotarabbit.providers.minimax.MiniMaxRefreshProvider
import com.alice.quotarabbit.providers.minimax.auth.MiniMaxSessionImporter
import com.alice.quotarabbit.providers.minimax.network.MiniMaxUsageClient
import com.alice.quotarabbit.providers.cursor.CursorRefreshProvider
import com.alice.quotarabbit.providers.cursor.auth.CursorSessionImporter
import com.alice.quotarabbit.providers.cursor.network.CursorUsageClient
import com.alice.quotarabbit.providers.kimi.KimiRefreshProvider
import com.alice.quotarabbit.providers.kimi.auth.KimiSessionImporter
import com.alice.quotarabbit.providers.kimi.network.KimiQuotaClient
import com.alice.quotarabbit.providers.kimicode.KimiCodeRefreshProvider
import com.alice.quotarabbit.providers.kimicode.auth.KimiCodeSessionImporter
import com.alice.quotarabbit.providers.kimicode.network.KimiCodeUsageClient
import com.alice.quotarabbit.providers.zaibalance.ZaiBalanceRefreshProvider
import com.alice.quotarabbit.providers.zaibalance.auth.ZaiBalanceSessionImporter
import com.alice.quotarabbit.providers.zaibalance.network.ZaiBalanceClient
import com.alice.quotarabbit.providers.claude.ClaudeRefreshProvider
import com.alice.quotarabbit.providers.claude.auth.ClaudeSessionImporter
import com.alice.quotarabbit.providers.claude.network.ClaudeUsageClient
import com.alice.quotarabbit.providers.antigravity.AntigravityRefreshProvider
import com.alice.quotarabbit.providers.antigravity.auth.AntigravitySessionImporter
import com.alice.quotarabbit.providers.antigravity.network.AntigravityQuotaClient
import com.alice.quotarabbit.providers.grok.GrokRefreshProvider
import com.alice.quotarabbit.providers.grok.GrokSessionCipher
import com.alice.quotarabbit.providers.grok.GrokBillingFetcher
import com.alice.quotarabbit.providers.grok.GrokTokenRefresh
import com.alice.quotarabbit.providers.grok.auth.GrokDeviceCodeLoginAttemptId
import com.alice.quotarabbit.providers.grok.auth.GrokDeviceCodeLoginController
import com.alice.quotarabbit.providers.grok.auth.GrokLoginIdentity
import com.alice.quotarabbit.providers.grok.auth.GrokOAuthToken
import com.alice.quotarabbit.providers.grok.auth.GrokSessionImporter
import com.alice.quotarabbit.providers.grok.auth.GrokTokenRefresher
import com.alice.quotarabbit.providers.grok.network.GrokBillingClient
import com.alice.quotarabbit.providers.grok.network.GrokDeviceCodeChallenge
import com.alice.quotarabbit.providers.grok.network.GrokDeviceCodeClient
import com.alice.quotarabbit.providers.grok.network.GrokOAuthDiscoveryClient
import com.alice.quotarabbit.providers.grok.session.GrokSessionPayload
import com.alice.quotarabbit.data.currency.ExchangeRateClient
import com.alice.quotarabbit.data.currency.ExchangeRateRepository
import com.alice.quotarabbit.data.preferences.CurrencyPreferencesDataStore
import com.alice.quotarabbit.refresh.AttemptIdProvider
import com.alice.quotarabbit.refresh.CompositeCurrentQuotaStatePublisher
import com.alice.quotarabbit.refresh.CompositeRefreshProvider
import com.alice.quotarabbit.refresh.CurrentQuotaStatePublisher
import com.alice.quotarabbit.refresh.ExchangeRateRefresher
import com.alice.quotarabbit.refresh.MultiAccountRefreshRunner
import com.alice.quotarabbit.refresh.RefreshCoordinator
import com.alice.quotarabbit.ui.account.AccountRefreshAllUseCase
import com.alice.quotarabbit.refresh.RefreshProvider
import com.alice.quotarabbit.ui.account.AccountQuotaAlertEvaluationRequester
import com.alice.quotarabbit.ui.home.HomeCurrentQuotaStateLoader
import com.alice.quotarabbit.ui.home.HomeRefreshUseCase
import com.alice.quotarabbit.ui.settings.SettingsBackgroundRefreshStatusReader
import com.alice.quotarabbit.ui.settings.SettingsDiagnosticsReader
import com.alice.quotarabbit.widget.WidgetDeletedAccountStateCleaner
import com.alice.quotarabbit.widget.WidgetQuotaStateUpdater
import com.alice.quotarabbit.widget.WidgetQuotaStateLoader
import com.alice.quotarabbit.ui.home.HomeTrendHistoryLoader
import java.time.Clock
import java.time.Instant
import java.util.UUID
import com.alice.quotarabbit.data.preferences.AppearancePreferences
import com.alice.quotarabbit.domain.theme.AppearancePreferenceStore
import com.alice.quotarabbit.domain.settings.LanguagePreference
import com.alice.quotarabbit.domain.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class AppContainer private constructor(
    val refreshCoordinator: RefreshCoordinator,
    val accountListUseCase: AccountListUseCase,
    val accountDeleteUseCase: AccountDeleteUseCase,
    val accountSwitchUseCase: AccountSwitchUseCase,
    val accountRenameUseCase: AccountRenameUseCase,
    val accountEditUseCase: AccountEditUseCase,
    val accountQuotaAlertEvaluationRequester: AccountQuotaAlertEvaluationRequester,
    val homeCurrentQuotaStateLoader: HomeCurrentQuotaStateLoader,
    val homeRefreshUseCase: HomeRefreshUseCase,
    val deviceCodeLoginController: DeviceCodeLoginController,
    /** Per-provider device-code login controllers; the legacy single property is the codex entry. */
    val deviceCodeLoginControllers: Map<ProviderId, DeviceCodeLoginController>,
    val deviceCodeLoginNotifiers: Map<ProviderId, DeviceCodeLoginNotifier>,
    val deviceCodeLoginNotifier: DeviceCodeLoginNotifier,
    val retentionPreferences: RetentionPreferences,
    val notificationPreferences: NotificationPreferenceStore,
    val primaryQuotaWindowPreferences: PrimaryQuotaWindowPreferenceStore,
    val quotaHistoryClearUseCase: QuotaHistoryClearUseCase,
    val homeTrendHistoryLoader: HomeTrendHistoryLoader,
    val backgroundRefreshStatusReader: SettingsBackgroundRefreshStatusReader,
    val settingsDiagnosticsReader: SettingsDiagnosticsReader,
    val appUpdateChecker: AppUpdateCheckUseCase,
    val appUpdateDownloader: AppUpdateDownloadUseCase,
    val updatePreferenceStore: UpdatePreferenceStore,
    val appUpdateNotifier: AppUpdateNotifier,
    val widgetQuotaStateLoader: WidgetQuotaStateLoader,
    val startupMaintenance: StartupMaintenance,
    val apiKeyLoginUseCase: SessionLoginUseCase,
    val appearancePreferences: AppearancePreferenceStore,
    val initialThemeMode: ThemeMode,
    val initialLanguagePreference: LanguagePreference,
    val currencyPreferences: CurrencyPreferencesDataStore,
    val exchangeRateRepository: ExchangeRateRepository,
    private val currentAccountStore: CurrentQuotaRefreshAccountStore,
    val exchangeRateRefresher: ExchangeRateRefresher,
    val notificationWindowChoicesLoader: NotificationWindowChoicesLoader,
) {
    suspend fun activeQuotaRefreshAccounts(): List<ProviderAccount> =
        currentAccountStore.activeAccounts()

    /**
     * Pull-to-refresh on the Account screen: refresh every manually-refreshable account in parallel,
     * including NeedsReauth ones, so a transient failure can be retried and self-heal on success.
     */
    val accountRefreshAllUseCase: AccountRefreshAllUseCase
        get() = AccountRefreshAllUseCase {
            MultiAccountRefreshRunner(
                refreshCoordinator = refreshCoordinator,
                exchangeRateRefresher = exchangeRateRefresher,
            ).refresh(
                accounts = currentAccountStore.manuallyRefreshableAccounts(),
                trigger = RefreshTrigger.Manual,
            )
        }

    companion object {
        fun create(context: Context): AppContainer {
            val appContext = context.applicationContext
            val database = Room.databaseBuilder(
                appContext,
                QuotaRabbitDatabase::class.java,
                QuotaRabbitDatabase.DATABASE_NAME,
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
            val currentAccountPreferences = CurrentAccountPreferences.create(
                file = appContext.preferencesDataStoreFile(PREFERENCES_FILE_NAME),
            )
            val appearancePreferences = AppearancePreferences.create(
                file = appContext.preferencesDataStoreFile(APPEARANCE_PREFERENCES_FILE_NAME),
            )
            // Preheat the first value synchronously so the first frame uses the correct theme.
            val initialThemeMode = runBlocking {
                appearancePreferences.themeMode.first()
            }
            val initialLanguagePreference = runBlocking {
                appearancePreferences.languagePreference.first()
            }
            val retentionPreferences = RetentionPreferences(currentAccountPreferences.dataStore)
            val notificationPreferences = NotificationPreferencesDataStore(currentAccountPreferences.dataStore)
            val primaryQuotaWindowPreferences = PrimaryQuotaWindowPreferences(currentAccountPreferences.dataStore)
            val updatePreferences = UpdatePreferencesDataStore.create(
                file = appContext.preferencesDataStoreFile(UPDATE_PREFERENCES_FILE_NAME),
            )
            val appUpdateNotifier = AndroidAppUpdateNotifier(
                notificationSink = AndroidNotificationSink(appContext),
            )
            val httpClient = ProviderHttpClient()
            val usageClient = CodexUsageClient(httpClient)
            val sessionStore = FileSecureSessionStore(
                directory = appContext.filesDir.resolve(SESSION_DIRECTORY_NAME),
            )
            val sessionCipher = AndroidKeystoreCodexSessionCipher()
            val payloadCipher: PayloadCipher = AesGcmPayloadCipher(AndroidKeystoreSecretKeyProvider())
            val widgetDeletedAccountStateCleaner = WidgetDeletedAccountStateCleaner(appContext)
            val clock = Clock.systemUTC()
            val currencyPreferences = CurrencyPreferencesDataStore(currentAccountPreferences.dataStore)
            val exchangeRateClient = ExchangeRateClient(httpClient)
            val exchangeRateRepository = ExchangeRateRepository(
                cache = currencyPreferences,
                fetch = { exchangeRateClient.fetchRates(clock.instant()) },
            )
            val defaultAccountDisplayName = appContext.getString(R.string.account_default_display_name)
            val currentQuotaStateFactory = CurrentQuotaStateFactory()
            val widgetQuotaStateRepository = WidgetQuotaStateRepository(
                providerAccountDao = database.providerAccountDao(),
                quotaSnapshotDao = database.quotaSnapshotDao(),
                refreshAttemptDao = database.refreshAttemptDao(),
                currentQuotaStateFactory = currentQuotaStateFactory,
                notificationPreferenceReader = notificationPreferences,
                currencyPreferenceReader = currencyPreferences,
                exchangeRateReader = exchangeRateRepository,
                clock = clock,
            )
            val currentQuotaStateRepository = CurrentQuotaStateRepository(
                currentAccountReader = currentAccountPreferences,
                providerAccountDao = database.providerAccountDao(),
                quotaSnapshotDao = database.quotaSnapshotDao(),
                refreshAttemptDao = database.refreshAttemptDao(),
                currentQuotaStateFactory = currentQuotaStateFactory,
                primaryQuotaWindowPreferenceReader = primaryQuotaWindowPreferences,
                notificationPreferenceReader = notificationPreferences,
                clock = clock,
                currencyPreferenceReader = currencyPreferences,
                exchangeRateReader = exchangeRateRepository,
            )
            val accountErrorPolicy = AccountErrorPolicy()

            val codexRefreshProvider = CodexRefreshProvider(
                sessionStore = sessionStore,
                sessionCipher = sessionCipher,
                tokenRefresh = CodexTokenRefresh(CodexTokenRefresher(httpClient)::refresh),
                usageFetcher = CodexUsageFetcher(usageClient::fetchUsage),
                renewalFetcher = CodexRenewalFetcher(CodexSubscriptionRenewalClient(httpClient)::fetchDates),
                clock = clock,
            )
            val deepseekBalanceClient = DeepSeekBalanceClient(httpClient)
            val deepseekRefreshProvider = DeepSeekRefreshProvider(
                client = deepseekBalanceClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val zaiQuotaClient = ZaiQuotaClient(httpClient)
            val zaiRefreshProvider = ZaiRefreshProvider(
                client = zaiQuotaClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val minimaxUsageClient = MiniMaxUsageClient(httpClient)
            val minimaxRefreshProvider = MiniMaxRefreshProvider(
                client = minimaxUsageClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val cursorUsageClient = CursorUsageClient(httpClient)
            val cursorRefreshProvider = CursorRefreshProvider(
                client = cursorUsageClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val kimiQuotaClient = KimiQuotaClient(httpClient)
            val kimiRefreshProvider = KimiRefreshProvider(
                client = kimiQuotaClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val kimiCodeUsageClient = KimiCodeUsageClient(httpClient)
            val kimiCodeRefreshProvider = KimiCodeRefreshProvider(
                client = kimiCodeUsageClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val zaiBalanceClient = ZaiBalanceClient(httpClient)
            val zaiBalanceRefreshProvider = ZaiBalanceRefreshProvider(
                client = zaiBalanceClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val claudeUsageClient = ClaudeUsageClient(httpClient)
            val claudeTokenClient = OAuthTokenClient(
                httpClient = httpClient,
                // Mirrors Hermes' proven exchange: claude.ai + JSON body + state + UA. The host is
                // claude.ai (not console.anthropic.com, whose /v1/oauth/token now 404s); the
                // console.anthropic.com/oauth/code/callback redirect_uri is just the registered
                // client identifier echoed back in the exchange, not a host we call.
                tokenEndpoint = "https://claude.ai/v1/oauth/token",
                clientId = "9d1c250a-e61b-44d9-88ed-5944d1962f5e",
                diagnosticsPrefix = "claude_oauth",
                useJsonBody = true,
                userAgent = "claude-cli/2.1.0 (external, cli)",
            )
            val claudeRefreshProvider = ClaudeRefreshProvider(
                client = claudeUsageClient,
                tokenClient = claudeTokenClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val antigravityQuotaClient = AntigravityQuotaClient(httpClient)
            val antigravityTokenClient = OAuthTokenClient(
                httpClient = httpClient,
                tokenEndpoint = "https://oauth2.googleapis.com/token",
                clientId = "1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com",
                clientSecret = BuildConfig.ANTIGRAVITY_OAUTH_CLIENT_SECRET,
                diagnosticsPrefix = "antigravity_oauth",
            )
            val antigravityRefreshProvider = AntigravityRefreshProvider(
                client = antigravityQuotaClient,
                tokenClient = antigravityTokenClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val grokSessionCipher = AesGcmGrokSessionCipher(payloadCipher)
            val grokBillingClient = GrokBillingClient(httpClient)
            // xAI rotates the refresh token on every grant: the refresher must keep its default
            // ProviderHttpClient.noRetry() client — a retrying shared client would silently burn
            // the retired refresh token when a refresh response is lost in transport.
            val grokTokenRefresher = GrokTokenRefresher()
            val grokRefreshProvider = GrokRefreshProvider(
                sessionStore = sessionStore,
                sessionCipher = grokSessionCipher,
                tokenRefresh = GrokTokenRefresh(grokTokenRefresher::refresh),
                billingFetcher = GrokBillingFetcher(grokBillingClient::fetchBilling),
                clock = clock,
            )
            val compositeRefreshProvider = CompositeRefreshProvider(
                providers = mapOf(
                    ProviderId("codex") to codexRefreshProvider,
                    ProviderId("deepseek") to deepseekRefreshProvider,
                    ProviderId("zai") to zaiRefreshProvider,
                    ProviderId("minimax") to minimaxRefreshProvider,
                    ProviderId("cursor") to cursorRefreshProvider,
                    ProviderId("kimi") to kimiRefreshProvider,
                    ProviderId("kimi_code") to kimiCodeRefreshProvider,
                    ProviderId("zai_balance") to zaiBalanceRefreshProvider,
                    ProviderId("claude") to claudeRefreshProvider,
                    ProviderId("antigravity") to antigravityRefreshProvider,
                    ProviderId("grok") to grokRefreshProvider,
                ),
            )
            val deepseekSessionImporter = DeepSeekSessionImporter(
                balanceClient = deepseekBalanceClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val minimaxSessionImporter = MiniMaxSessionImporter(
                usageClient = minimaxUsageClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val zaiSessionImporter = ZaiSessionImporter(
                client = zaiQuotaClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val cursorSessionImporter = CursorSessionImporter(
                usageClient = cursorUsageClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val kimiSessionImporter = KimiSessionImporter(
                client = kimiQuotaClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val kimiCodeSessionImporter = KimiCodeSessionImporter(
                usageClient = kimiCodeUsageClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val zaiBalanceSessionImporter = ZaiBalanceSessionImporter(
                client = zaiBalanceClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val claudeSessionImporter = ClaudeSessionImporter(
                tokenClient = claudeTokenClient,
                client = claudeUsageClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val antigravitySessionImporter = AntigravitySessionImporter(
                tokenClient = antigravityTokenClient,
                client = antigravityQuotaClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )

            return fromDatabase(
                appContext = appContext,
                database = database,
                currentAccountReader = currentAccountPreferences,
                currentAccountPreferences = currentAccountPreferences,
                retentionPreferences = retentionPreferences,
                notificationPreferences = notificationPreferences,
                primaryQuotaWindowPreferences = primaryQuotaWindowPreferences,
                sessionStore = sessionStore,
                sessionCipher = sessionCipher,
                refreshProvider = compositeRefreshProvider,
                usageClient = usageClient,
                deepseekSessionImporter = deepseekSessionImporter,
                minimaxSessionImporter = minimaxSessionImporter,
                zaiSessionImporter = zaiSessionImporter,
                cursorSessionImporter = cursorSessionImporter,
                kimiSessionImporter = kimiSessionImporter,
                kimiCodeSessionImporter = kimiCodeSessionImporter,
                zaiBalanceSessionImporter = zaiBalanceSessionImporter,
                claudeSessionImporter = claudeSessionImporter,
                antigravitySessionImporter = antigravitySessionImporter,
                deviceCodeLoginNotifier = AndroidDeviceCodeLoginNotifier(
                    notificationSink = AndroidNotificationSink(appContext),
                ),
                grokSessionCipher = grokSessionCipher,
                grokBillingClient = grokBillingClient,
                grokDeviceCodeLoginNotifier = AndroidDeviceCodeLoginNotifier(
                    notificationSink = AndroidNotificationSink(appContext),
                    providerDisplayName = ProviderRegistry.displayNameFor(ProviderRegistry.GROK),
                ),
                currentQuotaStatePublisher = CompositeCurrentQuotaStatePublisher(
                    listOf(
                        WidgetQuotaStateUpdater(
                            context = appContext,
                            notificationPreferenceReader = notificationPreferences,
                            widgetQuotaStateLoader = widgetQuotaStateRepository,
                        ),
                        CurrentQuotaNotificationPublisher(
                            notificationSink = AndroidNotificationSink(appContext),
                            alertStateStore = RoomNotificationAlertStateStore(database.alertStateDao()),
                            optionsReader = AndroidNotificationRequestOptionsReader(
                                context = appContext,
                                notificationPreferenceReader = notificationPreferences,
                            ),
                            alertThresholdsReader = NotificationPreferenceAlertThresholdsReader(
                                notificationPreferenceReader = notificationPreferences,
                            ),
                            alertWindowPreferenceReader = NotificationPreferenceQuotaAlertWindowReader(
                                notificationPreferenceReader = notificationPreferences,
                            ),
                            statusNotificationStateLoader = currentQuotaStateRepository,
                            accountErrorEventReader = AccountErrorEventReader { state ->
                                val account = state.account
                                val consecutiveFailureCount = if (account == null) {
                                    0
                                } else {
                                    database.refreshAttemptDao().countConsecutiveFailuresSinceLatestSuccess(
                                        providerId = account.providerId.value,
                                        localAccountId = account.localAccountId.value,
                                    )
                                }
                                accountErrorPolicy.evaluate(
                                    state = state,
                                    consecutiveFailureCount = consecutiveFailureCount,
                                )
                            },
                            clock = clock,
                            currencyPreferenceReader = currencyPreferences,
                            exchangeRateReader = exchangeRateRepository,
                        ),
                    ),
                ),
                currentQuotaStateRepository = currentQuotaStateRepository,
                accountListRepository = AccountListRepository(
                    providerAccountDao = database.providerAccountDao(),
                    quotaSnapshotDao = database.quotaSnapshotDao(),
                    currentAccountReader = currentAccountPreferences,
                ),
                accountDeletionRepository = AccountDeletionRepository(
                    database = database,
                    secureSessionStore = sessionStore,
                    currentAccountStore = currentAccountPreferences,
                    deletedAccountStateCleaner = widgetDeletedAccountStateCleaner,
                ),
                retentionCleanupRepository = RetentionCleanupRepository(database),
                quotaHistoryClearRepository = QuotaHistoryClearRepository(
                    database = database,
                    currentAccountReader = currentAccountPreferences,
                ),
                currentQuotaStateFactory = currentQuotaStateFactory,
                widgetQuotaStateRepository = widgetQuotaStateRepository,
                defaultAccountDisplayName = defaultAccountDisplayName,
                httpClient = httpClient,
                clock = clock,
                appearancePreferences = appearancePreferences,
                initialThemeMode = initialThemeMode,
                initialLanguagePreference = initialLanguagePreference,
                currencyPreferences = currencyPreferences,
                exchangeRateRepository = exchangeRateRepository,
                exchangeRateRefresher = ExchangeRateRefresher { exchangeRateRepository.refreshIfStale(clock.instant()) },
                updatePreferences = updatePreferences,
                appUpdateNotifier = appUpdateNotifier,
            )
        }

        private fun fromDatabase(
            appContext: Context,
            database: QuotaRabbitDatabase,
            currentAccountReader: CurrentAccountReader,
            currentAccountPreferences: CurrentAccountPreferences,
            retentionPreferences: RetentionPreferences,
            notificationPreferences: NotificationPreferencesDataStore,
            primaryQuotaWindowPreferences: PrimaryQuotaWindowPreferences,
            sessionStore: FileSecureSessionStore,
            sessionCipher: CodexSessionCipher,
            refreshProvider: RefreshProvider,
            usageClient: CodexUsageClient,
            deepseekSessionImporter: DeepSeekSessionImporter,
            minimaxSessionImporter: MiniMaxSessionImporter,
            zaiSessionImporter: ZaiSessionImporter,
            cursorSessionImporter: CursorSessionImporter,
            kimiSessionImporter: KimiSessionImporter,
            kimiCodeSessionImporter: KimiCodeSessionImporter,
            zaiBalanceSessionImporter: ZaiBalanceSessionImporter,
            claudeSessionImporter: ClaudeSessionImporter,
            antigravitySessionImporter: AntigravitySessionImporter,
            deviceCodeLoginNotifier: DeviceCodeLoginNotifier,
            grokSessionCipher: GrokSessionCipher,
            grokBillingClient: GrokBillingClient,
            grokDeviceCodeLoginNotifier: DeviceCodeLoginNotifier,
            currentQuotaStatePublisher: CurrentQuotaStatePublisher,
            currentQuotaStateRepository: CurrentQuotaStateRepository,
            accountListRepository: AccountListRepository,
            accountDeletionRepository: AccountDeletionRepository,
            retentionCleanupRepository: RetentionCleanupRepository,
            quotaHistoryClearRepository: QuotaHistoryClearRepository,
            currentQuotaStateFactory: CurrentQuotaStateFactory,
            widgetQuotaStateRepository: WidgetQuotaStateRepository,
            defaultAccountDisplayName: String,
            httpClient: ProviderHttpClient,
            clock: Clock,
            appearancePreferences: AppearancePreferenceStore,
            initialThemeMode: ThemeMode,
            initialLanguagePreference: LanguagePreference,
            currencyPreferences: CurrencyPreferencesDataStore,
            exchangeRateRepository: ExchangeRateRepository,
            exchangeRateRefresher: ExchangeRateRefresher,
            updatePreferences: UpdatePreferenceStore,
            appUpdateNotifier: AppUpdateNotifier,
        ): AppContainer {
            val sessionImporter = CodexSessionImporter(
                usageClient = CodexSessionImporter.UsageClient(usageClient::fetchUsage),
                mapper = CodexUsageMapper(),
                importPersistence = RoomCodexSessionImportPersistence(
                    database = database,
                    sessionStore = sessionStore,
                    currentAccountStore = currentAccountPreferences,
                ),
                sessionEnvelopeFactory = CodexSessionEnvelopeFactory(sessionCipher),
                localAccountIdProvider = CodexSessionImporter.LocalAccountIdProvider {
                    LocalAccountId("codex-${UUID.randomUUID()}")
                },
                defaultDisplayName = defaultAccountDisplayName,
                clock = clock,
            )
            // Grok mirrors Codex's two-phase device-code import; persistence reuses the same
            // Room reconcile-and-rollback path (reconciliation is by provider account id).
            val grokSessionImportPersistence = RoomCodexSessionImportPersistence(
                database = database,
                sessionStore = sessionStore,
                currentAccountStore = currentAccountPreferences,
            )
            val grokSessionImporter = GrokSessionImporter(
                billingClient = GrokSessionImporter.BillingClient(grokBillingClient::fetchBilling),
                importPersistence = GrokSessionImporter.ImportPersistence { account, envelope, snapshot ->
                    val committed = grokSessionImportPersistence.save(account, envelope, snapshot)
                    GrokSessionImporter.CommittedImport(
                        account = committed.account,
                        sessionEnvelope = committed.sessionEnvelope,
                        snapshot = committed.snapshot,
                    )
                },
                sessionEnvelopeFactory = GrokSessionEnvelopeFactory(grokSessionCipher),
                localAccountIdProvider = GrokSessionImporter.LocalAccountIdProvider {
                    LocalAccountId("grok-${UUID.randomUUID()}")
                },
                defaultDisplayName = defaultAccountDisplayName,
                clock = clock,
            )
            val sessionImportRouter = SessionImportRouter(
                importers = mapOf(
                    ProviderId("codex") to sessionImporter,
                    ProviderId("deepseek") to deepseekSessionImporter,
                    ProviderId("zai") to zaiSessionImporter,
                    ProviderId("minimax") to minimaxSessionImporter,
                    ProviderId("cursor") to cursorSessionImporter,
                    ProviderId("kimi") to kimiSessionImporter,
                    ProviderId("kimi_code") to kimiCodeSessionImporter,
                    ProviderId("zai_balance") to zaiBalanceSessionImporter,
                    ProviderId("claude") to claudeSessionImporter,
                    ProviderId("antigravity") to antigravitySessionImporter,
                    ProviderId("grok") to grokSessionImporter,
                ),
            )
            val sessionLoginUseCase = SessionLoginUseCase(
                importRouter = sessionImportRouter,
                database = database,
                accountDao = database.providerAccountDao(),
                snapshotDao = database.quotaSnapshotDao(),
                currentAccountStore = currentAccountPreferences,
                clock = clock,
            )
            val codexDeviceCodeClient = CodexDeviceCodeClient(httpClient)
            val codexDeviceCodeLoginUseCase = CodexDeviceCodeLoginUseCase(
                deviceCodeClient = object : CodexDeviceCodeLoginUseCase.DeviceCodeClient {
                    override suspend fun requestDeviceCode() =
                        codexDeviceCodeClient.requestDeviceCode()

                    override suspend fun pollAuthorization(challenge: DeviceCodeChallenge) =
                        codexDeviceCodeClient.pollAuthorization(challenge)
                },
                tokenExchanger = CodexDeviceCodeLoginUseCase.TokenExchanger(
                    CodexOAuthTokenExchanger(httpClient)::exchange,
                ),
                sessionImporter = object : CodexDeviceCodeLoginUseCase.SessionImporter {
                    override suspend fun prepareDeviceCodeSession(
                        session: CodexSessionPayload,
                    ): CodexSessionImporter.PrepareResult =
                        sessionImporter.prepareDeviceCodeSession(session)

                    override suspend fun commitPreparedDeviceCodeSession(
                        preparedImport: CodexSessionImporter.PreparedImport,
                    ): CodexSessionImporter.Result =
                        sessionImporter.commitPreparedDeviceCodeSession(preparedImport)
                },
                attemptIdProvider = {
                    DeviceCodeLoginAttemptId("device-${UUID.randomUUID()}")
                },
                clock = clock,
            )
            val grokOAuthDiscoveryClient = GrokOAuthDiscoveryClient(httpClient)
            val grokDeviceCodeClient = GrokDeviceCodeClient(httpClient)
            val grokDeviceCodeLoginController = GrokDeviceCodeLoginController(
                discoveryClient = GrokDeviceCodeLoginController.DiscoveryClient(
                    grokOAuthDiscoveryClient::fetchEndpoints,
                ),
                deviceCodeClient = object : GrokDeviceCodeLoginController.DeviceCodeClient {
                    override suspend fun requestDeviceCode(deviceAuthorizationEndpointUrl: String) =
                        grokDeviceCodeClient.requestDeviceCode(deviceAuthorizationEndpointUrl)

                    override suspend fun awaitAuthorization(
                        challenge: GrokDeviceCodeChallenge,
                        tokenEndpointUrl: String,
                    ) = grokDeviceCodeClient.awaitAuthorization(challenge, tokenEndpointUrl)
                },
                sessionImporter = object : GrokDeviceCodeLoginController.SessionImporter {
                    override suspend fun prepareDeviceCodeSession(
                        oauthToken: GrokOAuthToken,
                        identity: GrokLoginIdentity,
                        tokenEndpoint: String?,
                    ): GrokSessionImporter.PreparedImport =
                        grokSessionImporter.prepareDeviceCodeSession(oauthToken, identity, tokenEndpoint)

                    override suspend fun commitPreparedDeviceCodeSession(
                        preparedImport: GrokSessionImporter.PreparedImport,
                    ): GrokSessionImporter.Result =
                        grokSessionImporter.commitPreparedDeviceCodeSession(preparedImport)
                },
                attemptIdProvider = {
                    GrokDeviceCodeLoginAttemptId("device-${UUID.randomUUID()}")
                },
                clock = clock,
                onSaved = { saved ->
                    currentQuotaStatePublisher.publish(
                        currentQuotaStateFactory.create(
                            account = saved.account,
                            latestSnapshot = saved.snapshot,
                            latestAttempt = null,
                            now = clock.instant(),
                            primaryWindowId = primaryQuotaWindowPreferences.primaryQuotaWindowId(),
                        ),
                    )
                },
            )
            val currentQuotaRefreshAccountStore = CurrentQuotaRefreshAccountStore(
                currentAccountReader = currentAccountReader,
                providerAccountDao = database.providerAccountDao(),
            )
            val refreshCoordinator = RefreshCoordinator(
                provider = refreshProvider,
                snapshotStore = RoomQuotaSnapshotStore(database.quotaSnapshotDao()),
                attemptStore = RoomRefreshAttemptStore(database.refreshAttemptDao()),
                accountStatusStore = RoomRefreshAccountStatusStore(database.providerAccountDao()),
                attemptIdProvider = AttemptIdProvider {
                    RefreshAttemptId("periodic-${UUID.randomUUID()}")
                },
                currentQuotaStatePublisher = currentQuotaStatePublisher,
                primaryQuotaWindowPreferenceReader = primaryQuotaWindowPreferences,
                clock = clock,
            )
            val currentAccountStateRepublisher = CurrentAccountQuotaStateRepublisher(
                currentAccountReader = currentAccountPreferences,
                providerAccountDao = database.providerAccountDao(),
                quotaSnapshotDao = database.quotaSnapshotDao(),
                refreshAttemptDao = database.refreshAttemptDao(),
                currentQuotaStatePublisher = currentQuotaStatePublisher,
                currentQuotaStateFactory = currentQuotaStateFactory,
                primaryQuotaWindowPreferenceReader = primaryQuotaWindowPreferences,
                clock = clock,
            )
            val accountMutationRepository = AccountMutationRepository(
                providerAccountDao = database.providerAccountDao(),
                currentAccountStore = currentAccountPreferences,
                accountSwitchRefreshRequester = AccountSwitchRefreshRequester { account ->
                    try {
                        refreshCoordinator.refresh(
                            account = account,
                            trigger = RefreshTrigger.AccountSwitch,
                        )
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        // Account selection is already durable; a later refresh/app open can recover state.
                    }
                },
                currentAccountStateRepublisher = currentAccountStateRepublisher,
                clock = clock,
            )
            val homeRefreshUseCase = HomeRefreshCoordinatorUseCase(
                currentAccountStore = currentQuotaRefreshAccountStore,
                refreshCoordinator = refreshCoordinator,
                currentQuotaStateLoader = currentQuotaStateRepository,
                exchangeRateRefresher = exchangeRateRefresher,
            )
            val homeTrendHistoryRepository = HomeTrendHistoryRepository(
                currentAccountReader = currentAccountReader,
                providerAccountDao = database.providerAccountDao(),
                quotaSnapshotDao = database.quotaSnapshotDao(),
                clock = clock,
            )
            val backgroundRefreshStatusRepository = SettingsBackgroundRefreshStatusRepository(
                currentAccountReader = currentAccountReader,
                providerAccountDao = database.providerAccountDao(),
                refreshAttemptDao = database.refreshAttemptDao(),
            )
            val deviceCodeLoginController = CodexDeviceCodeLoginController(
                useCase = codexDeviceCodeLoginUseCase,
                onSaved = { saved ->
                    currentQuotaStatePublisher.publish(
                        currentQuotaStateFactory.create(
                            account = saved.account,
                            latestSnapshot = saved.snapshot,
                            latestAttempt = null,
                            now = clock.instant(),
                            primaryWindowId = primaryQuotaWindowPreferences.primaryQuotaWindowId(),
                        ),
                    )
                },
            )
            val deviceCodeLoginDiagnosticsReader = deviceCodeLoginController as DeviceCodeLoginDiagnosticsReader
            val settingsDiagnosticsReader = SettingsDiagnosticsRepository(
                context = appContext,
                currentAccountReader = currentAccountReader,
                providerAccountDao = database.providerAccountDao(),
                quotaSnapshotDao = database.quotaSnapshotDao(),
                refreshAttemptDao = database.refreshAttemptDao(),
                sessionStore = sessionStore,
                deviceCodeLoginDiagnosticsReader = deviceCodeLoginDiagnosticsReader,
                notificationPreferenceReader = notificationPreferences,
                retentionPreferenceReader = retentionPreferences,
            )

            return AppContainer(
                refreshCoordinator = refreshCoordinator,
                accountListUseCase = accountListRepository,
                accountDeleteUseCase = AccountDeleteUseCase { providerId, localAccountId ->
                    accountDeletionRepository.deleteAccount(
                        providerId = providerId,
                        localAccountId = localAccountId,
                    )
                },
                accountSwitchUseCase = accountMutationRepository,
                accountRenameUseCase = accountMutationRepository,
                accountEditUseCase = accountMutationRepository,
                accountQuotaAlertEvaluationRequester = AccountQuotaAlertEvaluationRequester { providerId, localAccountId, windowId ->
                    currentQuotaStatePublisher.publish(
                        currentQuotaStateRepository.loadAccountState(
                            providerId = providerId,
                            localAccountId = localAccountId,
                            primaryWindowId = windowId,
                        ),
                    )
                },
                homeCurrentQuotaStateLoader = currentQuotaStateRepository,
                homeRefreshUseCase = homeRefreshUseCase,
                deviceCodeLoginController = deviceCodeLoginController,
                deviceCodeLoginControllers = mapOf(
                    ProviderId("codex") to deviceCodeLoginController,
                    ProviderId("grok") to grokDeviceCodeLoginController,
                ),
                deviceCodeLoginNotifiers = mapOf(
                    ProviderId("codex") to deviceCodeLoginNotifier,
                    ProviderId("grok") to grokDeviceCodeLoginNotifier,
                ),
                deviceCodeLoginNotifier = deviceCodeLoginNotifier,
                retentionPreferences = retentionPreferences,
                notificationPreferences = RepublishingNotificationPreferenceStore(
                    delegate = notificationPreferences,
                    currentQuotaStateLoader = currentQuotaStateRepository,
                    currentQuotaStatePublisher = currentQuotaStatePublisher,
                ),
                primaryQuotaWindowPreferences = primaryQuotaWindowPreferences,
                quotaHistoryClearUseCase = RepublishingQuotaHistoryClearUseCase(
                    delegate = quotaHistoryClearRepository,
                    currentQuotaStateLoader = currentQuotaStateRepository,
                    currentQuotaStatePublisher = currentQuotaStatePublisher,
                ),
                homeTrendHistoryLoader = homeTrendHistoryRepository,
                backgroundRefreshStatusReader = backgroundRefreshStatusRepository,
                settingsDiagnosticsReader = settingsDiagnosticsReader,
                appUpdateChecker = GitHubReleaseAppUpdateChecker(httpClient),
                appUpdateDownloader = AndroidAppUpdateDownloader(appContext),
                updatePreferenceStore = updatePreferences,
                appUpdateNotifier = appUpdateNotifier,
                widgetQuotaStateLoader = widgetQuotaStateRepository,
                startupMaintenance = StartupMaintenance(
                    retentionPreferenceReader = retentionPreferences,
                    retentionCleanup = retentionCleanupRepository,
                    reporter = AndroidStartupMaintenanceReporter,
                    clock = clock,
                ),
                apiKeyLoginUseCase = sessionLoginUseCase,
                appearancePreferences = appearancePreferences,
                initialThemeMode = initialThemeMode,
                initialLanguagePreference = initialLanguagePreference,
                currencyPreferences = currencyPreferences,
                exchangeRateRepository = exchangeRateRepository,
                currentAccountStore = currentQuotaRefreshAccountStore,
                exchangeRateRefresher = exchangeRateRefresher,
                notificationWindowChoicesLoader = DefaultNotificationWindowChoicesLoader(
                    currentAccountReader = currentAccountReader,
                    quotaSnapshotDao = database.quotaSnapshotDao(),
                ),
            )
        }

        private const val PREFERENCES_FILE_NAME = "quotarabbit.preferences_pb"
        private const val APPEARANCE_PREFERENCES_FILE_NAME = "appearance.preferences_pb"
        private const val UPDATE_PREFERENCES_FILE_NAME = "update.preferences_pb"
        private const val SESSION_DIRECTORY_NAME = "sessions"
    }
}

private class CodexSessionEnvelopeFactory(
    private val sessionCipher: CodexSessionCipher,
) : CodexSessionImporter.SessionEnvelopeFactory {
    override fun create(
        payload: CodexSessionPayload,
        localAccountId: LocalAccountId,
        providerAccountId: ProviderAccountId?,
        now: Instant,
    ): ProviderSessionEnvelope {
        val envelope = ProviderSessionEnvelope(
            providerId = CODEX_PROVIDER_ID.value,
            localAccountId = localAccountId.value,
            providerAccountId = providerAccountId?.value,
            schemaVersion = CODEX_SESSION_SCHEMA_VERSION,
            payloadCiphertext = byteArrayOf(),
            payloadNonce = byteArrayOf(),
            createdAt = now.toString(),
            updatedAt = now.toString(),
        )
        return sessionCipher.encrypt(
            session = payload,
            envelope = envelope,
            updatedAt = now,
        )
    }

    private companion object {
        const val CODEX_SESSION_SCHEMA_VERSION = 1
    }
}

private val CODEX_PROVIDER_ID = ProviderId("codex")
private val GROK_PROVIDER_ID = ProviderId("grok")
private const val GROK_SESSION_SCHEMA_VERSION = 1

/** Encrypts/decrypts [GrokSessionPayload] envelopes with the shared Keystore-backed AES-GCM cipher. */
private class AesGcmGrokSessionCipher(
    private val payloadCipher: PayloadCipher,
) : GrokSessionCipher {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun decrypt(envelope: ProviderSessionEnvelope): Result<GrokSessionPayload> =
        runCatching {
            json.decodeFromString<GrokSessionPayload>(
                payloadCipher
                    .decrypt(envelope.payloadCiphertext, envelope.payloadNonce)
                    .decodeToString(),
            )
        }

    override fun encrypt(
        session: GrokSessionPayload,
        envelope: ProviderSessionEnvelope,
        updatedAt: Instant,
    ): ProviderSessionEnvelope {
        val encrypted = payloadCipher.encrypt(
            json.encodeToString(GrokSessionPayload.serializer(), session).encodeToByteArray(),
        )
        return envelope.copy(
            schemaVersion = GROK_SESSION_SCHEMA_VERSION,
            payloadCiphertext = encrypted.ciphertext,
            payloadNonce = encrypted.nonce,
            updatedAt = updatedAt.toString(),
        )
    }
}

private class GrokSessionEnvelopeFactory(
    private val sessionCipher: GrokSessionCipher,
) : GrokSessionImporter.SessionEnvelopeFactory {
    override fun create(
        payload: GrokSessionPayload,
        localAccountId: LocalAccountId,
        providerAccountId: ProviderAccountId?,
        now: Instant,
    ): ProviderSessionEnvelope {
        val envelope = ProviderSessionEnvelope(
            providerId = GROK_PROVIDER_ID.value,
            localAccountId = localAccountId.value,
            providerAccountId = providerAccountId?.value,
            schemaVersion = GROK_SESSION_SCHEMA_VERSION,
            payloadCiphertext = byteArrayOf(),
            payloadNonce = byteArrayOf(),
            createdAt = now.toString(),
            updatedAt = now.toString(),
        )
        return sessionCipher.encrypt(payload, envelope, now)
    }
}
