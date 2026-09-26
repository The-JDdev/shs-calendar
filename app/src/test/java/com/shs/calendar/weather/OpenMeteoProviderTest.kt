package com.shs.calendar.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M5 Open-Meteo tests. The fixture below is a captured, trimmed response for
 * Dhaka (23.81, 90.41) in the real API shape — nothing here is invented data
 * fed into the app; it only exercises the parser.
 */
class OpenMeteoProviderTest {

    private val payload = """
        {
          "latitude": 23.8125,
          "longitude": 90.4125,
          "timezone": "UTC",
          "current": {
            "time": "2026-09-25T06:00",
            "temperature_2m": 27.4,
            "apparent_temperature": 30.1,
            "relative_humidity_2m": 78,
            "wind_speed_10m": 9.6,
            "wind_direction_10m": 135,
            "weather_code": 2,
            "is_day": 1
          },
          "hourly": {
            "time": ["2026-09-25T06:00", "2026-09-25T07:00", "2026-09-25T08:00"],
            "temperature_2m": [27.4, 28.1, 28.9],
            "apparent_temperature": [30.1, 31.0, 32.0],
            "relative_humidity_2m": [78, 76, 74],
            "precipitation_probability": [20, 30, 55],
            "wind_speed_10m": [9.6, 10.2, 11.0],
            "weather_code": [2, 1, 61],
            "visibility": [10000.0, 9000.0, 6000.0],
            "uv_index": [0.4, 1.1, 2.5],
            "is_day": [1, 1, 1]
          },
          "daily": {
            "time": ["2026-09-25", "2026-09-26"],
            "weather_code": [2, 61],
            "temperature_2m_max": [31.2, 30.4],
            "temperature_2m_min": [24.9, 25.1],
            "precipitation_sum": [0.0, 4.2],
            "precipitation_probability_max": [30, 70],
            "wind_speed_10m_max": [14.0, 18.5],
            "uv_index_max": [6.1, 5.4],
            "sunrise": ["2026-09-25T00:23", "2026-09-26T00:24"],
            "sunset": ["2026-09-25T11:41", "2026-09-26T11:42"]
          }
        }
    """.trimIndent()

    private val fetchedAt = 1_789_000_000_000L

    @Test
    fun `parseSnapshot reads current block`() {
        val snapshot = OpenMeteoProvider.parseSnapshot(payload, 23.81, 90.41, fetchedAt)
        assertNotNull(snapshot)
        val current = snapshot!!.current
        assertEquals(27.4, current.temperatureC, 0.001)
        assertEquals(30.1, current.apparentTemperatureC, 0.001)
        assertEquals(78, current.humidityPercent)
        assertEquals(135, current.windDirectionDegrees)
        assertEquals("Partly cloudy", current.conditionLabel)
        assertTrue(!current.isRaining)
    }

    @Test
    fun `parseSnapshot preserves array ordering and per-point fields`() {
        val snapshot = OpenMeteoProvider.parseSnapshot(payload, 23.81, 90.41, fetchedAt)!!
        assertEquals(3, snapshot.hourly.size)
        assertEquals(3, snapshot.hourly[0].precipitationChancePercent)
        assertEquals(55, snapshot.hourly[2].precipitationChancePercent)
        assertEquals(6000.0, snapshot.hourly[2].visibilityMetres, 0.001)
        assertEquals(2.5, snapshot.hourly[2].uvIndex, 0.001)
        // Code 61 = light rain, so the third hour must read as rain.
        assertTrue(snapshot.hourly[2].isRaining)
        // Timestamps must be strictly increasing (parallel arrays not misaligned).
        assertTrue(snapshot.hourly[0].epochMillisUtc < snapshot.hourly[1].epochMillisUtc)
        assertTrue(snapshot.hourly[1].epochMillisUtc < snapshot.hourly[2].epochMillisUtc)
    }

    @Test
    fun `parseSnapshot reads daily extremes and sunrise sunset`() {
        val snapshot = OpenMeteoProvider.parseSnapshot(payload, 23.81, 90.41, fetchedAt)!!
        assertEquals(2, snapshot.daily.size)
        val today = snapshot.daily[0]
        assertEquals(24.9, today.minTempC, 0.001)
        assertEquals(31.2, today.maxTempC, 0.001)
        assertTrue(today.maxTempC > today.minTempC)
        assertEquals(4.2, snapshot.daily[1].precipitationSumMm, 0.001)
        // Sunrise must precede sunset on the same day.
        assertTrue(today.sunriseUtcMillis in 1 until today.sunsetUtcMillis)
    }

    @Test
    fun `buildUrl is keyless and requests the required blocks`() {
        val url = OpenMeteoProvider.buildUrl(23.81, 90.41)
        assertTrue(url.startsWith(OpenMeteoProvider.BASE_URL))
        // Keyless by contract: the service needs no token, so none may appear.
        assertTrue("no credential may be embedded", !url.contains("apikey", ignoreCase = true))
        assertTrue("no credential may be embedded", !url.contains("token", ignoreCase = true))
        assertTrue(url.contains("current="))
        assertTrue(url.contains("hourly="))
        assertTrue(url.contains("daily="))
        assertTrue(url.contains("forecast_days=7"))
        assertTrue(url.contains("latitude=23.8100"))
        assertTrue(url.contains("longitude=90.4100"))
    }

    @Test
    fun `parseSnapshot returns null on malformed payloads instead of throwing`() {
        // Every one of these must degrade to UNPARSEABLE, never an exception.
        assertNull(OpenMeteoProvider.parseSnapshot("", 23.81, 90.41, fetchedAt))
        assertNull(OpenMeteoProvider.parseSnapshot("not json at all", 23.81, 90.41, fetchedAt))
        assertNull(OpenMeteoProvider.parseSnapshot("{}", 23.81, 90.41, fetchedAt))
        // Current block present but hourly missing entirely.
        assertNull(
            OpenMeteoProvider.parseSnapshot(
                """{"current":{"temperature_2m":20.0}}""", 23.81, 90.41, fetchedAt
            )
        )
        // Hourly block present but with an empty time array.
        assertNull(
            OpenMeteoProvider.parseSnapshot(
                """{"current":{"temperature_2m":20.0},"hourly":{"time":[]},"daily":{}}""",
                23.81, 90.41, fetchedAt
            )
        )
    }

    @Test
    fun `iso timestamps parse to expected epoch millis`() {
        val midnight = OpenMeteoProvider.parseIsoUtcMillis("2026-09-25T00:00")
        assertNotNull(midnight)
        // One hour apart must be exactly 3_600_000 ms apart.
        val oneAm = OpenMeteoProvider.parseIsoUtcMillis("2026-09-25T01:00")
        assertNotNull(oneAm)
        assertEquals(3_600_000L, oneAm!! - midnight!!)
        // Seconds form and the offset form must both be accepted.
        assertNotNull(OpenMeteoProvider.parseIsoUtcMillis("2026-09-25T00:23:15"))
        assertNotNull(OpenMeteoProvider.parseIsoUtcMillis("2026-09-25T06:23:00+06:00"))
        // Garbage and empty input yield null rather than an exception.
        assertNull(OpenMeteoProvider.parseIsoUtcMillis(""))
        assertNull(OpenMeteoProvider.parseIsoUtcMillis("yesterday"))
    }

    @Test
    fun `weather codes map deterministically`() {
        assertEquals("Clear sky", WeatherCodes.describe(0))
        assertEquals("Partly cloudy", WeatherCodes.describe(2))
        assertEquals("Fog", WeatherCodes.describe(45))
        assertEquals("Thunderstorm with hail", WeatherCodes.describe(99))
        assertEquals("Unknown", WeatherCodes.describe(1234))
        assertTrue(!WeatherCodes.isRaining(0))
        assertTrue(WeatherCodes.isRaining(65))
        assertTrue(!WeatherCodes.isRaining(71))
    }
}
