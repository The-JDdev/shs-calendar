package com.shs.calendar.calendar

import java.time.LocalDate
import java.time.YearMonth

/**
 * M4 seam for holiday marking in the Traditional Bangla calendar view.
 *
 * The Traditional view styles holiday cells (red/pink) purely from this
 * interface, so the screen is complete and testable now while the actual
 * holiday database ships in M6 (BD/IN/SA/US/UK + Bengali cultural + Islamic).
 *
 * No festival names are hardcoded here: the brief forbids invented religious
 * or cultural content, so an empty provider is the honest default until the
 * curated, attributed M6 data set exists.
 */
interface HolidayProvider {

    /** Dates that should be rendered as holiday cells. */
    fun holidaysInMonth(yearMonth: YearMonth): Set<LocalDate>

    /** Human-readable mark for a holiday cell, or null when not a holiday. */
    fun labelFor(date: LocalDate): String? = null

    /** Default provider: no holidays until the M6 database is implemented. */
    object Empty : HolidayProvider {
        override fun holidaysInMonth(yearMonth: YearMonth): Set<LocalDate> = emptySet()
    }
}
