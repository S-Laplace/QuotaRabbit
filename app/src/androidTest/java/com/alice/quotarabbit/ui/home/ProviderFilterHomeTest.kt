package com.alice.quotarabbit.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses synthetic, offline dashboard state with multiple saved providers. */
@RunWith(AndroidJUnit4::class)
class ProviderFilterHomeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun choosingProviderFiltersCardsAndAllRestoresThem() {
        compose.setContent {
            QuotaRabbitTheme { HomeScreen(HomePreviewFixtures.state("mixed")) }
        }
        compose.onNodeWithTag("provider-filter:all").assertExists()
        compose.onNodeWithTag("provider-filter:codex-icon", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("provider-filter:deepseek-icon", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("provider-filter:deepseek").performClick()
        compose.onNodeWithTag("dashboard-account:codex:personal").assertDoesNotExist()
        compose.onNodeWithTag("dashboard-account:deepseek:balance").assertExists()
        compose.onNodeWithTag("provider-filter:all").performClick()
        compose.onNodeWithTag("dashboard-account:codex:personal").assertExists()
    }
}
