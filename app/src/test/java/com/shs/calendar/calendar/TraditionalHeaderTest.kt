package com.shs.calendar.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * M4 tests for the traditional Bengali printed-calendar header.
 *
 * Dates are chosen to exercise the month-wrap logic: 2026-09-25 sits in
 * Boishakh, and both its Bengali and Hijri month transitions are checked.
 */
class TraditionalHeaderTest {

    private val date = LocalDate.of(2026, 9, 25)

    @Test
    fun `bengali numerals convert arabic digits`() {
        assertEquals("২০২৬", TraditionalHeader.bn(2026))
        assertEquals("১", TraditionalHeader.bn(1))
        assertEquals("১৪৪৮", TraditionalHeader.bn(1448))
    }

    @Test
    fun `today line is today bangla gregorian`() {
        val line = TraditionalHeader.todayLine(date)
        assertTrue(line, line.startsWith("আজ "))
        assertTrue(line, line.endsWith(" ইংরেজি"))
        assertTrue(line, line.contains("২৫"))
        assertTrue(line, line.contains("২০২৬"))
    }

    @Test
    fun `weekday line pairs short and full bengali name`() {
        val line = TraditionalHeader.weekdayLine(date)
        val parts = line.split(" - ")
        assertEquals(2, parts.size)
        assertTrue(line, parts[0] in TraditionalHeader.weekdayHeaders())
        assertTrue(line, parts[1] in BengaliEngine.weekdayNames)
    }

    @Test
    fun `gregorian line is month and year in bangla digits`() {
        val line = TraditionalHeader.gregorianLine(date)
        assertEquals(2, line.trim().split(" ").size)
        assertTrue(line, line.contains("\u09b8\u09c7\u09aa\u09cd\u099f\u09c7\u09ae\u09cd\u09ac\u09b0"))
        assertTrue(line, line.endsWith(" " + TraditionalHeader.bn(2026)))
    }

    @Test
    fun `header is exactly five lines`() {
        assertEquals(5, TraditionalHeader.lines(date).size)
    }

    @Test
    fun `bengali line shows month transition and bangla year`() {
        val line = TraditionalHeader.bengaliLine(date)
        assertTrue(line, line.contains(" \u2014 "))
        assertTrue(line, line.endsWith(" \u09ac\u09be\u0982\u09b2\u09be"))
        assertTrue(line, line.contains(BengaliNumerals.number(
            BengaliEngine.fromGregorian(date).year)))
    }

    @Test
    fun `hijri line shows month transition and bangla year`() {
        val line = TraditionalHeader.hijriLine(date)
        assertTrue(line, line.contains(" \u2014 "))
        assertTrue(line, line.endsWith(" \u09b9\u09bf\u099c\u09b0\u09c0"))
    }

    @Test
    fun `weekday headers are the seven traditional names`() {
        val headers = TraditionalHeader.weekdayHeaders()
        assertEquals(7, headers.size)
        assertEquals(headers, headers.distinct())
        assertEquals(headers[0], TraditionalHeader.weekdayShort(LocalDate.of(2026, 9, 20)))
    }

    @Test
    fun `all header lines are non blank`() {
        TraditionalHeader.lines(date).forEachIndexed { i, l ->
            assertTrue("line $i blank", l.isNotBlank())
        }
    }
}
