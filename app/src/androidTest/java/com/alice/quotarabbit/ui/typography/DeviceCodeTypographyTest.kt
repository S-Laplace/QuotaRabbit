package com.alice.quotarabbit.ui.typography

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alice.quotarabbit.R
import com.alice.quotarabbit.ui.auth.DeviceCodeLoginUiStatus
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises only static device-code UI; callbacks never contact a provider or save account data. */
@RunWith(AndroidJUnit4::class)
class DeviceCodeTypographyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val appContext: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var localizedContext: Context
    private var specimen = "uninitialized"

    @Test
    fun pollingCodeCopyAndBrowserActionAcrossTypographyMatrix() = matrix(
        DeviceCodeLoginUiStatus.PollingAuthorization,
    ) { scenario ->
        val code = compose.onNodeWithTag("device-code-value").performScrollTo().assertIsDisplayed()
        assertTextFits(code.fetchSemanticsNode(), singleLine = true)
        assertEquals(listOf(localizedContext.getString(R.string.add_account_device_code_format,
            DeviceCodeTypographyPreviewActivity.DUMMY_USER_CODE)),
            code.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription))
        compose.onNodeWithTag("device-code-copy").assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        assertEquals(listOf(localizedContext.getString(R.string.add_account_copy_code)),
            compose.onNodeWithTag("device-code-copy").fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.ContentDescription))
        compose.onNodeWithTag("device-code-copy").performClick()
        scenario.onActivity { assertEquals(DeviceCodeTypographyPreviewActivity.DUMMY_USER_CODE, it.copiedCode) }
        assertVisibleText()
        screenshot("${specimen}_code")

        compose.onNodeWithTag("device-code-open-browser").performScrollTo().assertIsDisplayed().performClick()
        assertActionText("device-code-open-browser", localizedContext.getString(
            R.string.add_account_open_verification_page, "Codex"))
        val browserLabel = compose.onNode(hasText(localizedContext.getString(
            R.string.add_account_open_verification_page, "Codex")) and
            hasAnyAncestor(hasTestTag("device-code-open-browser")), useUnmergedTree = true)
            .fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.runOnIdle { browserLabel.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts) }
        for (layout in layouts) {
            for (line in 0 until layout.lineCount) {
                val text = layout.layoutInput.text.text.substring(layout.getLineStart(line), layout.getLineEnd(line)).trim()
                assertTrue("$specimen browser label must not orphan a single character: $text", text.length > 1)
            }
        }
        scenario.onActivity {
            assertEquals(DeviceCodeTypographyPreviewActivity.DUMMY_VERIFICATION_URI, it.openedVerificationUri)
        }
        assertVisibleText()
        screenshot("${specimen}_browser")
    }

    @Test
    fun validationFailureKeepsBothRetryActionsReadableAcrossTypographyMatrix() = matrix(
        DeviceCodeLoginUiStatus.ValidationFailed,
    ) { scenario ->
        compose.onNodeWithTag("device-code-retry-validation").performScrollTo().assertIsDisplayed().performClick()
        assertActionText("device-code-retry-validation", localizedContext.getString(R.string.add_account_retry_validation))
        scenario.onActivity { assertEquals(1, it.retryCount) }
        assertVisibleText()
        screenshot("${specimen}_retry")

        compose.onNodeWithTag("device-code-restart-login").performScrollTo().assertIsDisplayed().performClick()
        assertActionText("device-code-restart-login", localizedContext.getString(R.string.add_account_restart_login))
        scenario.onActivity { assertEquals(1, it.restartCount) }
        assertVisibleText()
        screenshot("${specimen}_restart")
    }

    @Test
    fun mismatchDecisionRemainsReadableAndCancellableAcrossTypographyMatrix() = matrix(
        DeviceCodeLoginUiStatus.AccountMismatchDecision,
    ) { scenario ->
        val dialog = compose.onNodeWithTag("device-code-mismatch-dialog").assertIsDisplayed().fetchSemanticsNode()
        val host = compose.onNodeWithTag("device-code-host").fetchSemanticsNode().boundsInRoot
        assertTrue("$specimen dialog must fit the narrow window", dialog.boundsInRoot.width <= host.width + 1f)
        assertTrue("$specimen dialog must fit the available height", dialog.boundsInRoot.height <= host.height + 1f)
        compose.onNodeWithText(localizedContext.getString(R.string.add_account_mismatch_dialog_title))
            .assertIsDisplayed()
        assertVisibleText()
        screenshot("${specimen}_dialog")
        val cancelLabel = localizedContext.getString(R.string.add_account_mismatch_dialog_cancel)
        val cancel = compose.onNode(hasText(cancelLabel) and hasAnyAncestor(hasTestTag("device-code-mismatch-dialog")))
        cancel.assertIsDisplayed()
        assertActionText("device-code-mismatch-dialog", cancelLabel)
        cancel.performClick()
        scenario.onActivity { assertTrue(it.mismatchDismissed) }
    }

    private fun matrix(
        status: DeviceCodeLoginUiStatus,
        check: (ActivityScenario<DeviceCodeTypographyPreviewActivity>) -> Unit,
    ) {
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
                    val intent = Intent(appContext, DeviceCodeTypographyPreviewActivity::class.java)
                        .putExtra("locale", locale).putExtra("theme", theme)
                        .putExtra("font_scale", scale).putExtra("width_dp", 320)
                        .putExtra("status", status.name)
                    ActivityScenario.launch<DeviceCodeTypographyPreviewActivity>(intent).use { scenario ->
                        scenario.onActivity { localizedContext = it.specimenContext }
                        compose.waitForIdle()
                        specimen = "${status.name}_${locale}_${theme}_font${(scale * 100).toInt()}"
                        try {
                            check(scenario)
                        } catch (failure: Throwable) {
                            runCatching { screenshot("${specimen}_FAILURE") }
                                .onFailure { failure.addSuppressed(it) }
                            throw AssertionError("Device-code specimen $specimen failed: ${failure.message}", failure)
                        }
                    }
                }
            }
        }
    }

    private fun assertActionText(tag: String, text: String) {
        val label = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode()
        assertTextFits(label)
        val action = compose.onNodeWithTag(tag).fetchSemanticsNode()
        assertTrue("$specimen action label must fit its control", action.boundsInRoot.containsFully(label.boundsInRoot))
    }

    private fun assertVisibleText() {
        val host = compose.onNodeWithTag("device-code-host").fetchSemanticsNode().boundsInRoot
        val nodes = compose.onAllNodes(SemanticsMatcher("has a text layout") {
            it.config.contains(SemanticsActions.GetTextLayoutResult)
        }, useUnmergedTree = true).fetchSemanticsNodes()
        for (node in nodes) {
            val bounds = Rect(node.positionInRoot.x, node.positionInRoot.y,
                node.positionInRoot.x + node.size.width, node.positionInRoot.y + node.size.height)
            val scroll = generateSequence(node.parent) { it.parent }
                .firstOrNull { it.config.contains(SemanticsActions.ScrollBy) }?.boundsInRoot
            if (bounds.width > 0f && bounds.height > 0f && host.containsFully(bounds) &&
                (scroll == null || scroll.containsFully(bounds))
            ) assertTextFits(node)
        }
    }

    private fun assertTextFits(node: SemanticsNode, singleLine: Boolean = false) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.runOnIdle { node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts) }
        assertTrue("$specimen text must expose its layout: ${node.config}", layouts.isNotEmpty())
        for (layout in layouts) {
            val detail = "$specimen text=[${layout.layoutInput.text}] size=${layout.size} lines=${layout.lineCount}"
            assertTrue("$detail is clipped by its ancestor", node.boundsInRoot.height >= node.size.height - 1f)
            assertTrue("$detail exceeds its measured height", layout.multiParagraph.height <= layout.size.height + 1f)
            assertTrue("$detail exceeds its maximum lines", !layout.multiParagraph.didExceedMaxLines)
            assertTrue("$detail must not ellipsize", (0 until layout.lineCount).none { layout.isLineEllipsized(it) })
            assertTrue("$detail exceeds its measured width", (0 until layout.lineCount).all {
                layout.getLineRight(it) - layout.getLineLeft(it) <= layout.size.width + 1f
            })
            if (singleLine) assertEquals("$detail must remain on one line", 1, layout.lineCount)
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
            ?: "device-code-typography-ui"
        val directory = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, directoryName)
        assertTrue(directory.exists() || directory.mkdirs())
        FileOutputStream(File(directory, "$name.png")).use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
