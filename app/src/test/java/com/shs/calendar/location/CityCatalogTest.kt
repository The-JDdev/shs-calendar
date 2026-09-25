package com.shs.calendar.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M1 location layer: offline city catalog, tz detection, great-circle math. */
class CityCatalogTest {

    @Test
    fun catalogCoversMajorRegions() {
        assertTrue("catalog should list 100+ cities", CityCatalog.all.size >= 100)
        val countries = CityCatalog.all.map { it.country }.toSet()
        assertTrue("Bangladesh present", "Bangladesh" in countries)
        assertTrue("India present", "India" in countries)
        assertTrue("Saudi Arabia present", "Saudi Arabia" in countries)
        assertTrue("United States present", "United States" in countries)
    }

    @Test
    fun allCitiesHaveValidCoordinatesAndTz() {
        CityCatalog.all.forEach { c ->
            assertTrue("lat in range for ${c.name}", c.latitude in -90.0..90.0)
            assertTrue("lon in range for ${c.name}", c.longitude in -180.0..180.0)
            assertTrue("tz valid for ${c.name}: ${c.timeZone}", TimeZones.isValid(c.timeZone))
        }
    }

    @Test
    fun searchMatchesNameCaseInsensitive() {
        val dhaka = CityCatalog.search("dhaka")
        assertTrue("dhaka search finds Dhaka", dhaka.any { it.name == "Dhaka" })
        val bd = CityCatalog.search("bangladesh")
        assertTrue("country search works", bd.size >= 10)
        val dhak = CityCatalog.search("dhak")
        assertTrue("prefix ranks first", dhak.firstOrNull()?.name?.startsWith("Dhak") == true)
        assertTrue("empty query returns all", CityCatalog.search("").size == CityCatalog.all.size)
        assertTrue("no match returns empty", CityCatalog.search("zzzzqq").isEmpty())
    }

    @Test
    fun nearestFindsClosestCity() {
        val near = CityCatalog.nearest(23.81, 90.41)
        assertEquals("Dhaka", near?.name)
        val distance = CityCatalog.distanceKm(23.8103, 90.4125, near!!.latitude, near.longitude)
        assertTrue("nearest within 10km", distance < 10.0)
    }

    @Test
    fun haversineKnownDistanceDhakaToMakkah() {
        // Dhaka -> Makkah great-circle ~ 4,970 km (well-known ballpark).
        val d = CityCatalog.distanceKm(23.8103, 90.4125, 21.4225, 39.8262)
        assertTrue("Dhaka-Makkah 4500-5500km, was $d", d in 4500.0..5500.0)
        // Zero distance
        assertEquals(0.0, CityCatalog.distanceKm(10.0, 20.0, 10.0, 20.0), 1e-6)
    }

    @Test
    fun timezoneValidation() {
        assertTrue(TimeZones.isValid("Asia/Dhaka"))
        assertTrue(TimeZones.isValid("UTC"))
        assertFalse(TimeZones.isValid("Not/AZone"))
        assertFalse(TimeZones.isValid(""))
        assertFalse(TimeZones.isValid(null))
    }

    @Test
    fun deviceZoneIsUsable() {
        assertTrue(TimeZones.isValid(TimeZones.deviceZone()))
    }

    @Test
    fun longitudeBasedOffsetIsWholeHours() {
        assertEquals(6 * 3600, TimeZones.offsetAt(23.8, 90.4))   // ~90E -> +6h
        assertEquals(0, TimeZones.offsetAt(0.0, 0.0))
        assertEquals(-5 * 3600, TimeZones.offsetAt(40.0, -75.0)) // ~75W -> -5h
    }

    @Test
    fun detectReturnsValidZoneForKnownSpots() {
        val dhakaTz = TimeZones.detect(23.8103, 90.4125, "UTC")
        assertTrue("Dhaka detect valid: $dhakaTz", TimeZones.isValid(dhakaTz))
        val nyTz = TimeZones.detect(40.7128, -74.0060, "UTC")
        assertTrue("NYC detect valid: $nyTz", TimeZones.isValid(nyTz))
        // Mid-ocean spot: detect either matches a plausible zone (e.g. UTC-2)
        // or falls back — the contract is only "always a valid zone id".
        val ocean = TimeZones.detect(0.0, -30.0, "Asia/Dhaka")
        assertTrue("ocean detect valid: $ocean", TimeZones.isValid(ocean))
    }

    @Test
    fun validTimeZoneCitiesFiltersNeverEmpty() {
        assertNotNull(CityCatalog.validTimeZoneCities())
        assertTrue(CityCatalog.validTimeZoneCities().size >= 100)
    }
}
