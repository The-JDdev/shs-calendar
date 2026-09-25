package com.shs.calendar.astronomy

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NOAA solar engine sanity: the SPEC's Dhaka validation window for
 * 2026-09-25, monotonic seasonal behaviour and polar degenerate cases.
 * No hardcoded tables — values are asserted as plausible windows.
 */
class SolarEngineTest {

    private val zone: ZoneId = ZoneId.of("Asia/Dhaka")
    private val dhakaLat = 23.8103
    private val dhakaLon = 90.4125
    private val anchor: LocalDate = LocalDate.of(2026, 9, 25)

    private fun minutes(t: LocalTime?): Double? =
        t?.let { it.hour * 60.0 + it.minute + it.second / 60.0 }

    @Test
    fun dhaka_anchor_date_sunset_in_plausible_window() {
        val day = SolarEngine.compute(anchor, dhakaLat, dhakaLon, zone)
        val rise = minutes(day.sunrise)
        val set = minutes(day.sunset)
        assertNotNull("sunrise present at Dhaka equinox", rise)
        assertNotNull("sunset present at Dhaka equinox", set)
        // Mid-late September at 23.8°N: sunrise ≈ 05:50–06:10, sunset ≈ 17:55–18:15 local.
        assertTrue("sunrise 05:30–06:30, was $rise", rise!! in 330.0..390.0)
        assertTrue("sunset 17:30–18:30, was $set", set!! in 1050.0..1110.0)
        // Sunset strictly after sunrise; day length ≈ 12 h ± 45 min.
        assertTrue(set > rise)
        val len = day.dayLengthMinutes
        assertNotNull(len)
        assertTrue("day length 675–765 min, was $len", len!! in 675.0..765.0)
    }

    @Test
    fun solar_noon_and_twilight_ordering_is_consistent() {
        val day = SolarEngine.compute(anchor, dhakaLat, dhakaLon, zone)
        // civil ⊆ ... ordering: dawn times ascend in depth, dusk descend.
        val civilDawn = minutes(day.civilDawn)!!
        val nautDawn = minutes(day.nauticalDawn)!!
        val astroDawn = minutes(day.astronomicalDawn)!!
        val civilDusk = minutes(day.civilDusk)!!
        val nautDusk = minutes(day.nauticalDusk)!!
        val astroDusk = minutes(day.astronomicalDusk)!!
        // Morning: astronomical (deepest) dawn happens first, civil last.
        assertTrue("astronomical dawn before nautical", astroDawn < nautDawn)
        assertTrue("nautical dawn before civil", nautDawn < civilDawn)
        assertTrue("civil dusk before nautical dusk", civilDusk < nautDusk)
        assertTrue("nautical dusk before astronomical dusk", nautDusk < astroDusk)
        // Solar noon sits between sunrise and sunset.
        val noon = minutes(day.solarNoon)!!
        assertTrue(noon in minutes(day.sunrise)!!..minutes(day.sunset)!!)
    }

    @Test
    fun summer_day_longer_than_winter_day_in_dhaka() {
        val june = SolarEngine.compute(LocalDate.of(2026, 6, 21), dhakaLat, dhakaLon, zone)
        val dec = SolarEngine.compute(LocalDate.of(2026, 12, 21), dhakaLat, dhakaLon, zone)
        assertTrue(
            "June solstice day longer than December solstice",
            june.dayLengthMinutes!! > dec.dayLengthMinutes!!
        )
        // Both still comfortably above 10 h and below 14 h at 23.8°N.
        assertTrue(june.dayLengthMinutes!! in 600.0..900.0)
        assertTrue(dec.dayLengthMinutes!! in 600.0..900.0)
    }

    @Test
    fun polar_night_yields_null_events_not_fabricated_times() {
        // Longyearbyen (78.2°N) on the December solstice: sun never rises.
        val svalbard = SolarEngine.compute(
            LocalDate.of(2026, 12, 21), 78.22, 15.63, ZoneId.of("Europe/Oslo")
        )
        assertNull(svalbard.sunrise)
        assertNull(svalbard.sunset)
        assertNull(svalbard.dayLengthMinutes)
        assertTrue(
            SolarEngine.neverRises(
                LocalDate.of(2026, 12, 21), 78.22, 15.63, ZoneId.of("Europe/Oslo")
            )
        )
    }

    @Test
    fun midnight_sun_yields_null_set_but_stable_noon() {
        // Longyearbyen on the June solstice: sun stays above the horizon.
        val svalbard = SolarEngine.compute(
            LocalDate.of(2026, 6, 21), 78.22, 15.63, ZoneId.of("Europe/Oslo")
        )
        assertNull(svalbard.sunrise)
        assertNull(svalbard.sunset)
        // Solar noon still exists as a well-defined instant.
        assertNotNull(svalbard.solarNoon)
        assertFalse(SolarEngine.neverRises(
            LocalDate.of(2026, 6, 21), 78.22, 15.63, ZoneId.of("Europe/Oslo")
        ))
    }

    @Test
    fun golden_hour_brackets_sunset() {
        val day = SolarEngine.compute(anchor, dhakaLat, dhakaLon, zone)
        val gStart = minutes(day.goldenHourStart)
        val gEnd = minutes(day.goldenHourEnd)
        assertNotNull(gStart)
        assertNotNull(gEnd)
        // Golden hour runs from 6° elevation (before sunset) to sunset itself.
        assertTrue(gStart!! < gEnd!!)
        assertTrue(gEnd!! <= minutes(day.sunset)!! + 1.0)
    }
}
