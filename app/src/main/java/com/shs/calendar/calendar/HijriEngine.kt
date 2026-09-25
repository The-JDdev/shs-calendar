package com.shs.calendar.calendar

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.chrono.HijrahEra
import java.time.chrono.ChronoLocalDate
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit

/**
 * Hijri calendar built on java.time.chrono.HijrahDate (Umm al-Qura-style
 * arithmetic calendar) plus a configurable manual adjustment (−3..+3 days,
 * moon-sighting offset) as required by SPEC.
 *
 * Deterministic and pure: adjustment is an explicit input, never device state.
 */
object HijriEngine {

    const val MIN_ADJUSTMENT = -3
    const val MAX_ADJUSTMENT = 3

    val monthNames = listOf(
        "Muharram", "Safar", "Rabiʿ al-Awwal", "Rabiʿ al-Thani",
        "Jumada al-Ula", "Jumada al-Thani", "Rajab", "Shaʿban",
        "Ramadan", "Shawwal", "Dhu al-Qiʿdah", "Dhu al-Hijjah"
    )

    val monthNamesShort = listOf(
        "Muharram", "Safar", "Rabi I", "Rabi II",
        "Jumada I", "Jumada II", "Rajab", "Shaʿban",
        "Ramadan", "Shawwal", "Dhu al-Q", "Dhu al-Hij"
    )

    /** Bengali transliterations for the Bangla UI. */
    val monthNamesBn = listOf(
        "মুহররম", "সফর", "রবিউল আউয়াল", "রবিউস সানি",
        "জমাদিউল আউয়াল", "জমাদিউস সানি", "রজব", "শাবান",
        "রমজান", "শাওয়াল", "জিলক্বদ", "জিলহজ্জ"
    )

    /** A resolved Hijri date including the sighting adjustment that produced it. */
    data class HijriDate(
        val year: Int,
        val month: Int,        // 1..12
        val day: Int,          // 1..30
        val adjustment: Int = 0
    ) {
        val monthName: String get() = monthNames[month - 1]
        val monthNameBn: String get() = monthNamesBn[month - 1]
        override fun toString(): String = "$day $monthName $year"
    }

    fun clampAdjustment(value: Int): Int = value.coerceIn(MIN_ADJUSTMENT, MAX_ADJUSTMENT)

    /** Gregorian -> Hijri, applying [adjustment] days (−3..+3). */
    fun fromGregorian(date: LocalDate, adjustment: Int = 0): HijriDate {
        val adj = clampAdjustment(adjustment)
        val shifted = date.plusDays(adj.toLong())
        val hd = HijrahDate.from(shifted)
        return HijriDate(
            year = hd.get(ChronoField.YEAR),
            month = hd.get(ChronoField.MONTH_OF_YEAR),
            day = hd.get(ChronoField.DAY_OF_MONTH),
            adjustment = adj
        )
    }

    /** Hijri -> Gregorian, reversing the same [adjustment]. Throws on invalid date. */
    fun toGregorian(h: HijriDate): LocalDate {
        require(h.month in 1..12) { "Hijri month must be 1..12, was ${h.month}" }
        require(h.day in 1..30) { "Hijri day must be 1..30, was ${h.day}" }
        val adj = clampAdjustment(h.adjustment)
        val hd = HijrahDate.of(h.year, h.month, h.day)
        return LocalDate.from(hd).minusDays(adj.toLong())
    }

    /** Non-throwing validity check (e.g. 30 Ramadan does not exist). */
    fun isValid(h: HijriDate): Boolean = try {
        h.month in 1..12 && h.day in 1..30 &&
            HijrahDate.of(h.year, h.month, h.day) != null
    } catch (_: Exception) {
        false
    }

    /** Length in days of a Hijri month in the underlying calendar (29 or 30). */
    fun monthLength(hijriYear: Int, month: Int): Int {
        val first = HijrahDate.of(hijriYear, month, 1)
        val nextMonth = first.plus(29, ChronoUnit.DAYS)
        return if (nextMonth.get(ChronoField.MONTH_OF_YEAR) == month) 30 else 29
    }

    /** True when the underlying arithmetic calendar says [hijriYear] has 12 months. */
    fun yearLength(hijriYear: Int): Int {
        var total = 0
        for (m in 1..12) total += monthLength(hijriYear, m)
        return total
    }

    /** Today's Hijri date with a given adjustment. */
    fun today(adjustment: Int = 0): HijriDate = fromGregorian(LocalDate.now(), adjustment)

    /** Convert between two adjustments without going through Gregorian explicitly. */
    fun withAdjustment(h: HijriDate, adjustment: Int): HijriDate =
        fromGregorian(toGregorian(h), adjustment)

    /** Convenience: true when [chrono] is (or represents) a Hijrah date. */
    fun isHijri(chrono: ChronoLocalDate): Boolean = chrono is HijrahDate
}
