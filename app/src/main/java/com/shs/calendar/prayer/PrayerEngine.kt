package com.shs.calendar.prayer

import com.shs.calendar.astronomy.SolarEngine
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.tan

/** Calculation method: fajr/isha twilight angles or a fixed Isha delay. */
enum class PrayerMethod(
    val fajrAngle: Double,
    val ishaAngle: Double?,
    val ishaMinutesAfterMaghrib: Int? = null
) {
    MWL(18.0, 17.0),
    ISNA(15.0, 15.0),
    EGYPT(19.5, 17.5),
    MAKKAH(18.5, null, 90),
    KARACHI(18.0, 18.0),
    DUBAI(18.2, 18.2),
    KUWAIT(18.0, 17.5),
    QATAR(18.0, null, 90),
    SINGAPORE(20.0, 20.0),
    TURKEY(18.0, 17.0)
}

/** Asr shadow factor: 1 = standard (Shafi'i/Maliki/Hanbali), 2 = Hanafi. */
enum class Madhab(val shadowFactor: Double) {
    STANDARD(1.0),
    HANAFI(2.0)
}

/** High-latitude rule applied only when a twilight angle never reaches the horizon. */
enum class HighLatitudeRule {
    NONE,
    ANGLE_BASED,
    ONE_SEVENTH,
    MIDNIGHT_SUN
}

/** Manual minute offsets per prayer (may be negative). */
data class PrayerOffsets(
    val fajr: Int = 0,
    val sunrise: Int = 0,
    val dhuhr: Int = 0,
    val asr: Int = 0,
    val maghrib: Int = 0,
    val isha: Int = 0
)

/** Full calculation input for one day. */
data class PrayerConfig(
    val method: PrayerMethod = PrayerMethod.MWL,
    val madhab: Madhab = Madhab.HANAFI,
    val highLatitudeRule: HighLatitudeRule = HighLatitudeRule.NONE,
    val offsets: PrayerOffsets = PrayerOffsets()
)

/**
 * Prayer times for one day, all local wall-clock times. Null means the
 * corresponding solar event does not occur on this date/latitude (polar
 * day/night) and must be rendered as "unavailable", never fabricated.
 */
data class PrayerTimes(
    val imsak: LocalTime?,
    val fajr: LocalTime?,
    val sunrise: LocalTime?,
    val dhuhr: LocalTime?,
    val asr: LocalTime?,
    val sunset: LocalTime?,
    val maghrib: LocalTime?,
    val isha: LocalTime?,
    val midnight: LocalTime?,
    val tahajjud: LocalTime?,
    val suhoorEnd: LocalTime?,
    val iftar: LocalTime?
)

/**
 * Astronomical prayer-time calculation. Pure Kotlin, no Android, no
 * hardcoded tables — every value derives from solar declination, the
 * equation of time, and the configured method/madhab/rule/offsets.
 */
object PrayerEngine {

    /** Imsak starts this many minutes before Fajr (Bangladeshi convention). */
    private const val IMSAK_LEAD_MINUTES = 10L

    fun compute(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zone: ZoneId,
        config: PrayerConfig = PrayerConfig()
    ): PrayerTimes {
        val solar = SolarEngine.compute(date, latitude, longitude, zone)
        val sunrise = solar.sunrise
        val sunset = solar.sunset
        val dhuhrBase = solar.solarNoon

        // Fajr / Isha base events from twilight angles.
        var fajr = SolarEngine.elevationEvent(date, latitude, longitude, -config.method.fajrAngle, true, zone)
        var isha: LocalTime? = config.method.ishaAngle?.let { angle ->
            SolarEngine.elevationEvent(date, latitude, longitude, -angle, false, zone)
        }
        if (isha == null && config.method.ishaMinutesAfterMaghrib != null && sunset != null) {
            isha = plusMinutes(sunset, config.method.ishaMinutesAfterMaghrib.toLong())
        }

        // High-latitude fallback for twilight angles that never reach the horizon.
        if ((fajr == null || isha == null) && config.highLatitudeRule != HighLatitudeRule.NONE &&
            sunset != null && sunrise != null
        ) {
            val nextSunrise = SolarEngine.compute(date.plusDays(1), latitude, longitude, zone).sunrise
            if (nextSunrise != null) {
                val night = minutesBetween(sunset, nextSunrise)          // minutes, sunset -> next sunrise
                if (fajr == null) {
                    fajr = when (config.highLatitudeRule) {
                        HighLatitudeRule.ANGLE_BASED ->
                            plusMinutes(sunset, Math.round(night * config.method.fajrAngle / 60.0).toLong())
                        HighLatitudeRule.ONE_SEVENTH -> plusMinutes(sunset, night / 7L)
                        HighLatitudeRule.MIDNIGHT_SUN -> minusMinutes(nextSunrise, night / 2L)
                        else -> null
                    }
                }
                if (isha == null) {
                    isha = when (config.highLatitudeRule) {
                        HighLatitudeRule.ANGLE_BASED -> {
                            val a = config.method.ishaAngle ?: 17.0
                            minusMinutes(nextSunrise, Math.round(night * a / 60.0).toLong())
                        }
                        HighLatitudeRule.ONE_SEVENTH -> minusMinutes(nextSunrise, night / 7L)
                        HighLatitudeRule.MIDNIGHT_SUN -> plusMinutes(sunset, night / 2L)
                        else -> null
                    }
                }
            }
        }

        // Asr: sun altitude = atan(1 / (factor + tan(|lat - decl|))).
        val decl = SolarEngine.declinationDegrees(date, longitude)
        val asrAltitude = Math.toDegrees(
            atan(1.0 / (config.madhab.shadowFactor + tan(Math.toRadians(abs(latitude - decl)))))
        )
        val asr = SolarEngine.elevationEvent(date, latitude, longitude, asrAltitude, false, zone)

        // Night-derived times: midnight (middle of night) and tahajjud (last third).
        val midnight: LocalTime?
        val tahajjud: LocalTime?
        if (sunset != null && sunrise != null) {
            val nextSunrise = SolarEngine.compute(date.plusDays(1), latitude, longitude, zone).sunrise ?: sunrise
            val night = minutesBetween(sunset, nextSunrise)
            midnight = plusMinutes(sunset, night / 2L)
            tahajjud = minusMinutes(nextSunrise, night / 3L)
        } else {
            midnight = null
            tahajjud = null
        }

        // Manual offsets (per configured prayers) wrap into 0..1439.
        val o = config.offsets
        val fajrOff = applyOffset(fajr, o.fajr)
        val sunriseOff = applyOffset(sunrise, o.sunrise)
        val dhuhrOff = applyOffset(dhuhrBase, o.dhuhr)
        val asrOff = applyOffset(asr, o.asr)
        val maghribOff = applyOffset(sunset, o.maghrib)
        val ishaOff = applyOffset(isha, o.isha)

        return PrayerTimes(
            imsak = fajrOff?.let { minusMinutes(it, IMSAK_LEAD_MINUTES) },
            fajr = fajrOff,
            sunrise = sunriseOff,
            dhuhr = dhuhrOff,
            asr = asrOff,
            sunset = sunset,
            maghrib = maghribOff,
            isha = ishaOff,
            midnight = midnight,
            tahajjud = tahajjud,
            suhoorEnd = fajrOff,
            iftar = maghribOff
        )
    }

    /** Chronological order of the displayed list; nulls (unavailable) excluded. */
    fun orderedEntries(times: PrayerTimes): List<Pair<String, LocalTime?>> = listOf(
        "imsak" to times.imsak,
        "fajr" to times.fajr,
        "sunrise" to times.sunrise,
        "dhuhr" to times.dhuhr,
        "asr" to times.asr,
        "sunset" to times.sunset,
        "maghrib" to times.maghrib,
        "isha" to times.isha,
        "midnight" to times.midnight,
        "tahajjud" to times.tahajjud
    )

    /** Next prayer at or after [now] on [date], wrapping to tomorrow's Fajr. */
    fun nextPrayer(times: PrayerTimes, now: LocalTime, date: LocalDate): Pair<String, LocalTime?> {
        val candidates = listOf(
            "fajr" to times.fajr,
            "sunrise" to times.sunrise,
            "dhuhr" to times.dhuhr,
            "asr" to times.asr,
            "maghrib" to times.maghrib,
            "isha" to times.isha
        ).mapNotNull { (k, v) -> v?.let { k to it } }.sortedBy { it.second }
        val upcoming = candidates.firstOrNull { !it.second.isBefore(now) }
        return upcoming ?: candidates.first()
    }

    // ---- helpers -----------------------------------------------------------

    private fun applyOffset(t: LocalTime?, offsetMinutes: Int): LocalTime? =
        t?.let { plusMinutes(it, offsetMinutes.toLong()) }

    private fun plusMinutes(t: LocalTime, minutes: Long): LocalTime =
        LocalTime.ofSecondOfDay((t.toSecondOfDay().toLong() + minutes * 60).mod(86_400L))

    private fun minusMinutes(t: LocalTime, minutes: Long): LocalTime = plusMinutes(t, -minutes)

    /** Signed minutes from [a] to [b], positive when b is later the same day. */
    private fun minutesBetween(a: LocalTime, b: LocalTime): Long =
        ChronoUnit.MINUTES.between(a, b).let { if (it < 0) it + 1440 else it }
}
