package com.alice.quotarabbit.ui.settings

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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.alice.quotarabbit.domain.theme.ThemeMode
import com.alice.quotarabbit.ui.components.QuotaRabbitBackdrop
import com.alice.quotarabbit.ui.theme.QuotaRabbitSystemBars
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import java.util.Locale

/** Isolated real SettingsScreen; its callbacks only update SettingsPreviewFixture in memory. */
class SettingsPreviewActivity : ComponentActivity() {
    // Activity resources may be requested before Kotlin delegates are initialized.
    private var previewConfiguration: Configuration? = null
    private var configurationGeneration by mutableIntStateOf(0)
    lateinit var fixture: SettingsPreviewFixture
        private set
    lateinit var previewContext: Context
        private set
    var effectiveDark = false
        private set

    override fun getResources(): Resources = previewConfiguration?.let {
        super.createConfigurationContext(it).resources
    } ?: super.getResources()

    override fun createConfigurationContext(overrideConfiguration: Configuration): Context {
        val merged = Configuration(overrideConfiguration)
        previewConfiguration?.let {
            merged.setLocales(it.locales)
            merged.fontScale = it.fontScale
            merged.screenWidthDp = it.screenWidthDp
        }
        return super.createConfigurationContext(merged)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scale = intent.getFloatExtra("font_scale", 1f)
        val width = intent.getIntExtra("width_dp", 320)
        previewConfiguration = Configuration(baseContext.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(intent.getStringExtra("locale") ?: "en"))
            fontScale = scale
            screenWidthDp = width
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        }
        previewContext = createConfigurationContext(requireNotNull(previewConfiguration))
        fixture = SettingsPreviewFixture(ThemeMode.fromStorage(intent.getStringExtra("theme") ?: "system"))
        setContent {
            val density = LocalDensity.current
            val configuration = remember(configurationGeneration) { requireNotNull(previewConfiguration) }
            CompositionLocalProvider(
                LocalContext provides previewContext,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(density.density, scale),
            ) {
                QuotaRabbitTheme(themeMode = fixture.state.appearance.themeMode) {
                    QuotaRabbitSystemBars(window)
                    val dark = QuotaRabbitTheme.colors.isDark
                    SideEffect { effectiveDark = dark }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        QuotaRabbitBackdrop(Modifier.fillMaxSize())
                        Box(Modifier.widthIn(max = width.dp).fillMaxSize().safeDrawingPadding().testTag("settings-preview-host")) {
                            fixture.Content()
                        }
                    }
                }
            }
        }
    }

    /** Test configuration event, with no modification to the emulator's actual night-mode setting. */
    fun dispatchSystemNightMode(dark: Boolean) {
        previewConfiguration = Configuration(requireNotNull(previewConfiguration)).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        previewContext = createConfigurationContext(requireNotNull(previewConfiguration))
        configurationGeneration++
    }
}
