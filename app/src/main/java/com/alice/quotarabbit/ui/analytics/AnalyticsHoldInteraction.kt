package com.alice.quotarabbit.ui.analytics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor

/** Hold-only selection. A short tap never activates it; up/cancel always clears it. */
internal fun Modifier.analyticsPressHoldDay(
    date: LocalDate,
    activeDate: MutableState<LocalDate?>,
): Modifier = pointerInput(date, activeDate) {
    try {
        detectDragGesturesAfterLongPress(
            onDragStart = { activeDate.value = date },
            onDragEnd = { activeDate.value = null },
            onDragCancel = { activeDate.value = null },
            onDrag = { change, _ -> change.consume() },
        )
    } finally {
        if (activeDate.value == date) activeDate.value = null
    }
}

/** Drawn over the plot without resizing or displacing any bar. */
@Composable
internal fun AnalyticsHoldDetail(
    date: LocalDate,
    rows: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    val palette = QuotaRabbitTheme.colors
    Surface(
        modifier = modifier.widthIn(max = 296.dp).testTag("analytics-hold-tooltip"),
        shape = RoundedCornerShape(14.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd.", Locale.ROOT)),
                color = palette.primary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelMedium)
            rows.take(7).forEach { (label, value) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(label, modifier = Modifier.weight(1f),
                        color = palette.secondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1)
                    Spacer(Modifier.width(12.dp))
                    Text(value, color = palette.primary,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}


/** Full plotting surface hit testing (including gaps and blank space).
 *  Does not consume ordinary swipes, preserving vertical and horizontal scrolling.
 */
internal enum class AnalyticsHoldPlotAxis {
    VERTICAL_SEVEN_DAYS, HORIZONTAL_SCROLLED, HORIZONTAL_FIT
}

internal fun Modifier.analyticsPressHoldPlot(
    dates: List<LocalDate>,
    activeDate: MutableState<LocalDate?>,
    axis: AnalyticsHoldPlotAxis,
    scrollOffsetPx: () -> Int = { 0 },
): Modifier = pointerInput(dates, activeDate, axis) {
    if (dates.isEmpty()) return@pointerInput
    val pitch = when (axis) {
        AnalyticsHoldPlotAxis.VERTICAL_SEVEN_DAYS -> 26.dp.toPx()
        AnalyticsHoldPlotAxis.HORIZONTAL_SCROLLED -> 30.dp.toPx()
        AnalyticsHoldPlotAxis.HORIZONTAL_FIT -> 0f
    }

    detectTapGestures(
        onPress = {
            try {
                tryAwaitRelease()
            } finally {
                activeDate.value = null
            }
        },
        onLongPress = { position: Offset ->
            val index = when (axis) {
                AnalyticsHoldPlotAxis.VERTICAL_SEVEN_DAYS ->
                    floor((position.y + 5.dp.toPx()) / pitch).toInt()
                AnalyticsHoldPlotAxis.HORIZONTAL_SCROLLED ->
                    floor((position.x + scrollOffsetPx() + 4.dp.toPx()) / pitch).toInt()
                AnalyticsHoldPlotAxis.HORIZONTAL_FIT ->
                    floor(position.x / size.width.coerceAtLeast(1) * dates.size).toInt()
            }.coerceIn(0, dates.lastIndex)
            activeDate.value = dates[index]
        },
    )
}
