package com.shs.calendar.holidays

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * M6: fixed-date and computus-based national holidays.
 *
 * Two kinds live here:
 *  - FIXED rules pin a Gregorian month/day (e.g. Bangladesh 26 March,
 *    US 4 July) and therefore repeat every year.
 *  - EASTER rules pin an offset from Easter Sunday, which is itself derived
 *    with the anonymous Gregorian computus (no per-year table, no Julian
 *    drift) — this is what makes Good Friday / Easter Monday correct for
 *    every year in the supported 1900-3000 range.
 *
 * All entries are well-known, publicly documented public holidays; nothing
 * here is invented cultural or religious content.
 */
object FixedHolidays {

    /** A Gregorian month/day that recurs annually. */
    data class Fixed(
        val month: Int,
        val day: Int,
        val nameEn: String,
        val nameBn: String,
        val country: HolidayCountry,
        val category: HolidayCategory = HolidayCategory.NATIONAL
    )

    /** An offset in days from Easter Sunday. */
    data class Easter(
        val offsetDays: Int,
        val nameEn: String,
        val nameBn: String,
        val country: HolidayCountry,
        val category: HolidayCategory = HolidayCategory.NATIONAL
    )

    private val BD = HolidayCountry.BANGLADESH
    private val IN = HolidayCountry.INDIA
    private val SA = HolidayCountry.SAUDI_ARABIA
    private val US = HolidayCountry.UNITED_STATES
    private val UK = HolidayCountry.UNITED_KINGDOM

    /** Sentinel for [NthWeekday.nth] meaning "the last such weekday". */
    const val LAST = 5

    /**
     * The nth [dayOfWeek] of [month] (e.g. the 3rd Monday of January, or the
     * last Monday of May when [nth] is [LAST]). [nth] must be 1..[LAST].
     */
    data class NthWeekday(
        val month: Int,
        val nth: Int,
        val dayOfWeek: DayOfWeek,
        val nameEn: String,
        val nameBn: String,
        val country: HolidayCountry,
        val category: HolidayCategory = HolidayCategory.NATIONAL
    ) {
        init {
            require(month in 1..12) { "month must be 1..12, was $month" }
            require(nth in 1..LAST) { "nth must be 1..$LAST, was $nth" }
        }
    }

    /** Bangladesh: fixed-date national days (Bangla Academy calendar). */
    val bangladeshFixed: List<Fixed> = listOf(
        Fixed(3, 17, "Sheikh Mujibur Rahman's Birthday", "শেখ মুজিবের জন্মদিন", BD),
        Fixed(3, 26, "Independence Day", "স্বাধীনতা দিবস", BD),
        Fixed(5, 1, "May Day", "মে দিবস", BD),
        Fixed(8, 15, "National Mourning Day", "জাতীয় শোক দিবস", BD),
        Fixed(12, 16, "Victory Day", "বিজয় দিবস", BD),
        Fixed(12, 25, "Christmas Day", "খ্রিস্টমাস", BD, HolidayCategory.NATIONAL)
    )

    /** India: fixed-date national days. */
    val indiaFixed: List<Fixed> = listOf(
        Fixed(1, 26, "Republic Day", "গণতন্ত্র দিবস", IN),
        Fixed(8, 15, "Independence Day", "স্বাধীনতা দিবস", IN),
        Fixed(10, 2, "Gandhi Jayanti", "গান্ধী জয়ন্তী", IN),
        Fixed(12, 25, "Christmas Day", "খ্রিস্টমাস", IN)
    )

    /** Saudi Arabia: Founding Day plus the fixed Islamic observances. */
    val saudiFixed: List<Fixed> = listOf(
        Fixed(2, 22, "Founding Day", "প্রতিষ্ঠা দিবস", SA),
        Fixed(9, 23, "Saudi National Day", "জাতীয় দিবস", SA)
    )

    /** United States: federal fixed-date holidays. */
    val usFixed: List<Fixed> = listOf(
        Fixed(1, 1, "New Year's Day", "নববর্ষ", US),
        Fixed(6, 19, "Juneteenth", "জুনিটিন্থ", US),
        Fixed(7, 4, "Independence Day", "স্বাধীনতা দিবস", US),
        Fixed(11, 11, "Veterans Day", "প্রবীণ দিবস", US),
        Fixed(12, 25, "Christmas Day", "খ্রিস্টমাস", US)
    )

    /** US federal holidays that fall on a fixed weekday, not a fixed date. */
    val usNthWeekday: List<NthWeekday> = listOf(
        NthWeekday(1, 3, DayOfWeek.MONDAY, "Martin Luther King Jr. Day", "মার্টিন লুথার কিং জন্মদিন", US),
        NthWeekday(2, 3, DayOfWeek.MONDAY, "Presidents' Day", "প্রেসিডেন্ট দিবস", US),
        NthWeekday(5, 1, DayOfWeek.MONDAY, "Memorial Day", "স্মৃতি দিবস", US),
        NthWeekday(9, 1, DayOfWeek.MONDAY, "Labor Day", "শ্রম দিবস", US),
        NthWeekday(10, 2, DayOfWeek.MONDAY, "Columbus Day", "কলাম্বাস দিবস", US),
        NthWeekday(11, 4, DayOfWeek.THURSDAY, "Thanksgiving Day", "ধন্যবাদ দিবস", US)
    )

    /** United Kingdom: fixed-date holidays common across the UK nations. */
    val ukFixed: List<Fixed> = listOf(
        Fixed(1, 1, "New Year's Day", "নববর্ষ", UK),
        Fixed(12, 25, "Christmas Day", "খ্রিস্টমাস", UK),
        Fixed(12, 26, "Boxing Day", "বক্সিং ডে", UK)
    )

    /** UK holidays pinned to a weekday, plus substitute-day conventions. */
    val ukNthWeekday: List<NthWeekday> = listOf(
        NthWeekday(1, 2, DayOfWeek.MONDAY, "New Year's Day (substitute)", "নববর্ষ (বিকল্প দিন)", UK),
        NthWeekday(5, LAST, DayOfWeek.MONDAY, "Spring Bank Holiday", "বসন্ত ব্যাংক ছুটি", UK),
        NthWeekday(8, LAST, DayOfWeek.MONDAY, "Summer Bank Holiday", "গ্রীষ্ম ব্যাংক ছুটি", UK),
        NthWeekday(12, 2, DayOfWeek.MONDAY, "Boxing Day (substitute)", "বক্সিং ডে (বিকল্প দিন)", UK)
    )

    /** Easter-anchored holidays, offset in days from Easter Sunday. */
    val easterRules: List<Easter> = listOf(
        Easter(-2, "Good Friday", "শুক্রবার", US),
        Easter(0, "Easter Sunday", "ইস্টার", US),
        Easter(1, "Easter Monday", "ইস্টার সোমবার", US, HolidayCategory.REGIONAL),
        Easter(-2, "Good Friday", "শুক্রবার", UK),
        Easter(1, "Easter Monday", "ইস্টার সোমবার", UK)
    )
}
