package com.shs.calendar.calendar

import java.time.LocalDate
import java.time.YearMonth

/**
 * M4: traditional Bengali printed-calendar header.
 *
 * Renders the five-line header in the classical printed format. Every value
 * is computed live from the Gregorian/Bengali/Hijri engines - nothing is
 * copied from a fixed example:
 *
 *   1: "আজ ২৫ সেপ্টেম্বর ২০২৬ ইংরেজি"  (today, Bangla Gregorian)
 *   2: "রোজ - শুক্রবার"                  (weekday pair)
 *   3: "আগস্ট ২০২৬"                     (Gregorian month + year, Bangla digits)
 *   4: "শ্রাবণ — ভাদ্র ১৪৩৩ বাংলা"       (Bengali month transition + year)
 *   5: "সফর — রবিউল আউয়াল ১৪৪৮ হিজরি"  (Hijri month transition + year)
 *
 * Lines 4 and 5 show the month transition (the Bengali and Hijri months the
 * date sits between), which is what traditional printed almanacs display.
 *
 * Pure Kotlin: no Android dependencies, so it is directly unit-testable.
 */
object TraditionalHeader {

    /**
     * Standard Bengali names for the 12 Gregorian months, in calendar order.
     * Ordinary Bengali vocabulary (not religious content) - the only lookup
     * table here; all date values come from the engines.
     */
    private val GREGORIAN_MONTHS_BN = listOf(
        "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
        "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
    )

    /** Short weekday pair used on printed calendars: রবি, সোম, ... শনি. */
    private val WEEKDAYS_SHORT = listOf(
        "রবি", "সোম", "মঙ্গল", "বুধ", "বৃহস্পতি", "শুক্র", "শনি"
    )

    /** Bangla digits for an integer, e.g. 2026 -> "২০২৬". */
    fun bn(value: Int): String = BengaliNumerals.number(value)

    private fun gregorianMonthBn(month: java.time.Month): String =
        GREGORIAN_MONTHS_BN[month.value - 1]

    /** Line 1: "আজ <day> <Gregorian month> <year> ইংরেজি". */
    fun todayLine(date: LocalDate): String = buildString {
        append("আজ ")
        append(bn(date.dayOfMonth))
        append(' ')
        append(gregorianMonthBn(date.month))
        append(' ')
        append(bn(date.year))
        append(" ইংরেজি")
    }

    /**
     * Line 2: weekday pair, e.g. "রোজ - শুক্রবার". The short name pairs with
     * the full name from BengaliEngine.weekdayNames (Sunday-first order).
     */
    fun weekdayLine(date: LocalDate): String {
        val idx = date.dayOfWeek.value % 7
        return "${WEEKDAYS_SHORT[idx]} - ${BengaliEngine.weekdayNames[idx]}"
    }

    /** Line 3: "আগস্ট ২০২৬" - Gregorian month and year in Bangla digits. */
    fun gregorianLine(date: LocalDate): String =
        "${gregorianMonthBn(date.month)} ${bn(date.year)}"

    /** Previous Bengali month name, wrapping from Boishakh back to Choitro. */
    private fun previousBengaliMonthName(b: BengaliEngine.BengaliDate): String {
        val idx = if (b.month == 1) BengaliEngine.MONTH_COUNT else b.month
        return BengaliEngine.monthNames[idx - 2]
    }

    /** Line 4: Bengali month transition, e.g. "Srabon \u2014 Bhadro 1433 Bangla". */
    fun bengaliLine(date: LocalDate): String {
        val b = BengaliEngine.fromGregorian(date)
        return "${previousBengaliMonthName(b)} \u2014 ${b.monthName} ${bn(b.year)} \u09ac\u09be\u0982\u09b2\u09be"
    }

    /** Previous Hijri month name, wrapping from Muharram back to Dhu al-Hijjah. */
    private fun previousHijriMonthName(h: HijriEngine.HijriDate): String {
        val idx = if (h.month == 1) 12 else h.month
        return HijriEngine.monthNamesBn[idx - 2]
    }

    /** Line 5: Hijri month transition, e.g. "Safar \u2014 Rabi al-Awwal 1448 Hijri". */
    fun hijriLine(date: LocalDate, adjustment: Int = 0): String {
        val h = HijriEngine.fromGregorian(date, adjustment)
        return "${previousHijriMonthName(h)} \u2014 ${h.monthNameBn} ${bn(h.year)} \u09b9\u09bf\u099c\u09b0\u09c0"
    }

    /** Full five-line header, top to bottom, as printed on a traditional calendar. */
    fun lines(date: LocalDate, adjustment: Int = 0): List<String> = listOf(
        todayLine(date),
        weekdayLine(date),
        gregorianLine(date),
        bengaliLine(date),
        hijriLine(date, adjustment)
    )

    /** Column headers for the date grid. */
    fun weekdayHeaders(): List<String> = WEEKDAYS_SHORT.toList()

    /** Short weekday name for a date, matching the grid's column order. */
    fun weekdayShort(date: LocalDate): String = WEEKDAYS_SHORT[date.dayOfWeek.value % 7]
}
