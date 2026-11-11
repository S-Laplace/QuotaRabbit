package com.alice.quotarabbit.ui.account

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.alice.quotarabbit.domain.account.AccountEditInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.account.AccountListResult
import com.alice.quotarabbit.domain.account.AccountListUseCase
import com.alice.quotarabbit.domain.account.NoopAccountDeleteUseCase
import com.alice.quotarabbit.domain.quota.CurrentQuotaStateFactory
import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.domain.model.ProviderAccountId
import com.alice.quotarabbit.ui.home.HomeCurrentQuotaStateLoader
import com.alice.quotarabbit.ui.home.HomeRefreshUseCase
import com.alice.quotarabbit.ui.navigation.QuotaRabbitNavHost
import com.alice.quotarabbit.ui.navigation.QuotaRabbitRoute
import com.alice.quotarabbit.domain.model.AccountStatus
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import com.alice.quotarabbit.domain.theme.ThemeMode
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Offline synthetic accounts only. No session store, provider request or real account mutation. */
@RunWith(AndroidJUnit4::class)
class AccountOverviewUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun overviewAccordionStartsClosedAndExpandsIndependently() {
        val accounts = listOf(codex("personal", 82, 76, true), codex("work", 53, 44))
        val viewState = mutableStateOf(state(accounts, initiallyExpanded = false))
        compose.setContent {
            QuotaRabbitTheme {
                AccountScreen(
                    uiState = viewState.value,
                    onToggleExpanded = { id ->
                        viewState.value = viewState.value.copy(accounts = viewState.value.accounts.map {
                            if (it.id == id) it.copy(isExpanded = !it.isExpanded) else it
                        })
                    },
                )
            }
        }
        compose.onNodeWithTag("account-window:weekly:personal").assertDoesNotExist()
        compose.onNodeWithTag("account-window:weekly:work").assertDoesNotExist()
        compose.onNodeWithTag("account-accordion-toggle:personal").performScrollTo().performClick()
        compose.onNodeWithTag("account-window:weekly:personal").assertExists()
        compose.onNodeWithTag("account-window:weekly:work").assertDoesNotExist()
        compose.onNodeWithTag("account-accordion-toggle:work").performScrollTo().performClick()
        compose.onNodeWithTag("account-window:weekly:work").assertExists()
        compose.onNodeWithTag("account-window:weekly:personal").assertExists()
        compose.onNodeWithTag("account-accordion-toggle:personal").performScrollTo().performClick()
        compose.onNodeWithTag("account-window:weekly:personal").assertDoesNotExist()
        compose.onNodeWithTag("account-window:weekly:work").assertExists()
    }

    @Test
    fun threeCodexCardsShowRemainingAndStableDetailsTarget() {
        var opened: LocalAccountId? = null
        val accounts = listOf(
            codex("personal", 82, 76, true),
            codex("work", 53, 44),
            codex("team", 7, 3),
        )
        compose.setContent {
            QuotaRabbitTheme {
                AccountScreen(state(accounts), onOpenDetails = { opened = it })
            }
        }
        listOf(82, 53, 7).forEachIndexed { index, percent ->
            val id = accounts[index].id.value
            compose.onNodeWithTag("account-overview:$id").assertExists()
            compose.onNodeWithTag("account-window:weekly:$id").assertExists()
            val ring = compose.onNodeWithTag("account-weekly-gauge:$id").fetchSemanticsNode()
            assertEquals(percent / 100f,
                ring.config[SemanticsProperties.ProgressBarRangeInfo].current, 0.0001f)
            compose.onNodeWithTag("account-progress:weekly:$id").assertExists()
            compose.onNodeWithTag("account-progress:five_hour:$id").assertExists()
            val weeklyWidth = compose.onNodeWithTag("account-window:weekly:$id").fetchSemanticsNode().boundsInRoot.width
            val fiveHourWidth = compose.onNodeWithTag("account-window:five_hour:$id").fetchSemanticsNode().boundsInRoot.width
            assertEquals(fiveHourWidth, weeklyWidth, 0.1f)
            compose.onNode(hasText(context.getString(R.string.dashboard_remaining_format, percent)) and
                hasAnyAncestor(hasTestTag("account-window:weekly:$id"))).assertExists()
            compose.onNodeWithTag("account-window:five_hour:$id").assertExists()
            compose.onNodeWithTag("account-banked:$id").assertExists()
            compose.onNodeWithTag("account-renewal:$id").assertDoesNotExist()
            compose.onNodeWithTag("account-success:$id").assertExists()
        }
        compose.onNodeWithTag("account-open-details:work").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(LocalAccountId("work"), opened) }
    }

    @Test
    fun onePercentGradientRemainsVisibleInsideBothQuotaTracks() {
        compose.setContent {
            QuotaRabbitTheme {
                AccountOverviewCard(codex("tiny", weekly = 1, fiveHour = 1).copy(isExpanded = true))
            }
        }
        for (window in listOf("weekly", "five_hour")) {
            val pixels = compose.onNodeWithTag("account-progress:$window:tiny")
                .captureToImage().toPixelMap()
            val middleY = pixels.height / 2
            val filledPixel = pixels[pixels.height / 2, middleY]
            val unfilledPixel = pixels[pixels.width / 2, middleY]
            assertTrue("$window should retain a visible gradient at 1%", filledPixel != unfilledPixel)
        }
    }

    @Test
    fun subscriptionCoverageOnAccountIsNotLabeledRenewal() {
        val item = codex("personal", 82, 76, true).copy(
            subscriptionActiveUntil = Instant.parse("2027-02-15T00:00:00Z"),
            activeUntilFromToken = true,
        )
        compose.setContent {
            QuotaRabbitTheme { AccountScreen(state(listOf(item))) }
        }
        compose.onNodeWithTag("account-renewal:personal", useUnmergedTree = true)
            .assertTextContains(context.getString(R.string.account_subscription_coverage_token, "").trim(), substring = true)
    }

    @Test
    fun reportedBenefitsAppearOnAccountOverviewWithoutLeakingToOtherAccounts() {
        val account = codex("personal", 82, 76, true).copy(
            bankedResetCount = 2,
            providerRenewalAt = Instant.parse("2027-02-01T00:00:00Z"),
        )
        val missing = codex("work", 53, 44)
        compose.setContent {
            QuotaRabbitTheme(themeMode = ThemeMode.DARK) {
                AccountScreen(state(listOf(account, missing)))
            }
        }
        compose.onNodeWithTag("account-banked:personal", useUnmergedTree = true)
            .assertTextEquals(context.getString(R.string.dashboard_banked_resets_count, 2))
        compose.onNodeWithTag("account-renewal:personal", useUnmergedTree = true)
            .assertTextContains(context.getString(R.string.account_renewal_provider, "").trim(), substring = true)
        compose.onNodeWithTag("account-banked:work", useUnmergedTree = true)
            .assertTextEquals(context.getString(R.string.dashboard_banked_resets_unavailable))
    }

    @Test
    fun mixedProvidersAndMissingDataDoNotAcquireCodexGauges() {
        val balance = basic("deepseek", "balance").copy(
            quotaSummaries = listOf(AccountQuotaSummaryUi(
                "balance", R.string.window_label_balance, null, valueText = "$12.50",
                originalValueText = "$10.00",
            )),
        )
        val count = basic("cursor", "count").copy(
            quotaSummaries = listOf(AccountQuotaSummaryUi(
                "requests", R.string.window_label_generic, null, valueText = "12 / 100",
            )),
        )
        val models = basic("antigravity", "models").copy(
            modelFamilies = listOf(AccountModelFamilyUi(
                "Gemini", listOf(AccountModelUi("Gemini Pro", 71)),
            )),
        )
        val missing = codex("unknown", null, null)
        compose.setContent {
            QuotaRabbitTheme {
                AccountScreen(state(listOf(missing, balance, count, models)))
            }
        }
        compose.onNodeWithTag("account-window:weekly:unknown").assertExists()
        assertTrue(compose.onNodeWithTag("account-weekly-gauge:unknown")
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) == null)
        compose.onNodeWithTag("account-window:weekly:balance").assertDoesNotExist()
        compose.onNodeWithTag("account-window:weekly:count").assertDoesNotExist()
        compose.onNodeWithTag("account-window:weekly:models").assertDoesNotExist()
        compose.onNodeWithTag("account-banked:balance").assertDoesNotExist()
        compose.onNode(hasText("$12.50") and
            hasAnyAncestor(hasTestTag("account-overview:balance"))).assertExists()
        compose.onNode(hasText("12 / 100") and
            hasAnyAncestor(hasTestTag("account-overview:count"))).assertExists()
        compose.onNode(hasText("Gemini Pro") and
            hasAnyAncestor(hasTestTag("account-overview:models"))).assertExists()
    }

    @Test
    fun detailActionsAndConfirmedDeleteRemainReachableAtLargeFontScale() {
        val selected = codex("work", 55, 40)
        val uiState = mutableStateOf(state(listOf(selected)))
        val events = mutableListOf<String>()
        compose.setContent {
            QuotaRabbitTheme(themeMode = ThemeMode.DARK) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    AccountScreen(
                        uiState = uiState.value,
                        modifier = Modifier.width(320.dp).fillMaxHeight(),
                        detailAccountId = selected.id,
                        onBackClick = { events.add("back") },
                        onSetCurrentClick = { events.add("current") },
                        onRenameClick = { events.add("rename") },
                        onReloginClick = { events.add("relogin") },
                        onQuotaAlertToggle = { _, _, _ -> events.add("alert") },
                        onDeleteClick = {
                            uiState.value = uiState.value.copy(
                                pendingDeleteAccount = AccountDeleteConfirmationUi(it, "Work"),
                            )
                        },
                        onDeleteConfirm = { events.add("confirmed") },
                        onDeleteDismiss = {
                            uiState.value = uiState.value.copy(pendingDeleteAccount = null)
                        },
                    )
                }
            }
        }
        compose.onNodeWithTag("account-detail-hero:work").performScrollTo().assertExists()
        compose.onNodeWithTag("account-detail-gauge:work").performScrollTo().assertExists()
        compose.onNodeWithTag("account-detail-window:weekly:work").performScrollTo().assertExists()
        compose.onNodeWithTag("account-detail-window:five_hour:work").performScrollTo().assertExists()
        for (action in listOf("current", "rename", "relogin")) {
            compose.onNodeWithTag("account-action:$action:work").performScrollTo().performClick()
        }
        compose.onNodeWithTag("account-alert:work:weekly").performScrollTo().performClick()
        compose.onNodeWithTag("account-action:delete:work").performScrollTo().performClick()
        compose.onNodeWithTag("account-delete-dialog").assertExists()
        compose.onNode(hasText(context.getString(R.string.account_delete_dialog_confirm)) and
            hasAnyAncestor(hasTestTag("account-delete-dialog"))).performClick()
        compose.onNodeWithTag("account-details-back").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(events.containsAll(listOf("current", "rename", "relogin", "alert", "confirmed", "back")))
        }
    }

    @Test
    fun actualNavHostOpensDetailsBySavedLocalIdAndReturns() {
        val id = LocalAccountId("stable-local-id")
        val saved = ProviderAccount.createNew(
            localAccountId = id,
            providerId = ProviderId("codex"),
            providerAccountId = ProviderAccountId("synthetic-provider-id"),
            displayName = "Synthetic Work",
            now = Instant.parse("2026-10-10T01:00:00Z"),
        )
        val stateLoader = HomeCurrentQuotaStateLoader {
            CurrentQuotaStateFactory().create(
                account = null, latestSnapshot = null, latestAttempt = null, now = Instant.EPOCH,
            )
        }
        val accountList = object : AccountListUseCase {
            override suspend fun loadAccounts() = AccountListResult(
                accounts = listOf(saved), currentAccountId = id,
            )
        }
        compose.setContent {
            QuotaRabbitTheme {
                QuotaRabbitNavHost(
                    startDestination = QuotaRabbitRoute.Account.route,
                    accountListUseCase = accountList,
                    accountDeleteUseCase = NoopAccountDeleteUseCase,
                    homeCurrentQuotaStateLoader = stateLoader,
                    homeRefreshUseCase = HomeRefreshUseCase { stateLoader.loadCurrentState() },
                )
            }
        }
        compose.onNodeWithTag("account-accordion-toggle:stable-local-id")
            .performScrollTo().performClick()
        compose.onNodeWithTag("account-open-details:stable-local-id")
            .performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                compose.onAllNodes(hasTestTag("account-details:stable-local-id"))
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        compose.onNodeWithTag("account-details:stable-local-id").assertExists()
        compose.onNodeWithTag("account-action:rename:stable-local-id")
            .performScrollTo().performClick()
        compose.onNodeWithTag("account-edit-dialog").assertExists()
        compose.onNode(hasText(context.getString(R.string.account_cancel)) and
            hasAnyAncestor(hasTestTag("account-edit-dialog"))).performClick()
        compose.onNodeWithTag("account-details-back").performScrollTo().performClick()
        compose.onNodeWithTag("account-accordion-toggle:stable-local-id").assertExists()
    }

    @Test
    fun responsiveOverviewStaysWithin320DpForLightDarkAndLargeFonts() {
        val mode = mutableStateOf(ThemeMode.LIGHT)
        val fontScale = mutableStateOf(1f)
        val items = listOf(codex("work", 84, 52, true), basic("deepseek", "balance"))
        compose.setContent {
            QuotaRabbitTheme(themeMode = mode.value) {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale.value)) {
                    AccountScreen(state(items), modifier = Modifier.width(320.dp).fillMaxHeight())
                }
            }
        }
        for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            for (scale in listOf(1f, 1.5f, 2f)) {
                compose.runOnIdle { mode.value = theme; fontScale.value = scale }
                val outer = compose.onNodeWithTag("account-screen").fetchSemanticsNode().boundsInRoot
                for (tag in listOf("account-open-details:work",
                    "account-window:weekly:work", "account-window:five_hour:work",
                    "account-open-details:balance")) {
                    val node = compose.onNodeWithTag(tag)
                    node.performScrollTo()
                    val bounds = node.fetchSemanticsNode().boundsInRoot
                    assertTrue("$theme / $scale $tag clipped on left", bounds.left >= outer.left - 1f)
                    assertTrue("$theme / $scale $tag clipped on right", bounds.right <= outer.right + 1f)
                }
                if (scale >= 1.5f) {
                    val weekly = compose.onNodeWithTag("account-window:weekly:work").apply {
                        performScrollTo()
                    }.fetchSemanticsNode().boundsInRoot
                    val ring = compose.onNodeWithTag("account-weekly-gauge:work")
                        .fetchSemanticsNode().boundsInRoot
                    assertTrue("$theme / $scale ring should stack above full-width bars",
                        ring.bottom <= weekly.top + 1f)
                }
            }
        }
    }

    @Test
    fun editCancelSaveAndClearManualDateRemainAccountSpecific() {
        val work = codex("work", 80, 65, true)
        val other = codex("other", 33, 20)
        val state = mutableStateOf(state(listOf(work, other)))
        val writes = mutableListOf<Pair<LocalAccountId, AccountEditInput>>()
        compose.setContent {
            QuotaRabbitTheme {
                AccountScreen(
                    uiState = state.value,
                    detailAccountId = work.id,
                    onRenameClick = {
                        state.value = state.value.copy(pendingEditAccount = state.value.accounts[0])
                    },
                    onEditDismiss = {
                        state.value = state.value.copy(pendingEditAccount = null)
                    },
                    onEditSave = { id, edit ->
                        writes += id to edit
                        val updated = state.value.accounts.map { account ->
                            if (account.id == id) account.copy(
                                displayName = edit.nickname,
                                accentColorId = edit.accentColorId,
                                manualRenewalDate = edit.manualRenewalDate,
                            ) else account
                        }
                        state.value = state.value.copy(accounts = updated, pendingEditAccount = null)
                    },
                )
            }
        }
        fun open() {
            compose.onNodeWithTag("account-action:rename:work").performScrollTo().performClick()
            compose.onNodeWithTag("account-edit-dialog").assertExists()
        }
        open()
        compose.onNodeWithTag("account-edit-nickname").performTextReplacement("Work New")
        compose.onNodeWithTag("account-edit-color:teal").performScrollTo().performClick()
        compose.onNodeWithTag("account-edit-renewal").performTextReplacement("2026-02-30")
        compose.onNodeWithTag("account-edit-save").performClick()
        compose.onNode(hasText(context.getString(R.string.account_edit_invalid)) and
            hasAnyAncestor(hasTestTag("account-edit-dialog"))).assertExists()
        compose.onNodeWithTag("account-edit-cancel").performClick()
        compose.runOnIdle {
            assertTrue(writes.isEmpty())
            assertEquals("Account work", state.value.accounts[0].displayName)
        }

        open()
        compose.onNodeWithTag("account-edit-nickname").performTextReplacement("Work New")
        compose.onNodeWithTag("account-edit-color:teal").performScrollTo().performClick()
        compose.onNodeWithTag("account-edit-renewal").performTextReplacement("2027-01-01")
        compose.onNodeWithTag("account-edit-save").performClick()
        compose.runOnIdle {
            assertEquals(1, writes.size)
            assertEquals("Work New", state.value.accounts[0].displayName)
            assertEquals("teal", state.value.accounts[0].accentColorId)
            assertEquals("2027-01-01", state.value.accounts[0].manualRenewalDate)
            assertEquals("Account other", state.value.accounts[1].displayName)
            assertEquals(null, state.value.accounts[1].manualRenewalDate)
        }
        compose.onNodeWithTag("account-renewal:work").assertExists()
        open()
        compose.onNodeWithTag("account-edit-clear-renewal").performScrollTo().performClick()
        compose.onNodeWithTag("account-edit-save").performClick()
        compose.runOnIdle {
            assertEquals(2, writes.size)
            assertEquals(null, state.value.accounts[0].manualRenewalDate)
            assertEquals("teal", state.value.accounts[0].accentColorId)
        }
    }

    @Test
    fun planAndProviderRemainInHeaderAcrossAccordionStates() {
        val account = codex("personal", 82, 76).copy(
            providerDisplayName = "Codex",
            providerIconResId = R.drawable.ic_brand_codex,
            planType = "Plus",
        )
        val cursor = basic("cursor", "work").copy(
            providerDisplayName = "Cursor",
            planType = "Cursor Pro",
        )
        val viewState = mutableStateOf(state(listOf(account, cursor), initiallyExpanded = false))
        compose.setContent {
            QuotaRabbitTheme {
                AccountScreen(uiState = viewState.value, onToggleExpanded = { id ->
                    viewState.value = viewState.value.copy(accounts = viewState.value.accounts.map {
                        if (it.id == id) it.copy(isExpanded = !it.isExpanded) else it
                    })
                })
            }
        }
        for (pair in listOf("personal" to "Codex · Plus", "work" to "Cursor · Pro")) {
            val (id, label) = pair
            val headerTag = "account-accordion-toggle:$id"
            compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag(headerTag)),
                useUnmergedTree = true).assertExists()
            compose.onNodeWithTag(headerTag).performClick()
            compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag(headerTag)),
                useUnmergedTree = true).assertExists()
            compose.onNodeWithTag(headerTag).performClick()
            compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag(headerTag)),
                useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun providerLogosRemainVisibleWhenAccountAccordionsToggle() {
        val accounts = listOf(
            codex("personal", 82, 76).copy(providerIconResId = R.drawable.ic_brand_codex),
            basic("deepseek", "balance").copy(providerIconResId = R.drawable.ic_brand_deepseek),
            basic("unknown", "legacy"),
        )
        val viewState = mutableStateOf(state(accounts, initiallyExpanded = false))
        compose.setContent {
            QuotaRabbitTheme {
                AccountScreen(
                    uiState = viewState.value,
                    onToggleExpanded = { id ->
                        viewState.value = viewState.value.copy(accounts = viewState.value.accounts.map {
                            if (it.id == id) it.copy(isExpanded = !it.isExpanded) else it
                        })
                    },
                )
            }
        }
        compose.onNodeWithTag("account-provider-logo:personal", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("account-provider-logo:balance", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("account-provider-logo:legacy", useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(hasText("unknown") and hasAnyAncestor(hasTestTag("account-overview:legacy")),
            useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("account-window:weekly:personal").assertDoesNotExist()
        compose.onNodeWithTag("account-accordion-toggle:personal").performScrollTo().performClick()
        compose.onNodeWithTag("account-provider-logo:personal", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("account-window:weekly:personal").assertExists()
        compose.onNodeWithTag("account-accordion-toggle:personal").performScrollTo().performClick()
        compose.onNodeWithTag("account-provider-logo:personal", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("account-window:weekly:personal").assertDoesNotExist()
    }

    @Test
    fun providerChipsFilterSavedAccountsAndHandleRemovedProvider() {
        val codexAccount = codex("personal", 82, 76, true)
        val state = mutableStateOf(state(listOf(codexAccount, basic("deepseek", "balance"))))
        compose.setContent {
            QuotaRabbitTheme { AccountScreen(uiState = state.value) }
        }
        compose.onNodeWithTag("provider-filter:codex-icon", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("provider-filter:deepseek-icon", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("provider-filter:deepseek").performClick()
        compose.onNodeWithTag("account-overview:personal").assertDoesNotExist()
        compose.onNodeWithTag("account-overview:balance").assertExists()

        compose.runOnIdle { state.value = state(listOf(codexAccount)) }
        compose.onNodeWithTag("provider-filter:deepseek").assertDoesNotExist()
        compose.onNodeWithTag("account-overview:personal").assertExists()
    }

    private fun state(accounts: List<AccountItemUi>, initiallyExpanded: Boolean = true) =
        AccountUiState.Empty.copy(
            contentStatus = AccountContentStatus.HasAccounts,
            accounts = accounts.map { it.copy(isExpanded = initiallyExpanded) },
            currentAccountId = accounts.firstOrNull { it.isCurrent }?.id,
            currentAccount = accounts.firstOrNull { it.isCurrent },
        )

    private fun codex(id: String, weekly: Int?, fiveHour: Int?, current: Boolean = false) =
        basic("codex", id).copy(
            isCurrent = current,
            quotaSummaries = listOf(
                AccountQuotaSummaryUi("five_hour", R.string.account_quota_five_hour_label,
                    fiveHour, resetAt = Instant.parse("2026-10-11T01:00:00Z")),
                AccountQuotaSummaryUi("weekly", R.string.account_quota_weekly_label,
                    weekly, resetAt = Instant.parse("2026-10-17T01:00:00Z")),
            ),
            alertToggles = listOf(
                AccountAlertToggleUi("weekly", R.string.account_quota_alert_weekly, true),
            ),
        )

    private fun basic(provider: String, id: String) = AccountItemUi(
        id = LocalAccountId(id),
        displayName = "Account $id",
        avatarInitial = "A",
        avatarColorKey = id,
        isCurrent = false,
        status = AccountStatus.Active,
        tone = AccountStatusTone.Success,
        badgeLabelResId = R.string.account_badge_available,
        statusLabelResId = R.string.account_status_active,
        refreshSummaryResId = R.string.account_last_refresh_format,
        lastSuccessfulRefreshAt = Instant.parse("2026-10-10T01:00:00Z"),
        planType = null,
        credits = AccountCreditsUi.Unavailable,
        isExpanded = false,
        alertToggles = emptyList(),
        quotaSummaries = emptyList(),
        providerId = ProviderId(provider),
        providerDisplayName = provider,
    )
}
