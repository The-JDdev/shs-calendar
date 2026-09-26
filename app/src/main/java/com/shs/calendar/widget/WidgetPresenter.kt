package com.shs.calendar.widget

import com.shs.calendar.astronomy.MoonPhase
import com.shs.calendar.calendar.BengaliEngine
import com.shs.calendar.calendar.HijriEngine
import com.shs.calendar.prayer.PrayerTimes
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pure presentation logic for the M7 home-screen widgets.
 *
 * Deliberately free of any Android dependency: widgets render through
 * RemoteViews, but every string they show is decided here so the logic can be
 * unit-tested on the JVM and reused by any provider.
 */
object WidgetPresenter {

    /** Shown when a value is genuinely unavailable (no permission, no cache). */
    const val DASH_TIME = "--:--"
    const val DASH_TEXT = "—"

    private val TIME_HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    fun formatTime(time: LocalTime?): String =
        time?.format(TIME_HM) ?: DASH_TIME

    /** "Friday, 26 December 2026" — the today-card header line. */
    fun formatTodayLong(date: LocalDate): String {
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val month = date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        return "$weekday, ${date.dayOfMonth} $month ${date.year}"
    }

    /** Short weekday, e.g. "Fri" — calendar widget header and agenda rows. */
    fun formatWeekdayShort(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    /** Bengali date, e.g. "26 Poush 1433". */
    fun formatBengali(b: BengaliEngine.BengaliDate): String =
        "${b.day} ${b.monthName} ${b.year}"

    /** Hijri date, e.g. "14 Rabi I 1448". */
    fun formatHijri(h: HijriEngine.HijriDate): String =
        "${h.day} ${h.monthName} ${h.year}"

    /**
     * Moon phase line: name plus illumination, e.g. "Waxing Gibbous · 82%".
     * Illumination comes from [MoonPhase] — never a hardcoded table.
     */
    fun formatMoonPhase(date: LocalDate): String {
        val phase = MoonPhase.phase(date)
        val percent = Math.round(phase.illumination * 100.0).toInt()
        return "${phase.name.label} · $percent%"
    }

    /**
     * Next prayer strictly after [now] on [times], as label to formatted time.
     *
     * Returns null once the day is over so callers can say so honestly rather
     * than invent a time. Null entries in [times] (polar day/night) are skipped.
     */
    fun nextPrayer(times: PrayerTimes, now: LocalTime): Pair<String, String>? {
        val order = listOf(
            "Imsak" to times.imsak, "Fajr" to times.fajr, "Sunrise" to times.sunrise,
            "Dhuhr" to times.dhuhr, "Asr" to times.asr,
            "Maghrib" to times.maghrib, "Isha" to times.isha
        )
        for ((name, time) in order) {
            if (time != null && time.isAfter(now)) return name to formatTime(time)
        }
        return null
    }

    /** Prayer rows for the prayer-times widget, in canonical order. */
    fun prayerRows(times: PrayerTimes): List<Pair<String, String>> = listOf(
        "Fajr" to times.fajr, "Sunrise" to times.sunrise, "Dhuhr" to times.dhuhr,
        "Asr" to times.asr, "Maghrib" to times.maghrib, "Isha" to times.isha
    ).map { it.first to formatTime(it.second) }
}
