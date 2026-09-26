package com.shs.calendar.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * M4 tests for the world clock model.
 *
 * A fixed Instant is used throughout so the assertions are deterministic
 * and independent of when the suite runs.
 */
class WorldClockTest {

    /** 2026-09-25T18:30:00Z - fixed reference point. */
    private val now: Instant = Instant.parse("2026-09-25T18:30:00Z")

    @Test
    fun `entry resolves real zone and reports offset`() {
        val entry = WorldClock.entryAt("Asia/Dhaka", now)
        assertEquals("Asia/Dhaka", entry.zoneId)
        assertEquals("UTC+06:00", entry.offsetLabel)
    }

    @Test
    fun `zero offset is rendered numerically, never as UTCZ`() {
        // ZoneOffset.getId() returns "Z" here, which must not leak into the label.
        val entry = WorldClock.entryAt("UTC", now)
        assertEquals("UTC+00:00", entry.offsetLabel)
        assertTrue(!entry.offsetLabel.contains("Z"))
    }

    @Test
    fun `negative offset is rendered with a minus sign`() {
        val entry = WorldClock.entryAt("America/New_York", now)
        assertEquals("UTC-04:00", entry.offsetLabel)
    }

    @Test
    fun `entry converts instant to zone local time`() {
        val entry = WorldClock.entryAt("Asia/Dhaka", now)
        // 18:30Z is 00:30 the next day in UTC+06:00
        assertEquals(0, entry.time.hour)
        assertEquals(30, entry.time.minute)
    }

    @Test
    fun `invalid zone falls back to device zone instead of throwing`() {
        val entry = WorldClock.entryAt("Not/AZone", now)
        assertEquals(ZoneId.systemDefault().id, entry.zoneId)
    }

    @Test
    fun `validity check agrees with zone helper`() {
        assertTrue(WorldClock.isValid("Asia/Dhaka"))
        assertTrue(!WorldClock.isValid("Not/AZone"))
    }

    @Test
    fun `time formatting honours 12 and 24 hour settings`() {
        val entry = WorldClock.entryAt("UTC", now)
        assertEquals("18:30", WorldClock.formatTime(entry.time, true))
        assertTrue(WorldClock.formatTime(entry.time, false).endsWith("PM"))
    }

    @Test
    fun `date line is english regardless of device locale`() {
        val entry = WorldClock.entryAt("UTC", now)
        val date = WorldClock.formatDate(entry.time)
        assertTrue(date, date.contains("2026"))
        assertTrue(date, date.contains("Sep"))
    }
}
