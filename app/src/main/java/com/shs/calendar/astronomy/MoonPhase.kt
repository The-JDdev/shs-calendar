package com.shs.calendar.astronomy

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor

/**
 * Moon phase via the synodic (lunation) method — pure function of date.
 * Reference new moon: 2000-01-06 18:14 UTC (JD 2451550.1), synodic month
 * 29.530588853 days. Deterministic; no lookup tables, no network.
 */
object MoonPhase {

    /** Phase descriptor for a date. */
    data class Phase(
        /** Normalized lunation age 0..1. */
        val fraction: Double,
        /** Elapsed days since reference new moon. */
        val ageDays: Double,
        /** Illuminated fraction 0..1. */
        val illumination: Double,
        /** Display name. */
        val name: PhaseName,
        /** Waxing (after new, before full). */
        val waxing: Boolean
    )

    enum class PhaseName(val label: String, val labelBn: String) {
        NEW("New Moon", "অমাবস্যা"),
        WAXING_CRESCENT("Waxing Crescent", "শুক্ল পক্ষ"),
        FIRST_QUARTER("First Quarter", "প্রথম কুয়াশা"),
        WAXING_GIBBOUS("Waxing Gibbous", "জ্যোৎস্না"),
        FULL("Full Moon", "পূর্ণিমা"),
        WANING_GIBBOUS("Waning Gibbous", "ক্ষয় পূর্ণিমা"),
        LAST_QUARTER("Last Quarter", "শেষ কুয়াশা"),
        WANING_CRESCENT("Waning Crescent", "অমাবস্যার আগে")
    }

    private const val SYNODIC = 29.530588853
    private const val REFERENCE_NEW_MOON_JD = 2451550.1 // 2000-01-06 18:14 UTC

    /** Julian Day at 0h UT for [date]. */
    private fun julianDay(date: LocalDate): Double {
        var y = date.year
        var m = date.monthValue
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + date.dayOfMonth + b - 1524.5
    }

    /** Phase for [date] at 0h UT (stable point within the day). */
    fun phase(date: LocalDate): Phase {
        val jd = julianDay(date)
        val age = ((jd - REFERENCE_NEW_MOON_JD) % SYNODIC + SYNODIC) % SYNODIC
        val fraction = age / SYNODIC
        val illum = (1 - cos(2 * PI * fraction)) / 2
        val waxing = fraction < 0.5
        val name = when {
            fraction < 0.0208 || fraction >= 0.9792 -> PhaseName.NEW
            fraction < 0.2292 -> PhaseName.WAXING_CRESCENT
            fraction < 0.2708 -> PhaseName.FIRST_QUARTER
            fraction < 0.4792 -> PhaseName.WAXING_GIBBOUS
            fraction < 0.5208 -> PhaseName.FULL
            fraction < 0.7292 -> PhaseName.WANING_GIBBOUS
            fraction < 0.7708 -> PhaseName.LAST_QUARTER
            else -> PhaseName.WANING_CRESCENT
        }
        return Phase(fraction, age, illum, name, waxing)
    }

    /** Days until the next full moon from [date] (0..29.53). */
    fun daysUntilFullMoon(date: LocalDate): Double {
        val p = phase(date)
        val toFull = (0.5 - p.fraction + 1.0) % 1.0
        return toFull * SYNODIC
    }

    /** Convenience: phase at [date] evaluated in [zone] (date-boundary safety). */
    fun phase(date: LocalDate, zone: ZoneId): Phase = phase(date.atStartOfDay(zone).toLocalDate())

    /** Age in whole days (0..29), floored for UI. */
    fun ageDaysRounded(date: LocalDate): Int = floor(phase(date).ageDays).toInt()

    /** Elapsed days between two dates — used for countdowns. */
    fun daysBetween(a: LocalDate, b: LocalDate): Long = abs(ChronoUnit.DAYS.between(a, b))
}
