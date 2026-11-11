package com.alice.quotarabbit.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alice.quotarabbit.R
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import com.alice.quotarabbit.ui.theme.accountAccentColor
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Four intensity levels derived from the account's selected accent color. */
internal fun activityIntensityColors(accent: Color): List<Color> = listOf(
    accent.copy(alpha = 0.28f),
    accent.copy(alpha = 0.48f),
    accent.copy(alpha = 0.73f),
    accent,
)

@Composable
internal fun AccountActivityHeatmap(
    providerId: String,
    accountId: String,
    accentColorId: String?,
    points: List<HomeTrendPointUi>,
    now: Instant,
) {
    var period by rememberSaveable(providerId, accountId) { mutableStateOf(ActivityPeriod.Daily) }
    var selected by remember(providerId, accountId, period) { mutableStateOf<ActivityCell?>(null) }
    val zone = ZoneId.systemDefault()
    val accent = accountAccentColor(accentColorId) ?: QuotaRabbitTheme.colors.accent
    val intensityColors = remember(accent) { activityIntensityColors(accent) }
    val neutral = MaterialTheme.colorScheme.outlineVariant
    val noSample = neutral.copy(alpha = 0.42f)
    val futureSample = neutral.copy(alpha = 0.26f)
    val noActivity = neutral.copy(alpha = 0.85f)
    val highlight = Color(0xFFFFC857)
    val isHourly = period == ActivityPeriod.Hourly
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val axisWidth = if (isHourly) 36.dp else if (largeText) 0.dp else 27.dp
    val axisGap = if (largeText && !isHourly) 0.dp else 4.dp
    val gap = 2.5.dp
    val axisStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp)

    Column(
        modifier = Modifier.fillMaxWidth().testTag("dashboard-activity:$providerId:$accountId"),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(stringResource(R.string.home_activity_title), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActivityPeriod.entries.forEach { option ->
                FilterChip(
                    selected = period == option,
                    onClick = { period = option },
                    modifier = Modifier.testTag("dashboard-activity-mode:$providerId:$accountId:${option.name}"),
                    label = {
                        Text(stringResource(when (option) {
                            ActivityPeriod.Hourly -> R.string.home_activity_hourly
                            ActivityPeriod.Daily -> R.string.home_activity_daily
                        }), style = MaterialTheme.typography.labelSmall)
                    },
                )
            }
        }
        // Exact slot date/time is available by tapping a square, without a scrolling grid.
        selected?.let { cell ->
            Text(
                text = cell.time.format(DateTimeFormatter.ofPattern(
                    if (isHourly) "yy.MM.dd. HH:mm" else "yy.MM.dd. (EEE)",
                    Locale.getDefault(),
                )),
                modifier = Modifier.testTag("dashboard-activity-selected:$providerId:$accountId"),
                style = axisStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val availableWidth = (maxWidth - axisWidth - axisGap).coerceAtLeast(1.dp)
            val columnCount = activityColumnCount(availableWidth.value)
            val grid = remember(points, period, now.epochSecond / 300, zone, columnCount) {
                AccountActivityHeatmapData.build(points, period, now.atZone(zone), columnCount)
            }
            val columns = grid.columns.size
            val rows = grid.columns.firstOrNull()?.cells?.size ?: 0
            val cellSize = ((availableWidth - gap * (columns - 1)) / columns)
                .coerceIn(0.dp, 18.dp)
            val stride = cellSize + gap
            val chartWidth = cellSize * columns + gap * (columns - 1)
            val chartHeight = cellSize * rows + gap * (rows - 1)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Spacer(Modifier.width(axisWidth + axisGap))
                    if (isHourly) {
                        // Labels align to their own hour columns, rather than being
                        // spaced independently across the chart width.
                        val hourTicks = hourlyHourTickIndices(columns).toSet()
                        Row(Modifier.width(chartWidth).height(if (largeText) 30.dp else 18.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            repeat(columns) { index ->
                                Box(Modifier.width(cellSize), contentAlignment = Alignment.Center) {
                                    if (index in hourTicks) Text(
                                        grid.columns[index].label, maxLines = 1,
                                        style = axisStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (index < columns - 1) Spacer(Modifier.width(gap))
                            }
                        }
                    } else {
                        val positions = if (chartWidth < 190.dp || largeText)
                            listOf(0, columns - 1) else listOf(0, columns / 2, columns - 1)
                        Row(
                            Modifier.width(chartWidth).height(if (largeText) 30.dp else 18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            positions.distinct().forEach { index ->
                                val column = grid.columns[index]
                                val time = if (index == columns - 1)
                                    column.cells.lastOrNull { !it.future }?.time ?: column.cells.last().time
                                else column.cells.first().time
                                Text(
                                    time.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())),
                                    maxLines = 1,
                                    style = axisStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.width(axisWidth).height(chartHeight),
                        verticalArrangement = Arrangement.spacedBy(gap)) {
                        repeat(rows) { row ->
                            Box(Modifier.height(cellSize).fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart) {
                                val time = grid.columns.lastOrNull()?.cells?.get(row)?.time
                                val label = when {
                                    isHourly -> hourlyMinuteLabel(row)
                                    largeText || time == null -> ""
                                    time.dayOfWeek in setOf(
                                        DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY,
                                    ) -> time.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                                    else -> ""
                                }
                                if (label.isNotBlank()) Text(label,
                                    modifier = if (isHourly) Modifier.testTag("dashboard-activity-minute:$row")
                                        else Modifier,
                                    style = axisStyle, maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.width(axisGap))
                    val description = stringResource(if (grid.observed > 0)
                        R.string.home_activity_observed else R.string.home_activity_no_samples)
                    Canvas(Modifier.width(chartWidth).height(chartHeight)
                        .testTag("dashboard-activity-grid:$providerId:$accountId")
                        .semantics { contentDescription = description }
                        .pointerInput(grid, cellSize, gap) {
                            detectTapGestures { tap ->
                                val step = (cellSize + gap).toPx()
                                if (step > 0f) {
                                    val col = (tap.x / step).toInt()
                                    val row = (tap.y / step).toInt()
                                    selected = grid.columns.getOrNull(col)?.cells?.getOrNull(row)
                                }
                            }
                        }) {
                        val edge = cellSize.toPx()
                        val step = stride.toPx()
                        grid.columns.forEachIndexed { col, column ->
                            column.cells.forEachIndexed { row, value ->
                                val color = when {
                                    value.future -> futureSample
                                    value.amount == null -> noSample
                                    value.intensity == 0 -> noActivity
                                    else -> intensityColors[value.intensity - 1]
                                }
                                val topLeft = Offset(col * step, row * step)
                                val shape = CornerRadius((edge * 0.22f).coerceAtLeast(1f))
                                drawRoundRect(color, topLeft, Size(edge, edge), shape)
                                if (value.isCurrent) drawRoundRect(
                                    highlight, topLeft, Size(edge, edge), shape,
                                    style = Stroke(width = 1.2.dp.toPx()),
                                )
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 3.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_activity_less), style = axisStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    (listOf(noActivity) + intensityColors).forEach { swatch ->
                        Box(Modifier.padding(end = 2.dp).size(9.dp)
                            .background(swatch, RoundedCornerShape(2.dp)))
                    }
                    Text(stringResource(R.string.home_activity_more), style = axisStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
