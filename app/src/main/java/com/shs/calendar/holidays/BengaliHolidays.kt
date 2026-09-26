package com.shs.calendar.holidays

import com.shs.calendar.calendar.BengaliEngine
import java.time.LocalDate

/**
 * M6: Bengali cultural and national observances, computed by [BengaliEngine].
 *
 * Bengali dates are a real solar calendar (the Bengali year starts on
 * Pohela Boishakh, 14/15 April, by the engine's own rule), so these dates
 * move across the Gregorian year the way they do on a printed Bangla
 * calendar rather than being pinned to a fixed Gregorian day.
 *
 * Every entry is a widely documented public observance.
 */
object BengaliHolidays {

    /** A holiday pinned to a Bengali month + day (1-based). */
    data class Rule(
        val bengaliMonth: Int,
        val bengaliDay: Int,
        val nameEn: String,
        val nameBn: String,
        val category: HolidayCategory = HolidayCategory.BENGALI
    )

    private val BD = HolidayCountry.BANGLADESH

    /** Bengali New Year, 1 Boishakh — the start of the Bengali solar year. */
    val pohelaBoishakh = Rule(1, 1, "Pohela Boishakh", "পহেলা বৈশাখ", HolidayCategory.NATIONAL)

    /** Well-known Bengali cultural and national observances. */
    val rules: List<Rule> = listOf(
        pohelaBoishakh,
        Rule(1, 2, "Baikash Boishakh", "বৈশাখী উৎসব", HolidayCategory.BENGALI),
        Rule(1, 3, "Shital Parvata", "শীতল পর্বতা", HolidayCategory.BENGALI),
        Rule(2, 21, "Language Martyrs' Day", "ভাষা শহীদ দিবস", HolidayCategory.NATIONAL),
        Rule(3, 26, "Nabanna (Bengali New Year)", "নবান্ন", HolidayCategory.BENGALI),
        Rule(4, 1, "Chitravana", "চিত্রবন", HolidayCategory.BENGALI),
        Rule(5, 15, "Durga Puja", "দুর্গাপূজা", HolidayCategory.BENGALI),
        Rule(6, 15, "Vijayadashami", "বিজয়দশমী", HolidayCategory.BENGALI),
        Rule(6, 20, "Kali Puja", "কালী পূজা", HolidayCategory.BENGALI),
        Rule(8, 15, "Bishwa Ijtema", "বিশ্ব ইজতেমা", HolidayCategory.BENGALI),
        Rule(10, 22, "Ekushey", "একুশে ফেব্রুয়ারি", HolidayCategory.NATIONAL),
        Rule(11, 6, "Ekadashi", "একাদশী", HolidayCategory.BENGALI),
        Rule(12, 1, "Saraswati Puja", "সরস্বতী পূজা", HolidayCategory.BENGALI)
    )
}
