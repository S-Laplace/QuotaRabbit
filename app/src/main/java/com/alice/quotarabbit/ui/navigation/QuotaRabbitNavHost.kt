package com.alice.quotarabbit.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alice.quotarabbit.R
import com.alice.quotarabbit.app.NotificationWindowChoicesLoader
import com.alice.quotarabbit.data.currency.ExchangeRateReader
import com.alice.quotarabbit.domain.account.AccountDeleteUseCase
import com.alice.quotarabbit.domain.account.AccountEditUseCase
import com.alice.quotarabbit.domain.account.AccountListUseCase
import com.alice.quotarabbit.domain.account.AccountRenameUseCase
import com.alice.quotarabbit.domain.account.AccountSwitchUseCase
import com.alice.quotarabbit.domain.account.NoopAccountRenameUseCase
import com.alice.quotarabbit.domain.account.NoopAccountSwitchUseCase
import com.alice.quotarabbit.domain.auth.DeviceCodeLoginController
import com.alice.quotarabbit.domain.auth.DeviceCodeLoginNotifier
import com.alice.quotarabbit.domain.auth.NoopDeviceCodeLoginController
import com.alice.quotarabbit.domain.auth.NoopDeviceCodeLoginNotifier
import com.alice.quotarabbit.domain.auth.SessionLoginUseCase
import com.alice.quotarabbit.domain.currency.CurrencyPreferenceReader
import com.alice.quotarabbit.domain.currency.CurrencyPreferenceStore
import com.alice.quotarabbit.domain.currency.CurrencyPreferences
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.settings.NoopQuotaHistoryClearUseCase
import com.alice.quotarabbit.domain.settings.NotificationPreferenceStore
import com.alice.quotarabbit.domain.settings.QuotaHistoryClearUseCase
import com.alice.quotarabbit.domain.settings.RetentionPreferenceStore
import com.alice.quotarabbit.domain.theme.AppearancePreferenceStore
import com.alice.quotarabbit.domain.update.AppUpdateCheckUseCase
import com.alice.quotarabbit.domain.update.AppUpdateDownloadUseCase
import com.alice.quotarabbit.domain.update.NoopAppUpdateCheckUseCase
import com.alice.quotarabbit.domain.update.NoopAppUpdateDownloadUseCase
import com.alice.quotarabbit.domain.update.NoopUpdatePreferenceStore
import com.alice.quotarabbit.domain.update.UpdatePreferenceStore
import com.alice.quotarabbit.refresh.RefreshWorkScheduler
import com.alice.quotarabbit.ui.account.AccountQuotaAlertEvaluationRequester
import com.alice.quotarabbit.ui.account.AccountRefreshAllUseCase
import com.alice.quotarabbit.ui.account.AccountRoute
import com.alice.quotarabbit.ui.account.NoopAccountQuotaAlertEvaluationRequester
import com.alice.quotarabbit.ui.account.NoopAccountRefreshAllUseCase
import com.alice.quotarabbit.ui.auth.AddAccountEntryMode
import com.alice.quotarabbit.ui.auth.AddAccountRoute
import com.alice.quotarabbit.ui.auth.ApiKeyAuthRegion
import com.alice.quotarabbit.ui.auth.ApiKeyAuthScreen
import com.alice.quotarabbit.ui.auth.ProviderSelectionScreen
import com.alice.quotarabbit.ui.auth.ProviderSelectionSheet
import com.alice.quotarabbit.ui.auth.WebViewAuthConfig
import com.alice.quotarabbit.ui.auth.WebViewAuthScreen
import com.alice.quotarabbit.ui.components.QuotaRabbitBackdrop
import com.alice.quotarabbit.ui.home.HomeCurrentQuotaStateLoader
import com.alice.quotarabbit.ui.home.HomeRefreshUseCase
import com.alice.quotarabbit.ui.home.HomeRoute
import com.alice.quotarabbit.ui.home.HomeTrendHistoryLoader
import com.alice.quotarabbit.ui.motion.QuotaRabbitPageCascade
import com.alice.quotarabbit.ui.settings.DefaultSettingsDiagnosticsReader
import com.alice.quotarabbit.ui.settings.InMemoryNotificationPreferenceStore
import com.alice.quotarabbit.ui.settings.InMemoryRetentionPreferenceStore
import com.alice.quotarabbit.ui.settings.NoopAppearancePreferenceStore
import com.alice.quotarabbit.ui.settings.NoopCurrencyPreferenceStore
import com.alice.quotarabbit.ui.settings.SettingsDiagnosticsReader
import com.alice.quotarabbit.ui.settings.SettingsRoute
import com.alice.quotarabbit.update.UpdateCheckWorkScheduler
import java.security.MessageDigest
import java.security.SecureRandom

@Composable
fun QuotaRabbitNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = QuotaRabbitRoute.Home.route,
    // Per-provider device-code login controllers; lookup falls back to the Noop controller so
    // previews/tests stay valid with an empty map. The legacy Codex modes resolve the codex entry.
    deviceCodeLoginControllers: Map<ProviderId, DeviceCodeLoginController> = emptyMap(),
    deviceCodeLoginNotifiers: Map<ProviderId, DeviceCodeLoginNotifier> = emptyMap(),
    deviceCodeLoginNotifier: DeviceCodeLoginNotifier = NoopDeviceCodeLoginNotifier,
    accountListUseCase: AccountListUseCase,
    accountDeleteUseCase: AccountDeleteUseCase,
    accountSwitchUseCase: AccountSwitchUseCase = NoopAccountSwitchUseCase,
    accountRenameUseCase: AccountRenameUseCase = NoopAccountRenameUseCase,
    accountEditUseCase: AccountEditUseCase = AccountEditUseCase { _, _, _ -> null },
    accountQuotaAlertEvaluationRequester: AccountQuotaAlertEvaluationRequester =
        NoopAccountQuotaAlertEvaluationRequester,
    accountRefreshAllUseCase: AccountRefreshAllUseCase = NoopAccountRefreshAllUseCase,
    homeCurrentQuotaStateLoader: HomeCurrentQuotaStateLoader,
    homeRefreshUseCase: HomeRefreshUseCase,
    homeTrendHistoryLoader: HomeTrendHistoryLoader = HomeTrendHistoryLoader { _, _ -> emptyList() },
    currencyPreferenceReader: CurrencyPreferenceReader = object : CurrencyPreferenceReader {
        override suspend fun currencyPreferences() = CurrencyPreferences()
    },
    exchangeRateReader: ExchangeRateReader = object : ExchangeRateReader {
        override suspend fun currentRates() = null
    },

    retentionPreferenceStore: RetentionPreferenceStore = InMemoryRetentionPreferenceStore(),
    notificationPreferenceStore: NotificationPreferenceStore = InMemoryNotificationPreferenceStore(),
    backgroundRefreshStatusReader: com.alice.quotarabbit.ui.settings.SettingsBackgroundRefreshStatusReader =
        com.alice.quotarabbit.ui.settings.InMemoryBackgroundRefreshStatusReader(),
    settingsDiagnosticsReader: SettingsDiagnosticsReader = DefaultSettingsDiagnosticsReader,
    quotaHistoryClearUseCase: QuotaHistoryClearUseCase = NoopQuotaHistoryClearUseCase,
    appUpdateChecker: AppUpdateCheckUseCase = NoopAppUpdateCheckUseCase,
    appUpdateDownloader: AppUpdateDownloadUseCase = NoopAppUpdateDownloadUseCase,
    apiKeyLoginUseCase: SessionLoginUseCase? = null,
    notificationWindowChoicesLoader: NotificationWindowChoicesLoader =
        NotificationWindowChoicesLoader { _, _ -> emptyList() },
    currencyPreferenceStore: CurrencyPreferenceStore = NoopCurrencyPreferenceStore,
    appearancePreferenceStore: AppearancePreferenceStore = NoopAppearancePreferenceStore,
    updatePreferenceStore: UpdatePreferenceStore = NoopUpdatePreferenceStore,
    launchDestinationValue: String? = null,
) {
    val tabs = QuotaRabbitRoute.bottomTabs
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: QuotaRabbitRoute.Home.route

    // The provider picker is a bottom sheet that unfolds from the tab bar; hoisted here so the sheet
    // and the tab bar are siblings in the root Box (the bar must paint on top of the sheet).
    var providerSheetVisible by remember { mutableStateOf(false) }

    fun navigateToAddAccount(entryMode: AddAccountEntryMode) {
        navController.navigate(QuotaRabbitRoute.AddAccount.routeFor(entryMode))
    }

    fun navigateToProviderSelection() {
        providerSheetVisible = true
    }

    // After a successful add, land on the Account tab and clear the add-account flow off the stack.
    fun navigateToAccountAfterSave() {
        navController.navigate(QuotaRabbitRoute.Account.route) {
            popUpTo(QuotaRabbitRoute.Home.route) { inclusive = false }
            launchSingleTop = true
        }
    }

    fun navigateBasedOnAuthType(providerId: com.alice.quotarabbit.domain.model.ProviderId) {
        val config = com.alice.quotarabbit.providers.ProviderRegistry.configFor(providerId)
        val nextMode = when (config.authKind) {
            com.alice.quotarabbit.providers.ProviderAuthKind.OAuthWebView ->
                AddAccountEntryMode.LoginToCodex
            com.alice.quotarabbit.providers.ProviderAuthKind.ApiKeyImport ->
                AddAccountEntryMode.ApiKeyInput(providerId)
            com.alice.quotarabbit.providers.ProviderAuthKind.CookieAuth ->
                AddAccountEntryMode.WebViewCookieAuth(providerId)
            com.alice.quotarabbit.providers.ProviderAuthKind.OAuthPkceLogin ->
                AddAccountEntryMode.WebViewOAuthPkce(providerId)
            com.alice.quotarabbit.providers.ProviderAuthKind.DeviceCodeLogin ->
                AddAccountEntryMode.DeviceCodeLogin(providerId)
        }
        navigateToAddAccount(nextMode)
    }

    // Re-login goes straight to the account's own provider sign-in (no provider picker), carrying the
    // target account so the new credential rebinds in place instead of creating a duplicate.
    fun navigateToRelogin(
        providerId: com.alice.quotarabbit.domain.model.ProviderId,
        localAccountId: com.alice.quotarabbit.domain.model.LocalAccountId,
        providerAccountId: String?,
    ) {
        val config = com.alice.quotarabbit.providers.ProviderRegistry.configFor(providerId)
        val nextMode = when (config.authKind) {
            com.alice.quotarabbit.providers.ProviderAuthKind.OAuthWebView ->
                AddAccountEntryMode.CodexRelogin(providerAccountId)
            com.alice.quotarabbit.providers.ProviderAuthKind.ApiKeyImport ->
                AddAccountEntryMode.ApiKeyInput(providerId, reloginAccountId = localAccountId)
            com.alice.quotarabbit.providers.ProviderAuthKind.CookieAuth ->
                AddAccountEntryMode.WebViewCookieAuth(providerId, reloginAccountId = localAccountId)
            com.alice.quotarabbit.providers.ProviderAuthKind.OAuthPkceLogin ->
                AddAccountEntryMode.WebViewOAuthPkce(providerId, reloginAccountId = localAccountId)
            com.alice.quotarabbit.providers.ProviderAuthKind.DeviceCodeLogin ->
                AddAccountEntryMode.DeviceCodeLogin(
                    providerId = providerId,
                    reloginAccountId = localAccountId,
                    expectedProviderAccountId = providerAccountId,
                )
        }
        navigateToAddAccount(nextMode)
    }

    Box(modifier = modifier.fillMaxSize()) {
        QuotaRabbitBackdrop(modifier = Modifier.fillMaxSize())
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
        ) { innerPadding ->
        @Composable
        fun AddAccountDestination(entryMode: AddAccountEntryMode) {
            when (entryMode) {
                is AddAccountEntryMode.ProviderSelection, AddAccountEntryMode.Choose -> {
                    QuotaRabbitDestination(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = innerPadding,
                    ) {
                        ProviderSelectionScreen(
                            onProviderSelected = { providerId ->
                                navigateBasedOnAuthType(providerId)
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                is AddAccountEntryMode.LoginToCodex, is AddAccountEntryMode.CodexRelogin -> {
                    QuotaRabbitDestination(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = innerPadding,
                    ) {
                        AddAccountRoute(
                            entryMode = entryMode,
                            deviceCodeLoginController = deviceCodeLoginControllers[
                                com.alice.quotarabbit.providers.ProviderRegistry.CODEX,
                            ] ?: NoopDeviceCodeLoginController,
                            deviceCodeLoginNotifier = deviceCodeLoginNotifier,
                            onBackClick = { navController.popBackStack() },
                            onLoginSaved = { navigateToAccountAfterSave() },
                        )
                    }
                }
                is AddAccountEntryMode.DeviceCodeLogin -> {
                    QuotaRabbitDestination(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = innerPadding,
                    ) {
                        AddAccountRoute(
                            entryMode = entryMode,
                            deviceCodeLoginController = deviceCodeLoginControllers[
                                entryMode.providerId,
                            ] ?: NoopDeviceCodeLoginController,
                            deviceCodeLoginNotifier = deviceCodeLoginNotifiers[
                                entryMode.providerId,
                            ] ?: deviceCodeLoginNotifier,
                            providerDisplayName = com.alice.quotarabbit.providers.ProviderRegistry
                                .displayNameFor(entryMode.providerId),
                            onBackClick = { navController.popBackStack() },
                            onLoginSaved = { navigateToAccountAfterSave() },
                        )
                    }
                }
                is AddAccountEntryMode.ApiKeyInput -> {
                    QuotaRabbitDestination(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = innerPadding,
                    ) {
                        val useCase = apiKeyLoginUseCase
                        if (useCase != null) {
                            // Region options for providers whose balance/usage API differs by platform.
                            // Endpoint URLs are pending owner verification; persisted per account.
                            val regions = remember(entryMode.providerId) {
                                when (entryMode.providerId.value) {
                                    "zai" -> listOf(
                                        ApiKeyAuthRegion("🇨🇳 中国站 (bigmodel.cn)", "https://open.bigmodel.cn"),
                                        ApiKeyAuthRegion("🌍 国际站 (z.ai)", "https://api.z.ai"),
                                    )
                                    "minimax" -> listOf(
                                        ApiKeyAuthRegion("🌍 国际站 (api.minimax.io)", "https://api.minimax.io"),
                                        ApiKeyAuthRegion("🇨🇳 中国站 (api.minimaxi.com)", "https://api.minimaxi.com"),
                                    )
                                    // Kimi Code console keys live on one of two platforms; the first
                                    // entry is the default region.
                                    "kimi_code" -> listOf(
                                        ApiKeyAuthRegion("🇨🇳 中国站 (api.kimi.com)", "https://api.kimi.com"),
                                        ApiKeyAuthRegion("🌍 国际站 (api.kimi.ai)", "https://api.kimi.ai"),
                                    )
                                    else -> emptyList()
                                }
                            }
                            ApiKeyAuthScreen(
                                providerId = entryMode.providerId,
                                regions = regions,
                                onImportApiKey = { apiKey, label, apiBaseUrl ->
                                    val reloginAccountId = entryMode.reloginAccountId
                                    if (reloginAccountId != null) {
                                        useCase.reloginApiKey(
                                            localAccountId = reloginAccountId,
                                            apiKey = apiKey,
                                            apiBaseUrl = apiBaseUrl,
                                        ).map { }
                                    } else {
                                        useCase.importApiKey(
                                            providerId = entryMode.providerId,
                                            providerDisplayName = com.alice.quotarabbit.providers.ProviderRegistry
                                                .displayNameFor(entryMode.providerId),
                                            apiKey = apiKey,
                                            label = label,
                                            apiBaseUrl = apiBaseUrl,
                                        ).map { }
                                    }
                                },
                                onBack = { navController.popBackStack() },
                                onSaved = { navigateToAccountAfterSave() },
                            )
                        } else {
                            StubAuthScreen(
                                title = "API Key — ${entryMode.providerId.value}",
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
                is AddAccountEntryMode.WebViewCookieAuth -> {
                    QuotaRabbitDestination(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = innerPadding,
                    ) {
                        val useCase = apiKeyLoginUseCase
                        if (useCase != null) {
                            val cookieConfig = remember(entryMode.providerId) {
                                when (entryMode.providerId.value) {
                                    "cursor" -> WebViewAuthConfig.Cookie(
                                        providerId = entryMode.providerId,
                                        loginUrl = "https://cursor.com",
                                        cookieDomain = "cursor.com",
                                        targetCookieNames = listOf("WorkosCursorSessionToken"),
                                    )
                                    // CodexBar uses a single www.kimi.com host (no region split);
                                    // the kimi-auth cookie is the JWT used for the billing API.
                                    // Load /code directly: /code/console client-redirects logged-out
                                    // users to /code, and in a WebView that redirected page's login
                                    // button stops opening the modal. kimi sets a guest kimi-auth
                                    // before login, so capture only on explicit "Done".
                                    "kimi" -> WebViewAuthConfig.Cookie(
                                        providerId = entryMode.providerId,
                                        loginUrl = "https://www.kimi.com/code",
                                        cookieDomain = "www.kimi.com",
                                        targetCookieNames = listOf("kimi-auth"),
                                        autoCapture = false,
                                        cookiePaths = listOf(
                                            "/apiv2/kimi.gateway.billing.v1.BillingService/GetUsages",
                                            "/code/console",
                                            "/code",
                                        ),
                                        localStorageTokenKey = "access_token",
                                        tipResId = R.string.auth_tip_kimi,
                                        // Open the login modal automatically, unless it is already shown.
                                        injectOnLoadJs = """
                                            if(!document.querySelector('input[type=tel]')){
                                              var t=[].slice.call(document.querySelectorAll('a,button,[role=button]'))
                                                .filter(function(e){return /登录|登入|log\s*in|sign\s*in/i.test(e.textContent||'')})[0];
                                              if(t)t.click();
                                            }
                                        """.trimIndent(),
                                    )
                                    else -> null
                                }
                            }
                            if (cookieConfig != null) {
                                WebViewAuthScreen(
                                    config = cookieConfig,
                                    onCredentialExtracted = { cookie, _ ->
                                        val reloginAccountId = entryMode.reloginAccountId
                                        if (reloginAccountId != null) {
                                            useCase.reloginCookie(
                                                localAccountId = reloginAccountId,
                                                cookieValue = cookie,
                                            ).map { }
                                        } else {
                                            useCase.importCookie(
                                                providerId = entryMode.providerId,
                                                providerDisplayName = com.alice.quotarabbit.providers.ProviderRegistry
                                                    .displayNameFor(entryMode.providerId),
                                                cookieValue = cookie,
                                            ).map { }
                                        }
                                    },
                                    onBack = { navController.popBackStack() },
                                    onSaved = { navigateToAccountAfterSave() },
                                )
                            } else {
                                StubAuthScreen(
                                    title = "Web Login — ${entryMode.providerId.value}",
                                    onBack = { navController.popBackStack() },
                                )
                            }
                        } else {
                            StubAuthScreen(
                                title = "Web Login — ${entryMode.providerId.value}",
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
                is AddAccountEntryMode.WebViewOAuthPkce -> {
                    QuotaRabbitDestination(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = innerPadding,
                    ) {
                        val useCase = apiKeyLoginUseCase
                        if (useCase != null) {
                            val pkceParams = remember {
                                val verifier = generateCodeVerifier()
                                val challenge = generateCodeChallenge(verifier)
                                val state = generateRandomState()
                                Triple(verifier, challenge, state)
                            }
                            val (codeVerifier, codeChallenge, state) = pkceParams

                            val pkceConfig = remember(entryMode.providerId) {
                                when (entryMode.providerId.value) {
                                    "claude" -> {
                                        val authUrl = "https://claude.ai/oauth/authorize?" +
                                            "code=true" +
                                            "&client_id=9d1c250a-e61b-44d9-88ed-5944d1962f5e" +
                                            "&response_type=code" +
                                            "&redirect_uri=" + java.net.URLEncoder.encode(
                                                "https://console.anthropic.com/oauth/code/callback", "UTF-8") +
                                            "&scope=" + java.net.URLEncoder.encode(
                                                "org:create_api_key user:profile user:inference", "UTF-8") +
                                            "&state=$state" +
                                            "&code_challenge=$codeChallenge" +
                                            "&code_challenge_method=S256"
                                        WebViewAuthConfig.OAuthIntercept(
                                            providerId = entryMode.providerId,
                                            authorizationUrl = authUrl,
                                            redirectUriPrefix = "https://console.anthropic.com/oauth/code/callback",
                                            expectedState = state,
                                            appendStateToCode = true,
                                            tipResId = R.string.auth_tip_claude,
                                        )
                                    }
                                    // The embedded WebView now passes Google's bot/UA checks, so we
                                    // intercept the loopback redirect in-app instead of bouncing to an
                                    // external browser (which never returned the callback). Google's
                                    // loopback clients accept any 127.0.0.1 port; nothing actually binds
                                    // it — the redirect is caught before the WebView loads it.
                                    "antigravity" -> {
                                        val redirectUri = "http://127.0.0.1:8089/callback"
                                        val authUrl = buildOAuthUrl(
                                            "https://accounts.google.com/o/oauth2/v2/auth",
                                            "1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com",
                                            redirectUri,
                                            "https://www.googleapis.com/auth/cloud-platform https://www.googleapis.com/auth/userinfo.email",
                                            codeChallenge,
                                            state,
                                        ) + "&access_type=offline"
                                        WebViewAuthConfig.OAuthIntercept(
                                            providerId = entryMode.providerId,
                                            authorizationUrl = authUrl,
                                            redirectUriPrefix = redirectUri,
                                            expectedState = state,
                                        )
                                    }
                                    else -> null
                                }
                            }
                            if (pkceConfig != null) {
                                WebViewAuthScreen(
                                    config = pkceConfig,
                                    onCredentialExtracted = { code, redirectUri ->
                                        val reloginAccountId = entryMode.reloginAccountId
                                        if (reloginAccountId != null) {
                                            useCase.reloginOAuthPkce(
                                                localAccountId = reloginAccountId,
                                                code = code,
                                                verifier = codeVerifier,
                                                redirectUri = redirectUri.orEmpty(),
                                            ).map { }
                                        } else {
                                            useCase.importOAuthPkce(
                                                providerId = entryMode.providerId,
                                                providerDisplayName = com.alice.quotarabbit.providers.ProviderRegistry
                                                    .displayNameFor(entryMode.providerId),
                                                code = code,
                                                verifier = codeVerifier,
                                                redirectUri = redirectUri.orEmpty(),
                                            ).map { }
                                        }
                                    },
                                    onBack = { navController.popBackStack() },
                                    onSaved = { navigateToAccountAfterSave() },
                                )
                            } else {
                                StubAuthScreen(
                                    title = "OAuth PKCE — ${entryMode.providerId.value}",
                                    onBack = { navController.popBackStack() },
                                )
                            }
                        } else {
                            StubAuthScreen(
                                title = "OAuth PKCE — ${entryMode.providerId.value}",
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }

        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(QuotaRabbitRoute.Home.route) {
                QuotaRabbitDestination(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                ) {
                    HomeRoute(
                        currentQuotaStateLoader = homeCurrentQuotaStateLoader,
                        refreshUseCase = homeRefreshUseCase,
                        trendHistoryLoader = homeTrendHistoryLoader,
                        notificationPreferenceReader = notificationPreferenceStore,
                        currencyPreferenceReader = currencyPreferenceReader,
                        exchangeRateReader = exchangeRateReader,
                        onLoginClick = { navigateToProviderSelection() },
                        onManageAccountsClick = {
                            navController.navigate(QuotaRabbitRoute.Account.route) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
            }
            composable(QuotaRabbitRoute.Account.route) {
                QuotaRabbitDestination(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                ) {
                    AccountRoute(
                        accountListUseCase = accountListUseCase,
                        accountDeleteUseCase = accountDeleteUseCase,
                        accountSwitchUseCase = accountSwitchUseCase,
                        accountRenameUseCase = accountRenameUseCase,
                        accountEditUseCase = accountEditUseCase,
                        notificationPreferenceStore = notificationPreferenceStore,
                        quotaAlertEvaluationRequester = accountQuotaAlertEvaluationRequester,
                        refreshAllUseCase = accountRefreshAllUseCase,
                        currencyPreferenceReader = currencyPreferenceReader,
                        exchangeRateReader = exchangeRateReader,
                        onLoginToCodexClick = { navigateToProviderSelection() },
                        onAddAccountClick = { navigateToProviderSelection() },
                        onReloginAccount = { providerId, localAccountId, providerAccountId ->
                            navigateToRelogin(providerId, localAccountId, providerAccountId)
                        },
                        onOpenDetails = { id ->
                            navController.navigate(QuotaRabbitRoute.AccountDetails.forAccount(id))
                        },
                    )
                }
            }
            composable(
                route = QuotaRabbitRoute.AccountDetails.pattern,
                arguments = listOf(navArgument(QuotaRabbitRoute.AccountDetails.ACCOUNT_ID) {
                    type = NavType.StringType
                }),
            ) { entry ->
                val accountId = entry.arguments?.getString(QuotaRabbitRoute.AccountDetails.ACCOUNT_ID)
                QuotaRabbitDestination(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                ) {
                    AccountRoute(
                        accountListUseCase = accountListUseCase,
                        accountDeleteUseCase = accountDeleteUseCase,
                        accountSwitchUseCase = accountSwitchUseCase,
                        accountRenameUseCase = accountRenameUseCase,
                        accountEditUseCase = accountEditUseCase,
                        notificationPreferenceStore = notificationPreferenceStore,
                        quotaAlertEvaluationRequester = accountQuotaAlertEvaluationRequester,
                        refreshAllUseCase = accountRefreshAllUseCase,
                        currencyPreferenceReader = currencyPreferenceReader,
                        exchangeRateReader = exchangeRateReader,
                        onLoginToCodexClick = { navigateToProviderSelection() },
                        onAddAccountClick = { navigateToProviderSelection() },
                        onReloginAccount = { providerId, localAccountId, providerAccountId ->
                            navigateToRelogin(providerId, localAccountId, providerAccountId)
                        },
                        detailAccountId = LocalAccountId(accountId.orEmpty()),
                        onBackClick = { navController.popBackStack() },
                    )
                }
            }
            composable(QuotaRabbitRoute.Settings.route) {
                val settingsContext = LocalContext.current
                QuotaRabbitDestination(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                ) {
                    SettingsRoute(
                        accountListUseCase = accountListUseCase,
                        retentionPreferenceStore = retentionPreferenceStore,
                        notificationPreferenceStore = notificationPreferenceStore,
                        backgroundRefreshStatusReader = backgroundRefreshStatusReader,
                        diagnosticsReader = settingsDiagnosticsReader,
                        quotaHistoryClearUseCase = quotaHistoryClearUseCase,
                        appUpdateChecker = appUpdateChecker,
                        appUpdateDownloader = appUpdateDownloader,
                        backgroundRefreshScheduler = { minutes ->
                            RefreshWorkScheduler.from(settingsContext).applyIntervalMinutes(minutes)
                        },
                        notificationWindowChoicesLoader = notificationWindowChoicesLoader,
                        currencyPreferenceStore = currencyPreferenceStore,
                        appearancePreferenceStore = appearancePreferenceStore,
                        updatePreferenceStore = updatePreferenceStore,
                        updateCheckScheduler = { enabled ->
                            UpdateCheckWorkScheduler.from(settingsContext).setAutoCheckEnabled(enabled)
                        },
                        openUpdateDialogOnLaunch =
                            launchDestinationValue == QuotaRabbitLaunchDestination.SettingsUpdate.value,
                    )
                }
            }
            composable(QuotaRabbitRoute.AddAccount.route) {
                AddAccountDestination(entryMode = AddAccountEntryMode.Choose)
            }
            composable(
                route = QuotaRabbitRoute.AddAccount.routeWithEntryMode,
                arguments = listOf(
                    navArgument(QuotaRabbitRoute.AddAccount.ENTRY_MODE_ARG) {
                        type = NavType.StringType
                    },
                ),
            ) { backStackEntry ->
                AddAccountDestination(
                    entryMode = AddAccountEntryMode.fromRouteValue(
                        backStackEntry.arguments?.getString(QuotaRabbitRoute.AddAccount.ENTRY_MODE_ARG),
                    ),
                )
            }
        }
        }
        // The per-provider login flow is a full-screen modal; hiding the tab bar there keeps its
        // actions and error text from being covered by the floating bar.
        val showBottomBar = tabs.any { it.route == currentRoute }
        if (showBottomBar) {
        QuotaRabbitBottomBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            tabs = tabs,
            currentRoute = currentRoute,
            onTabSelected = { tab ->
                // Don't leave the picker hovering over a freshly switched tab.
                providerSheetVisible = false
                navController.navigate(tab.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
        )
        }
        // Provider picker sheet — rendered LAST so it paints over the tab bar: the panel grows up
        // out of the bar's position and covers it while open. Dismisses before branching into the
        // per-provider full-screen login.
        ProviderSelectionSheet(
            visible = providerSheetVisible,
            onProviderSelected = { providerId ->
                providerSheetVisible = false
                navigateBasedOnAuthType(providerId)
            },
            onDismiss = { providerSheetVisible = false },
        )
    }
}

@Composable
private fun QuotaRabbitDestination(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        QuotaRabbitBackdrop(modifier = Modifier.fillMaxSize())
        QuotaRabbitPageCascade(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            content()
        }
    }
}

private fun generateCodeVerifier(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

private fun generateCodeChallenge(verifier: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
    return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}

private fun generateRandomState(): String {
    val bytes = ByteArray(16)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

private fun buildOAuthUrl(
    endpoint: String,
    clientId: String,
    redirectUri: String,
    scope: String,
    codeChallenge: String,
    state: String,
): String =
    "$endpoint?" +
        "client_id=${java.net.URLEncoder.encode(clientId, "UTF-8")}" +
        "&redirect_uri=${java.net.URLEncoder.encode(redirectUri, "UTF-8")}" +
        "&response_type=code" +
        "&scope=${java.net.URLEncoder.encode(scope, "UTF-8")}" +
        "&state=$state" +
        "&code_challenge=$codeChallenge" +
        "&code_challenge_method=S256"
