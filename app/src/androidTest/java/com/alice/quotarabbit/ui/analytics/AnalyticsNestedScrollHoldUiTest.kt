package com.alice.quotarabbit.ui.analytics
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
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
class AnalyticsNestedScrollHoldUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pressOnThirtyDayChartInsideRealisticNestedScrollShowsDetail() {
        compose.setContent {
            QuotaRabbitTheme {
                val held = remember { mutableStateOf<LocalDate?>(null) }
                val first = LocalDate.of(2026, 10, 5)
                Column(Modifier.fillMaxWidth().height(410.dp)
                    .verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(120.dp))
                    Box(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth()
                            .horizontalScroll(rememberScrollState())) {
                            repeat(30) { index ->
                                Column(Modifier.width(22.dp)
                                    .analyticsPressHoldDay(first.plusDays(index.toLong()), held)
                                    .testTag("nested-day-$index")) {
                                    Spacer(Modifier.height(166.dp))
                                    Spacer(Modifier.height(17.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                        }
                        held.value?.let {
                            AnalyticsHoldDetail(it, listOf("Total" to "5.0"),
                                Modifier.align(Alignment.TopCenter))
                        }
                    }
                    Spacer(Modifier.height(500.dp))
                }
            }
        }
        val target = compose.onNodeWithTag("nested-day-2")
        target.performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 120)
            move()
        }
        compose.onNodeWithTag("analytics-hold-tooltip").assertExists()
        target.performTouchInput { up() }
        compose.onNodeWithTag("analytics-hold-tooltip").assertDoesNotExist()
    }
}
