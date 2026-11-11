package com.alice.quotarabbit.ui.home

import java.time.Instant
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountActivityHeatmapDataTest {
    private val now = ZonedDateTime.parse("2026-10-11T16:03:00Z")
    private fun at(time: String, value: Double) =
        HomeTrendPointUi(Instant.parse(time), value)

    @Test fun `hourly groups samples into chronological ten minute rows per whole hour`() {
        val grid = AccountActivityHeatmapData.build(listOf(
            at("2026-10-11T15:31:00Z", 3.0),
            at("2026-10-11T15:39:00Z", 4.0),
            at("2026-10-11T15:57:00Z", 2.0),
            at("2026-10-11T16:01:00Z", 1.0),
        ), ActivityPeriod.Hourly, now, columnCount = 12)
        assertEquals(12, grid.columns.size)
        assertTrue(grid.columns.all { it.cells.size == 6 })
        assertEquals("2026-10-11T05:00", grid.columns.first().cells.first().time.toString())
        assertEquals("2026-10-11T15:30", grid.columns[10].cells[3].time.toString())
        assertEquals("2026-10-11T15:50", grid.columns[10].cells[5].time.toString())
        assertEquals("2026-10-11T16:00", grid.columns.last().cells.first().time.toString())
        assertTrue(grid.columns.last().cells.first().isCurrent)
        assertTrue(grid.columns.last().cells.drop(1).all { it.future && it.amount == null })
        assertEquals(7.0, grid.columns[10].cells[3].amount ?: -1.0, 0.001)
        assertEquals(2.0, grid.columns[10].cells[5].amount ?: -1.0, 0.001)
        assertEquals(1.0, grid.columns.last().cells[0].amount ?: -1.0, 0.001)
        assertEquals(3, grid.observed)
    }

    @Test fun `hourly shows 24 hour labels and marks the containing bucket at 09 23`() {
        val seoul = ZonedDateTime.parse("2026-10-28T09:23:00+09:00[Asia/Seoul]")
        val grid = AccountActivityHeatmapData.build(emptyList(), ActivityPeriod.Hourly, seoul, 16)
        assertEquals("2026-10-27T18:00", grid.columns.first().cells[0].time.toString())
        assertEquals("2026-10-28T09:20", grid.columns.last().cells[2].time.toString())
        assertTrue(grid.columns.last().cells[2].isCurrent)
        assertTrue(grid.columns.last().cells.drop(3).all { it.future && it.amount == null })
        assertFalse(grid.columns.last().cells[1].future)
        assertEquals(listOf(0, 3, 6, 9, 12, 15), hourlyHourTickIndices(16))
        assertEquals(listOf("18", "21", "00", "03", "06", "09"),
            hourlyHourTickIndices(16).map { grid.columns[it].label })
        assertEquals(listOf("00", "10", "20", "30", "40", "50"),
            (0 until AccountActivityHeatmapData.HOURLY_ROWS).map(::hourlyMinuteLabel))
    }

    @Test fun `daily aligns every week Monday through Sunday and today follows its weekday`() {
        val tuesday = ZonedDateTime.parse("2026-10-20T16:03:00Z")
        val grid = AccountActivityHeatmapData.build(listOf(
            at("2026-10-19T15:31:00Z", 3.0),
            at("2026-10-20T15:57:00Z", 2.0),
            at("2026-10-21T15:57:00Z", 4.0), // Ignore future observations.
        ), ActivityPeriod.Daily, tuesday, columnCount = 12)
        assertEquals(12, grid.columns.size)
        assertTrue(grid.columns.all { it.cells.size == 7 })
        assertTrue(grid.columns.all { it.cells.first().time.dayOfWeek == java.time.DayOfWeek.MONDAY })
        assertEquals("2026-08-03T00:00", grid.columns.first().cells.first().time.toString())
        assertEquals("2026-10-18T00:00", grid.columns[10].cells.last().time.toString())
        assertEquals("2026-10-19T00:00", grid.columns.last().cells[0].time.toString())
        assertEquals("2026-10-20T00:00", grid.columns.last().cells[1].time.toString())
        assertEquals("2026-10-25T00:00", grid.columns.last().cells.last().time.toString())
        assertTrue(grid.columns.last().cells[1].isCurrent)
        assertFalse(grid.columns.last().cells.last().isCurrent)
        assertTrue(grid.columns.last().cells.drop(2).all { it.future && it.amount == null })
        assertEquals(3.0, grid.columns.last().cells[0].amount ?: -1.0, 0.001)
        assertEquals(2.0, grid.columns.last().cells[1].amount ?: -1.0, 0.001)
        assertEquals(2, grid.observed)
    }

    @Test fun `empty and invalid points leave unknown cells empty not zero or future`() {
        val grid = AccountActivityHeatmapData.build(listOf(
            at("2026-10-11T15:59:00Z", 0.0),
            at("2026-10-11T16:04:00Z", 8.0),
            at("2026-10-11T15:58:00Z", Double.NaN),
            at("2026-10-11T15:58:00Z", -4.0),
        ), ActivityPeriod.Hourly, now)
        assertEquals(0.0, grid.columns[grid.columns.lastIndex - 1].cells[5].amount ?: -1.0, 0.001)
        assertEquals(0, grid.columns[grid.columns.lastIndex - 1].cells[5].intensity)
        assertNull(grid.columns.last().cells[0].amount)
        assertEquals(1, grid.observed)
        assertTrue(grid.columns.last().cells[0].isCurrent)
        assertTrue(grid.columns.last().cells[1].future)
        assertEquals(0, AccountActivityHeatmapData.build(emptyList(), ActivityPeriod.Daily, now).observed)
    }

    @Test fun `mobile column count keeps cells legible and avoids full-year or day range`() {
        assertEquals(12, activityColumnCount(200f))
        assertEquals(16, activityColumnCount(300f))
        assertEquals(6, activityColumnCount(100f))
        assertEquals(16, activityColumnCount(500f))
        assertEquals(1, activityColumnCount(0f))

        for (period in ActivityPeriod.entries) {
            val grid = AccountActivityHeatmapData.build(emptyList(), period, now, columnCount = 12)
            assertEquals(12, grid.columns.size)
            val span = if (period == ActivityPeriod.Hourly) 6 * 12 else 7 * 12
            assertEquals(span, grid.columns.sumOf { it.cells.size })
            val current = grid.columns.last().cells.single { it.isCurrent }
            assertEquals(
                if (period == ActivityPeriod.Hourly) "2026-10-11T16:00" else "2026-10-11T00:00",
                current.time.toString(),
            )
            if (period == ActivityPeriod.Hourly) {
                assertEquals(listOf(2, 5, 8, 11), hourlyHourTickIndices(12))
                assertTrue(grid.columns.last().cells.drop(1).all { it.future })
            } else {
                assertTrue(grid.columns.all { it.cells.first().time.dayOfWeek == java.time.DayOfWeek.MONDAY })
            }
        }
    }

    @Test fun `every cell is strictly earlier than the cell beneath or in the next column`() {
        for (period in ActivityPeriod.entries) {
            val grid = AccountActivityHeatmapData.build(emptyList(), period, now)
            val times = grid.columns.flatMap { column -> column.cells.map { it.time } }
            assertEquals(times.size, times.distinct().size)
            assertTrue(times.zipWithNext().all { (before, after) -> before.isBefore(after) })
            assertFalse(grid.columns.dropLast(1).flatMap { it.cells }.any { it.isCurrent })
        }
    }
}
