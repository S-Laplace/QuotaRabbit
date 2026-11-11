package com.alice.quotarabbit.ui.home

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alice.quotarabbit.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/** Exercises production Compose rendering with debug-only, local specimens; no provider calls. */
@RunWith(AndroidJUnit4::class)
class HomeDashboardScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var previewAccounts: List<HomeDashboardAccountUi> = emptyList()

    @Test
    fun threeCodexAccountsShareDashboardWithoutSwitching() = preview("codex") {
        for ((id, value) in listOf("personal" to "82%", "work" to "62%", "team" to "6%")) {
            // Metadata and large fonts can make cards taller than the viewport.
            scrollTo("codex", id)
            compose.onNodeWithTag(tag("codex", id)).assertIsDisplayed()
            compose.onNode(hasText(value) and hasAnyAncestor(hasTestTag(tag("codex", id))))
                .performScrollTo().assertIsDisplayed()
            screenshot("home_three_codex_light_$id")
        }
    }

    @Test
    fun mixedProviderUnitsAndUnknownValues() {
        for (theme in listOf("light", "dark")) preview("mixed", theme) {
            val prefix = if (theme == "light") "home" else "home_dark"
            scrollTo("deepseek", "balance")
            compose.onNodeWithTag(tag("deepseek", "balance")).assert(
                hasAnyDescendant(hasText("42.50", substring = true)),
            )
            screenshot("${prefix}_balance")
            scrollTo("cursor", "requests")
            compose.onNodeWithTag(tag("cursor", "requests")).assert(
                hasAnyDescendant(hasText("125", substring = true)),
            )
            compose.onNodeWithTag(tag("cursor", "requests")).assert(
                hasAnyDescendant(hasText("500", substring = true)),
            )
            screenshot("${prefix}_counts")
            scrollTo("antigravity", "models")
            compose.onNodeWithText("Model A").assertIsDisplayed()
            compose.onNodeWithText("Model B").assertIsDisplayed()
            compose.onNodeWithTag(tag("antigravity", "models")).assert(
                !hasAnyDescendant(hasText(context.getString(R.string.home_quota_status_unavailable))),
            )
            screenshot("${prefix}_models")
            scrollTo("kimi", "cycle")
            compose.onNodeWithTag(tag("kimi", "cycle")).assert(
                hasAnyDescendant(hasText(context.getString(R.string.home_quota_status_unavailable))),
            )
            screenshot("${prefix}_unknown")
        }
    }

    @Test
    fun failedAndExpiredAuthenticationKeepTheirOwnCachedQuota() = preview("states") {
        scrollTo("codex", "work")
        compose.onNodeWithTag(tag("codex", "work")).assert(
            hasAnyDescendant(hasText(context.getString(R.string.home_state_error_lkg_title))),
        )
        compose.onNodeWithTag(tag("codex", "work")).assert(hasAnyDescendant(hasText("62%")))
        scrollTo("codex", "team")
        compose.onNodeWithTag(tag("codex", "team")).assert(
            hasAnyDescendant(hasText(context.getString(R.string.home_state_auth_required_title))),
        )
        compose.onNodeWithTag(tag("codex", "team")).assert(hasAnyDescendant(hasText("6%")))
        screenshot("home_failed_auth_cached")
        for (id in listOf("stale", "expired", "refreshing", "disconnected")) scrollTo("codex", id)
        scrollTo("deepseek", "unknown")
        compose.onNodeWithTag(tag("deepseek", "unknown")).assert(
            hasAnyDescendant(hasText(context.getString(R.string.home_state_no_data_title))),
        )
        screenshot("home_no_snapshot")
    }

    @Test
    fun narrowLargeFontBothThemesRemainScrollable() {
        for (theme in listOf("light", "dark")) preview("codex", theme, 320, 1.5f) {
            screenshot("home_${theme}_320dp_font150_top")
            for (id in listOf("personal", "work", "team")) scrollTo("codex", id)
            compose.onNodeWithText("Team").assertIsDisplayed()
            screenshot("home_${theme}_320dp_font150_bottom")
            // Extra heatmap height can place the trend item outside LazyColumn composition.
            // Scroll by its stable item index before asserting child semantics.
            val trendIndex = 1 + previewAccounts.groupBy { it.providerId }.size + previewAccounts.size
            compose.onNodeWithTag("dashboard-list").performScrollToIndex(trendIndex)
            assertTextNotClipped("home-trend-title")
            assertTextNotClipped("home-trend-metric")
        }
    }

    @Test
    fun cachedLoadingAndEmptyScreensRenderOffline() {
        preview("cached") {
            compose.onNodeWithTag(tag("codex", "personal")).assertIsDisplayed()
            screenshot("home_cached_offline")
        }
        preview("loading") {
            compose.onNodeWithText(context.getString(R.string.home_loading_card_title)).assertIsDisplayed()
            screenshot("home_loading")
        }
        preview("empty") {
            compose.onNodeWithText(context.getString(R.string.home_state_unauthenticated_title)).assertIsDisplayed()
            screenshot("home_empty")
        }
    }

    private fun scrollTo(provider: String, id: String) {
        // Home has one title item followed by each provider heading and its account items.
        // Absolute index scrolling avoids Compose's relative search loop on large-font cards.
        var index = 1
        var targetIndex: Int? = null
        for (accounts in previewAccounts.groupBy { it.providerId }.values) {
            index += 1
            for (account in accounts) {
                if (account.providerId.value == provider && account.localAccountId.value == "preview-$id") {
                    targetIndex = index
                }
                index += 1
            }
        }
        compose.onNodeWithTag("dashboard-list").performScrollToIndex(requireNotNull(targetIndex))
        compose.onNodeWithTag(tag(provider, id)).assertIsDisplayed()
    }

    private fun tag(provider: String, id: String) = "dashboard-account:$provider:preview-$id"

    private fun assertTextNotClipped(tag: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("$tag must expose its text layout", layouts.isNotEmpty())
        // Compose rounds a text node to integer pixels while line bounds retain fractions.
        assertTrue("$tag must render its full localized text: ${layouts.map { "${it.size} lines=${it.lineCount} right=${it.getLineRight(it.lineCount - 1)}" }}",
            layouts.none { layout ->
                layout.didOverflowHeight || (0 until layout.lineCount).any { line ->
                    layout.getLineRight(line) > layout.size.width + 1f
                }
            })
    }

    private fun preview(
        scenario: String, theme: String = "light", widthDp: Int = 0, fontScale: Float = 1f,
        check: () -> Unit,
    ) {
        previewAccounts = HomePreviewFixtures.state(scenario).accountCards
        val intent = Intent(context, HomePreviewActivity::class.java)
            .putExtra("scenario", scenario).putExtra("theme", theme)
            .putExtra("width_dp", widthDp).putExtra("font_scale", fontScale)
        ActivityScenario.launch<HomePreviewActivity>(intent).use {
            compose.waitForIdle()
            check()
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        // Compose idleness does not include the system's Activity/window transition.
        SystemClock.sleep(400)
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val outDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "quota-ui-home")
        assertTrue(outDir.exists() || outDir.mkdirs())
        FileOutputStream(File(outDir, "$name.png")).use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
