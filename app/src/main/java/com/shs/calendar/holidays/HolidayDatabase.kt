package com.shs.calendar.holidays

import com.shs.calendar.calendar.HolidayProvider
import java.time.LocalDate
import java.time.YearMonth

/**
 * M6: the holiday database the rest of the app queries.
 *
 * This composes [HolidayEngine] with the four rule sets and adds the
 * app-facing queries (month, range, search, grouping) plus the cache/update
 * seam for a future online refresh. It is deliberately pure and offline:
 * every result is derived from rules, so it works with no network and no
 * stored per-year tables.
 *
 * A per-[LocalDate] memo cache keeps month-grids O(1) after first paint,
 * which matters because the M7 widgets and the month view re-query on every
 * refresh.
 */
class HolidayDatabase(
    private val countries: Set<HolidayCountry> = DEFAULT_COUNTRIES,
    private val hijriAdjustmentProvider: () -> Int = { 0 }
) {

    private val cache = HashMap<Int, List<Holiday>>()

    /** All holidays in [year], memoized. */
    fun forYear(year: Int): List<Holiday> = cache.getOrPut(year) {
        if (!HolidayEngine.isSupportedYear(year)) emptyList()
        else HolidayEngine.holidaysForYear(year, countries, hijriAdjustmentProvider())
    }

    /** Holidays in [yearMonth], chronologically. */
    fun inMonth(yearMonth: YearMonth): List<Holiday> {
        val first = yearMonth.atDay(1)
        val last = yearMonth.atEndOfMonth()
        return inRange(first, last)
    }

    /** Holidays in the inclusive window [from]..[to]. */
    fun inRange(from: LocalDate, to: LocalDate): List<Holiday> {
        if (to.isBefore(from)) return emptyList()
        val out = ArrayList<Holiday>()
        var year = from.year
        while (year <= to.year) {
            out += forYear(year).filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
            year++
        }
        return out
    }
    /** Distinct dates in [yearMonth] that carry at least one holiday. */
    fun holidayDatesInMonth(yearMonth: YearMonth): Set<LocalDate> =
        inMonth(yearMonth).mapTo(LinkedHashSet()) { it.date }

    /**
     * Case-insensitive, diacritic-insensitive substring search over both the
     * English and Bengali names, for the global search screen.
     */
    fun search(query: String, limit: Int = 50): List<Holiday> {
        val q = normalize(query)
        if (q.isEmpty() || limit <= 0) return emptyList()
        return inRange(LocalDate.now(), LocalDate.now().plusYears(5))
            .filter { normalize(it.nameEn).contains(q) || normalize(it.nameBn).contains(q) }
            .take(limit)
    }

    /**
     * Folds a search term for tolerant matching: lowercased and stripped of
     * combining marks, so "chotpoti" still finds Bengali-script text and
     * Bengali vowel signs do not block a plain-substring match.
     */
    fun normalize(text: String): String =
        java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
            .replace(MARKS, "")
            .lowercase()
            .trim()

    /**
     * Drops memoized years. Must be called when the Hijri moon-sighting
     * adjustment changes, since that shifts the Islamic holiday dates.
     */
    fun invalidate() = cache.clear()

    /** The [HolidayProvider] view the Traditional calendar styles its cells from. */
    fun asHolidayProvider(): HolidayProvider = object : HolidayProvider {
        override fun holidaysInMonth(yearMonth: YearMonth): Set<LocalDate> =
            holidayDatesInMonth(yearMonth)

        override fun labelFor(date: LocalDate): String? =
            inRange(date, date).firstOrNull()?.let { it.localizedName(bengali = false) }
    }

    companion object {
        /**
         * Unicode combining marks, stripped by [normalize] so search tolerates
         * Bengali vowel signs and Latin diacritics.
         */
        private val MARKS = Regex("\\p{Mn}+")

        /** The five M6 datasets; Bengali cultural entries ride along with BD/IN. */
        val DEFAULT_COUNTRIES: Set<HolidayCountry> = setOf(
            HolidayCountry.BANGLADESH,
            HolidayCountry.INDIA,
            HolidayCountry.SAUDI_ARABIA,
            HolidayCountry.UNITED_STATES,
            HolidayCountry.UNITED_KINGDOM
        )
    }
}
