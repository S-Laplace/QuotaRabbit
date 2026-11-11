package com.alice.quotarabbit.ui.theme

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inspector.WindowInspector
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.theme.ThemeMode
import com.alice.quotarabbit.ui.typography.TypographyPreviewActivity
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real offline routes and Settings, with pixel checks independent of native transition frames. */
@RunWith(AndroidJUnit4::class)
class SolidBackgroundTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var localizedContext: Context
    private var specimen = "uninitialized"
    private var density = 1f
    private var mainWindowToken: IBinder? = null

    @Test
    fun solidSurfacesAcrossNarrowLightDarkAndLargeFonts() {
        for (locale in listOf("en", "zh-CN")) for (scale in listOf(1f, 2f)) {
            for (dark in listOf(false, true)) launch(locale, scale, if (dark) "dark" else "light") { scenario ->
                assertScreen(scenario, dark, "home", nativeEvidence = true)
                assertCardSurface("dashboard-account:codex:typo-latin", dark)
                compose.onNodeWithTag("dashboard-list").performScrollToIndex(11)
                compose.onNodeWithTag("home-trend-title").assertIsDisplayed()
                assertScreen(scenario, dark, "home_bottom")

                compose.onNodeWithTag("nav-tab:account").performClick()
                assertScreen(scenario, dark, "account")
                assertCardSurface("account-overview:typo-latin", dark)
                scrollAboveNavigation("account-open-details:typo-latin", "account-screen").performClick()
                scrollAboveNavigation("account-action:rename:typo-latin", "account-screen")
                assertScreen(scenario, dark, "account_details")
                compose.onNodeWithTag("account-action:rename:typo-latin").performClick()
                assertDialogSurface("account-edit-dialog", dark)
                cancelDialog("account-edit-dialog")
                compose.onNodeWithTag("account-details-back").performScrollTo().performClick()

                compose.onNodeWithTag("nav-tab:settings").performClick()
                assertScreen(scenario, dark, "settings")
                openSettingsChoice()
                assertDialogSurface("settings-choice-dialog", dark)
                cancelDialog("settings-choice-dialog")
                scrollAboveNavigation("settings-update-check", "settings-screen")
                assertScreen(scenario, dark, "settings_bottom")

                openProviderSheet()
                assertProviderSheetSurface(dark)
                compose.onNodeWithTag("provider-card:codex").performScrollTo().performClick()
                compose.onNodeWithContentDescription(localizedContext.getString(R.string.auth_back)).assertIsDisplayed()
                assertScreen(scenario, dark, "codex_auth")
                compose.onNodeWithContentDescription(localizedContext.getString(R.string.auth_back)).performClick()
                compose.onNodeWithTag("nav-tab:home").performClick()
                assertScreen(scenario, dark, "returned_home")
            }
        }
    }

    @Test
    fun followSystemUpdatesOpenRoutesAndDialogsWhileExplicitModesOverrideIt() = launch("en", 2f, "system") { scenario ->
        scenario.onActivity { it.dispatchSystemNightMode(false) }
        assertScreen(scenario, false, "system_home_light")
        scenario.onActivity { it.dispatchSystemNightMode(true) }
        assertScreen(scenario, true, "system_home_dark")

        compose.onNodeWithTag("nav-tab:account").performClick()
        scrollAboveNavigation("account-open-details:typo-latin", "account-screen").performClick()
        scrollAboveNavigation("account-action:rename:typo-latin", "account-screen").performClick()
        assertDialogSurface("account-edit-dialog", true)
        scenario.onActivity { it.dispatchSystemNightMode(false) }
        assertDialogSurface("account-edit-dialog", false)
        cancelDialog("account-edit-dialog")
        assertScreen(scenario, false, "system_account_details_light")
        compose.onNodeWithTag("account-details-back").performScrollTo().performClick()

        compose.onNodeWithTag("nav-tab:settings").performClick()
        selectAppearance("dark")
        scenario.onActivity { assertEquals(ThemeMode.DARK, it.appearancePreferenceStore.themeMode.value) }
        assertScreen(scenario, true, "explicit_dark_over_light_system")
        scenario.onActivity { it.dispatchSystemNightMode(true) }
        selectAppearance("light")
        scenario.onActivity { assertEquals(ThemeMode.LIGHT, it.appearancePreferenceStore.themeMode.value) }
        assertScreen(scenario, false, "explicit_light_over_dark_system")
        selectAppearance("system")
        assertScreen(scenario, true, "system_settings_dark")
        openSettingsChoice()
        scenario.onActivity { it.dispatchSystemNightMode(false) }
        assertDialogSurface("settings-choice-dialog", false)
        cancelDialog("settings-choice-dialog")
        assertScreen(scenario, false, "system_settings_light")

        openProviderSheet()
        assertProviderSheetSurface(false)
        scenario.onActivity { it.dispatchSystemNightMode(true) }
        assertProviderSheetSurface(true)
        compose.onNodeWithTag("provider-card:codex").performScrollTo().performClick()
        assertScreen(scenario, true, "system_auth_dark")
        scenario.onActivity { it.dispatchSystemNightMode(false) }
        assertScreen(scenario, false, "system_auth_light", nativeEvidence = true)
    }

    @Test
    fun followSystemRespondsToActualOsNightModeChanges() {
        val originalOutput = shell("cmd uimode night")
        val originalMode = requireNotNull(Regex("Night mode: (yes|no|auto|custom)")
            .find(originalOutput)?.groupValues?.get(1)) { "Cannot preserve OS night mode: $originalOutput" }
        try {
            shell("cmd uimode night no")
            launch("en", 2f, "system") { scenario ->
                waitForSystemNightMode(scenario, false)
                assertScreen(scenario, false, "os_home_light", nativeEvidence = true)
                shell("cmd uimode night yes")
                waitForSystemNightMode(scenario, true)
                assertScreen(scenario, true, "os_home_dark", nativeEvidence = true)
                compose.onNodeWithTag("nav-tab:account").performClick()
                assertScreen(scenario, true, "os_account_dark")
                shell("cmd uimode night no")
                waitForSystemNightMode(scenario, false)
                assertScreen(scenario, false, "os_account_light", nativeEvidence = true)
                compose.onNodeWithTag("nav-tab:settings").performClick()
                compose.onNodeWithTag("settings-appearance:system").performScrollTo().assertIsSelected()
            }
        } finally {
            shell("cmd uimode night $originalMode")
        }
    }

    private fun waitForSystemNightMode(scenario: ActivityScenario<TypographyPreviewActivity>, dark: Boolean) {
        compose.waitUntil(timeoutMillis = 10_000) {
            var actual = !dark
            scenario.onActivity {
                actual = it.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            }
            actual == dark
        }
        compose.waitForIdle()
    }

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    private fun launch(locale: String, scale: Float, mode: String, check: (ActivityScenario<TypographyPreviewActivity>) -> Unit) {
        specimen = "${locale}_${mode}_font${(scale * 100).toInt()}"
        val intent = Intent(context, TypographyPreviewActivity::class.java)
            .putExtra("locale", locale).putExtra("font_scale", scale)
            .putExtra("width_dp", 320).putExtra("theme", mode)
        ActivityScenario.launch<TypographyPreviewActivity>(intent).use { scenario ->
            scenario.onActivity {
                localizedContext = it.specimenContext
                density = it.resources.displayMetrics.density
                mainWindowToken = it.window.decorView.windowToken
            }
            compose.waitForIdle()
            val host = compose.onNodeWithTag("typography-host").fetchSemanticsNode()
            assertEquals("$specimen must render at 320dp", 320f, host.size.width / density, 1f)
            check(scenario)
        }
    }

    private fun assertScreen(scenario: ActivityScenario<TypographyPreviewActivity>, dark: Boolean, route: String,
        nativeEvidence: Boolean = false) {
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag("typography-host").captureToImage().asAndroidBitmap()
        save(bitmap, "${route}_compose")
        val expected = background(dark)
        // Outer gutters remain empty even when content scrolls. Exclude only the actual floating
        // pill and its shadow; sample the safe breathing space below it separately.
        val navigation = compose.onAllNodes(hasTestTag("bottom-navigation")).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
        for (y in 2 until bitmap.height - 2 step maxOf(1, (8 * density).roundToInt())) {
            if (navigation != null && y >= navigation.top - 8 * density && y <= navigation.bottom + 8 * density) continue
            for (x in listOf(1, bitmap.width - 2)) assertPixel(bitmap, x, y, expected, "$route gutter")
        }
        for (x in listOf(1, bitmap.width / 2, bitmap.width - 2)) {
            assertPixel(bitmap, x, 1, expected, "$route top edge")
            assertPixel(bitmap, x, bitmap.height - 2, expected, "$route bottom edge")
        }
        assertSystemBars(scenario, dark)
        if (nativeEvidence) captureNative(scenario, dark, route)
    }

    private fun assertCardSurface(tag: String, dark: Boolean) {
        val bitmap = compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().captureToImage().asAndroidBitmap()
        // Empty inner side padding avoids text, avatars, border antialiasing and rounded corners.
        assertPatch(bitmap, (5 * density).roundToInt(), bitmap.height / 2, surface(dark), tag)
        assertTrue("$specimen $tag must be distinct from the page", colorDistance(surface(dark), background(dark)) >= 9)
    }

    private fun assertDialogSurface(tag: String, dark: Boolean) {
        val node = compose.onNodeWithTag(tag).assertIsDisplayed()
        val bitmap = node.captureToImage().asAndroidBitmap()
        for (part in listOf(0.33f, 0.5f, 0.67f)) {
            assertPatch(bitmap, (5 * density).roundToInt(), (bitmap.height * part).roundToInt(), surface(dark), tag)
        }
        save(bitmap, "${tag}_${if (dark) "dark" else "light"}_compose")
        compose.runOnIdle {
            val windows = WindowInspector.getGlobalWindowViews().filter { it.isShown && it.windowToken != mainWindowToken }
            assertTrue("$specimen $tag must own a visible native window", windows.isNotEmpty())
            for (view in windows) {
                val appearance = requireNotNull(view.windowInsetsController).systemBarsAppearance
                assertEquals("$specimen $tag status bar icon brightness", !dark,
                    appearance and WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS != 0)
                assertEquals("$specimen $tag navigation bar icon brightness", !dark,
                    appearance and WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS != 0)
            }
        }
    }

    private fun assertProviderSheetSurface(dark: Boolean) {
        compose.onNodeWithTag("provider-card:codex").performScrollTo().assertIsDisplayed()
        val row = compose.onNodeWithTag("provider-card:codex").fetchSemanticsNode().boundsInRoot
        val bitmap = compose.onNodeWithTag("typography-host").captureToImage().asAndroidBitmap()
        assertPatch(bitmap, (3 * density).roundToInt(), row.center.y.roundToInt(), surface(dark), "provider sheet edge")
        assertPatch(bitmap, (3 * density).roundToInt(), bitmap.height - (5 * density).roundToInt(), surface(dark), "provider sheet bottom")
        save(bitmap, "provider_sheet_${if (dark) "dark" else "light"}_compose")
    }

    private fun selectAppearance(mode: String) {
        scrollAboveNavigation("settings-appearance:$mode", "settings-screen").performClick()
        compose.onNodeWithTag("settings-appearance:$mode").assertIsSelected()
    }

    private fun openSettingsChoice() {
        scrollAboveNavigation(compose.onNodeWithText(localizedContext.getString(
            R.string.settings_notification_account_title)), "settings-screen").performClick()
        compose.onNodeWithTag("settings-choice-dialog").assertIsDisplayed()
    }

    private fun openProviderSheet() {
        compose.onNodeWithTag("nav-tab:account").performClick()
        // The add affordance is in scroll content, so return to the header after viewing details.
        compose.onNodeWithTag("account-title").performScrollTo()
        compose.onNodeWithContentDescription(localizedContext.getString(R.string.account_add_account)).performClick()
    }

    private fun cancelDialog(tag: String) {
        compose.onNode(hasText(localizedContext.getString(R.string.account_cancel)) and hasAnyAncestor(hasTestTag(tag)))
            .performClick()
    }

    private fun scrollAboveNavigation(tag: String, scrollTag: String): SemanticsNodeInteraction =
        scrollAboveNavigation(compose.onNodeWithTag(tag), scrollTag)

    private fun scrollAboveNavigation(target: SemanticsNodeInteraction, scrollTag: String): SemanticsNodeInteraction {
        val node = target.performScrollTo()
        // Account Details is a full-screen destination without the root bottom nav.
        // On tabs, clear the floating nav; on details, clear the host viewport edge.
        val top = compose.onAllNodes(hasTestTag("bottom-navigation")).fetchSemanticsNodes()
            .firstOrNull()?.boundsInRoot?.top
            ?: compose.onNodeWithTag("typography-host").fetchSemanticsNode().boundsInRoot.bottom
        val overlap = node.fetchSemanticsNode().boundsInRoot.bottom - top
        if (overlap > 0) compose.onNodeWithTag(scrollTag).performSemanticsAction(SemanticsActions.ScrollBy) {
            it(0f, overlap + 16 * density)
        }
        node.assertIsDisplayed()
        assertTrue("$specimen essential control must clear navigation", node.fetchSemanticsNode().boundsInRoot.bottom <= top + 1)
        return node
    }

    private fun assertSystemBars(scenario: ActivityScenario<TypographyPreviewActivity>, dark: Boolean) {
        scenario.onActivity {
            val controller = WindowCompat.getInsetsController(it.window, it.window.decorView)
            assertEquals("$specimen status bar icon brightness", !dark, controller.isAppearanceLightStatusBars)
            assertEquals("$specimen navigation bar icon brightness", !dark, controller.isAppearanceLightNavigationBars)
        }
    }

    private fun captureNative(scenario: ActivityScenario<TypographyPreviewActivity>, dark: Boolean, route: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        var lastFailure: AssertionError? = null
        // SystemUI renders its own icon transition after the Activity's Configuration and window
        // flags have changed. Keep every frame and require the same pixel gate within six captures.
        for (attempt in 1..6) {
            SystemClock.sleep(if (attempt == 1) 350 else 200)
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            if (bitmap == null) {
                lastFailure = AssertionError("$specimen $route native screenshot API returned null, attempt $attempt")
                continue
            }
            save(bitmap, "${route}_window_attempt$attempt")
            var statusInset = 0
            var navigationInset = 0
            scenario.onActivity {
                val insets = it.windowManager.currentWindowMetrics.windowInsets
                    .getInsets(WindowInsets.Type.systemBars())
                statusInset = insets.top
                navigationInset = insets.bottom
            }
            try {
                val expected = background(dark)
                // Left edge is clear of status text and the gesture handle. Reject blank frames
                // and dimmed foreign overlays, including when Compose's own capture is valid.
                for (y in listOf(1, maxOf(1, statusInset / 2), bitmap.height / 2, bitmap.height - 2)) {
                    assertPixel(bitmap, 1, y, expected, "$route native system-bar edge")
                }
                assertTrue("$specimen $route must have a visible status bar", statusInset > 0)
                var iconPixels = 0
                for (y in 1 until statusInset) for (x in 16 until bitmap.width - 16) {
                    val pixel = bitmap.getPixel(x, y)
                    val luminance = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                    if (if (dark) luminance > 180 else luminance < 100) iconPixels++
                }
                assertTrue("$specimen $route status icons must remain visible: dark=$dark, " +
                    "statusHeight=$statusInset, readablePixels=$iconPixels, attempt=$attempt", iconPixels > 20)
                assertTrue("$specimen $route must have a visible navigation bar", navigationInset > 0)
                var navigationPixels = 0
                for (y in bitmap.height - navigationInset until bitmap.height - 1) {
                    for (x in bitmap.width / 4 until bitmap.width * 3 / 4) {
                        val pixel = bitmap.getPixel(x, y)
                        val luminance = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                        if (if (dark) luminance > 180 else luminance < 100) navigationPixels++
                    }
                }
                assertTrue("$specimen $route navigation glyphs must remain visible: dark=$dark, " +
                    "navigationHeight=$navigationInset, readablePixels=$navigationPixels, attempt=$attempt",
                    navigationPixels > 20)
                save(bitmap, "${route}_window")
                return
            } catch (failure: AssertionError) {
                lastFailure = failure
            } finally {
                bitmap.recycle()
            }
        }
        throw AssertionError("$specimen $route native frame did not converge after 6 captures: ${lastFailure?.message}", lastFailure)
    }

    private fun assertPatch(bitmap: Bitmap, x: Int, y: Int, expected: Int, label: String) {
        for (dx in -1..1) for (dy in -1..1) assertPixel(bitmap, x + dx, y + dy, expected, label)
    }

    private fun assertPixel(bitmap: Bitmap, x: Int, y: Int, expected: Int, label: String) {
        val actual = bitmap.getPixel(x, y)
        assertTrue("$specimen $label pixel($x,$y) expected=${Integer.toHexString(expected)} actual=${Integer.toHexString(actual)}",
            Color.alpha(actual) == 255 && colorDistance(actual, expected) <= 2)
    }

    private fun colorDistance(first: Int, second: Int) = maxOf(abs(Color.red(first) - Color.red(second)),
        abs(Color.green(first) - Color.green(second)), abs(Color.blue(first) - Color.blue(second)))

    private fun background(dark: Boolean) = if (dark) Color.rgb(24, 24, 24) else Color.rgb(246, 246, 246)
    private fun surface(dark: Boolean) = if (dark) Color.rgb(36, 36, 36) else Color.WHITE

    private fun save(bitmap: Bitmap, name: String) {
        val directoryName = InstrumentationRegistry.getArguments().getString("solid_evidence_directory") ?: "solid-background-ui"
        val directory = File(context.getExternalFilesDir(null) ?: context.filesDir, directoryName)
        assertTrue(directory.exists() || directory.mkdirs())
        FileOutputStream(File(directory, "${specimen}_$name.png")).use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
