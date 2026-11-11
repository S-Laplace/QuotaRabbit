package com.alice.quotarabbit.ui.analytics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalyticsHoldInteractionUiTest {
    @get:Rule val compose = createComposeRule()

    private fun setUpChart() {
        compose.setContent {
            QuotaRabbitTheme {
                val held = remember { mutableStateOf<LocalDate?>(null) }
                val date = LocalDate.of(2026, 11, 3)
                Box(Modifier.fillMaxWidth().height(160.dp)) {
                    Box(Modifier.size(240.dp, 125.dp)
                        .analyticsPressHoldDay(date, held)
                        .testTag("press-target"))
                    held.value?.let {
                        AnalyticsHoldDetail(it, listOf("Total credits" to "12.3"),
                            Modifier.align(Alignment.TopEnd))
                    }
                }
            }
        }
    }

    @Test fun shortTapNeverOpensTooltip() {
        setUpChart()
        compose.onNodeWithTag("press-target").performTouchInput { down(center); up() }
        compose.onNodeWithTag("analytics-hold-tooltip").assertDoesNotExist()
    }

    @Test fun longPressShowsOnlyWhileFingerIsDown() {
        setUpChart()
        val target = compose.onNodeWithTag("press-target")
        target.performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            move()
        }
        compose.onNodeWithTag("analytics-hold-tooltip").assertExists()
        target.performTouchInput { up() }
        compose.onNodeWithTag("analytics-hold-tooltip").assertDoesNotExist()
    }

    @Test fun cancellingTheGestureClosesTooltipImmediately() {
        setUpChart()
        val target = compose.onNodeWithTag("press-target")
        target.performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            move()
        }
        compose.onNodeWithTag("analytics-hold-tooltip").assertExists()
        target.performTouchInput { cancel() }
        compose.onNodeWithTag("analytics-hold-tooltip").assertDoesNotExist()
    }
}
