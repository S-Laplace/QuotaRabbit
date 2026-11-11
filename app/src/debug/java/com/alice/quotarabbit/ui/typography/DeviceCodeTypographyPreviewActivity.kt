package com.alice.quotarabbit.ui.typography

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import com.alice.quotarabbit.domain.theme.ThemeMode
import com.alice.quotarabbit.ui.auth.AddAccountScreen
import com.alice.quotarabbit.ui.auth.DeviceCodeLoginUiState
import com.alice.quotarabbit.ui.auth.DeviceCodeLoginUiStatus
import com.alice.quotarabbit.ui.components.QuotaRabbitBackdrop
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import java.util.Locale

/** Static display specimens only: no controller, network, clipboard, or saved account access. */
class DeviceCodeTypographyPreviewActivity : ComponentActivity() {
    private var specimenConfiguration: Configuration? = null
    lateinit var specimenContext: Context
        private set
    var copiedCode: String? = null
        private set
    var openedVerificationUri: String? = null
        private set
    var retryCount = 0
        private set
    var restartCount = 0
        private set
    var mismatchDismissed = false
        private set

    override fun getResources(): Resources = specimenConfiguration?.let {
        super.createConfigurationContext(it).resources
    } ?: super.getResources()

    // Dialog's themed context must inherit the same locale and font scale as the page.
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
        val width = intent.getIntExtra("width_dp", 320)
        val requestedScale = intent.getFloatExtra("font_scale", 1f)
        val configuration = Configuration(baseContext.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(intent.getStringExtra("locale") ?: "en"))
            fontScale = requestedScale
            screenWidthDp = width
        }
        specimenConfiguration = configuration
        specimenContext = createConfigurationContext(configuration)
        val theme = if (intent.getStringExtra("theme") == "dark") ThemeMode.DARK else ThemeMode.LIGHT
        val initialStatus = DeviceCodeLoginUiStatus.valueOf(
            intent.getStringExtra("status") ?: DeviceCodeLoginUiStatus.PollingAuthorization.name,
        )
        setContent {
            val density = LocalDensity.current
            var status by remember { mutableStateOf(initialStatus) }
            CompositionLocalProvider(
                LocalContext provides specimenContext,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(density.density, requestedScale),
                LocalActivityResultRegistryOwner provides this@DeviceCodeTypographyPreviewActivity,
                LocalActivity provides this@DeviceCodeTypographyPreviewActivity,
            ) {
                QuotaRabbitTheme(themeMode = theme) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.widthIn(max = width.dp).fillMaxSize().testTag("device-code-host")) {
                            QuotaRabbitBackdrop(Modifier.fillMaxSize())
                            AddAccountScreen(
                                uiState = DeviceCodeLoginUiState.Empty.copy(
                                    status = status,
                                    attemptId = "offline-typography",
                                    userCode = DUMMY_USER_CODE,
                                    verificationUri = DUMMY_VERIFICATION_URI,
                                ),
                                modifier = Modifier.safeDrawingPadding(),
                                onCopyCodeClick = { copiedCode = it },
                                onOpenVerificationClick = { openedVerificationUri = it },
                                onRetryValidationClick = { retryCount++ },
                                onStartLoginClick = { restartCount++ },
                                onCancelMismatchClick = {
                                    mismatchDismissed = true
                                    status = DeviceCodeLoginUiStatus.PollingAuthorization
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val DUMMY_USER_CODE = "DEMO-0000"
        const val DUMMY_VERIFICATION_URI = "https://example.invalid/device"
    }
}
