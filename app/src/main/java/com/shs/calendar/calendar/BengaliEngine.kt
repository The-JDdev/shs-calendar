package com.shs.calendar.calendar

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Bengali (Bangabda) calendar — classic Bangladesh / West-Bengal rule per SPEC:
 *  - 1 Boishakh = April 14 (fixed)
 *  - Boishakh..Bhadro = 31 days (5 months)
 *  - Ashwin..Choitro = 30 days (7 months)
 *  - Falgun = 31 in Gregorian leap years, else 30
 *  - Bengali year = Gregorian − 593 after Pohela Boishakh, − 594 before
 *
 * VALIDATION ANCHOR: 2026-09-25 = 10 Ashshin 1433 (Friday)
 * Additional anchor: 14 April 2025 = 1 Boishakh 1432.
 *
 * No hardcoded lookup tables: everything derives from April 14 + fixed
 * month-length rules. The 2019 reform is intentionally NOT implemented;
 * [BengaliVariant] exposes the setting hook required by SPEC.
 */
object BengaliEngine {

    /** Setting hook for a future 2019-reform variant (SPEC: expose, do not implement). */
    enum class BengaliVariant { CLASSIC, REFORM_2019 }

    const val MONTH_COUNT = 12

    /** Gregorian month/day where the Bengali year begins. */
    private const val YEAR_START_MONTH = 4
    private const val YEAR_START_DAY = 14

    /** Offset: BengaliYear + 593 = GregorianYear of the April-14 start. */
    const val YEAR_OFFSET = 593

    val monthNames = listOf(
        "বৈশাখ", "জ্যৈষ্ঠ", "আষাঢ়", "শ্রাবণ", "ভাদ্র",
        "আশ্বিন", "কার্তিক", "অগ্রহায়ণ", "পৌষ", "মাঘ",
        "ফাল্গুন", "চৈত্র"
    )

    /** Alternative spelling used in some regions (SPEC allows আশ্বিন/আশ্বিন pair). */
    val monthNamesAlt = listOf(
        "বৈশাখ", "জ্যৈষ্ঠ", "আষাঢ়", "শ্রাবণ", "ভাদ্র",
        "আশ্বিন", "কার্তিক", "অগ্রহায়ণ", "পৌষ", "মাঘ",
        "ফাল্গুন", "চৈত্র"
    )

    /** Romanized month names (English UI / conversion screen). */
    val monthNamesEn = listOf(
        "Boishakh", "Jyeshtho", "Asharho", "Srabon", "Bhadro",
        "Ashshin", "Kartik", "Ogrohayon", "Poush", "Magh",
        "Falgun", "Choitro"
    )

    /** Seasons (ঋতু), two Bengali months each, per Bengali almanac tradition. */
    val seasons = listOf(
        "গ্রীষ্ম", // Boishakh, Jyeshtho
        "বর্ষা",   // Asharho, Srabon
        "শরৎ",     // Bhadro, Ashshin
        "হেমন্ত",  // Kartik, Ogrohayon
        "শীত",     // Poush, Magh
        "বসন্ত"    // Falgun, Choitro
    )

    val weekdayNames = listOf(
        "রবিবার", "সোমবার", "মঙ্গলবার", "বুধবার",
        "বৃহস্পতিবার", "শুক্রবার", "শনিবার"
    )

    /** A resolved Bengali date (year/month/day are Bengali calendar units). */
    data class BengaliDate(
        val year: Int,
        val month: Int,   // 1..12
        val day: Int      // 1..monthLength
    ) {
        val monthName: String get() = monthNames[month - 1]
        val monthNameEn: String get() = monthNamesEn[month - 1]
        val season: String get() = seasons[(month - 1) / 2]
        override fun toString(): String = "$day $monthNameEn $year"
    }

    private fun isGregorianLeap(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

    /** Length in days of a Bengali month. Falgun (11) is leap-sensitive. */
    fun monthLength(bengaliYear: Int, month: Int): Int = when (month) {
        in 1..5 -> 31                    // Boishakh..Bhadro
        // Falgun spans Feb–Mar of Gregorian year (bengaliYear + 594): it has
        // 31 days iff THAT February contains a leap day (round-trip verified).
        11 -> if (isGregorianLeap(bengaliYear + YEAR_OFFSET + 1)) 31 else 30
        in 6..12 -> 30                   // Ashwin..Choitro
        else -> throw IllegalArgumentException("Bengali month must be 1..12, was $month")
    }

    /** Total days in a Bengali year (365, or 366 when its Falgun covers Feb 29). */
    fun yearLength(bengaliYear: Int): Int =
        (1..MONTH_COUNT).sumOf { monthLength(bengaliYear, it) }

    /** Gregorian date of 1 Boishakh of [bengaliYear]. */
    fun yearStart(bengaliYear: Int): LocalDate =
        LocalDate.of(bengaliYear + YEAR_OFFSET, YEAR_START_MONTH, YEAR_START_DAY)

    /** Bengali year that [date] belongs to. */
    fun bengaliYearOf(date: LocalDate): Int {
        val gYear = date.year
        val pohela = LocalDate.of(gYear, YEAR_START_MONTH, YEAR_START_DAY)
        return if (!date.isBefore(pohela)) gYear - YEAR_OFFSET else gYear - YEAR_OFFSET - 1
    }

    /** Gregorian -> Bengali. */
    fun fromGregorian(date: LocalDate): BengaliDate {
        val by = bengaliYearOf(date)
        var remaining = ChronoUnit.DAYS.between(yearStart(by), date).toInt()
        if (remaining < 0) throw IllegalArgumentException("Negative offset for $date")
        var month = 1
        while (month <= MONTH_COUNT) {
            val len = monthLength(by, month)
            if (remaining < len) return BengaliDate(by, month, remaining + 1)
            remaining -= len
            month++
        }
        throw IllegalStateException("Date $date outside Bengali year $by")
    }

    /** Bengali -> Gregorian, validating day range (throws on invalid date). */
    fun toGregorian(b: BengaliDate): LocalDate {
        require(b.month in 1..MONTH_COUNT) { "Bengali month must be 1..12, was ${b.month}" }
        val len = monthLength(b.year, b.month)
        require(b.day in 1..len) { "Bengali day must be 1..$len, was ${b.day}" }
        var offset = b.day - 1
        for (m in 1 until b.month) offset += monthLength(b.year, m)
        return yearStart(b.year).plusDays(offset.toLong())
    }

    /** True when the Bengali date exists (no exception thrown). */
    fun isValid(b: BengaliDate): Boolean =
        b.month in 1..MONTH_COUNT && b.day in 1..monthLength(b.year, b.month)

    /** First day of a Bengali month as Gregorian. */
    fun monthStart(bengaliYear: Int, month: Int): LocalDate =
        toGregorian(BengaliDate(bengaliYear, month, 1))
}
