package com.shs.calendar.data.recurrence

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * RFC 5545 RRULE subset parser + occurrence expansion (pure Kotlin, no tables).
 *
 * Supported: FREQ=DAILY|WEEKLY|MONTHLY|YEARLY, INTERVAL, COUNT, UNTIL,
 * BYDAY (weekly: "MO,WE,FR"; monthly: positional "2TU","-1FR"), BYMONTHDAY.
 * Everything else is ignored gracefully — expansion never throws on unknown
 * parts, it just produces fewer refinements (documented subset per SPEC).
 */
data class RRule(
    val freq: Freq,
    val interval: Int = 1,
    val count: Int? = null,
    val untilUtcMillis: Long? = null,
    val byDay: List<ByDay> = emptyList(),
    val byMonthDay: List<Int> = emptyList()
) {
    enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

    /** dayOfWeek plus optional positional week (1 = first, -1 = last, 0 = none). */
    data class ByDay(val day: DayOfWeek, val position: Int = 0)

    companion object {
        /**
         * Parses an RRULE string. Returns null when the string is blank or
         * has no recognisable FREQ (treated as a single occurrence).
         */
        fun parse(rrule: String?): RRule? {
            if (rrule.isNullOrBlank()) return null
            val parts = rrule.split(';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .associate {
                    val i = it.indexOf('=')
                    if (i <= 0) "" to "" else it.substring(0, i).uppercase() to it.substring(i + 1)
                }
            val freq = parts["FREQ"]?.let { f ->
                Freq.entries.firstOrNull { it.name == f }
            } ?: return null

            val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val count = parts["COUNT"]?.toIntOrNull()?.takeIf { it > 0 }
            val until = parseUntil(parts["UNTIL"])
            val byDay = parseByDay(parts["BYDAY"])
            val byMonthDay = parts["BYMONTHDAY"].orEmpty()
                .split(',').mapNotNull { it.trim().toIntOrNull() }
                .filter { it in -31..31 && it != 0 }
            return RRule(freq, interval, count, until, byDay, byMonthDay)
        }

        private fun parseUntil(raw: String?): Long? {
            if (raw.isNullOrBlank()) return null
            if (raw.endsWith("Z")) {
                return runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
            }
            if (raw.all { it.isDigit() }) {
                return when (raw.length) {
                    // yyyyMMdd → inclusive end of that day (UTC).
                    8 -> runCatching {
                        LocalDate.parse(raw).atTime(23, 59).atZone(ZoneId.of("UTC"))
                            .toInstant().toEpochMilli()
                    }.getOrNull()
                    // Already epoch milliseconds (clients pass raw millis).
                    else -> raw.toLongOrNull()
                }
            }
            return runCatching {
                LocalDate.parse(raw).atTime(23, 59).atZone(ZoneId.of("UTC"))
                    .toInstant().toEpochMilli()
            }.getOrNull()
        }

        private fun parseByDay(raw: String?): List<ByDay> =
            raw.orEmpty().split(',').mapNotNull { token ->
                val t = token.trim().uppercase()
                if (t.length < 2) return@mapNotNull null
                val suffix = t.substring(t.length - 2)
                val dow = DayOfWeek.entries.firstOrNull { it.name.startsWith(suffix) }
                    ?: return@mapNotNull null
                val pos = t.dropLast(2).toIntOrNull() ?: 0
                ByDay(dow, pos)
            }
    }
}

/**
 * Expands [master] occurrences inside [fromUtcMillis, toUtcMillis].
 *
 * Semantics: recurrence generates occurrence START instants; each occurrence
 * keeps the master's duration. COUNT counts from the master start (RFC 5545).
 * Result is sorted ascending and deduplicated by start instant.
 */
object RecurrenceExpander {

    private const val MAX_OCCURRENCES = 5_000
    private const val DAY_MS = 86_400_000L

    /**
     * @param startUtcMillis master start (epoch UTC)
     * @param durationMs     occurrence duration (end − start), clamped ≥ 0
     * @param zone           zone used to map instants → local calendar dates
     */
    fun expand(
        rrule: RRule?,
        startUtcMillis: Long,
        durationMs: Long,
        zone: ZoneId,
        fromUtcMillis: Long,
        toUtcMillis: Long
    ): List<Long> {
        if (rrule == null) {
            val end = startUtcMillis + durationMs.coerceAtLeast(0)
            return if (startUtcMillis < toUtcMillis && end > fromUtcMillis) listOf(startUtcMillis)
            else emptyList()
        }

        val results = ArrayList<Long>()
        val startDate = Instant.ofEpochMilli(startUtcMillis).atZone(zone).toLocalDate()
        var produced = 0

        // Candidate dates for each frequency, walked in ascending order.
        for (date in candidateDates(rrule, startDate, zone)) {
            val occStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
            if (rrule.untilUtcMillis != null && occStart > rrule.untilUtcMillis!!) break
            // COUNT applies from the first occurrence (the master start).
            if (rrule.count != null && produced >= rrule.count!!) break
            produced++
            if (occStart >= toUtcMillis) break
            // Window filters on the occurrence START instant: an occurrence is
            // included iff it starts inside [from, to).
            if (occStart >= fromUtcMillis && occStart < toUtcMillis) {
                if (occStart >= startUtcMillis) results.add(occStart)
            }
            if (results.size >= MAX_OCCURRENCES) break
        }
        return results.distinct().sorted()
    }

    /** Convenience wrapper: expand using the entity's own zone string. */
    fun expand(
        rruleText: String?,
        startUtcMillis: Long,
        endUtcMillis: Long,
        zoneId: String,
        fromUtcMillis: Long,
        toUtcMillis: Long
    ): List<Long> = expand(
        RRule.parse(rruleText),
        startUtcMillis,
        endUtcMillis - startUtcMillis,
        runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.of("UTC")),
        fromUtcMillis,
        toUtcMillis
    )

    /**
     * Infinite lazy sequence of qualifying local dates (ascending) starting at
     * or before [startDate]. Bounded internally by MAX_OCCURRENCES steps.
     */
    private fun candidateDates(rrule: RRule, startDate: LocalDate, zone: ZoneId): Sequence<LocalDate> =
        sequence {
            var steps = 0
            when (rrule.freq) {
                RRule.Freq.DAILY -> {
                    var d = startDate
                    while (steps++ < MAX_OCCURRENCES * 4) {
                        yield(d)
                        d = d.plusDays(rrule.interval.toLong())
                    }
                }
                RRule.Freq.WEEKLY -> {
                    val days: List<DayOfWeek> =
                        rrule.byDay.map { it.day }.ifEmpty { listOf(startDate.dayOfWeek) }
                    // Anchor week = week containing startDate.
                    val weekStart = startDate.minusDays(startDate.dayOfWeek.value.toLong() - 1)
                    var week = 0L
                    while (steps++ < MAX_OCCURRENCES * 4) {
                        val base = weekStart.plusWeeks(week)
                        val dates = days.map { dow ->
                            base.plusDays((dow.value - 1).toLong())
                        }.filter { !it.isBefore(startDate) }.sorted()
                        for (d in dates) yield(d)
                        week += rrule.interval.toLong()
                    }
                }
                RRule.Freq.MONTHLY -> {
                    // Skip forward to the month containing startDate.
                    var anchor = startDate.withDayOfMonth(1)
                    var guard = 0
                    while (steps++ < MAX_OCCURRENCES * 4) {
                        val dates = monthlyCandidates(rrule, anchor, startDate)
                        for (d in dates) yield(d)
                        anchor = anchor.plusMonths(rrule.interval.toLong())
                        if (guard++ > 1 && anchor.year > 9999) break
                    }
                }
                RRule.Freq.YEARLY -> {
                    var anchor = startDate.withDayOfYear(1)
                    while (steps++ < MAX_OCCURRENCES * 4) {
                        val dates = yearlyCandidates(rrule, anchor, startDate)
                        for (d in dates) yield(d)
                        anchor = anchor.plusYears(rrule.interval.toLong())
                        if (anchor.year > 9999) break
                    }
                }
            }
        }

    private fun monthlyCandidates(rrule: RRule, anchor: LocalDate, startDate: LocalDate): List<LocalDate> {
        val lastDay = anchor.lengthOfMonth()
        val candidates = ArrayList<LocalDate>()
        if (rrule.byDay.isNotEmpty()) {
            for (bd in rrule.byDay) {
                val date = when {
                    bd.position == 0 -> {
                        // Any such weekday within the month (first match ≥ 1).
                        val target = bd.day.value
                        val first = 1 + ((target - anchor.dayOfWeek.value + 7) % 7)
                        if (first <= lastDay) anchor.withDayOfMonth(first) else null
                    }
                    bd.position > 0 -> {
                        val target = bd.day.value
                        val first = 1 + ((target - anchor.dayOfWeek.value + 7) % 7)
                        val day = first + (bd.position - 1) * 7
                        if (day in 1..lastDay) anchor.withDayOfMonth(day) else null
                    }
                    else -> { // last occurrence
                        val target = bd.day.value
                        val last = lastDay - ((anchor.withDayOfMonth(lastDay).dayOfWeek.value - target + 7) % 7)
                        if (last >= 1) anchor.withDayOfMonth(last) else null
                    }
                }
                if (date != null) candidates.add(date)
            }
        } else if (rrule.byMonthDay.isNotEmpty()) {
            for (md in rrule.byMonthDay) {
                val day = if (md > 0) md else lastDay + md + 1
                if (day in 1..lastDay) candidates.add(anchor.withDayOfMonth(day))
            }
        } else {
            // Default: same day-of-month as the start, clamped to month length.
            val day = minOf(startDate.dayOfMonth, lastDay)
            candidates.add(anchor.withDayOfMonth(day))
        }
        return candidates.filter { !it.isBefore(startDate) }.distinct().sorted()
    }

    private fun yearlyCandidates(rrule: RRule, anchor: LocalDate, startDate: LocalDate): List<LocalDate> {
        val candidates = ArrayList<LocalDate>()
        if (rrule.byDay.isNotEmpty()) {
            for (bd in rrule.byDay) {
                // Yearly BYDAY = nth weekday of the (start's) month.
                val month = startDate.month
                val firstOfMonth = anchor.withYear(anchor.year).withMonth(month.value).withDayOfMonth(1)
                val lastDay = firstOfMonth.lengthOfMonth()
                val target = bd.day.value
                val pos = if (bd.position == 0) 1 else bd.position
                val date = if (pos > 0) {
                    val first = 1 + ((target - firstOfMonth.dayOfWeek.value + 7) % 7)
                    val day = first + (pos - 1) * 7
                    if (day in 1..lastDay) firstOfMonth.withDayOfMonth(day) else null
                } else {
                    val last = lastDay - ((firstOfMonth.withDayOfMonth(lastDay).dayOfWeek.value - target + 7) % 7)
                    if (last >= 1) firstOfMonth.withDayOfMonth(last) else null
                }
                if (date != null) candidates.add(date)
            }
        } else if (rrule.byMonthDay.isNotEmpty()) {
            for (md in rrule.byMonthDay) {
                val last = anchor.lengthOfMonth()
                val day = if (md > 0) md else last + md + 1
                if (day in 1..last) candidates.add(anchor.withDayOfMonth(day))
            }
        } else {
            // Same month/day as the start; clamp for Feb 29 in non-leap years.
            val m = startDate.monthValue
            val d = startDate.dayOfMonth
            val last = anchor.withMonth(m).lengthOfMonth()
            candidates.add(anchor.withMonth(m).withDayOfMonth(minOf(d, last)))
        }
        return candidates.filter { !it.isBefore(startDate) }.distinct().sorted()
    }
}
