package com.shs.calendar.data

import com.shs.calendar.data.recurrence.RRule
import com.shs.calendar.data.recurrence.RecurrenceExpander
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RFC 5545 subset coverage for the recurrence engine: parsing, COUNT/UNTIL
 * bounds, interval stepping and window filtering. Pure JVM, deterministic.
 */
class RecurrenceTest {

    private val zone: ZoneId = ZoneId.of("Asia/Dhaka")
    private val start: Long = LocalDate.of(2026, 1, 5)
        .atStartOfDay(zone).toInstant().toEpochMilli()
    private val oneDayMs = 86_400_000L

    @Test
    fun parse_returns_null_for_blank_or_freqless_input() {
        assertNull(RRule.parse(null))
        assertNull(RRule.parse(""))
        assertNull(RRule.parse("INTERVAL=2;COUNT=3")) // no FREQ
        assertNotNull(RRule.parse("FREQ=DAILY;COUNT=3"))
    }

    @Test
    fun parse_reads_interval_count_until_and_byday() {
        val r = RRule.parse("FREQ=WEEKLY;INTERVAL=2;COUNT=5;BYDAY=MO,WE")!!
        assertEquals(RRule.Freq.WEEKLY, r.freq)
        assertEquals(2, r.interval)
        assertEquals(5, r.count)
        assertEquals(2, r.byDay.size)
        // INTERVAL below 1 is coerced to 1 (RFC requires a positive interval).
        assertEquals(1, RRule.parse("FREQ=DAILY;INTERVAL=0")!!.interval)
        // COUNT <= 0 is ignored rather than producing zero occurrences.
        assertNull(RRule.parse("FREQ=DAILY;COUNT=0")!!.count)
    }

    @Test
    fun daily_count_bounds_occurrences() {
        val list = RecurrenceExpander.expand(
            RRule.parse("FREQ=DAILY;COUNT=7"),
            start, oneDayMs, zone,
            fromUtcMillis = 0, toUtcMillis = Long.MAX_VALUE
        )
        assertEquals(7, list.size)
        // Strictly ascending daily steps.
        assertEquals(start, list.first())
        for (i in 1 until list.size) {
            assertEquals(oneDayMs, list[i] - list[i - 1])
        }
    }

    @Test
    fun daily_interval_steps_every_other_day() {
        val list = RecurrenceExpander.expand(
            RRule.parse("FREQ=DAILY;INTERVAL=3;COUNT=4"),
            start, oneDayMs, zone,
            fromUtcMillis = 0, toUtcMillis = Long.MAX_VALUE
        )
        assertEquals(4, list.size)
        for (i in 1 until list.size) {
            assertEquals(3 * oneDayMs, list[i] - list[i - 1])
        }
    }

    @Test
    fun weekly_byday_matches_expected_weekdays() {
        val list = RecurrenceExpander.expand(
            RRule.parse("FREQ=WEEKLY;BYDAY=SU;COUNT=4"),
            start, oneDayMs, zone,
            fromUtcMillis = 0, toUtcMillis = Long.MAX_VALUE
        )
        assertEquals(4, list.size)
        val days = list.map { Instant_toDate(it, zone).dayOfWeek }
        assertTrue(days.all { it.name == "SUNDAY" })
    }

    @Test
    fun window_filters_occurrences_outside_range() {
        val full = RecurrenceExpander.expand(
            RRule.parse("FREQ=DAILY;COUNT=10"),
            start, oneDayMs, zone,
            fromUtcMillis = 0, toUtcMillis = Long.MAX_VALUE
        )
        // Restrict the window to days 4..6 of the series.
        val from = full[3] - oneDayMs / 2
        val to = full[6] + oneDayMs / 2
        val windowed = RecurrenceExpander.expand(
            RRule.parse("FREQ=DAILY;COUNT=10"),
            start, oneDayMs, zone,
            fromUtcMillis = from, toUtcMillis = to
        )
        assertEquals(4, windowed.size)
        assertTrue(windowed.all { it in full })
    }

    @Test
    fun until_stops_expansion_at_the_bound() {
        val until = start + 4 * oneDayMs + oneDayMs / 2 // through day 4 only
        val rrule = "FREQ=DAILY;UNTIL=$until"
        val list = RecurrenceExpander.expand(
            RRule.parse(rrule), start, oneDayMs, zone,
            fromUtcMillis = 0, toUtcMillis = Long.MAX_VALUE
        )
        assertEquals(5, list.size) // days 0..4
        assertTrue(list.last() <= until)
    }

    @Test
    fun single_event_without_rrule_is_returned_once_inside_window() {
        val list = RecurrenceExpander.expand(
            rruleText = null,
            startUtcMillis = start,
            endUtcMillis = start + oneDayMs,
            zoneId = "Asia/Dhaka",
            fromUtcMillis = 0,
            toUtcMillis = Long.MAX_VALUE
        )
        assertEquals(listOf(start), list)

        val outside = RecurrenceExpander.expand(
            rruleText = null,
            startUtcMillis = start,
            endUtcMillis = start + oneDayMs,
            zoneId = "Asia/Dhaka",
            fromUtcMillis = start + 10 * oneDayMs,
            toUtcMillis = Long.MAX_VALUE
        )
        assertTrue(outside.isEmpty())
    }

    @Test
    fun expansion_is_capped_and_sorted() {
        val list = RecurrenceExpander.expand(
            RRule.parse("FREQ=DAILY"), // unbounded: must still terminate
            start, oneDayMs, zone,
            fromUtcMillis = 0, toUtcMillis = Long.MAX_VALUE
        )
        assertTrue(list.isNotEmpty())
        assertEquals(list, list.sorted())
        assertEquals(list.distinct(), list) // no duplicates
        assertTrue(list.size <= 5_000)
    }

    private fun Instant_toDate(millis: Long, zone: ZoneId): LocalDate =
        java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
