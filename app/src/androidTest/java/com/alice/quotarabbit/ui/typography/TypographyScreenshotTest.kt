package com.alice.quotarabbit.ui.typography

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.WindowInsets
import android.view.inspector.WindowInspector
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
 import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.quota.QuotaWindowDisplayKind
import com.alice.quotarabbit.providers.ProviderRegistry
import com.alice.quotarabbit.ui.quota.formatProviderBalance
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real application navigation and routes, with immutable display fixtures and no live providers. */
@RunWith(AndroidJUnit4::class)
class TypographyScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val appContext: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var localizedContext: Context
    private var activeSpecimen = "uninitialized"
    private var mainWindowToken: android.os.IBinder? = null
    private var safeWindowBounds = android.graphics.Rect()

    @Test
    fun homeQuotaIdentityAndBottomContentAcrossTypographyMatrix() = matrix(case = "home") { specimen ->
        compose.onNodeWithTag("home-title").assertIsDisplayed()
        assertTextLayout(compose.onNodeWithTag("home-title").fetchSemanticsNode(), singleLine = true)
        assertNavigation()
        assertVisibleText()
        screenshot("${specimen}_home_top")

        for ((index, card) in TypographyPreviewFixtures.dashboard().accountStates.withIndex()) {
            val account = requireNotNull(card.account)
            val tag = "dashboard-account:${account.providerId.value}:${account.localAccountId.value}"
            val providerCount = TypographyPreviewFixtures.dashboard().accountStates.take(index + 1)
                .map { it.account?.providerId }.distinct().size
            compose.onNodeWithTag("dashboard-list").performScrollToIndex(1 + providerCount + index)
            compose.onNodeWithTag(tag).assertIsDisplayed()
            compose.onNode(hasText(account.displayName) and hasAnyAncestor(hasTestTag(tag)))
                .assertIsDisplayed()
            assertVisibleText()
            for (window in requireNotNull(card.snapshot).windows) {
                val texts = when (window.displayKind) {
                    QuotaWindowDisplayKind.Percent -> listOf(localizedContext.getString(
                        R.string.home_quota_percent_format, requireNotNull(window.remainingPercent)))
                    QuotaWindowDisplayKind.Balance -> listOf(requireNotNull(
                        formatProviderBalance(window.balanceAmount, window.balanceCurrency)))
                    QuotaWindowDisplayKind.UsageCount -> listOf(localizedContext.getString(
                        R.string.dashboard_used_count_format, window.usedCount.toString(), window.limitCount.toString()))
                    QuotaWindowDisplayKind.MultiModelFraction -> window.modelBuckets.map { it.displayName }
                }
                for (text in texts) {
                    val node = scrollAboveNavigation(compose.onNode(hasText(text) and
                        hasAnyAncestor(hasTestTag(tag))), "dashboard-list")
                    node.assertIsDisplayed()
                    assertVisibleText()
                    if (window.displayKind == QuotaWindowDisplayKind.Percent ||
                        window.displayKind == QuotaWindowDisplayKind.Balance
                    ) assertTextLayout(node.fetchSemanticsNode(), singleLine = true)
                    if (window.displayKind == QuotaWindowDisplayKind.UsageCount) {
                        assertNumericTokensWhole(node.fetchSemanticsNode(), listOf("1250", "50000"))
                    }
                }
                if (window.displayKind == QuotaWindowDisplayKind.MultiModelFraction) {
                    for (model in window.modelBuckets) {
                        val percent = localizedContext.getString(R.string.home_quota_percent_format,
                            (model.remainingFraction * 100).toInt())
                        val node = scrollAboveNavigation(compose.onNode(hasText(percent) and
                            hasAnyAncestor(hasTestTag(tag))), "dashboard-list")
                        assertTextLayout(node.fetchSemanticsNode(), singleLine = true)
                    }
                }
                screenshot("${specimen}_home_${account.localAccountId.value}_${window.windowId.value}")
            }
        }
        compose.onNodeWithTag("dashboard-list").performScrollToIndex(11)
        compose.onNodeWithTag("home-trend-title").assertIsDisplayed()
        assertTextLayout(compose.onNodeWithTag("home-trend-title").fetchSemanticsNode())
        assertTextLayout(compose.onNodeWithTag("home-trend-metric").fetchSemanticsNode())
        assertAboveNavigation(compose.onNodeWithTag("home-trend-metric").fetchSemanticsNode())
        screenshot("${specimen}_home_bottom")
    }

    @Test
    fun accountInformationActionsAndDialogsAcrossTypographyMatrix() = matrix(case = "account") { specimen ->
        compose.onNodeWithTag("nav-tab:account").performClick()
        compose.onNodeWithTag("account-title").assertIsDisplayed()
        assertNavigation()
        assertVisibleText()
        screenshot("${specimen}_account_top")
        for (id in TypographyPreviewFixtures.accountIds) {
            compose.onNodeWithTag("account-overview:$id").assertExists()
            scrollAboveNavigation("account-open-details:$id", "account-screen").performClick()
            val detailTag = "account-details:$id"
            compose.onNodeWithTag(detailTag).assertExists()
            val state = TypographyPreviewFixtures.dashboard().accountStates.single {
                it.account?.localAccountId?.value == id
            }
            for (window in requireNotNull(state.snapshot).windows) {
                val text = when (window.displayKind) {
                    QuotaWindowDisplayKind.Balance -> requireNotNull(
                        formatProviderBalance(window.balanceAmount, window.balanceCurrency))
                    QuotaWindowDisplayKind.UsageCount -> "${window.usedCount} / ${window.limitCount}"
                    else -> localizedContext.getString(R.string.account_quota_percent_format,
                        requireNotNull(window.remainingPercent))
                }
                val quotaAncestor = if (state.account?.providerId?.value == "codex")
                    "account-detail-window:${window.windowId.value}:$id" else detailTag
                // The reference-style hero legitimately repeats the primary percentage.
                // Select the exact window's text instead of assuming it appears once.
                val quota = scrollWithinDetails(compose.onNode(hasText(text) and
                    hasAnyAncestor(hasTestTag(quotaAncestor))))
                quota.assertIsDisplayed()
                assertTextLayout(quota.fetchSemanticsNode(),
                    singleLine = window.displayKind != QuotaWindowDisplayKind.UsageCount)
                if (window.displayKind == QuotaWindowDisplayKind.UsageCount) {
                    assertNumericTokensWhole(quota.fetchSemanticsNode(), listOf("1250", "50000"))
                }
                assertVisibleText()
                screenshot("${specimen}_account_${id}_${window.windowId.value}")
                if (window.displayKind == QuotaWindowDisplayKind.MultiModelFraction) {
                    scrollWithinDetails(compose.onNode(
                        hasText(localizedContext.getString(R.string.account_model_details_title)) and
                            hasAnyAncestor(hasTestTag(detailTag)))).performClick()
                    for (model in window.modelBuckets) {
                        scrollWithinDetails(compose.onNode(hasText(model.displayName) and
                            hasAnyAncestor(hasTestTag(detailTag)))).assertIsDisplayed()
                        assertVisibleText()
                        screenshot("${specimen}_account_${id}_${model.modelId}")
                    }
                }
            }
            for (action in listOf("current", "rename", "relogin", "delete")) {
                val node = scrollWithinDetails("account-action:$action:$id")
                node.assertIsDisplayed()
                assertVisibleText()
            }
            screenshot("${specimen}_account_$id")
            if (id in TypographyPreviewFixtures.accountIds.take(3)) {
                scrollWithinDetails("account-action:rename:$id").performClick()
                assertDialogFitsWindow("account-edit-dialog")
                compose.onNodeWithTag("account-edit-nickname").assertIsDisplayed()
                assertVisibleText()
                screenshot("${specimen}_rename_$id")
                cancelDialog("account-edit-dialog")
                scrollWithinDetails("account-action:delete:$id").performClick()
                assertDialogFitsWindow("account-delete-dialog")
                assertVisibleText()
                screenshot("${specimen}_delete_$id")
                cancelDialog("account-delete-dialog")
            }
            scrollWithinDetails("account-details-back").performClick()
            assertNavigation()
        }
    }

    @Test
    fun settingsChoicesAndNavigationAcrossTypographyMatrix() = matrix(case = "settings") { specimen ->
        compose.onNodeWithTag("nav-tab:settings").performClick()
        assertNavigation()
        for (option in listOf("light", "dark", "system")) {
            val node = scrollAboveNavigation("settings-appearance:$option", "settings-screen")
            node.assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            assertAboveNavigation(node.fetchSemanticsNode())
            assertVisibleText()
        }
        screenshot("${specimen}_settings_appearance")
        scrollAboveNavigation(compose.onNodeWithText(
            localizedContext.getString(R.string.settings_notification_account_title)), "settings-screen").performClick()
        assertDialogFitsWindow("settings-choice-dialog")
        for (name in TypographyPreviewFixtures.accountNames) {
            compose.onNodeWithText(name).performScrollTo().assertIsDisplayed()
            assertVisibleText()
        }
        screenshot("${specimen}_settings_account_dialog")
        cancelDialog("settings-choice-dialog")
        for ((section, labelRes) in listOf(
            "alerts" to R.string.settings_account_errors_title,
            "currency" to R.string.settings_currency_target_title,
            "balance_thresholds" to R.string.settings_balance_threshold_caption,
            "refresh" to R.string.settings_background_refresh_title,
            "refresh_interval" to R.string.settings_refresh_interval_title,
            "retention" to R.string.settings_retention_title,
            "clear_current" to R.string.settings_data_clear_current_title,
            "clear_all" to R.string.settings_data_clear_all_title,
            "diagnostics" to R.string.settings_diagnostics_title,
            "update_auto" to R.string.settings_update_auto_check_title,
            "update_notify" to R.string.settings_update_notify_title,
        )) {
            val node = scrollAboveNavigation(compose.onNodeWithText(localizedContext.getString(labelRes)),
                "settings-screen")
            assertTextLayout(node.fetchSemanticsNode())
            assertVisibleText()
            screenshot("${specimen}_settings_$section")
        }
        // The sliders and balance steppers share translated title prefixes but have distinct text.
        // Inspect each occurrence instead of assuming a neighbouring screenshot includes all rows.
        for (titleRes in listOf(R.string.settings_threshold_caution, R.string.settings_threshold_warning)) {
            val prefix = localizedContext.getString(titleRes)
            val labels = compose.onAllNodes(hasText(prefix, substring = true) and
                hasAnyAncestor(hasTestTag("settings-screen")) and SemanticsMatcher("threshold text layout") {
                    it.config.contains(SemanticsActions.GetTextLayoutResult)
                }, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue("$activeSpecimen must include percent and balance thresholds", labels.size >= 2)
            for (label in labels) {
                val target = compose.onNode(SemanticsMatcher("threshold node ${label.id}") { it.id == label.id },
                    useUnmergedTree = true)
                assertTextLayout(scrollAboveNavigation(target, "settings-screen").fetchSemanticsNode())
                assertVisibleText()
            }
        }
        // The title describes the action; the second Clear button invokes Clear all history.
        scrollAboveNavigation(compose.onAllNodesWithText(
            localizedContext.getString(R.string.settings_data_clear_action))[1], "settings-screen").performClick()
        assertDialogFitsWindow("settings-clear-history-dialog")
        assertVisibleText()
        screenshot("${specimen}_settings_clear_history_dialog")
        cancelDialog("settings-clear-history-dialog")
        scrollAboveNavigation("settings-update-check", "settings-screen").assertIsDisplayed()
        assertAboveNavigation(compose.onNodeWithTag("settings-update-check").fetchSemanticsNode())
        assertVisibleText()
        screenshot("${specimen}_settings_bottom")
    }

    @Test
    fun actualProviderSheetAcrossTypographyMatrix() = matrix(case = "provider_sheet") { specimen ->
        compose.onNodeWithTag("nav-tab:account").performClick()
        compose.onNodeWithContentDescription(localizedContext.getString(R.string.account_add_account)).performClick()
        compose.onNodeWithText(localizedContext.getString(R.string.add_provider_title)).assertIsDisplayed()
        assertVisibleText()
        screenshot("${specimen}_provider_sheet_top")
        for (provider in ProviderRegistry.all) {
            val tag = "provider-card:${provider.providerId.value}"
            val card = compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().fetchSemanticsNode()
            assertFullyVisible(card)
            val labels = compose.onAllNodes(hasAnyAncestor(hasTestTag(tag)) and
                SemanticsMatcher("provider sheet label") {
                    it.config.contains(SemanticsActions.GetTextLayoutResult)
                }, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue("$activeSpecimen provider row needs name and auth method", labels.size >= 2)
            labels.forEach { assertTextLayout(it) }
            assertVisibleText()
        }
        screenshot("${specimen}_provider_sheet_bottom")
    }

    @Test
    fun staticAuthenticationChromeAcrossTypographyMatrix() = matrix(screen = "auth") { specimen ->
        compose.onNodeWithTag("typography-auth-sample").assertIsDisplayed()
        compose.onNodeWithText(TypographyPreviewActivity.AUTH_SAMPLE_TITLE).assertIsDisplayed()
        assertVisibleText()
        screenshot("${specimen}_auth_header")
    }

    @Test
    fun providerPickerAndBlankApiKeyFormAcrossTypographyMatrix() = matrix(screen = "providers") { specimen ->
        val picker = compose.onNode(hasScrollToIndexAction() and
            hasAnyAncestor(hasTestTag("typography-provider-picker")))
        for (provider in ProviderRegistry.all) {
            val tag = "provider-card:${provider.providerId.value}"
            picker.performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            compose.onNode(hasText(provider.displayName) and hasAnyAncestor(hasTestTag(tag)),
                useUnmergedTree = true).assertIsDisplayed()
            val labels = compose.onAllNodes(hasAnyAncestor(hasTestTag(tag)) and
                SemanticsMatcher("provider name or auth method text") {
                    it.config.contains(SemanticsActions.GetTextLayoutResult)
                }, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue("$activeSpecimen must show provider name and authentication method for $tag", labels.size >= 2)
            labels.forEach { assertTextLayout(it) }
            if (specimen.endsWith("font200") && provider.providerId.value in listOf("kimi_code", "zai")) {
                screenshot("${specimen}_provider_${provider.providerId.value}")
            }
        }
        picker.performScrollToNode(hasTestTag("provider-card:deepseek"))
        compose.onNodeWithTag("provider-card:deepseek").assertIsDisplayed()
        assertVisibleText()
        screenshot("${specimen}_provider_picker")
        compose.onNodeWithTag("provider-card:deepseek").performClick()
        compose.onNodeWithTag("typography-api-key-sample").assertIsDisplayed()
        assertVisibleText()
        screenshot("${specimen}_api_key_top")
        compose.onNodeWithTag("api-key-submit").performScrollTo().assertIsDisplayed()
            .assertIsNotEnabled().assertHeightIsAtLeast(48.dp)
        assertVisibleText()
        screenshot("${specimen}_api_key_bottom")
    }

    private fun matrix(screen: String = "nav", case: String = screen, check: (String) -> Unit) {
        val arguments = InstrumentationRegistry.getArguments()
        val localeFilter = arguments.getString("typography_locale")
        val themeFilter = arguments.getString("typography_theme")
        val scaleArgument = arguments.getString("typography_scale")
        val scaleFilter = scaleArgument?.toFloatOrNull()?.let { if (it >= 10f) it / 100f else it }
        require(localeFilter == null || localeFilter in listOf("en", "ko", "zh-CN")) { "Invalid typography_locale" }
        require(themeFilter == null || themeFilter in listOf("light", "dark")) { "Invalid typography_theme" }
        require(scaleArgument == null || scaleFilter in listOf(1f, 1.5f, 2f)) { "Invalid typography_scale" }
        for (locale in listOf("en", "ko", "zh-CN")) {
            if (localeFilter != null && locale != localeFilter) continue
            for (theme in listOf("light", "dark")) {
                if (themeFilter != null && theme != themeFilter) continue
                for (scale in listOf(1f, 1.5f, 2f)) {
                    if (scaleFilter != null && scale != scaleFilter) continue
                    val intent = Intent(appContext, TypographyPreviewActivity::class.java)
                        .putExtra("locale", locale).putExtra("theme", theme)
                        .putExtra("width_dp", 320).putExtra("font_scale", scale)
                        .putExtra("screen", screen)
                    ActivityScenario.launch<TypographyPreviewActivity>(intent).use { scenario ->
                        scenario.onActivity {
                            localizedContext = it.specimenContext
                            mainWindowToken = it.window.decorView.windowToken
                            val metrics = it.windowManager.currentWindowMetrics
                            val insets = metrics.windowInsets.getInsets(
                                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                            safeWindowBounds = android.graphics.Rect(metrics.bounds).apply {
                                left += insets.left
                                top += insets.top
                                right -= insets.right
                                bottom -= insets.bottom
                            }
                        }
                        compose.waitForIdle()
                        val specimen = "${locale}_${theme}_font${(scale * 100).toInt()}"
                        activeSpecimen = "${case}_$specimen"
                        try {
                            check(specimen)
                        } catch (failure: Throwable) {
                            runCatching { screenshot("${activeSpecimen}_FAILURE") }
                                .onFailure { failure.addSuppressed(it) }
                            throw AssertionError("Typography specimen $activeSpecimen failed: ${failure.message}", failure)
                        }
                    }
                }
            }
        }
    }

    private fun assertNavigation() {
        for ((tab, labelRes) in listOf("home" to R.string.tab_home,
            "account" to R.string.tab_account, "settings" to R.string.tab_settings)) {
            compose.onNodeWithTag("nav-tab:$tab").assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            assertTextLayout(compose.onNode(hasText(localizedContext.getString(labelRes)) and
                hasAnyAncestor(hasTestTag("nav-tab:$tab")), useUnmergedTree = true)
                .fetchSemanticsNode(), singleLine = true)
            val tabBounds = compose.onNodeWithTag("nav-tab:$tab").fetchSemanticsNode().boundsInRoot
            val labelBounds = compose.onNode(hasText(localizedContext.getString(labelRes)) and
                hasAnyAncestor(hasTestTag("nav-tab:$tab")), useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertTrue("$activeSpecimen nav label $tab must fit its touch target: $labelBounds in $tabBounds",
                tabBounds.containsFully(labelBounds))
        }
    }

    private fun cancelDialog(tag: String) {
        compose.onNode(hasText(localizedContext.getString(R.string.account_cancel)) and
            hasAnyAncestor(hasTestTag(tag))).performClick()
        compose.waitForIdle()
    }

    private fun assertDialogFitsWindow(tag: String) {
        val dialog = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val window = compose.onNodeWithTag("typography-host").fetchSemanticsNode().boundsInRoot
        assertTrue("$tag exceeds the narrow window width", dialog.width <= window.width + 1f)
        assertTrue("$tag exceeds the available window height", dialog.height <= window.height + 1f)
        // Compose dialog roots have their own coordinate origin. Native decor locations provide
        // physical screen coordinates, so compare those with the Activity's safe window bounds.
        compose.runOnIdle {
            val dialogWindows = WindowInspector.getGlobalWindowViews().filter {
                it.isShown && it.windowToken != mainWindowToken
            }
            assertTrue("$tag must have a visible native dialog window", dialogWindows.isNotEmpty())
            for (view in dialogWindows) {
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                val bounds = android.graphics.Rect(location[0], location[1],
                    location[0] + view.width, location[1] + view.height)
                assertTrue("$activeSpecimen $tag lies outside safe screen bounds: $bounds in $safeWindowBounds",
                    safeWindowBounds.contains(bounds))
            }
        }
    }

    private fun scrollWithinDetails(tag: String): SemanticsNodeInteraction =
        scrollWithinDetails(compose.onNodeWithTag(tag))

    private fun scrollWithinDetails(target: SemanticsNodeInteraction): SemanticsNodeInteraction {
        val node = target.performScrollTo()
        assertFullyVisible(node.fetchSemanticsNode())
        return node
    }

    private fun scrollAboveNavigation(tag: String, scrollTag: String): SemanticsNodeInteraction {
        return scrollAboveNavigation(compose.onNodeWithTag(tag), scrollTag)
    }

    private fun scrollAboveNavigation(target: SemanticsNodeInteraction, scrollTag: String): SemanticsNodeInteraction {
        val node = target.performScrollTo()
        val barTop = compose.onNodeWithTag("bottom-navigation").fetchSemanticsNode().boundsInRoot.top
        val overlap = node.fetchSemanticsNode().boundsInRoot.bottom - barTop
        if (overlap > 0f) {
            compose.onNodeWithTag(scrollTag).performSemanticsAction(SemanticsActions.ScrollBy) {
                it(0f, overlap + 16f)
            }
        }
        assertFullyVisible(node.fetchSemanticsNode())
        assertAboveNavigation(node.fetchSemanticsNode())
        return node
    }

    private fun assertFullyVisible(node: SemanticsNode) {
        val bounds = Rect(node.positionInRoot.x, node.positionInRoot.y,
            node.positionInRoot.x + node.size.width, node.positionInRoot.y + node.size.height)
        val viewport = compose.onNodeWithTag("typography-host").fetchSemanticsNode().boundsInRoot
        assertTrue("$activeSpecimen essential node extends outside the host: $bounds in $viewport",
            viewport.containsFully(bounds))
        val scrollViewport = generateSequence(node.parent) { it.parent }
            .firstOrNull { it.config.contains(SemanticsActions.ScrollBy) }?.boundsInRoot
        assertTrue("$activeSpecimen essential node is clipped by scrolling: $bounds in $scrollViewport",
            scrollViewport == null || scrollViewport.containsFully(bounds))
    }

    private fun assertAboveNavigation(node: SemanticsNode) {
        val bar = compose.onNodeWithTag("bottom-navigation").fetchSemanticsNode().boundsInRoot
        assertTrue("${node.config} must be fully above the navigation bar", node.boundsInRoot.bottom <= bar.top + 1f)
    }

    private fun assertVisibleText() {
        val viewport = compose.onNodeWithTag("typography-host").fetchSemanticsNode().boundsInRoot
        val nodes = compose.onAllNodes(SemanticsMatcher("has a text layout") {
            it.config.contains(SemanticsActions.GetTextLayoutResult)
        }, useUnmergedTree = true).fetchSemanticsNodes()
        for (node in nodes) {
            if (node.boundsInRoot.width <= 0f || node.boundsInRoot.height <= 0f) continue
            val bounds = Rect(node.positionInRoot.x, node.positionInRoot.y,
                node.positionInRoot.x + node.size.width, node.positionInRoot.y + node.size.height)
            val scrollViewport = generateSequence(node.parent) { it.parent }
                .firstOrNull { it.config.contains(SemanticsActions.ScrollBy) }?.boundsInRoot
            if (bounds.width > 0f && bounds.height > 0f && viewport.containsFully(bounds) &&
                (scrollViewport == null || scrollViewport.containsFully(bounds)) &&
                !node.config.contains(SemanticsProperties.EditableText)
            ) assertTextLayout(node)
        }
    }

    private fun assertTextLayout(node: SemanticsNode, singleLine: Boolean = false) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.runOnIdle { node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts) }
        assertTrue("Text must expose layout: ${node.config}", layouts.isNotEmpty())
        for (layout in layouts) {
            val label = layout.layoutInput.text.text
            val diagnostic = "$activeSpecimen text=[$label] size=${layout.size} " +
                "constraints=${layout.layoutInput.constraints} paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height} " +
                "overflowWidth=${layout.didOverflowWidth} overflowHeight=${layout.didOverflowHeight} " +
                "fontSize=${layout.layoutInput.style.fontSize} lineHeight=${layout.layoutInput.style.lineHeight} " +
                "fontFamily=${layout.layoutInput.style.fontFamily} density=${layout.layoutInput.density} " +
                "lines=${layout.lineCount} lineBounds=${(0 until layout.lineCount).map { line ->
                    "${layout.getLineLeft(line)}..${layout.getLineRight(line)} " +
                        "width=${layout.getLineRight(line) - layout.getLineLeft(line)}"
                }} nodeSize=${node.size} clippedBounds=${node.boundsInRoot}"
            assertTrue("Text is clipped by an ancestor: $diagnostic", node.boundsInRoot.height >= node.size.height - 1f)
            assertTrue("Text is horizontally clipped by an ancestor: $diagnostic",
                node.boundsInRoot.width >= node.size.width - 1f)
            // Paragraph width can retain maxWidth after text is measured to its narrower ink width.
            // Alignment offsets remain relative to that paragraph; compare each line's actual width.
            assertTrue("Text exceeds its measured height: $diagnostic",
                layout.multiParagraph.height <= layout.size.height + 1f)
            assertTrue("Text exceeds its maximum lines: $diagnostic", !layout.multiParagraph.didExceedMaxLines)
            assertTrue("Essential text must not ellipsize: $diagnostic",
                (0 until layout.lineCount).none { layout.isLineEllipsized(it) })
            assertTrue("Text exceeds its measured width: $diagnostic",
                (0 until layout.lineCount).all {
                    layout.getLineRight(it) - layout.getLineLeft(it) <= layout.size.width + 1f
                })
            if (singleLine) assertTrue("Essential title or value must remain on one line: $diagnostic", layout.lineCount == 1)
        }
    }

    private fun assertNumericTokensWhole(node: SemanticsNode, tokens: List<String>) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.runOnIdle { node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts) }
        for (layout in layouts) {
            val text = layout.layoutInput.text.text
            for (token in tokens) {
                val start = text.indexOf(token)
                assertTrue("Missing numeric value $token in $text", start >= 0)
                assertTrue("Numeric value splits across lines: $token",
                    layout.getLineForOffset(start) == layout.getLineForOffset(start + token.length - 1))
            }
        }
    }

    private fun Rect.containsFully(other: Rect) = other.left >= left - 1f && other.right <= right + 1f &&
        other.top >= top - 1f && other.bottom <= bottom + 1f

    private fun screenshot(name: String) {
        compose.waitForIdle()
        // Compose can be idle before the native window transition reaches the compositor.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(350)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        var captured = automation.takeScreenshot()
        for (attempt in 1..2) {
            if (captured != null) break
            SystemClock.sleep(200)
            captured = automation.takeScreenshot()
        }
        val bitmap = requireNotNull(captured) { "Screenshot API returned null after three attempts: $name" }
        val directoryName = InstrumentationRegistry.getArguments().getString("typography_evidence_directory")
            ?: "typography-ui"
        val directory = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, directoryName)
        assertTrue(directory.exists() || directory.mkdirs())
        FileOutputStream(File(directory, "$name.png")).use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
