package com.shs.calendar.calendar

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hijrah adjustment clamping, validity and round-trips (SPEC: adjustments ±3). */
class HijriEngineTest {

    @Test
    fun adjustment_is_clamped_to_plus_minus_3() {
        assertEquals(3, HijriEngine.clampAdjustment(10))
        assertEquals(-3, HijriEngine.clampAdjustment(-10))
        assertEquals(0, HijriEngine.clampAdjustment(0))
        assertEquals(2, HijriEngine.clampAdjustment(2))
    }

    @Test
    fun adjustment_shifts_the_underlying_gregorian_day() {
        val base = LocalDate.of(2026, 9, 25)
        // Round-trip through the shifted conversion: the reverse mapping must
        // land exactly `adjustment` days away from the unadjusted reverse.
        val zero = HijriEngine.fromGregorian(base, 0)
        val plus = HijriEngine.fromGregorian(base, 2)
        val minus = HijriEngine.fromGregorian(base, -2)
        // Same Hijri day-numbering family: all three are valid conversions of
        // nearby Gregorian days, so they must not all be identical.
        assertEquals(HijriEngine.fromGregorian(base.plusDays(2), 0).let { zero.adjustment; it }.copy(adjustment = 2), plus)
        assertEquals(HijriEngine.fromGregorian(base.minusDays(2), 0).copy(adjustment = -2), minus)
        assertTrue(HijriEngine.isValid(plus))
        assertTrue(HijriEngine.isValid(minus))
    }

    @Test
    fun roundTrip_within_adjusted_window() {
        var date = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2027, 1, 1)
        while (date.isBefore(end)) {
            val h = HijriEngine.fromGregorian(date, 0)
            assertTrue("valid hijri for $date", HijriEngine.isValid(h))
            assertEquals(date, HijriEngine.toGregorian(h))
            date = date.plusDays(1)
        }
    }

    @Test
    fun deterministic_within_allowed_adjustment_range() {
        val date = LocalDate.of(2026, 9, 25)
        val results = (HijriEngine.MIN_ADJUSTMENT..HijriEngine.MAX_ADJUSTMENT).map {
            HijriEngine.fromGregorian(date, it)
        }
        assertEquals(7, results.size)
        assertEquals(results, results.distinct())
    }
}
