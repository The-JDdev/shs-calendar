package com.shs.calendar.astronomy

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * NOAA solar calculations (pure Kotlin, no Android, no hardcoded times).
 * Reference: NOAA Solar Calculator spreadsheet equations, based on
 * Astronomical Almanac algorithms.
 *
 * All results are pure functions of (date, latitude, longitude, timezone).
 * When the sun never reaches the requested elevation (polar day/night),
 * the corresponding event is null — callers must render an
 * "unavailable" state rather than fabricate a time.
 */
object SolarEngine {

    /** Degenerate/edge results are expressed as null, not sentinel strings. */
    data class SolarDay(
        val sunrise: LocalTime?,
        val sunset: LocalTime?,
        val solarNoon: LocalTime,
        val civilDawn: LocalTime?,
        val civilDusk: LocalTime?,
        val nauticalDawn: LocalTime?,
        val nauticalDusk: LocalTime?,
        val astronomicalDawn: LocalTime?,
        val astronomicalDusk: LocalTime?,
        val goldenHourStart: LocalTime?,
        val goldenHourEnd: LocalTime?,
        val dayLengthMinutes: Double?
    )

    private const val DEG = 180.0 / PI
    private const val RAD = PI / 180.0

    /** Julian day for a given local date at [hour] UT (approx, adequate for NOAA). */
    private fun julianDay(year: Int, month: Int, day: Int, hourUT: Double): Double {
        var y = year
        var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5 + hourUT / 24.0
    }

    /** Julian centuries since J2000.0 for the given JD. */
    private fun julianCentury(jd: Double): Double = (jd - 2451545.0) / 36525.0

    private fun geomMeanLongSun(t: Double): Double {
        var l = 280.46646 + t * (36000.76983 + 0.0003032 * t)
        l %= 360.0
        if (l < 0) l += 360.0
        return l
    }

    private fun geomMeanAnomalySun(t: Double): Double =
        357.52911 + t * (35999.05029 - 0.0001537 * t)

    private fun eccentricityEarthOrbit(t: Double): Double =
        0.016708634 - t * (0.000042037 + 0.0000001267 * t)

    private fun sunEqOfCenter(t: Double): Double {
        val m = geomMeanAnomalySun(t) * RAD
        return sin(m) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * m) * (0.019993 - 0.000101 * t) +
            sin(3 * m) * 0.000289
    }

    private fun sunApparentLong(t: Double): Double {
        val trueLong = geomMeanLongSun(t) + sunEqOfCenter(t)
        val omega = 125.04 - 1934.136 * t
        return trueLong - 0.00569 - 0.00478 * sin(omega * RAD)
    }

    private fun meanObliquityOfEcliptic(t: Double): Double {
        val seconds = 21.448 - t * (46.8150 + t * (0.00059 - t * 0.001813))
        return 23.0 + (26.0 + seconds / 60.0) / 60.0
    }

    private fun obliquityCorrection(t: Double): Double {
        val e0 = meanObliquityOfEcliptic(t)
        val omega = 125.04 - 1934.136 * t
        return e0 + 0.00256 * cos(omega * RAD)
    }

    /** Sun declination in degrees for Julian century [t]. */
    private fun sunDeclination(t: Double): Double {
        val e = obliquityCorrection(t) * RAD
        val lambda = sunApparentLong(t) * RAD
        return asin(sin(e) * sin(lambda)) * DEG
    }

    /** Equation of time in minutes for Julian century [t]. */
    private fun equationOfTime(t: Double): Double {
        val epsilon = obliquityCorrection(t) * RAD
        val l0 = geomMeanLongSun(t) * RAD
        val e = eccentricityEarthOrbit(t)
        val m = geomMeanAnomalySun(t) * RAD
        var y = tan(epsilon / 2.0)
        y *= y
        val eTime = y * sin(2 * l0) - 2 * e * sin(m) +
            4 * e * y * sin(m) * cos(2 * l0) -
            0.5 * y * y * sin(4 * l0) - 1.25 * e * e * sin(2 * m)
        return eTime * 4.0 // minutes
    }

    /** Hour angle (degrees) of the sun at elevation [angleDeg]; NaN if never reached. */
    private fun hourAngle(latDeg: Double, angleDeg: Double, declDeg: Double): Double {
        val lat = latDeg * RAD
        val decl = declDeg * RAD
        val cosH = (sin(angleDeg * RAD) - sin(lat) * sin(decl)) / (cos(lat) * cos(decl))
        if (cosH < -1.0 || cosH > 1.0) return Double.NaN
        return acosDeg(cosH)
    }

    private fun acosDeg(x: Double): Double = kotlin.math.acos(x) * DEG

    /**
     * NOAA event time in minutes UT for a solar event at elevation [angleDeg]
     * with [isSunrise] choosing the +/- branch. NaN when unreachable.
     */
    private fun eventMinutesUT(
        date: LocalDate,
        latDeg: Double,
        lonDeg: Double,
        angleDeg: Double,
        isSunrise: Boolean
    ): Double {
        // Work at local noon, expressed as UT hours, for a stable base.
        val utcHourNoon = 12.0 - lonDeg / 15.0
        val jd = julianDay(date.year, date.monthValue, date.dayOfMonth, utcHourNoon)
        val t = julianCentury(jd)
        val noonMinutes = 720.0 - 4.0 * lonDeg - equationOfTime(t)
        val decl = sunDeclination(t)
        val ha = hourAngle(latDeg, angleDeg, decl)
        if (ha.isNaN()) return Double.NaN
        return if (isSunrise) noonMinutes - 4.0 * ha else noonMinutes + 4.0 * ha
    }

    /** Solar noon (minutes UT) for the date. */
    private fun solarNoonMinutesUT(date: LocalDate, lonDeg: Double): Double {
        val utcHourNoon = 12.0 - lonDeg / 15.0
        val jd = julianDay(date.year, date.monthValue, date.dayOfMonth, utcHourNoon)
        val t = julianCentury(jd)
        return 720.0 - 4.0 * lonDeg - equationOfTime(t)
    }

    /** Convert minutes UT on [date] to a local time in [zone]; null if unreachable. */
    private fun minutesToLocal(
        date: LocalDate,
        minutesUT: Double,
        lonDeg: Double,
        zone: ZoneId
    ): LocalTime? {
        if (minutesUT.isNaN()) return null
        val baseUtcMidnight = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
        val instant = baseUtcMidnight.plusMillis((minutesUT * 60_000).toLong())
        return LocalTime.from(instant.atZone(zone))
    }

    /**
     * Compute the full solar day for [date] at [lat]/[lon] in [zone].
     * Returns null events for anything the sun never reaches.
     */
    fun compute(date: LocalDate, lat: Double, lon: Double, zone: ZoneId): SolarDay {
        val noonUT = solarNoonMinutesUT(date, lon)

        fun riseSet(angleDeg: Double): Pair<LocalTime?, LocalTime?> {
            val riseUT = eventMinutesUT(date, lat, lon, angleDeg, isSunrise = true)
            val setUT = eventMinutesUT(date, lat, lon, angleDeg, isSunrise = false)
            return minutesToLocal(date, riseUT, lon, zone) to minutesToLocal(date, setUT, lon, zone)
        }

        val (sunrise, sunset) = riseSet(-0.833)   // official, refraction included
        val (civilDawn, civilDusk) = riseSet(-6.0)
        val (nautDawn, nautDusk) = riseSet(-12.0)
        val (astroDawn, astroDusk) = riseSet(-18.0)
        // Golden hour: sun between 6° above horizon and set.
        val (goldenStart, goldenEnd) = riseSet(6.0)

        val solarNoon = minutesToLocal(date, noonUT, lon, zone)
            ?: LocalTime.NOON

        val dayLength = if (sunrise != null && sunset != null) {
            var diff = sunset.toSecondOfDay() / 60.0 - sunrise.toSecondOfDay() / 60.0
            if (diff < 0) diff += 24 * 60.0
            diff
        } else null

        return SolarDay(
            sunrise = sunrise,
            sunset = sunset,
            solarNoon = solarNoon,
            civilDawn = civilDawn,
            civilDusk = civilDusk,
            nauticalDawn = nautDawn,
            nauticalDusk = nautDusk,
            astronomicalDawn = astroDawn,
            astronomicalDusk = astroDusk,
            goldenHourStart = goldenStart,
            goldenHourEnd = goldenEnd,
            dayLengthMinutes = dayLength
        )
    }

    /** True when sun stays below horizon all day (polar night at this angle). */
    fun neverRises(date: LocalDate, lat: Double, lon: Double, zone: ZoneId): Boolean =
        compute(date, lat, lon, zone).sunrise == null &&
        compute(date, lat, lon, zone).sunset == null
}
