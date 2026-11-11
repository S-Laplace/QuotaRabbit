package com.alice.quotarabbit.widget

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real configuration route with local account metadata and a recording save callback. */
@RunWith(AndroidJUnit4::class)
class WidgetConfigurationUiTest {
    @get:Rule val compose = createComposeRule()
    private val saved = AtomicReference<SavedConfiguration?>(null)
    private val accounts = listOf("codex", "codex", "codex", "deepseek", "cursor")
        .mapIndexed { index, provider ->
            ProviderAccount.createNew(
                localAccountId = LocalAccountId("fixture-${index + 1}"),
                providerId = ProviderId(provider),
                providerAccountId = null,
                displayName = "Fixture account ${index + 1}",
                now = Instant.EPOCH,
            )
        }

    @Test
    fun savesClickOrderAndDisablesFifthAccountAtFour() {
        showRoute()
        for (index in listOf(2, 0, 4, 1)) selectAccount(index)
        accountRow(3).performScrollTo().assertIsNotEnabled().assertIsOff()

        save()

        assertEquals(listOf(2, 0, 4, 1).map(::selection), saved.get()?.accounts)
        assertEquals(4, saved.get()?.accounts?.size)
    }

    @Test
    fun deselectingAndReselectingAppendsAccountToSavedOrder() {
        showRoute(selected = listOf(2, 0, 4, 1).map(::selection))
        accountRow(3).performScrollTo().assertIsNotEnabled()

        selectAccount(0)
        accountRow(0).assertIsOff()
        accountRow(3).performScrollTo().assertIsEnabled()
        selectAccount(0)
        accountRow(0).assertIsOn()
        accountRow(3).performScrollTo().assertIsNotEnabled()
        save()

        assertEquals(listOf(2, 4, 1, 0).map(::selection), saved.get()?.accounts)
    }

    @Test
    fun emptySelectionDisablesSaveAndDoesNotInvokeCallback() {
        showRoute(selected = listOf(selection(0)))
        selectAccount(0)
        accountRow(0).assertIsOff()

        saveButton().performScrollTo().assertIsNotEnabled()
        compose.waitForIdle()

        assertNull(saved.get())
    }

    @Test
    fun legacySingleAccountSavesExistingWindowOrderUnchanged() {
        val existingWindowIds = listOf("weekly", "five_hour")
        showRoute(
            state = fixtureState().copy(
                selectedProviderId = accounts[0].providerId.value,
                selectedLocalAccountId = accounts[0].localAccountId.value,
                selectedAccounts = emptyList(),
                selectedWindowIds = existingWindowIds,
            ),
        )
        accountRow(0).assertIsOn()

        save()

        assertEquals(listOf(selection(0)), saved.get()?.accounts)
        assertEquals(existingWindowIds, saved.get()?.windowIds)
    }

    @Test
    fun savingFooterKeepsFullEnglishLabelsAt320DpAnd200PercentFont() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
            fontScale = 2f
            screenWidthDp = 320
        }
        val englishContext = context.createConfigurationContext(configuration)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalContext provides englishContext,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(density.density, 2f),
            ) {
                QuotaRabbitTheme {
                    Box(Modifier.width(320.dp).testTag("widget-config-typography-host")) {
                        WidgetConfigurationScreen(
                            state = fixtureState(listOf(selection(0))),
                            expandedSection = null,
                            isSaving = true,
                            onSectionToggle = {},
                            onAccountSelected = {},
                            onWindowToggled = {},
                            onCancel = { error("Saving layout must not invoke cancel") },
                            onSave = { error("Saving layout must not invoke save") },
                        )
                    }
                }
            }
        }
        for ((tag, labelRes) in listOf("widget-config-cancel" to R.string.widget_config_cancel,
            "widget-config-save" to R.string.widget_config_saving)) {
            val button = compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
                .assertIsNotEnabled().assertHeightIsAtLeast(48.dp).fetchSemanticsNode()
            val label = compose.onNode(hasText(englishContext.getString(labelRes)) and
                hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.runOnIdle { label.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts) }
            assertTrue("Footer label must expose text layout", layouts.isNotEmpty())
            for (layout in layouts) {
                assertEquals("Footer label must stay on one line", 1, layout.lineCount)
                assertTrue("Footer label must not clip or ellipsize",
                    layout.getLineRight(0) - layout.getLineLeft(0) <= layout.size.width + 1f &&
                        layout.multiParagraph.height <= layout.size.height + 1f &&
                        !layout.multiParagraph.didExceedMaxLines && !layout.isLineEllipsized(0))
                assertTrue("Footer label must fit its button", label.boundsInRoot.left >= button.boundsInRoot.left - 1f &&
                    label.boundsInRoot.right <= button.boundsInRoot.right + 1f &&
                    label.boundsInRoot.height >= label.size.height - 1f)
            }
        }
        compose.waitForIdle()
        val cancel = compose.onNodeWithTag("widget-config-cancel").fetchSemanticsNode().positionInRoot
        val save = compose.onNodeWithTag("widget-config-save").fetchSemanticsNode().positionInRoot
        assertTrue("Large-font footer actions must stack", save.y > cancel.y)
        assertNull(saved.get())
        val bitmap = compose.onNodeWithTag("widget-config-typography-host").captureToImage().asAndroidBitmap()
        val directory = File(context.getExternalFilesDir(null) ?: context.filesDir, "widget-config-typography-ui")
        assertTrue(directory.exists() || directory.mkdirs())
        FileOutputStream(File(directory, "saving_en_320dp_font200.png")).use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }

    private fun showRoute(
        selected: List<WidgetAccountSelection> = emptyList(),
        state: WidgetConfigurationScreenState = fixtureState(selected),
    ) {
        compose.setContent {
            MaterialTheme {
                WidgetConfigurationRoute(
                    appWidgetId = 17,
                    loadState = { state },
                    onCancel = {},
                    onSave = { selectedAccounts, windowIds ->
                        saved.set(SavedConfiguration(selectedAccounts.toList(), windowIds.toList()))
                    },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun fixtureState(selected: List<WidgetAccountSelection> = emptyList()) = WidgetConfigurationScreenState(
        accounts = accounts,
        selectedProviderId = selected.firstOrNull()?.providerId,
        selectedLocalAccountId = selected.firstOrNull()?.localAccountId,
        selectedAccounts = selected,
        selectedWindowIds = if (selected.isEmpty()) emptyList() else listOf("five_hour"),
        windowOptionsByAccount = accounts.associate { account ->
            account.localAccountId.value to when (account.providerId.value) {
                "deepseek" -> listOf(WidgetWindowOption("balance", R.string.window_label_balance))
                "cursor" -> listOf(WidgetWindowOption("cursor_plan", R.string.window_label_plan))
                else -> listOf(
                    WidgetWindowOption("five_hour", R.string.account_quota_five_hour_label),
                    WidgetWindowOption("weekly", R.string.account_quota_weekly_label),
                )
            }
        },
    )

    private fun accountRow(index: Int) =
        compose.onNode(isToggleable() and hasText(accounts[index].displayName))

    private fun selectAccount(index: Int) {
        accountRow(index).performScrollTo().performClick()
    }

    private fun saveButton() = compose.onNodeWithText(
        InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.widget_config_save),
    )

    private fun save() {
        saveButton().performScrollTo().assertIsEnabled().performClick()
        compose.waitForIdle()
    }

    private fun selection(index: Int) = WidgetAccountSelection(
        accounts[index].providerId.value,
        accounts[index].localAccountId.value,
    )

    private data class SavedConfiguration(
        val accounts: List<WidgetAccountSelection>,
        val windowIds: List<String>,
    )
}
