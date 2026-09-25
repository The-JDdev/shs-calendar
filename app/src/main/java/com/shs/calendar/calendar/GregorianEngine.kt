package com.shs.calendar.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pure Gregorian calendar helpers on top of java.time.
 * Deterministic; no Android dependencies. Navigation must stay smooth
 * through year 3000 and beyond (all java.time, no tables).
 */
object GregorianEngine {

    /** A single cell of a rendered month grid. */
    data class MonthCell(
        val date: LocalDate,
        val inMonth: Boolean,
        val isToday: Boolean
    )

    fun today(): LocalDate = LocalDate.now()

    fun isLeap(year: Int): Boolean = java.time.Year.isLeap(year.toLong())

    fun monthName(date: LocalDate, locale: Locale = Locale.ENGLISH): String =
        date.month.getDisplayName(TextStyle.FULL, locale)

    fun monthName(yearMonth: YearMonth, locale: Locale = Locale.ENGLISH): String =
        yearMonth.month.getDisplayName(TextStyle.FULL, locale)

    fun weekdayName(date: LocalDate, locale: Locale = Locale.ENGLISH): String =
        date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)

    fun weekdayShort(date: LocalDate, locale: Locale = Locale.ENGLISH): String =
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)

    /**
     * Full month grid including leading/trailing days from adjacent months.
     * @param firstDayOfWeek typically SUNDAY or MONDAY, from settings.
     */
    fun monthGrid(
        yearMonth: YearMonth,
        firstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY,
        today: LocalDate = LocalDate.now()
    ): List<MonthCell> {
        val first = yearMonth.atDay(1)
        val daysInMonth = yearMonth.lengthOfMonth()
        val lead = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        val cells = ArrayList<MonthCell>(42)

        val leadStart = first.minusDays(lead.toLong())
        for (i in 0 until lead) {
            val d = leadStart.plusDays(i.toLong())
            cells += MonthCell(d, inMonth = false, isToday = d == today)
        }
        for (day in 1..daysInMonth) {
            val d = yearMonth.atDay(day)
            cells += MonthCell(d, inMonth = true, isToday = d == today)
        }
        // Pad to whole weeks, always showing at least 5 rows (35 cells),
        // so short months like Feb 2026 (28 days, Sunday-aligned) still
        // render a full 5×7 grid.
        var trail = (7 - cells.size % 7) % 7
        if (cells.size + trail < 35) trail += 7
        var next = first.plusDays(daysInMonth.toLong())
        for (i in 0 until trail) {
            cells += MonthCell(next, inMonth = false, isToday = next == today)
            next = next.plusDays(1)
        }
        return cells
    }

    fun nextMonth(yearMonth: YearMonth): YearMonth = yearMonth.plusMonths(1)

    fun previousMonth(yearMonth: YearMonth): YearMonth = yearMonth.minusMonths(1)

    /** Weekday short labels starting from [firstDayOfWeek] — for grid headers. */
    fun weekdayHeaders(
        firstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY,
        locale: Locale = Locale.ENGLISH
    ): List<String> = (0 until 7).map {
        DayOfWeek.of(((firstDayOfWeek.value - 1 + it) % 7) + 1)
            .getDisplayName(TextStyle.SHORT, locale)
    }

    /** ISO date string (yyyy-MM-dd), stable for DB and serialization. */
    fun iso(date: LocalDate): String = date.toString()

    fun parseIso(text: String): LocalDate = LocalDate.parse(text)

    /** Clamp/normalize arbitrary year navigation (defensive; java.time handles 3000+). */
    fun yearMonthOf(date: LocalDate): YearMonth = YearMonth.from(date)
}
