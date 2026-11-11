package com.alice.quotarabbit.ui.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alice.quotarabbit.R
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import com.alice.quotarabbit.domain.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Home reads only synthetic display metadata; no provider requests. */
@RunWith(AndroidJUnit4::class)
class Phase06HomeManualMetadataTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun freshHomeCardOmitsFreshnessAndShowsSelectableActivityGrid() {
        val card = HomePreviewFixtures.state("metadata", HomePreviewFixtures.metadataNow).accountCards.first()
        compose.setContent {
            QuotaRabbitTheme { HomeDashboardAccountCard(card, onManageAccountsClick = {}) }
        }
        compose.onNodeWithText("Data fresh").assertDoesNotExist()
        compose.onNodeWithTag("dashboard-activity:codex:preview-personal").assertDoesNotExist()
        compose.onNodeWithTag("dashboard-accordion-toggle:codex:preview-personal").performClick()
        compose.onNodeWithTag("dashboard-activity:codex:preview-personal").assertExists()
        compose.onNodeWithTag("dashboard-activity-grid:codex:preview-personal").assertExists()
        compose.onNodeWithTag("dashboard-activity-mode:codex:preview-personal:Daily").assertIsSelected()
        compose.onNodeWithTag("dashboard-activity-mode:codex:preview-personal:Hourly").performClick()
        compose.onNodeWithTag("dashboard-activity-mode:codex:preview-personal:Hourly").assertIsSelected()
    }

    @Test fun providerReportedBenefitsStayAccountSpecificAndManualDateOverridesThem() {
        val cards = HomePreviewFixtures.state("metadata", HomePreviewFixtures.metadataNow).accountCards
        val current = mutableStateOf(cards.first().copy(
            state = cards.first().state.copy(
                account = cards.first().state.account!!.copy(
                    bankedResetCount = 2,
                    providerRenewalAt = java.time.Instant.parse("2027-02-01T00:00:00Z"),
                ),
            ),
        ))
        compose.setContent {
            QuotaRabbitTheme(themeMode = ThemeMode.DARK) {
                HomeDashboardAccountCard(current.value, onManageAccountsClick = {})
            }
        }
        compose.onNodeWithTag("dashboard-renewal:codex:preview-personal").assertDoesNotExist()
        compose.onNodeWithTag("dashboard-accordion-toggle:codex:preview-personal").performClick()
        compose.onNodeWithTag("dashboard-banked:codex:preview-personal", useUnmergedTree = true)
            .assertTextEquals(context.getString(R.string.dashboard_banked_resets_count, 2))
        compose.onNodeWithTag("dashboard-renewal:codex:preview-personal", useUnmergedTree = true)
            .assertTextContains(context.getString(R.string.account_renewal_provider, "").trim(), substring = true)
        compose.runOnIdle { current.value = cards[1] }
        compose.onNodeWithTag("dashboard-banked:codex:preview-work", useUnmergedTree = true)
            .assertTextEquals("Banked resets: Unavailable")
    }

    @Test fun jwtCoverageIsVisibleWithoutClaimingItIsAConfirmedRenewal() {
        val cards = HomePreviewFixtures.state("metadata", HomePreviewFixtures.metadataNow).accountCards
        val initial = cards.first()
        val current = mutableStateOf(initial.copy(state = initial.state.copy(
            account = initial.state.account!!.copy(
                subscriptionActiveUntil = java.time.Instant.parse("2027-02-15T00:00:00Z"),
                activeUntilFromToken = true,
            ),
        )))
        compose.setContent {
            QuotaRabbitTheme(themeMode = ThemeMode.DARK) {
                HomeDashboardAccountCard(current.value, onManageAccountsClick = {})
            }
        }
        compose.onNodeWithTag("dashboard-renewal:codex:preview-personal").assertDoesNotExist()
        compose.onNodeWithTag("dashboard-accordion-toggle:codex:preview-personal").performClick()
        compose.onNodeWithTag("dashboard-renewal:codex:preview-personal", useUnmergedTree = true)
            .assertTextContains(context.getString(R.string.account_subscription_coverage_token, "").trim(), substring = true)
        compose.runOnIdle {
            current.value = current.value.copy(state = current.value.state.copy(
                account = current.value.state.account!!.copy(manualRenewalDate = "2027-02-01"),
            ))
        }
        compose.onNodeWithTag("dashboard-renewal:codex:preview-personal", useUnmergedTree = true)
            .assertTextEquals(context.getString(R.string.account_renewal_manual, "27.02.01."))
    }

    @Test fun manualRenewalUsesLocalizedCopyAndDoesNotLeakBetweenAccounts() {
        val cards = HomePreviewFixtures.state("metadata", HomePreviewFixtures.metadataNow).accountCards
        val current = mutableStateOf(cards.first())
        compose.setContent {
            QuotaRabbitTheme(themeMode = ThemeMode.DARK) {
                HomeDashboardAccountCard(current.value, onManageAccountsClick = {})
            }
        }
        compose.onNodeWithTag("dashboard-renewal:codex:preview-personal").assertDoesNotExist()
        compose.onNodeWithTag("dashboard-accordion-toggle:codex:preview-personal").performClick()
        compose.onNodeWithTag("dashboard-renewal:codex:preview-personal").assertDoesNotExist()
        compose.runOnIdle {
            current.value = cards.first().copy(state = cards.first().state.copy(
                account = cards.first().state.account!!.copy(
                    manualRenewalDate = "2027-01-01", accentColorId = "teal",
                ),
            ))
        }
        compose.onNodeWithTag("dashboard-renewal:codex:preview-personal", useUnmergedTree = true)
            .assertTextEquals(context.getString(R.string.account_renewal_manual, "27.01.01."))
        compose.runOnIdle { current.value = cards[1] }
        compose.onNodeWithTag("dashboard-renewal:codex:preview-work").assertDoesNotExist()
    }
}
