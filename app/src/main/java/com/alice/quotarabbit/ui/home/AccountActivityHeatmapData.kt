package com.alice.quotarabbit.ui.home

import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

/** Quota snapshot deltas, not Git contributions or actual token totals. */
enum class ActivityPeriod { Hourly, Daily }

data class ActivityCell(
    val time: LocalDateTime,
    val amount: Double?,
    val intensity: Int,
    val future: Boolean = false,
    val isCurrent: Boolean = false,
)
data class ActivityColumn(val label: String, val cells: List<ActivityCell>)
data class ActivityGrid(val columns: List<ActivityColumn>, val observed: Int)

/** Keep the newest hour labelled, then show every third hour working right to left. */
internal fun hourlyHourTickIndices(columns: Int): List<Int> =
    (0 until columns).filter { (columns - 1 - it) % 3 == 0 }

internal fun hourlyMinuteLabel(row: Int): String =
    (row * 10).toString().padStart(2, '0')

/** Fit readable activity squares into the width left after the labels, without scrolling. */
internal fun activityColumnCount(availableWidthDp: Float): Int =
    ((availableWidthDp.coerceAtLeast(0f) + 2.5f) / 16.5f).toInt().coerceIn(1, 16)

object AccountActivityHeatmapData {
    const val HOURLY_ROWS = 6
    const val DAILY_ROWS = 7
    private const val MAX_MOBILE_COLUMNS = 16
    private const val TEN_MINUTES = 10L

    fun build(
        points: List<HomeTrendPointUi>,
        period: ActivityPeriod,
        now: ZonedDateTime,
        columnCount: Int = 12,
    ): ActivityGrid {
        val columns = columnCount.coerceIn(1, MAX_MOBILE_COLUMNS)
        val zone = now.zone
        val valid = points.filter {
            it.usageValue.isFinite() && it.usageValue >= 0.0 &&
                !it.capturedAt.isAfter(now.toInstant())
        }
        // Bucket samples by their local hour and 10-minute interval. The instant key
        // also separates repeated local clock hours during daylight-saving transitions.
        val byTenMinutes = valid.groupBy({ point ->
            val at = point.capturedAt.atZone(zone)
            at.truncatedTo(ChronoUnit.HOURS)
                .plusMinutes(at.minute / TEN_MINUTES * TEN_MINUTES)
                .toInstant()
        }, { it.usageValue }).mapValues { (_, values) -> values.sum() }
        val byDay = valid.groupBy({ it.capturedAt.atZone(zone).toLocalDate() }, { it.usageValue })
            .mapValues { (_, values) -> values.sum() }
        val currentHour = now.truncatedTo(ChronoUnit.HOURS)
        val today = now.toLocalDate()
        // Daily columns are complete Monday-to-Sunday calendar weeks, not rolling seven-day blocks.
        val currentWeekMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val raw = when (period) {
            ActivityPeriod.Hourly -> (0 until columns).map { col ->
                val hour = currentHour.minusHours((columns - 1 - col).toLong())
                val cells = (0 until HOURLY_ROWS).map { row ->
                    val start = hour.plusMinutes(row * TEN_MINUTES)
                    val future = start.toInstant().isAfter(now.toInstant())
                    val isCurrent = !future && now.toInstant().isBefore(
                        start.plusMinutes(TEN_MINUTES).toInstant())
                    ActivityCell(start.toLocalDateTime(),
                        if (future) null else byTenMinutes[start.toInstant()], 0,
                        future = future, isCurrent = isCurrent)
                }
                ActivityColumn(hour.format(DateTimeFormatter.ofPattern("HH", Locale.ROOT)), cells)
            }
            ActivityPeriod.Daily -> (0 until columns).map { col ->
                val monday = currentWeekMonday.minusWeeks((columns - 1 - col).toLong())
                val cells = (0 until DAILY_ROWS).map { row ->
                    val day = monday.plusDays(row.toLong())
                    val isFuture = day.isAfter(today)
                    ActivityCell(day.atStartOfDay(), if (isFuture) null else byDay[day], 0,
                        future = isFuture, isCurrent = day == today)
                }
                val firstDay = cells.first().time.toLocalDate()
                val priorDay = firstDay.minusDays(7)
                val label = if (col == 0 || firstDay.month != priorDay.month)
                    firstDay.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) else ""
                ActivityColumn(label, cells)
            }
        }
        val max = raw.flatMap { it.cells }.mapNotNull { it.amount }
            .maxOrNull()?.takeIf { it > 0 } ?: 0.0
        val normalized = raw.map { column ->
            column.copy(cells = column.cells.map { cell ->
                cell.copy(intensity = if (cell.amount == null || cell.amount <= 0 || max == 0.0) 0
                    else ceil(cell.amount / max * 4).toInt().coerceIn(1, 4))
            })
        }
        return ActivityGrid(normalized, normalized.sumOf { column ->
            column.cells.count { it.amount != null }
        })
    }
}
