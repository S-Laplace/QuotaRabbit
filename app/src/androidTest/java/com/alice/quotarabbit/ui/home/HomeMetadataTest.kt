package com.alice.quotarabbit.ui.home

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alice.quotarabbit.R
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Production Home composables with synthetic display facts; never connects to a live account. */
@RunWith(AndroidJUnit4::class)
class HomeMetadataTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var localizedContext: Context
    private lateinit var cards: List<HomeDashboardAccountUi>
    private var specimen = "uninitialized"
    private var english = true

    @Test
    fun activityIsHiddenByDefaultAndShownOnlyForExpandedAccount() = launch("en", "light", 1f) {
        val first = cards.first()
        val second = cards[1]
        scrollTo(first)
        tagged(first, "dashboard-activity").assertDoesNotExist()
        tagged(first, "dashboard-renewal").assertDoesNotExist()
        tagged(first, "dashboard-last-success").assertDoesNotExist()
        tagged(first, "dashboard-accordion-toggle").performClick()
        tagged(first, "dashboard-activity").assertExists()
        tagged(first, "dashboard-last-success").assertExists()
        val hourlyTag = "dashboard-activity-mode:${first.providerId.value}:${first.localAccountId.value}:Hourly"
        compose.onNodeWithTag(hourlyTag).performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(compose.onNodeWithTag(hourlyTag).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Selected) == true)
        scrollTo(second)
        tagged(second, "dashboard-activity").assertDoesNotExist()
        scrollTo(first)
        tagged(first, "dashboard-accordion-toggle").performClick()
        tagged(first, "dashboard-activity").assertDoesNotExist()
        tagged(first, "dashboard-renewal").assertDoesNotExist()
        tagged(first, "dashboard-last-success").assertDoesNotExist()
        assertText(tagged(first, "dashboard-banked"), "Banked resets: Unavailable")
        tagged(first, "dashboard-accordion-toggle").performClick()
        assertTrue("Hourly selection must survive a temporary collapse",
            compose.onNodeWithTag(hourlyTag).fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.Selected) == true)
    }

    @Test
    fun unknownMetadataIsExplicitAndKeepsSuccessfulTime() = launch("en", "light", 1f) {
        for (card in cards.take(3)) {
            scrollTo(card)
            tagged(card, "dashboard-last-success").assertDoesNotExist()
            tagged(card, "dashboard-accordion-toggle").performClick()
            assertText(tagged(card, "dashboard-banked"), "Banked resets: Unavailable")
            tagged(card, "dashboard-renewal").assertDoesNotExist()
            val success = requireNotNull(card.state.refresh.lastSuccessfulRefreshAt)
            assertText(tagged(card, "dashboard-last-success"), localizedContext.getString(
                R.string.home_last_refresh_format, formatted(success)))
            val displayed = text(tagged(card, "dashboard-last-success"))
            assertFalse("$specimen must show success rather than the later failed attempt",
                displayed.contains(formatted(requireNotNull(card.state.refresh.lastAttemptFinishedAt))))
            screenshot("${card.localAccountId.value}_metadata")
        }
    }

    @Test
    fun independentCodexQuotaAndMetadataRemainReadableAcrossTypographyMatrix() {
        for (locale in listOf("en", "zh-CN")) for (theme in listOf("light", "dark")) {
            for (scale in listOf(1f, 1.5f, 2f)) launch(locale, theme, scale) {
                assertEquals(3, cards.count { it.providerId.value == "codex" })
                for (card in cards.take(3)) {
                    scrollTo(card)
                    tagged(card, "dashboard-accordion-toggle").performClick()
                    assertText(scopedText(card, requireNotNull(card.state.account).displayName),
                        requireNotNull(card.state.account).displayName)
                    for (quota in card.state.quotaCards) {
                        assertPercent(card, requireNotNull(quota.percent))
                        assertText(scopedText(card, localizedContext.getString(quota.titleResId)),
                            localizedContext.getString(quota.titleResId))
                        assertReset(card, requireNotNull(quota.resetAt))
                    }
                    if (card.state.contentStatus == HomeContentStatus.Fresh) {
                        scopedText(card, localizedContext.getString(card.state.statusTitleResId))
                            .assertDoesNotExist()
                    } else {
                        assertText(scopedText(card, localizedContext.getString(card.state.statusTitleResId)),
                            localizedContext.getString(card.state.statusTitleResId))
                    }
                    assertUnavailable(card, "dashboard-banked", "Banked resets: Unavailable")
                    tagged(card, "dashboard-renewal").assertDoesNotExist()
                    assertSuccess(card)
                    screenshot("${card.localAccountId.value}_successful_metadata")
                }
            }
        }
    }

    @Test
    fun mixedProviderUnitsAndMissingMetadataStayIndependent() {
        for (locale in listOf("en", "zh-CN")) for (theme in listOf("light", "dark")) {
            launch(locale, theme, 2f) {
                for (card in cards.drop(3)) {
                    scrollTo(card)
                    tagged(card, "dashboard-accordion-toggle").performScrollTo().performClick()
                    compose.waitForIdle()
                    tagged(card, "dashboard-last-success").assertExists()
                    tagged(card, "dashboard-banked").assertDoesNotExist()
                    for (quota in card.state.quotaCards) {
                        when (card.providerId.value) {
                            "deepseek" -> {
                                assertNoPercentGauge(card)
                                assertText(scopedText(card, "$42.50"), "$42.50")
                                assertText(scopedText(card, localizedContext.getString(R.string.quota_balance_breakdown_format,
                                    "$10.00", "$32.50")), localizedContext.getString(
                                    R.string.quota_balance_breakdown_format, "$10.00", "$32.50"))
                                scopedText(card, localizedContext.getString(R.string.home_reset_unavailable)).assertDoesNotExist()
                            }
                            "cursor" -> {
                                assertNoPercentGauge(card)
                                val counts = localizedContext.getString(R.string.dashboard_used_count_format, "125", "500")
                                assertText(scopedText(card, counts), counts)
                                val remaining = localizedContext.getString(R.string.dashboard_remaining_count_format, 375)
                                assertText(scopedText(card, remaining), remaining)
                                assertText(scopedText(card, localizedContext.getString(R.string.home_reset_unavailable)),
                                    localizedContext.getString(R.string.home_reset_unavailable))
                            }
                            "antigravity" -> for ((index, model) in quota.modelBuckets.withIndex()) {
                                assertText(scopedText(card, model.displayName), model.displayName)
                                assertPercent(card, listOf(82, 24)[index])
                                assertReset(card, requireNotNull(model.resetAt))
                            }
                            "kimi" -> {
                                assertText(scopedText(card, localizedContext.getString(R.string.dashboard_unknown)),
                                    localizedContext.getString(R.string.dashboard_unknown))
                                assertText(scopedText(card, localizedContext.getString(R.string.home_quota_status_unavailable)),
                                    localizedContext.getString(R.string.home_quota_status_unavailable))
                                assertText(scopedText(card, localizedContext.getString(R.string.home_reset_unavailable)),
                                    localizedContext.getString(R.string.home_reset_unavailable))
                            }
                        }
                    }
                    tagged(card, "dashboard-renewal").assertDoesNotExist()
                    if (card.state.refresh.lastSuccessfulRefreshAt == null) {
                        assertText(tagged(card, "dashboard-last-success"),
                            localizedContext.getString(R.string.account_last_refresh_unavailable))
                        assertText(scopedText(card, localizedContext.getString(R.string.home_state_no_data_title)),
                            localizedContext.getString(R.string.home_state_no_data_title))
                    } else assertSuccess(card)
                    screenshot("${card.providerId.value}_${card.localAccountId.value}_metadata")
                }
            }
        }
    }

    @Test
    fun cachedExpiredAndDisconnectedCardsRetainSuccessfulMetadata() {
        for ((locale, theme) in listOf("en" to "light", "zh-CN" to "dark")) {
            launch(locale, theme, 1.5f, scenarioName = "cached") {
                for (card in cards) {
                    scrollTo(card)
                    tagged(card, "dashboard-accordion-toggle").performClick()
                    assertSuccess(card)
                }
            }
            launch(locale, theme, 2f, scenarioName = "states") {
                for (card in cards) {
                    scrollTo(card)
                    tagged(card, "dashboard-accordion-toggle").performClick()
                    val status = if (card.isDisconnected) R.string.dashboard_disconnected else card.state.statusTitleResId
                    if (!card.isDisconnected && card.state.contentStatus == HomeContentStatus.Fresh) {
                        scopedText(card, localizedContext.getString(status)).assertDoesNotExist()
                    } else {
                        assertText(scopedText(card, localizedContext.getString(status)), localizedContext.getString(status))
                    }
                    if (card.state.refresh.lastSuccessfulRefreshAt != null) assertSuccess(card)
                    else assertText(tagged(card, "dashboard-last-success"),
                        localizedContext.getString(R.string.account_last_refresh_unavailable))
                    if (card.providerId.value == "codex") {
                        assertUnavailable(card, "dashboard-banked", "Banked resets: Unavailable")
                        for (quota in card.state.quotaCards) assertPercent(card, requireNotNull(quota.percent))
                    }
                    tagged(card, "dashboard-renewal").assertDoesNotExist()
                    if (card.localAccountId.value in listOf("preview-expired", "preview-disconnected")) {
                        screenshot("${card.localAccountId.value}_cached_metadata")
                    }
                }
            }
        }
    }

    @Test
    fun refreshCompletionChangesOnlyItsOwnSuccessfulTimestampAndQuota() = launch("en", "dark", 1.5f) { scenario ->
        val original = cards.take(3)
        val work = original[1]
        val failedAt = HomePreviewFixtures.metadataNow.plusSeconds(3600)
        scenario.onActivity { activity -> activity.updateAccount(work.localAccountId.value) { state ->
            state.copy(refresh = state.refresh.copy(lastAttemptFinishedAt = failedAt))
        } }
        compose.waitForIdle()
        for (card in original) {
            scrollTo(card)
            tagged(card, "dashboard-accordion-toggle").performClick()
            assertSuccess(card)
        }

        val successfulAt = HomePreviewFixtures.metadataNow.plusSeconds(7200)
        val newReset = successfulAt.plusSeconds(18000)
        scenario.onActivity { activity -> activity.updateAccount(work.localAccountId.value) { state ->
            state.copy(contentStatus = HomeContentStatus.Fresh, statusTitleResId = R.string.home_state_fresh_title,
                errorMessageResId = null, quotaCards = state.quotaCards.mapIndexed { index, quota ->
                    if (index == 0) quota.copy(usedPercent = 53, resetAt = newReset) else quota
                }, refresh = state.refresh.copy(lastSuccessfulRefreshAt = successfulAt,
                    lastAttemptFinishedAt = successfulAt))
        } }
        compose.waitForIdle()
        for (card in original) {
            scrollTo(card)
            if (card == work) {
                assertPercent(card, 47)
                assertPercent(card, 27)
                assertReset(card, newReset)
                assertText(tagged(card, "dashboard-last-success"), localizedContext.getString(
                    R.string.home_last_refresh_format, formatted(successfulAt)))
                screenshot("work_after_success")
            } else {
                for (quota in card.state.quotaCards) assertPercent(card, requireNotNull(quota.percent))
                assertSuccess(card)
            }
            assertUnavailable(card, "dashboard-banked", "Banked resets: Unavailable")
            tagged(card, "dashboard-renewal").assertDoesNotExist()
        }
        scenario.onActivity { activity ->
            assertEquals(listOf("preview-personal"), activity.previewState.accountCards.filter { it.isCurrent }
                .map { it.localAccountId.value })
        }
    }

    private fun assertUnavailable(card: HomeDashboardAccountUi, prefix: String, expectedEnglish: String) {
        val node = tagged(card, prefix).performScrollTo().assertIsDisplayed()
        if (english) assertEquals(expectedEnglish, text(node)) else assertTrue(
            "$specimen must explicitly disclose unavailable metadata", text(node).endsWith(
                localizedContext.getString(R.string.home_quota_status_unavailable)))
        assertTextLayout(node.fetchSemanticsNode())
    }

    private fun assertSuccess(card: HomeDashboardAccountUi) = assertText(tagged(card, "dashboard-last-success"),
        localizedContext.getString(R.string.home_last_refresh_format,
            formatted(requireNotNull(card.state.refresh.lastSuccessfulRefreshAt))))

    private fun assertReset(card: HomeDashboardAccountUi, resetAt: Instant) {
        val absolute = localizedContext.getString(R.string.home_reset_at_format, formatted(resetAt))
        assertText(scopedText(card, absolute), absolute)
    }

    private fun assertPercent(card: HomeDashboardAccountUi, remaining: Int) {
        val percent = localizedContext.getString(R.string.home_quota_percent_format, remaining)
        assertText(scopedText(card, percent), percent)
        val gaugeLabel = localizedContext.getString(R.string.dashboard_remaining_format, remaining)
        val gauge = compose.onNode(hasContentDescription(gaugeLabel) and hasAnyAncestor(hasTestTag(accountTag(card))),
            useUnmergedTree = true).fetchSemanticsNode()
        val progress = requireNotNull(gauge.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo))
        assertEquals("$specimen circular gauge must encode remaining quota", remaining / 100f, progress.current, 0.001f)
        assertEquals(0f..1f, progress.range)
    }

    private fun assertNoPercentGauge(card: HomeDashboardAccountUi) {
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo) and
            hasAnyAncestor(hasTestTag(accountTag(card))), useUnmergedTree = true).assertCountEquals(0)
    }

    private fun accountTag(card: HomeDashboardAccountUi) = "dashboard-account:${card.providerId.value}:${card.localAccountId.value}"

    private fun scopedText(card: HomeDashboardAccountUi, value: String) = compose.onNode(
        hasText(value) and hasAnyAncestor(hasTestTag(accountTag(card))), useUnmergedTree = true)

    private fun tagged(card: HomeDashboardAccountUi, prefix: String) = compose.onNodeWithTag(
        "$prefix:${card.providerId.value}:${card.localAccountId.value}", useUnmergedTree = true)

    private fun scrollTo(card: HomeDashboardAccountUi) {
        // Home lists accounts directly after the header and provider chips (no group rows).
        val accountIndex = cards.indexOfFirst {
            it.providerId == card.providerId && it.localAccountId == card.localAccountId
        }
        require(accountIndex >= 0)
        compose.onNodeWithTag("dashboard-list").performScrollToIndex(accountIndex + 2)
    }

    private fun text(node: SemanticsNodeInteraction): String = node.fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }

    private fun assertText(node: SemanticsNodeInteraction, expected: String) {
        node.performScrollTo().assertIsDisplayed()
        assertEquals("$specimen preserves the full metadata value", expected, text(node))
        assertTextLayout(node.fetchSemanticsNode())
    }

    private fun assertTextLayout(node: SemanticsNode) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.runOnIdle { node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts) }
        assertTrue("$specimen text exposes its measured layout", layouts.isNotEmpty())
        for (layout in layouts) {
            val value = layout.layoutInput.text.text
            val diagnostic = "$specimen [$value] size=${layout.size}, lines=${layout.lineCount}"
            assertTrue("$diagnostic exceeds measured height", layout.multiParagraph.height <= layout.size.height + 1f)
            assertTrue("$diagnostic exceeds measured width", (0 until layout.lineCount).all {
                layout.getLineRight(it) - layout.getLineLeft(it) <= layout.size.width + 1f })
            assertFalse("$diagnostic exceeds max lines", layout.multiParagraph.didExceedMaxLines)
            assertTrue("$diagnostic must not ellipsize", (0 until layout.lineCount).none(layout::isLineEllipsized))
            if (english) for (word in Regex("[A-Za-z]{4,}").findAll(value)) {
                assertEquals("$diagnostic splits ${word.value}", layout.getLineForOffset(word.range.first),
                    layout.getLineForOffset(word.range.last))
            }
        }
        val full = Rect(node.positionInRoot.x, node.positionInRoot.y,
            node.positionInRoot.x + node.size.width, node.positionInRoot.y + node.size.height)
        val viewport = compose.onNodeWithTag("dashboard-list").fetchSemanticsNode().boundsInRoot
        assertTrue("$specimen essential text must clear the viewport: $full in $viewport", viewport.containsFully(full))
        assertTrue("$specimen ancestor clips text", node.boundsInRoot.width >= node.size.width - 1f &&
            node.boundsInRoot.height >= node.size.height - 1f)
    }

    private fun Rect.containsFully(other: Rect) = other.left >= left - 1f && other.top >= top - 1f &&
        other.right <= right + 1f && other.bottom <= bottom + 1f

    private fun formatted(value: Instant): String {
        val locale = localizedContext.resources.configuration.locales[0]
        val zone = ZoneId.systemDefault()
        // Independent expected formatter; never call the production display formatter in assertions.
        return DateTimeFormatter.ofPattern("yy.MM.dd. HH:mm", locale).withZone(zone).format(value)
    }

    private fun launch(locale: String, theme: String, scale: Float, scenarioName: String = "metadata",
        check: (ActivityScenario<HomePreviewActivity>) -> Unit) {
        english = locale == "en"
        specimen = "${locale}_${theme}_width320_font${(scale * 100).toInt()}"
        val context: Context = ApplicationProvider.getApplicationContext()
        val intent = Intent(context, HomePreviewActivity::class.java).putExtra("scenario", scenarioName)
            .putExtra("locale", locale).putExtra("theme", theme).putExtra("width_dp", 320).putExtra("font_scale", scale)
            .putExtra("now", HomePreviewFixtures.metadataNow.toString())
        ActivityScenario.launch<HomePreviewActivity>(intent).use { scenario ->
            scenario.onActivity { localizedContext = it.previewContext; cards = it.previewState.accountCards }
            compose.waitForIdle()
            assertEquals("$specimen must use an actual 320dp content viewport", 320f,
                compose.onNodeWithTag("dashboard-list").fetchSemanticsNode().size.width /
                    localizedContext.resources.displayMetrics.density, 1f)
            check(scenario)
        }
    }

    private fun screenshot(route: String) {
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag("home-preview-host").captureToImage().asAndroidBitmap()
        val context: Context = ApplicationProvider.getApplicationContext()
        val directoryName = InstrumentationRegistry.getArguments().getString("home_metadata_evidence_directory")
            ?: "phase04-home-metadata-ui"
        val directory = File(context.getExternalFilesDir(null), directoryName)
        assertTrue(directory.exists() || directory.mkdirs())
        File(directory, "${specimen}_$route.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
