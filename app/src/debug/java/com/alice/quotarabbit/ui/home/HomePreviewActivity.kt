package com.alice.quotarabbit.ui.home

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.alice.quotarabbit.domain.theme.ThemeMode
import com.alice.quotarabbit.ui.components.QuotaRabbitBackdrop
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import java.time.Instant
import java.util.Locale

/**
 * Credential-free rendering of the production Home screen. Intent extras choose `scenario`
 * (codex/mixed/states/cached/loading/empty/metadata), `theme`, `locale`, `width_dp`, and `font_scale`.
 * `now` optionally supplies an ISO instant; metadata otherwise uses deterministic fixture time.
 * The fixtures never read saved accounts, invoke a provider, or write session/preferences data.
 */
class HomePreviewActivity : ComponentActivity() {
    private var previewConfiguration: Configuration? = null
    lateinit var previewContext: Context
        private set
    var previewState by mutableStateOf(HomeUiState.loading())
        private set

    override fun getResources(): Resources = previewConfiguration?.let {
        super.createConfigurationContext(it).resources
    } ?: super.getResources()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scenario = intent.getStringExtra("scenario") ?: "codex"
        val now = intent.getStringExtra("now")?.let(Instant::parse)
            ?: if (scenario == "metadata") HomePreviewFixtures.metadataNow else Instant.now()
        previewState = HomePreviewFixtures.state(scenario, now)
        val theme = when (intent.getStringExtra("theme")) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
        val maxWidth = intent.getIntExtra("width_dp", 0).takeIf { it > 0 }
        val fontScale = intent.getFloatExtra("font_scale", 0f).takeIf { it > 0f }
        previewConfiguration = Configuration(baseContext.resources.configuration).apply {
            intent.getStringExtra("locale")?.let { setLocale(Locale.forLanguageTag(it)) }
            fontScale?.let { this.fontScale = it }
            maxWidth?.let { screenWidthDp = it }
        }
        previewContext = createConfigurationContext(requireNotNull(previewConfiguration))
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalContext provides previewContext,
                LocalConfiguration provides requireNotNull(previewConfiguration),
                LocalDensity provides Density(density.density, fontScale ?: density.fontScale),
            ) {
                QuotaRabbitTheme(themeMode = theme) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        QuotaRabbitBackdrop(modifier = Modifier.fillMaxSize())
                        HomeScreen(
                            uiState = previewState,
                            modifier = Modifier.testTag("home-preview-host").safeDrawingPadding().then(
                                if (maxWidth != null) Modifier.widthIn(max = maxWidth.dp) else Modifier,
                            ),
                        )
                    }
                }
            }
        }
    }

    /** Simulated refresh completion only; no repository, session, preferences, or provider writes. */
    fun updateAccount(id: String, transform: (HomeUiState) -> HomeUiState) {
        previewState = previewState.copy(accountCards = previewState.accountCards.map { card ->
            if (card.localAccountId.value == id) card.copy(state = transform(card.state)) else card
        })
    }
}
