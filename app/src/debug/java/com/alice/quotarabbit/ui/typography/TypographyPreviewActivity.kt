package com.alice.quotarabbit.ui.typography

import android.content.res.Configuration
import android.content.res.Resources
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.alice.quotarabbit.domain.account.AccountDeleteUseCase
import com.alice.quotarabbit.domain.account.AccountListUseCase
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.quota.CurrentQuotaState
import com.alice.quotarabbit.domain.quota.HomeDashboardState
import com.alice.quotarabbit.domain.theme.ThemeMode
import com.alice.quotarabbit.domain.theme.AppearancePreferenceStore
import com.alice.quotarabbit.ui.auth.AuthScaffold
import com.alice.quotarabbit.ui.auth.ApiKeyAuthScreen
import com.alice.quotarabbit.ui.auth.ProviderSelectionScreen
import com.alice.quotarabbit.ui.components.QuotaRabbitBackdrop
import com.alice.quotarabbit.ui.home.HomeCurrentQuotaStateLoader
import com.alice.quotarabbit.ui.home.HomeRefreshUseCase
import com.alice.quotarabbit.ui.navigation.QuotaRabbitNavHost
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import com.alice.quotarabbit.ui.theme.QuotaRabbitSystemBars
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow

/** Offline acceptance of the real app shell; all injected use cases are immutable local fakes. */
class TypographyPreviewActivity : ComponentActivity() {
    // Android may ask for resources before Kotlin property delegates are initialized.
    private var specimenConfiguration: Configuration? = null
    private var configurationGeneration by mutableIntStateOf(0)
    lateinit var specimenContext: Context
        private set
    lateinit var appearancePreferenceStore: PreviewAppearancePreferenceStore
        private set

    override fun getResources(): Resources = specimenConfiguration?.let {
        super.createConfigurationContext(it).resources
    } ?: super.getResources()

    // Dialog's themed context requests a configuration context from the Activity.
    override fun createConfigurationContext(overrideConfiguration: Configuration): Context {
        val merged = Configuration(overrideConfiguration)
        specimenConfiguration?.let {
            merged.setLocales(it.locales)
            merged.fontScale = it.fontScale
            merged.screenWidthDp = it.screenWidthDp
        }
        return super.createConfigurationContext(merged)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val requestedScale = intent.getFloatExtra("font_scale", 1f)
        val width = intent.getIntExtra("width_dp", 320)
        val configuration = Configuration(baseContext.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(intent.getStringExtra("locale") ?: "en"))
            fontScale = requestedScale
            screenWidthDp = width
        }
        specimenConfiguration = configuration
        specimenContext = createConfigurationContext(configuration)
        appearancePreferenceStore = PreviewAppearancePreferenceStore(
            ThemeMode.fromStorage(intent.getStringExtra("theme") ?: "light"),
        )
        val dashboard = TypographyPreviewFixtures.dashboard()
        val accountList = TypographyPreviewFixtures.accounts(dashboard)
        val loader = object : HomeCurrentQuotaStateLoader {
            override suspend fun loadCurrentState(): CurrentQuotaState = dashboard.currentState
            override suspend fun loadDashboardState(): HomeDashboardState = dashboard
        }
        setContent {
            val density = LocalDensity.current
            val currentConfiguration = remember(configurationGeneration) { requireNotNull(specimenConfiguration) }
            val theme by appearancePreferenceStore.themeMode.collectAsState()
            CompositionLocalProvider(
                LocalContext provides specimenContext,
                LocalConfiguration provides currentConfiguration,
                LocalDensity provides Density(density.density, requestedScale),
                LocalActivityResultRegistryOwner provides this@TypographyPreviewActivity,
                LocalActivity provides this@TypographyPreviewActivity,
            ) {
                QuotaRabbitTheme(themeMode = theme) {
                    QuotaRabbitSystemBars(window)
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.widthIn(max = width.dp).fillMaxSize().testTag("typography-host")) {
                            if (intent.getStringExtra("screen") == "auth") {
                                QuotaRabbitBackdrop(Modifier.fillMaxSize())
                                AuthScaffold(
                                    title = AUTH_SAMPLE_TITLE, onBack = {},
                                    modifier = Modifier.safeDrawingPadding().testTag("typography-auth-sample"),
                                ) {
                                    Text("Offline sample · 오프라인 예시 · 离线示例",
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onBackground)
                                }
                            } else if (intent.getStringExtra("screen") == "providers") {
                                QuotaRabbitBackdrop(Modifier.fillMaxSize())
                                var selectedProvider by remember { mutableStateOf<ProviderId?>(null) }
                                val provider = selectedProvider
                                if (provider == null) ProviderSelectionScreen(
                                    onProviderSelected = { selectedProvider = it }, onBack = {},
                                    modifier = Modifier.safeDrawingPadding().testTag("typography-provider-picker"),
                                ) else {
                                    ApiKeyAuthScreen(
                                        providerId = provider,
                                        onImportApiKey = { _, _, _ ->
                                            error("Typography acceptance must never submit credentials")
                                        },
                                        onBack = { selectedProvider = null },
                                        modifier = Modifier.safeDrawingPadding().testTag("typography-api-key-sample"),
                                    )
                                }
                            } else QuotaRabbitNavHost(
                                appearancePreferenceStore = appearancePreferenceStore,
                                accountListUseCase = AccountListUseCase { accountList },
                                accountDeleteUseCase = AccountDeleteUseCase { _, _ ->
                                    error("Typography acceptance must never confirm account deletion")
                                },
                                homeCurrentQuotaStateLoader = loader,
                                homeRefreshUseCase = object : HomeRefreshUseCase {
                                    override suspend fun refreshCurrentState(): CurrentQuotaState = dashboard.currentState
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val previous = specimenConfiguration ?: return
        specimenConfiguration = Configuration(newConfig).apply {
            setLocales(previous.locales)
            fontScale = previous.fontScale
            screenWidthDp = previous.screenWidthDp
        }
        specimenContext = createConfigurationContext(requireNotNull(specimenConfiguration))
        configurationGeneration++
    }

    /** Delivers a night-mode configuration event without changing the emulator's global settings. */
    fun dispatchSystemNightMode(dark: Boolean) {
        val configuration = Configuration(requireNotNull(specimenConfiguration))
        configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        onConfigurationChanged(configuration)
    }

    companion object {
        const val AUTH_SAMPLE_TITLE = "Connect account · 계정 연결 · 连接账号"
    }
}

/** Isolated appearance state: Settings exercises its real ViewModel without DataStore writes. */
class PreviewAppearancePreferenceStore(initialMode: ThemeMode) : AppearancePreferenceStore {
    override val themeMode = MutableStateFlow(initialMode)
    override suspend fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
    }
}
