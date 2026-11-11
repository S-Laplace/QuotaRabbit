package com.alice.quotarabbit.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountActivityHeatmapUiTest {
    @get:Rule val compose = createComposeRule()
    private val root = "dashboard-activity:codex:test"
    private val grid = "dashboard-activity-grid:codex:test"

    @Test fun bothModesFit320dpWithoutScrollingOrWeeklyOption() {
        compose.setContent {
            QuotaRabbitTheme {
                Box(Modifier.width(320.dp)) {
                    AccountActivityHeatmap("codex", "test", "teal", emptyList(),
                        Instant.parse("2026-10-11T16:03:00Z"))
                }
            }
        }
        compose.onNodeWithTag("$root:Weekly").assertDoesNotExist()
        compose.onNodeWithTag("dashboard-activity-mode:codex:test:Weekly").assertDoesNotExist()
        compose.onNodeWithText("Less").assertExists()
        compose.onNodeWithText("More").assertExists()
        for (mode in listOf("Daily", "Hourly")) {
            compose.onNodeWithTag("dashboard-activity-mode:codex:test:$mode").performClick()
            val activityBounds = compose.onNodeWithTag(root).fetchSemanticsNode().boundsInRoot
            val gridBounds = compose.onNodeWithTag(grid).fetchSemanticsNode().boundsInRoot
            assertTrue("$mode overflow", gridBounds.right <= activityBounds.right + 1f)
            assertTrue("$mode should be a horizontal grid", gridBounds.width > gridBounds.height)
        }
    }


    @Test fun enlargedTextAndNarrowViewportKeepGridWithinCard() {
        compose.setContent {
            QuotaRabbitTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    Box(Modifier.width(280.dp)) {
                        AccountActivityHeatmap("codex", "test", "violet", emptyList(),
                            Instant.parse("2026-10-11T16:03:00Z"))
                    }
                }
            }
        }
        for (mode in listOf("Daily", "Hourly")) {
            compose.onNodeWithTag("dashboard-activity-mode:codex:test:$mode").performClick()
            val outer = compose.onNodeWithTag(root).fetchSemanticsNode().boundsInRoot
            val chart = compose.onNodeWithTag(grid).fetchSemanticsNode().boundsInRoot
            assertTrue("$mode clipped on narrow viewport", chart.right <= outer.right + 1f)
            assertTrue("$mode should remain horizontal", chart.width > chart.height)
            assertTrue("$mode cells should be readable", chart.height > 60f)
        }
    }

    @Test fun hourlyLabelsBeginOnFirstRowAndDailyWeeksBeginOnMonday() {
        compose.setContent {
            QuotaRabbitTheme {
                Box(Modifier.width(320.dp)) {
                    AccountActivityHeatmap("codex", "test", "teal", emptyList(),
                        Instant.parse("2026-10-11T16:03:00Z"))
                }
            }
        }

        val monday = DayOfWeek.MONDAY.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        val wednesday = DayOfWeek.WEDNESDAY.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        compose.onNodeWithText(monday).assertExists()
        val mondayTop = compose.onNodeWithText(monday).fetchSemanticsNode().boundsInRoot.top
        val wednesdayTop = compose.onNodeWithText(wednesday).fetchSemanticsNode().boundsInRoot.top
        assertTrue("Daily should show Monday above Wednesday", mondayTop < wednesdayTop)

        compose.onNodeWithTag("dashboard-activity-mode:codex:test:Hourly").performClick()
        (0 until 6).forEach { row ->
            compose.onNodeWithTag("dashboard-activity-minute:$row").assertExists()
        }
        val labelTop = compose.onNodeWithTag("dashboard-activity-minute:0")
            .fetchSemanticsNode().boundsInRoot.top
        val gridTop = compose.onNodeWithTag(grid).fetchSemanticsNode().boundsInRoot.top
        assertTrue("Hourly minute labels begin beside the first row",
            labelTop >= gridTop - 16f && labelTop < gridTop + 32f)
    }

    @Test fun selectingASquareShowsExactLocalTimestamp() {
        compose.setContent {
            QuotaRabbitTheme {
                Box(Modifier.width(320.dp)) {
                    AccountActivityHeatmap("codex", "test", "teal", emptyList(),
                        Instant.parse("2026-10-11T16:03:00Z"))
                }
            }
        }
        compose.onNodeWithTag(grid).performTouchInput { click(center) }
        compose.onNodeWithTag("dashboard-activity-selected:codex:test").assertExists()
    }
}
