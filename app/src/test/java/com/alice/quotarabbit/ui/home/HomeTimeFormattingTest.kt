package com.alice.quotarabbit.ui.home

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTimeFormattingTest {
    @Test
    fun `same instant crosses the date boundary only in the selected time zone`() {
        val instant = Instant.parse("2026-01-01T15:30:00Z")
        assertEquals("26.01.01. 15:30", normalized(formatHomeInstant(instant, Locale.US, ZoneId.of("UTC"))))
        assertEquals("26.01.02. 00:30", normalized(formatHomeInstant(instant, Locale.US, ZoneId.of("Asia/Seoul"))))
        assertEquals("26.01.01. 07:30", normalized(formatHomeInstant(instant, Locale.US, ZoneId.of("America/Los_Angeles"))))
    }

    @Test
    fun `absolute date and 24-hour time use the same fixed format across locales`() {
        val instant = Instant.parse("2026-01-02T03:04:00Z")
        for (locale in listOf(Locale.UK, Locale.US, Locale.KOREA, Locale.SIMPLIFIED_CHINESE)) {
            assertEquals("26.01.02. 03:04", normalized(formatHomeInstant(instant, locale, ZoneId.of("UTC"))))
        }
        assertEquals("26.10.29. 12:29", formatHomeInstant(
            Instant.parse("2026-10-29T03:29:00Z"), Locale.KOREA, ZoneId.of("Asia/Seoul")))
    }

    @Test
    fun `daylight saving overlap omits offsets while retaining device-local clock time`() {
        val zone = ZoneId.of("America/New_York")
        assertEquals("26.11.01. 01:30", normalized(formatHomeInstant(
            Instant.parse("2026-11-01T05:30:00Z"), Locale.US, zone)))
        assertEquals("26.11.01. 01:30", normalized(formatHomeInstant(
            Instant.parse("2026-11-01T06:30:00Z"), Locale.US, zone)))
    }

    @Test
    fun `manual renewal date changes display only while retaining ISO input`() {
        assertEquals("26.11.16.", formatManualRenewalDate("2026-11-16"))
        assertEquals("26.10.29.", formatDisplayDateOnly(java.time.LocalDate.of(2026, 10, 29)))
        assertEquals("invalid", formatManualRenewalDate("invalid"))
    }

    private fun normalized(value: String) = value.replace('\u202f', ' ').replace('\u00a0', ' ')
}
