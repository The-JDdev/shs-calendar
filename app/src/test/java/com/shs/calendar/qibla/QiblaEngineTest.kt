package com.shs.calendar.qibla

import com.shs.calendar.qibla.QiblaEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Qibla bearing/distance against independently published values
 * (tolerances generous to spherical-model differences, ±3° / ±60 km).
 */
class QiblaEngineTest {

    @Test
    fun `dhaka bearing is west-north-west toward kaaba`() {
        // Cross-checked: two independent bearing formulations + rhumb-line
        // geometry (Mecca 2.4 deg S / 50.6 deg W of Dhaka; great circle bows
        // poleward) -> 277.6. Same formula matches published London/NYC/Sydney.
        val b = QiblaEngine.bearingToKaaba(23.8103, 90.4125)
        assertEquals(277.6, b, 3.0)
    }

    @Test
    fun `london bearing is south-east toward kaaba`() {
        val b = QiblaEngine.bearingToKaaba(51.5074, -0.1278)
        assertEquals(118.9, b, 3.0)
    }

    @Test
    fun `new york bearing is north-east toward kaaba`() {
        val b = QiblaEngine.bearingToKaaba(40.7128, -74.006)
        assertEquals(58.4, b, 3.0)
    }

    @Test
    fun `sydney bearing is west toward kaaba`() {
        val b = QiblaEngine.bearingToKaaba(-33.8688, 151.2093)
        assertEquals(277.5, b, 3.0)
    }

    @Test
    fun `bearing from kaaba itself is zero`() {
        assertEquals(0.0, QiblaEngine.bearingToKaaba(21.4225, 39.8262), 1e-9)
    }

    @Test
    fun `bearing always normalized to 0-360`() {
        val samples = listOf(
            0.0 to 0.0, 89.9 to 179.9, -33.9 to 151.2,
            64.0 to -21.9, -90.0 to 0.0, 0.0 to 180.0
        )
        for ((lat, lon) in samples) {
            val b = QiblaEngine.bearingToKaaba(lat, lon)
            assertTrue("bearing $b out of range for ($lat,$lon)", b >= 0.0 && b < 360.0)
        }
    }

    @Test
    fun `dhaka to kaaba distance is about 5130 km`() {
        val d = QiblaEngine.distanceToKaabaKm(23.8103, 90.4125)
        assertEquals(5170.0, d, 50.0)
    }

    @Test
    fun `kaaba to kaaba distance is zero`() {
        assertEquals(0.0, QiblaEngine.distanceToKaabaKm(21.4225, 39.8262), 1e-6)
    }

    @Test
    fun `shortest delta picks short way around`() {
        assertEquals(10.0, QiblaEngine.shortestDelta(350.0, 0.0), 1e-9)
        assertEquals(-20.0, QiblaEngine.shortestDelta(10.0, 350.0), 1e-9)
        assertEquals(180.0, QiblaEngine.shortestDelta(0.0, 180.0), 1e-9)
        assertEquals(180.0, QiblaEngine.shortestDelta(0.0, -180.0), 1e-9)
    }
}
