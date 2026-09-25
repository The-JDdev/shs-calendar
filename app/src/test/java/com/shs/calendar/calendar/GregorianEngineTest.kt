package com.shs.calendar.calendar

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gregorian leap rules, grid bounds and navigation smooth through year 3000. */
class GregorianEngineTest {

    @Test
    fun leap_year_rules() {
        assertTrue(GregorianEngine.isLeap(2024))
        assertTrue(GregorianEngine.isLeap(2000))  // %400
        assertFalse(GregorianEngine.isLeap(1900)) // %100
        assertFalse(GregorianEngine.isLeap(2023))
        assertTrue(GregorianEngine.isLeap(3000 + 4)) // 3004
    }

    @Test
    fun month_grid_covers_whole_month_including_year_3000() {
        for (ym in listOf(YearMonth.of(2026, 2), YearMonth.of(3000, 2), YearMonth.of(3000, 12))) {
            val cells = GregorianEngine.monthGrid(ym)
            assertEquals("grid aligned for $ym", 0, cells.size % 7)
            assertTrue("grid not empty for $ym", cells.size in 35..42)
            val inMonth = cells.filter { it.inMonth }
            assertEquals("days of $ym", ym.lengthOfMonth(), inMonth.size)
            assertEquals("first in-month day", ym.atDay(1), inMonth.first().date)
            assertEquals("last in-month day", ym.atDay(ym.lengthOfMonth()), inMonth.last().date)
        }
    }

    @Test
    fun navigation_is_contiguous_through_3000() {
        var ym = YearMonth.of(2999, 11)
        repeat(5) {
            val next = GregorianEngine.nextMonth(ym)
            assertEquals(ym.plusMonths(1), next)
            assertEquals(ym, GregorianEngine.previousMonth(next))
            ym = next
        }
        assertEquals(YearMonth.of(3000, 4), ym)
    }

    @Test
    fun weekday_and_month_names_are_stable() {
        // Known: 2026-09-25 is a Friday (product-owner anchor).
        assertEquals("Friday", GregorianEngine.weekdayName(LocalDate.of(2026, 9, 25)))
        assertEquals("September", GregorianEngine.monthName(LocalDate.of(2026, 9, 25)))
        assertEquals(LocalDate.of(2026, 9, 25), GregorianEngine.parseIso("2026-09-25"))
    }
}
