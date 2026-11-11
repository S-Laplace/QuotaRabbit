package com.alice.quotarabbit.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.model.QuotaWindowId
import com.alice.quotarabbit.domain.theme.ThemeMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual SettingsScreen rendering and callback wiring; all effects end in the memory fixture. */
@RunWith(AndroidJUnit4::class)
class SettingsReadabilityTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var localizedContext: Context
    private var specimen = "uninitialized"
    private var english = true
    private var density = 1f

    @Test
    fun appearanceLabelsRemainWholeAcrossNarrowWidthsAndFontScales() {
        for (locale in listOf("en", "zh-CN")) for (scale in listOf(1f, 1.5f, 2f)) {
            for (theme in listOf("light", "dark")) launch(locale, scale, theme) { scenario ->
                checkAppearance(scenario)
            }
        }
    }

    @Test
    fun appearanceMeasuresLabelsAtNormalWidthAndModeratelyEnlargedFonts() {
        // Run this method on a physical 400 dp window; keep native dialog checks at 320 dp.
        // A width-only or fontScale >= 1.3 heuristic misses this measured-label boundary.
        for (theme in listOf("light", "dark")) launch("en", 1.15f, theme, width = 400) { scenario ->
            checkAppearance(scenario)
        }
    }

    private fun checkAppearance(scenario: ActivityScenario<SettingsPreviewActivity>) {
        val options = listOf(
            ThemeMode.LIGHT to R.string.settings_appearance_light,
            ThemeMode.DARK to R.string.settings_appearance_dark,
            ThemeMode.SYSTEM to R.string.settings_appearance_system,
        )
        for ((mode, label) in options) {
            val option = scroll(compose.onNodeWithTag("settings-appearance:${mode.name.lowercase()}"))
            option.assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            assertText(text(label), singleLine = true)
        }
        screenshot("appearance")
        for ((mode, _) in options) {
            val option = scroll(compose.onNodeWithTag("settings-appearance:${mode.name.lowercase()}"))
            option.performClick().assertIsSelected()
            scenario.onActivity { assertEquals(mode, it.fixture.state.appearance.themeMode) }
        }
    }

    @Test
    fun switchesSlidersSteppersAndSectionsExposeMeaningfulSemantics() {
        for (locale in listOf("en", "zh-CN")) launch(locale, 2f, "dark") { scenario ->
            for (title in switchTitles) {
                val control = scroll(switch(title))
                assertControl(control)
                control.assert(SemanticsMatcher("named switch exposes a toggle state") {
                    it.config.contains(SemanticsProperties.ToggleableState)
                })
            }
            for ((title, value) in listOf(R.string.settings_threshold_caution to 40f, R.string.settings_threshold_warning to 8f)) {
                val control = scroll(slider(title))
                assertControl(control)
                control.assert(SemanticsMatcher("slider exposes range and current value") {
                    it.config.contains(SemanticsProperties.ProgressBarRangeInfo)
                })
                control.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(value)) }
                compose.waitForIdle()
                assertEquals(value, control.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, 0.01f)
            }
            for (title in listOf(R.string.settings_threshold_caution, R.string.settings_threshold_warning)) {
                val decrease = balanceButton(title, R.string.settings_balance_decrease_description)
                val increase = balanceButton(title, R.string.settings_balance_increase_description)
                assertControl(scroll(decrease))
                assertControl(scroll(increase))
                decrease.performScrollTo().performClick()
                increase.performScrollTo().performClick()
            }
            scenario.onActivity {
                assertEquals(40, it.fixture.state.alerts.thresholds.caution)
                assertEquals(8, it.fixture.state.alerts.thresholds.warning)
                assertEquals(10.0, it.fixture.state.alerts.balanceCaution, 0.0)
                assertEquals(3.0, it.fixture.state.alerts.balanceWarning, 0.0)
                assertTrue(it.fixture.events.containsAll(listOf("balance_caution:9.0", "balance_caution:10.0", "balance_warning:2.0", "balance_warning:3.0")))
            }
            for (title in sectionTitles) {
                scroll(compose.onNode(hasText(localizedContext.getString(title)) and SemanticsMatcher("section heading") {
                    it.config.contains(SemanticsProperties.Heading)
                }))
            }
        }
    }

    @Test
    fun expandedDiagnosticsActionsKeepWholeWordsAtLargeFonts() = launch("en", 2f, "light") {
        scroll(text(R.string.settings_diagnostics_show)).performClick()
        for (title in listOf(R.string.settings_diagnostics_copy, R.string.settings_diagnostics_recheck)) {
            val button = scroll(text(title))
            assertControl(button)
            assertText(compose.onNodeWithText(localizedContext.getString(title), useUnmergedTree = true))
        }
        screenshot("diagnostics_actions")
    }

    @Test
    fun descriptionsAndActionsRemainReadableAcrossTypographyMatrix() {
        for (locale in listOf("en", "zh-CN")) for (scale in listOf(1f, 1.5f, 2f)) {
            for (theme in listOf("light", "dark")) launch(locale, scale, theme) {
                for (title in readabilityText) {
                    assertText(compose.onNodeWithText(localizedContext.getString(title), useUnmergedTree = true))
                }
                for ((title, amount) in listOf(R.string.settings_threshold_caution to 30, R.string.settings_threshold_warning to 10)) {
                    assertText(compose.onNodeWithText(localizedContext.getString(R.string.settings_threshold_value_format,
                        localizedContext.getString(title), amount), useUnmergedTree = true))
                    assertControl(scroll(slider(title)))
                }
                screenshot("thresholds")
                scroll(text(R.string.settings_diagnostics_show)).performClick()
                for (label in listOf(R.string.settings_diagnostics_copy, R.string.settings_diagnostics_recheck)) {
                    assertControl(scroll(text(label)))
                    assertText(compose.onNodeWithText(localizedContext.getString(label), useUnmergedTree = true))
                }
                screenshot("expanded_diagnostics")
                assertControl(scroll(compose.onNodeWithTag("settings-update-check")))
                assertText(compose.onNodeWithText(localizedContext.getString(R.string.settings_update_check), useUnmergedTree = true))
                screenshot("bottom")
            }
        }
    }

    @Test
    fun notificationCurrencyRefreshAndRetentionChoicesPreserveCallbacks() {
        for ((locale, theme) in listOf("en" to "light", "zh-CN" to "dark")) launch(locale, 2f, theme) { scenario ->
            toggle(R.string.settings_status_notification_title, false)
            toggle(R.string.settings_account_errors_title, false)
            openChoice(R.string.settings_notification_account_title)
            for (account in SettingsPreviewFixture.accounts) assertChoiceOption(account.displayName)
            screenshot("notification_accounts", "settings-choice-dialog")
            choice(SettingsPreviewFixture.accounts.first().displayName).performScrollTo().performClick()
            compose.onNodeWithTag("settings-choice-dialog").assertDoesNotExist()
            scenario.onActivity { assertEquals(SettingsPreviewFixture.accounts.first(), it.fixture.state.persistentNotification.accountSelection) }
            assertText(compose.onNodeWithText(SettingsPreviewFixture.accounts.first().displayName, useUnmergedTree = true))

            openChoice(R.string.settings_notification_display_quota_title)
            assertChoiceOption(localizedContext.getString(R.string.home_quota_window_five_hour)).assertIsSelected()
            assertChoiceOption(localizedContext.getString(R.string.home_quota_window_weekly)).performClick()
            scenario.onActivity { assertEquals(QuotaWindowId("weekly"), it.fixture.state.persistentNotification.selectedWindowId) }
            openChoice(R.string.settings_notification_display_quota_title)
            choice(localizedContext.getString(R.string.home_quota_window_weekly)).assertIsSelected()
            dialogAction("settings-choice-dialog", R.string.account_cancel).performClick()

            openChoice(R.string.settings_currency_target_title)
            for (currency in listOf("USD", "CNY", "EUR", "KRW")) assertChoiceOption(currency)
            choice("CNY").performScrollTo().performClick()
            scenario.onActivity { assertEquals("CNY", it.fixture.state.currency.targetCurrency) }
            assertText(compose.onNodeWithText("CNY", useUnmergedTree = true))

            scroll(text(R.string.settings_battery_optimization_action)).performClick()
            toggle(R.string.settings_background_refresh_title, false)
            text(R.string.settings_battery_optimization_action).assertDoesNotExist()
            openChoice(R.string.settings_refresh_interval_title)
            for (interval in SettingsRefreshInterval.entries) assertChoiceOption(localizedContext.getString(interval.labelResId))
            choice(localizedContext.getString(SettingsRefreshInterval.Thirty.labelResId)).performScrollTo().performClick()
            scenario.onActivity { assertEquals(SettingsRefreshInterval.Thirty, it.fixture.state.refresh.interval) }
            assertText(compose.onNodeWithText(localizedContext.getString(R.string.settings_refresh_last_result_format,
                localizedContext.getString(R.string.settings_refresh_last_result_failed)), useUnmergedTree = true))

            openChoice(R.string.settings_retention_title)
            for (retention in SettingsRetentionOption.entries) assertChoiceOption(localizedContext.getString(retention.labelResId))
            screenshot("retention_choices", "settings-choice-dialog")
            choice(localizedContext.getString(SettingsRetentionOption.Forever.labelResId)).performScrollTo().performClick()
            scenario.onActivity {
                assertEquals(SettingsRetentionOption.Forever, it.fixture.state.data.retention)
                assertTrue(it.fixture.events.containsAll(listOf("status:false", "errors:false", "notification_account",
                    "notification_window:weekly", "currency:CNY", "battery", "background:false", "interval:Thirty", "retention:Forever")))
            }
        }
    }

    @Test
    fun diagnosticsUpdatesAndHistoryActionsRemainCancellableAndScoped() {
        for ((locale, theme) in listOf("en" to "dark", "zh-CN" to "light")) launch(locale, 2f, theme) { scenario ->
            scroll(text(R.string.settings_diagnostics_show)).performClick()
            assertText(compose.onNodeWithText(SettingsPreviewFixture.DIAGNOSTICS, useUnmergedTree = true))
            scroll(text(R.string.settings_diagnostics_copy)).performClick()
            scroll(text(R.string.settings_diagnostics_recheck)).performClick()
            scroll(text(R.string.settings_diagnostics_hide)).performClick()
            text(R.string.settings_diagnostics_copy).assertDoesNotExist()
            scroll(text(R.string.settings_about_repository_label)).performClick()

            toggle(R.string.settings_update_notify_title, false)
            toggle(R.string.settings_update_auto_check_title, false)
            scroll(switch(R.string.settings_update_notify_title)).assertIsNotEnabled()
            toggle(R.string.settings_update_auto_check_title, true)
            switch(R.string.settings_update_notify_title).assertIsEnabled()
            scroll(compose.onNodeWithTag("settings-update-check")).performClick()
            compose.onNodeWithTag("settings-update-check").assertIsNotEnabled()
            text(R.string.settings_update_checking).assertIsDisplayed()
            scenario.onActivity { it.fixture.completeUpdateCheck() }
            compose.waitForIdle()
            assertDialog("settings-update-dialog")
            assertText(compose.onNodeWithText(localizedContext.getString(R.string.settings_update_dialog_message, "99.0.0"),
                useUnmergedTree = true), inDialog = true)
            assertDialogAction("settings-update-dialog", R.string.settings_update_dialog_download)
            assertDialogAction("settings-update-dialog", R.string.settings_update_dialog_cancel)
            screenshot("update_confirmation", "settings-update-dialog")
            dialogAction("settings-update-dialog", R.string.settings_update_dialog_cancel).performClick()
            scenario.onActivity { assertFalse(it.fixture.events.contains("update_download")) }
            scroll(compose.onNodeWithText(localizedContext.getString(R.string.settings_update_download_available, "99.0.0"))).performClick()
            dialogAction("settings-update-dialog", R.string.settings_update_dialog_download).performClick()
            compose.onNodeWithTag("settings-update-dialog").assertDoesNotExist()

            for ((action, title) in listOf(
                SettingsPendingDataAction.ClearCurrentHistory to R.string.settings_data_clear_current_title,
                SettingsPendingDataAction.ClearAllHistory to R.string.settings_data_clear_all_title,
            )) {
                val completedBefore = clearConfirmCount(scenario)
                scroll(destructiveAction(title)).performClick()
                assertDialog("settings-clear-history-dialog")
                assertText(compose.onNodeWithText(localizedContext.getString(action.titleResId), useUnmergedTree = true), inDialog = true)
                assertText(compose.onNodeWithText(localizedContext.getString(action.messageResId), useUnmergedTree = true), inDialog = true)
                assertDialogAction("settings-clear-history-dialog", R.string.settings_data_confirm_clear)
                assertDialogAction("settings-clear-history-dialog", R.string.account_cancel)
                assertEquals("Opening a confirmation must not clear anything", completedBefore, clearConfirmCount(scenario))
                screenshot("${action.name}_confirmation", "settings-clear-history-dialog")
                dialogAction("settings-clear-history-dialog", R.string.account_cancel).performClick()
                assertEquals("Cancel must not invoke the destructive callback", completedBefore, clearConfirmCount(scenario))
                scenario.onActivity { assertEquals(null, it.fixture.state.data.pendingAction) }
                scroll(destructiveAction(title)).performClick()
                dialogAction("settings-clear-history-dialog", R.string.settings_data_confirm_clear).performClick()
                compose.onNodeWithTag("settings-clear-history-dialog").assertDoesNotExist()
                assertEquals(completedBefore + 1, clearConfirmCount(scenario))
                scenario.onActivity {
                    assertEquals(action, it.fixture.state.data.confirmedAction)
                    assertEquals(SettingsPreviewFixture.accounts, it.fixture.state.persistentNotification.accountChoices.drop(1).map { choice -> choice.selection })
                }
            }
            scenario.onActivity {
                assertEquals(1, it.fixture.state.diagnostics.recheckRequestCount)
                assertTrue(it.fixture.events.containsAll(listOf("diagnostics_copy", "diagnostics_recheck", "repository",
                    "update_notify:false", "update_auto:false", "update_auto:true", "update_check", "update_cancel", "update_download")))
                assertEquals(1, it.fixture.events.count { event -> event == "update_download" })
            }
        }
    }

    @Test
    fun explicitModesAndFollowSystemApplyImmediatelyToSettings() = launch("en", 2f, "system") { scenario ->
        scenario.onActivity { assertFalse(it.effectiveDark) }
        scroll(compose.onNodeWithTag("settings-appearance:dark")).performClick().assertIsSelected()
        scenario.onActivity { it.dispatchSystemNightMode(false) }
        compose.waitForIdle()
        scenario.onActivity { assertTrue(it.effectiveDark) }
        scroll(compose.onNodeWithTag("settings-appearance:light")).performClick().assertIsSelected()
        scenario.onActivity { it.dispatchSystemNightMode(true) }
        compose.waitForIdle()
        scenario.onActivity { assertFalse(it.effectiveDark) }
        scroll(compose.onNodeWithTag("settings-appearance:system")).performClick().assertIsSelected()
        compose.waitForIdle()
        scenario.onActivity { assertTrue(it.effectiveDark); it.dispatchSystemNightMode(false) }
        compose.waitForIdle()
        scenario.onActivity { assertFalse(it.effectiveDark) }
    }

    private fun toggle(@StringRes title: Int, enabled: Boolean) {
        val control = scroll(switch(title))
        control.performClick()
        control.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState,
            if (enabled) ToggleableState.On else ToggleableState.Off))
    }

    private fun openChoice(@StringRes title: Int) {
        scroll(text(title)).performClick()
        assertDialog("settings-choice-dialog")
    }

    private fun choice(label: String): SemanticsNodeInteraction = compose.onNode(hasText(label) and
        hasAnyAncestor(hasTestTag("settings-choice-dialog")))

    private fun assertChoiceOption(label: String): SemanticsNodeInteraction {
        val option = choice(label).performScrollTo().assertIsDisplayed()
        option.assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        assertText(compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("settings-choice-dialog")),
            useUnmergedTree = true), inDialog = true)
        return option
    }

    private fun assertDialog(tag: String) {
        val dialog = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
        val host = compose.onNodeWithTag("settings-preview-host").fetchSemanticsNode()
        // Compare dimensions only: a Dialog and Activity have different root coordinate origins.
        assertTrue("$specimen $tag exceeds the narrow host", dialog.size.width <= host.size.width + 1)
        assertTrue("$specimen $tag exceeds the usable height", dialog.size.height <= host.size.height + 1)
    }

    private fun dialogAction(tag: String, @StringRes label: Int): SemanticsNodeInteraction =
        compose.onNode(hasText(localizedContext.getString(label)) and hasAnyAncestor(hasTestTag(tag)))

    private fun assertDialogAction(tag: String, @StringRes label: Int) {
        val action = dialogAction(tag, label).assertIsDisplayed()
        action.assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        assertText(compose.onNode(hasText(localizedContext.getString(label)) and hasAnyAncestor(hasTestTag(tag)),
            useUnmergedTree = true), inDialog = true)
    }

    private fun destructiveAction(@StringRes title: Int): SemanticsNodeInteraction =
        compose.onNode(SemanticsMatcher("destructive action identifies its scope") {
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(localizedContext.getString(title)) == true &&
                it.config.contains(SemanticsActions.OnClick)
        })

    private fun clearConfirmCount(scenario: ActivityScenario<SettingsPreviewActivity>): Int {
        var count = 0
        scenario.onActivity { count = it.fixture.events.count { event -> event.startsWith("clear_confirm:") } }
        return count
    }

    private fun switch(@StringRes title: Int): SemanticsNodeInteraction {
        val label = localizedContext.getString(title)
        return compose.onNode(SemanticsMatcher("switch named $label") { node ->
            node.config.getOrNull(SemanticsProperties.Role) == Role.Switch && node.includesLabel(label)
        })
    }

    private fun slider(@StringRes title: Int): SemanticsNodeInteraction {
        val label = localizedContext.getString(title)
        return compose.onNode(SemanticsMatcher("slider named $label") {
            it.config.contains(SemanticsActions.SetProgress) && it.includesLabel(label)
        })
    }

    private fun balanceButton(@StringRes title: Int, @StringRes action: Int): SemanticsNodeInteraction {
        val label = localizedContext.getString(action, localizedContext.getString(title))
        return compose.onNode(SemanticsMatcher("balance action named $label") {
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true &&
                it.config.contains(SemanticsActions.OnClick)
        })
    }

    private fun SemanticsNode.includesLabel(label: String): Boolean =
        config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.contains(label, ignoreCase = true) } == true ||
            config.getOrNull(SemanticsProperties.Text)?.any { it.text.contains(label, ignoreCase = true) } == true

    private fun text(@StringRes title: Int): SemanticsNodeInteraction = compose.onNodeWithText(localizedContext.getString(title))

    private fun assertControl(node: SemanticsNodeInteraction) {
        node.assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        assertFullyVisible(node.fetchSemanticsNode())
    }

    private fun scroll(node: SemanticsNodeInteraction): SemanticsNodeInteraction {
        node.performScrollTo().assertIsDisplayed()
        assertFullyVisible(node.fetchSemanticsNode())
        return node
    }

    private fun assertFullyVisible(node: SemanticsNode) {
        val bounds = Rect(node.positionInRoot.x, node.positionInRoot.y,
            node.positionInRoot.x + node.size.width, node.positionInRoot.y + node.size.height)
        val host = compose.onNodeWithTag("settings-preview-host").fetchSemanticsNode().boundsInRoot
        val scrollBounds = generateSequence(node.parent) { it.parent }
            .firstOrNull { it.config.contains(SemanticsActions.ScrollBy) }?.boundsInRoot
        assertTrue("$specimen control lies outside host: $bounds in $host", host.containsFully(bounds))
        assertTrue("$specimen control is scroll-clipped: $bounds in $scrollBounds", scrollBounds == null || scrollBounds.containsFully(bounds))
    }

    private fun assertText(node: SemanticsNodeInteraction, singleLine: Boolean = false, inDialog: Boolean = false) {
        val semantic = if (inDialog) node.assertIsDisplayed().fetchSemanticsNode() else scroll(node).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.runOnIdle { semantic.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts) }
        assertTrue("$specimen text must expose measured layout", layouts.isNotEmpty())
        for (layout in layouts) {
            val value = layout.layoutInput.text.text
            val diagnostic = "$specimen [$value] size=${layout.size}, lines=${layout.lineCount}"
            // Compose rounds the measured box to pixels; the paragraph can be fractionally taller.
            assertTrue("$diagnostic exceeds measured height", layout.multiParagraph.height <= layout.size.height + 1f)
            assertTrue("$diagnostic exceeds measured ink width", (0 until layout.lineCount).all {
                layout.getLineRight(it) - layout.getLineLeft(it) <= layout.size.width + 1f
            })
            assertFalse("$diagnostic must not exceed max lines", layout.multiParagraph.didExceedMaxLines)
            assertTrue("$diagnostic must not ellipsize", (0 until layout.lineCount).none(layout::isLineEllipsized))
            assertTrue("$diagnostic ancestor must contain text", semantic.boundsInRoot.width >= semantic.size.width - 1f &&
                semantic.boundsInRoot.height >= semantic.size.height - 1f)
            if (singleLine) assertEquals("$diagnostic full Appearance label must fit one line", 1, layout.lineCount)
            if (english) for (word in Regex("[A-Za-z]{4,}").findAll(value)) {
                assertEquals("$diagnostic splits the word ${word.value}", layout.getLineForOffset(word.range.first),
                    layout.getLineForOffset(word.range.last))
            }
        }
    }

    private fun Rect.containsFully(other: Rect): Boolean = other.left >= left - 1f && other.top >= top - 1f &&
        other.right <= right + 1f && other.bottom <= bottom + 1f

    private fun launch(locale: String, scale: Float, theme: String, width: Int = 320,
        check: (ActivityScenario<SettingsPreviewActivity>) -> Unit) {
        english = locale == "en"
        specimen = "${locale}_${theme}_width${width}_font${(scale * 100).toInt()}"
        val context: Context = ApplicationProvider.getApplicationContext()
        val intent = Intent(context, SettingsPreviewActivity::class.java)
            .putExtra("locale", locale).putExtra("font_scale", scale).putExtra("theme", theme).putExtra("width_dp", width)
        ActivityScenario.launch<SettingsPreviewActivity>(intent).use { scenario ->
            scenario.onActivity { localizedContext = it.previewContext }
            compose.waitForIdle()
            density = localizedContext.resources.displayMetrics.density
            assertEquals("$specimen must use requested width", width.toFloat(),
                compose.onNodeWithTag("settings-preview-host").fetchSemanticsNode().size.width / density, 1f)
            check(scenario)
        }
    }

    private fun screenshot(route: String, tag: String = "settings-preview-host") {
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val directoryName = InstrumentationRegistry.getArguments().getString("settings_evidence_directory") ?: "phase03-settings-ui"
        val context: Context = ApplicationProvider.getApplicationContext()
        val directory = File(context.getExternalFilesDir(null), directoryName)
        assertTrue(directory.exists() || directory.mkdirs())
        File(directory, "${specimen}_$route.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    companion object {
        private val switchTitles = listOf(R.string.settings_status_notification_title, R.string.settings_account_errors_title,
            R.string.settings_background_refresh_title, R.string.settings_update_auto_check_title, R.string.settings_update_notify_title)
        private val sectionTitles = listOf(R.string.settings_group_appearance, R.string.settings_group_persistent_notification,
            R.string.settings_group_alerts, R.string.settings_group_refresh, R.string.settings_group_data,
            R.string.settings_group_diagnostics, R.string.settings_group_about)
        private val readabilityText = listOf(R.string.settings_status_notification_title, R.string.settings_status_notification_description,
            R.string.settings_account_errors_title, R.string.settings_account_errors_description,
            R.string.settings_currency_target_title, R.string.settings_currency_target_description, R.string.settings_balance_threshold_caption,
            R.string.settings_background_refresh_title, R.string.settings_background_refresh_description,
            R.string.settings_battery_optimization_title, R.string.settings_battery_optimization_description, R.string.settings_battery_optimization_action,
            R.string.settings_retention_title, R.string.settings_retention_description,
            R.string.settings_data_clear_current_title, R.string.settings_data_clear_current_description,
            R.string.settings_data_clear_all_title, R.string.settings_data_clear_all_description,
            R.string.settings_diagnostics_title, R.string.settings_diagnostics_description,
            R.string.settings_update_auto_check_title, R.string.settings_update_auto_check_description,
            R.string.settings_update_notify_title, R.string.settings_update_notify_description)
    }
}
