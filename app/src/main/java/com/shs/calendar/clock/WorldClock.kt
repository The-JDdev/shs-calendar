package com.shs.calendar.clock

import com.shs.calendar.location.TimeZones
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * M4: world clock model.
 *
 * Pure Kotlin (no Android dependencies) so it is directly unit-testable.
 * Zone validity is delegated to the shared [TimeZones] helper rather than a
 * duplicated list, keeping one source of truth for zone ids.
 */
object WorldClock {

    /** Resolved time in one watched zone. */
    data class Entry(
        val zoneId: String,
        val time: ZonedDateTime,
        val offsetLabel: String
    )

    private val TIME_H24: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val TIME_H12: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val DATE_FMT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)

    /** True when [zoneId] is a usable IANA zone. */
    fun isValid(zoneId: String): Boolean = TimeZones.isValid(zoneId)

    /** Current time in [zoneId]; falls back to the device zone when invalid. */
    fun entryAt(zoneId: String, now: Instant = Instant.now()): Entry {
        val zone = if (TimeZones.isValid(zoneId)) {
            ZoneId.of(zoneId)
        } else {
            ZoneId.of(TimeZones.deviceZone())
        }
        val zdt = now.atZone(zone)
        return Entry(zone.id, zdt, offsetLabel(zdt))
    }

    /**
     * UTC offset rendered as "UTC+06:00" or "UTC+00:00".
     *
     * ZoneOffset.getId() returns "Z" for zero offset, so the id is not
     * usable here; the seconds are formatted explicitly instead.
     */
    fun offsetLabel(zdt: ZonedDateTime): String {
        val total = zdt.offset.totalSeconds
        val sign = if (total < 0) "-" else "+"
        val abs = kotlin.math.abs(total)
        val hours = abs / 3600
        val minutes = (abs % 3600) / 60
        return "UTC$sign%02d:%02d".format(Locale.US, hours, minutes)
    }

    /** Clock time, honouring the 12/24 hour setting. */
    fun formatTime(zdt: ZonedDateTime, use24Hour: Boolean): String =
        (if (use24Hour) TIME_H24 else TIME_H12).format(zdt)

    /** Full date line, e.g. "Sat, 26 Sep 2026". */
    fun formatDate(zdt: ZonedDateTime): String = DATE_FMT.format(zdt)

    /** Local date in the zone, used to flag "today" in the city list. */
    fun localDate(zdt: ZonedDateTime): LocalDate = zdt.toLocalDate()
}
