package com.shs.calendar.holidays

import com.shs.calendar.calendar.BengaliEngine
import com.shs.calendar.calendar.HijriEngine
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * M6: resolves holiday rules into dated [Holiday] records.
 *
 * Every date here is *computed*, never tabulated:
 *  - fixed Gregorian month/day rules repeat annually,
 *  - Islamic rules are converted by [HijriEngine] (so the app's moon-sighting
 *    adjustment shifts them coherently with the prayer/Hijri surfaces),
 *  - Bengali rules are converted by [BengaliEngine]'s solar calendar,
 *  - Easter rules use the anonymous Gregorian computus.
 *
 * Supported range is 1900..3000 inclusive, matching the brief.
 */
object HolidayEngine {

    const val MIN_YEAR = 1900
    const val MAX_YEAR = 3000

    fun isSupportedYear(year: Int): Boolean = year in MIN_YEAR..MAX_YEAR

    /**
     * Easter Sunday for [year] via the anonymous Gregorian computus.
     * Valid for the full Gregorian period, hence for 1900..3000.
     */
    fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }

    /**
     * The nth [dayOfWeek] of [month] in [year]. When [nth] is
     * [FixedHolidays.LAST] this is the final such weekday of the month, so
     * "last Monday of May" is correct in every year.
     */
    fun nthWeekday(year: Int, month: Int, nth: Int, dayOfWeek: DayOfWeek): LocalDate {
        require(month in 1..12) { "month must be 1..12, was $month" }
        require(nth in 1..FixedHolidays.LAST) { "nth must be 1..5, was $nth" }
        val dowOrdinal = dayOfWeek.value % 7 // Monday=1..Sunday=0
        return if (nth == FixedHolidays.LAST) {
            LocalDate.of(year, month, 1)
                .with(TemporalAdjusters.lastInMonth(dayOfWeek))
        } else {
            LocalDate.of(year, month, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(nth, dayOfWeek))
        }.also {
            require(it.dayOfWeek.value % 7 == dowOrdinal) { "computed weekday mismatch" }
        }
    }

    /**
     * All holidays for [year] in [countries], sorted by date then name.
     *
     * Only dates that actually fall inside [year] are returned: an Islamic
     * rule pinned to a Hijri month can straddle a Gregorian year boundary,
     * and a Hijri month can be shorter than its nominal day number, so each
     * candidate is validated and simply dropped when it does not exist in
     * that year rather than being clamped to a wrong date.
     */
    fun holidaysForYear(
        year: Int,
        countries: Set<HolidayCountry> = HolidayCountry.entries.toSet(),
        hijriAdjustment: Int = 0
    ): List<Holiday> {
        if (!isSupportedYear(year)) return emptyList()
        val out = ArrayList<Holiday>(128)
        for (c in countries) {
            fixedFor(c, year)?.let { out += it }
            for (rule in weekdayFor(c)) {
                out += Holiday(
                    date = nthWeekday(year, rule.month, rule.nth, rule.dayOfWeek),
                    nameEn = rule.nameEn,
                    nameBn = rule.nameBn,
                    country = rule.country,
                    category = rule.category
                )
            }
            for (rule in IslamicHolidays.rules) {
                if (rule.countries.contains(c)) {
                    islamic(rule, year, hijriAdjustment)?.let { out += it }
                }
            }
        }
        if (countries.contains(HolidayCountry.BANGLADESH) ||
            countries.contains(HolidayCountry.INDIA)
        ) {
            for (rule in BengaliHolidays.rules) {
                out += bengali(rule, year)
            }
        }
        return out.distinctBy { it.date to it.nameEn to it.country.code }
            .sortedWith(compareBy({ it.date }, { it.nameEn }))
    }

    /** Fixed month/day + Easter-anchored national days for [country]. */
    private fun fixedFor(country: HolidayCountry, year: Int): List<Holiday> {
        val fixed = when (country) {
            HolidayCountry.BANGLADESH -> FixedHolidays.bangladeshFixed
            HolidayCountry.INDIA -> FixedHolidays.indiaFixed
            HolidayCountry.SAUDI_ARABIA -> FixedHolidays.saudiFixed
            HolidayCountry.UNITED_STATES -> FixedHolidays.usFixed
            HolidayCountry.UNITED_KINGDOM -> FixedHolidays.ukFixed
            HolidayCountry.GLOBAL -> emptyList()
        }
        val out = fixed.mapNotNull { rule ->
            // Feb 29 simply does not exist in a common year: drop the rule
            // for that year rather than clamping it onto 28 February.
            val date = runCatching { LocalDate.of(year, rule.month, rule.day) }.getOrNull()
            date?.let {
                Holiday(it, rule.nameEn, rule.nameBn, rule.country, rule.category)
            }
        }.toMutableList()
        val easter = easterSunday(year)
        for (rule in FixedHolidays.easterRules) {
            if (rule.country != country) continue
            out += Holiday(
                date = easter.plusDays(rule.offsetDays.toLong()),
                nameEn = rule.nameEn,
                nameBn = rule.nameBn,
                country = rule.country,
                category = rule.category
            )
        }
        return out
    }

    /** nth-weekday national days for [country]. */
    private fun weekdayFor(country: HolidayCountry): List<FixedHolidays.NthWeekday> =
        when (country) {
            HolidayCountry.UNITED_STATES -> FixedHolidays.usNthWeekday
            HolidayCountry.UNITED_KINGDOM -> FixedHolidays.ukNthWeekday
            else -> emptyList()
        }

    /**
     * Gregorian date of Hijri month/day [rule] inside Gregorian [year], or
     * null when that Hijri month is shorter than the pinned day.
     *
     * The Hijri year overlapping Gregorian [year] is the one containing
     * 1 Muharram of that Gregorian year plus the previous one, so a lunar
     * holiday that lands in early January is still found.
     */
    private fun islamic(
        rule: IslamicHolidays.Rule,
        year: Int,
        adjustment: Int
    ): Holiday? {
        // java.time's HijrahDate spans only ~1882..2174 CE. Outside that window
        // the lunar rules are absent rather than fatal: fixed, Bengali and
        // Easter rules still resolve, and the caller gets what exists.
        val hijriYear = runCatching {
            HijriEngine.fromGregorian(LocalDate.of(year, 1, 1)).year
        }.getOrNull() ?: return null
        for (hy in hijriYear - 1..hijriYear + 1) {
            val target = HijriEngine.HijriDate(hy, rule.hijriMonth, rule.hijriDay, adjustment)
            if (!HijriEngine.isValid(target)) continue // e.g. day 30 of a 29-day month
            val g = runCatching { HijriEngine.toGregorian(target) }.getOrNull() ?: continue
            if (g.year != year) continue
            return Holiday(g, rule.nameEn, rule.nameBn, rule.countries.first(), HolidayCategory.ISLAMIC)
        }
        return null
    }

    /**
     * Gregorian dates of a Bengali month/day that fall inside Gregorian
     * [year], or null when the day overflows a short Bengali month.
     *
     * A Bengali year runs 14/15 April to 13/14 April, so a Gregorian year is
     * covered by the tail of one Bengali year and the head of the next.
     * Both are checked, which is what makes Ekushey (Bengali 10/22, i.e.
     * 8 February) resolve on the Gregorian year that actually contains it.
     */
    private fun bengali(rule: BengaliHolidays.Rule, year: Int): List<Holiday> {
        val base = BengaliEngine.bengaliYearOf(LocalDate.of(year, 4, 15))
        val out = ArrayList<Holiday>(2)
        for (by in base..base + 1) {
            val target = BengaliEngine.BengaliDate(by, rule.bengaliMonth, rule.bengaliDay)
            if (!BengaliEngine.isValid(target)) continue
            val g = runCatching { BengaliEngine.toGregorian(target) }.getOrNull() ?: continue
            if (g.year != year) continue
            out += Holiday(g, rule.nameEn, rule.nameBn, HolidayCountry.BANGLADESH, rule.category)
        }
        return out
    }
}
