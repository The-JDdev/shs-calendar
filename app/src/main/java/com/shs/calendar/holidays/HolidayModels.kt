package com.shs.calendar.holidays

import java.time.LocalDate

/**
 * M6: holiday data model shared by every source, the list screen and search.
 *
 * A [Holiday] is always a *computed* record: its [date] is derived from a
 * documented rule (fixed Gregorian month/day, an Islamic lunar date via
 * [com.shs.calendar.calendar.HijriEngine], a Bengali solar date via
 * [com.shs.calendar.calendar.BengaliEngine], or the Gregorian computus for
 * Easter). Nothing here is a per-year lookup table, so the 1900-3000 range
 * is genuine rather than interpolated.
 */
data class Holiday(
    val date: LocalDate,
    val nameEn: String,
    val nameBn: String,
    val country: HolidayCountry,
    val category: HolidayCategory
) {
    /** True when this holiday is an official nationwide day off. */
    val isPublic: Boolean
        get() = category == HolidayCategory.NATIONAL ||
            category == HolidayCategory.ISLAMIC

    fun localizedName(bengali: Boolean): String = if (bengali) nameBn else nameEn
}

/** The five M6 country datasets plus the global (multi-country) entries. */
enum class HolidayCountry(val code: String, val displayEn: String, val displayBn: String) {
    BANGLADESH("BD", "Bangladesh", "বাংলাদেশ"),
    INDIA("IN", "India", "ভারত"),
    SAUDI_ARABIA("SA", "Saudi Arabia", "সৌদি আরব"),
    UNITED_STATES("US", "United States", "যুক্তরাষ্ট্র"),
    UNITED_KINGDOM("UK", "United Kingdom", "যুক্তরাজ্য"),
    GLOBAL("XX", "Global", "বিশ্ব");
}

/** Categorisation required by the brief. */
enum class HolidayCategory(val displayEn: String, val displayBn: String) {
    NATIONAL("National", "জাতীয়"),
    ISLAMIC("Islamic", "ইসলামি"),
    BENGALI("Bengali cultural", "বাঙালি সংস্কৃতি"),
    REGIONAL("Regional", "আঞ্চলিক");
}
