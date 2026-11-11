package com.alice.quotarabbit.ui.home

import android.content.res.Configuration
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import org.junit.Rule
import org.junit.Test

/** Restores the emulator timezone after a real system change; never changes the clock or app preferences. */
class HomeTimeZoneTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun sameSnapshotUpdatesAfterTimeZoneBroadcastWithoutReloadingQuota() {
        val original = TimeZone.getDefault()
        try {
            setSystemTimeZone("UTC")
            val instant = Instant.parse("2026-01-01T15:30:00Z")
            compose.setContent {
                val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.US) }
                CompositionLocalProvider(LocalConfiguration provides configuration) {
                    Text(formattedInstant(instant), Modifier.testTag("timestamp"))
                }
            }
            compose.onNodeWithTag("timestamp").assertTextContains("15:30", substring = true)
            // The platform emits the real protected event and updates its timezone cache.
            // Merely broadcasting after TimeZone.setDefault is invalid: Android resets that
            // process override to the unchanged system zone before application receivers run.
            setSystemTimeZone("Asia/Seoul")
            compose.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    compose.onNodeWithTag("timestamp").assertTextContains("00:30", substring = true)
                }.isSuccess
            }
            compose.waitForIdle()
            compose.onNodeWithTag("timestamp").assertTextContains("26.01.02.", substring = true)
            compose.onNodeWithTag("timestamp").assertTextContains("00:30", substring = true)
        } finally {
            setSystemTimeZone(original.id)
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun localeChangeReformatsSameSuccessfulInstant() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val locale = mutableStateOf(Locale.US)
            compose.setContent {
                val configuration = Configuration(LocalConfiguration.current).apply { setLocale(locale.value) }
                CompositionLocalProvider(LocalConfiguration provides configuration) {
                    Text(formattedInstant(Instant.parse("2026-01-02T03:04:00Z")), Modifier.testTag("timestamp"))
                }
            }
            compose.onNodeWithTag("timestamp").assertTextContains("26.01.02.", substring = true)
            compose.runOnIdle { locale.value = Locale.UK }
            compose.onNodeWithTag("timestamp").assertTextContains("26.01.02. 03:04", substring = true)
            compose.onNodeWithTag("timestamp").assertTextContains("03:04", substring = true)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    private fun setSystemTimeZone(zone: String) {
        require(zone.matches(Regex("[A-Za-z0-9_+/-]+")))
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "cmd alarm set-timezone $zone",
        ).use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
        compose.waitUntil(timeoutMillis = 5_000) { TimeZone.getDefault().id == zone }
    }
}
