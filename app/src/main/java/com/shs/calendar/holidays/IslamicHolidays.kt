package com.shs.calendar.holidays

import com.shs.calendar.calendar.HijriEngine
import java.time.LocalDate

/**
 * M6: Islamic (Hijri) holiday rules, computed live by [HijriEngine].
 *
 * Each rule pins a Hijri month + day, e.g. "10 Dhu al-Hijjah" (Eid al-Adha).
 * The Gregorian date is produced by the engine, so a moon-sighting
 * adjustment (the user's -3..+3 setting) shifts these dates coherently with
 * the rest of the app instead of a frozen per-year table.
 */
object IslamicHolidays {

    /** One Hijri-pinned rule. [countries] limits which datasets observe it. */
    data class Rule(
        val hijriMonth: Int,
        val hijriDay: Int,
        val nameEn: String,
        val nameBn: String,
        val countries: Set<HolidayCountry>
    )

    private val BD = HolidayCountry.BANGLADESH
    private val IN = HolidayCountry.INDIA
    private val SA = HolidayCountry.SAUDI_ARABIA
    private val GLOBAL = HolidayCountry.GLOBAL
    private val MUSLIM_WORLD = setOf(BD, IN, SA, GLOBAL)

    /** Rules are well-known, publicly documented observances only. */
    val rules: List<Rule> = listOf(
        Rule(1, 1, "Islamic New Year", "ইসলামি নববর্ষ", MUSLIM_WORLD),
        Rule(1, 10, "Ashura", "আশুরা", MUSLIM_WORLD),
        Rule(3, 12, "Mawlid an-Nabi", "মওলদ শবে মুহাম্মদ", MUSLIM_WORLD),
        Rule(7, 27, "Isra and Mi'raj", "ইসরা ও মি'রাজ", setOf(SA, IN)),
        Rule(8, 15, "Shab-e-Barat", "শবে বারাত", setOf(BD, IN)),
        Rule(10, 1, "Eid al-Fitr", "ঈদুল ফিতর", MUSLIM_WORLD),
        Rule(10, 2, "Eid al-Fitr Holiday", "ঈদুল ফিতর (দ্বিতীয় দিন)", setOf(BD, SA, IN)),
        Rule(12, 9, "Day of Arafah", "আরাফাহ দিবস", setOf(SA)),
        Rule(12, 10, "Eid al-Adha", "ঈদুল আযহা", MUSLIM_WORLD),
        Rule(12, 11, "Eid al-Adha Holiday", "ঈদুল আযহা (দ্বিতীয় দিন)", setOf(BD, SA, IN))
    )
}
