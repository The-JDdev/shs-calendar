package com.shs.calendar.calendar

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * SPEC-mandated engine tests: product-owner anchors, leap-year sensitivity,
 * month/year boundaries and round-trips in both directions. Pure JVM, no
 * hardcoded conversion tables are used by the engine itself.
 */
class BengaliEngineTest {

    @Test
    fun anchor_2026_09_25_is_10_Ashshin_1433() {
        val b = BengaliEngine.fromGregorian(LocalDate.of(2026, 9, 25))
        assertEquals(1433, b.year)
        assertEquals(6, b.month)          // Ashwin
        assertEquals(10, b.day)
        assertEquals("Ashshin", b.monthNameEn)
        assertEquals("10 Ashshin 1433", b.toString())
    }

    @Test
    fun anchor_pohela_boishakh_2025_is_1_Boishakh_1432() {
        val b = BengaliEngine.fromGregorian(LocalDate.of(2025, 4, 14))
        assertEquals(1432, b.year)
        assertEquals(1, b.month)
        assertEquals(1, b.day)
    }

    @Test
    fun dayBefore_pohela_is_Choitro_of_previous_year() {
        val b = BengaliEngine.fromGregorian(LocalDate.of(2025, 4, 13))
        assertEquals(1431, b.year)
        assertEquals(12, b.month)
    }

    @Test
    fun falgun_is_31_days_iff_gregorian_year_is_leap() {
        // Falgun of Bengali year Y spans Feb of Gregorian Y+594.
        assertEquals(31, BengaliEngine.monthLength(1430, 11)) // Feb 2024 (leap)
        assertEquals(30, BengaliEngine.monthLength(1431, 11)) // Feb 2025
        assertEquals(30, BengaliEngine.monthLength(1432, 11)) // Feb 2026
        assertEquals(366, BengaliEngine.yearLength(1430))
        assertEquals(365, BengaliEngine.yearLength(1432))
    }

    @Test
    fun month_lengths_match_spec() {
        for (m in 1..5) assertEquals(31, BengaliEngine.monthLength(1433, m))
        for (m in 6..12) if (m != 11) assertEquals(30, BengaliEngine.monthLength(1433, m))
    }

    @Test
    fun roundTrip_gregorian_to_bengali_and_back_full_year() {
        var date = BengalEngineStart()
        val end = date.plusYears(1)
        while (date.isBefore(end)) {
            assertEquals(date, BengaliEngine.toGregorian(BengaliEngine.fromGregorian(date)))
            date = date.plusDays(1)
        }
    }

    @Test
    fun roundTrip_year_3000_and_back() {
        val date = LocalDate.of(3000, 2, 28)
        assertEquals(date, BengaliEngine.toGregorian(BengaliEngine.fromGregorian(date)))
        val b = BengaliEngine.fromGregorian(LocalDate.of(3000, 8, 1))
        assertEquals(3000 - 593, b.year) // after Pohela Boishakh 3000
    }

    @Test
    fun invalid_dates_rejected_without_crash() {
        assertFalse(BengaliEngine.isValid(BengaliEngine.BengaliDate(1433, 13, 1)))
        assertFalse(BengaliEngine.isValid(BengaliEngine.BengaliDate(1433, 6, 31))) // Ashwin max 30
        try {
            BengaliEngine.toGregorian(BengaliEngine.BengaliDate(1433, 6, 31))
            fail("Out-of-range day must throw")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    private fun BengalEngineStart(): LocalDate =
        BengaliEngine.yearStart(BengaliEngine.bengaliYearOf(LocalDate.now()))
}
