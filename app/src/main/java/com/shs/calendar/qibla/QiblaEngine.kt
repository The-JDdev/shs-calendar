package com.shs.calendar.qibla

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Qibla direction engine — pure math, no Android dependencies.
 *
 * Computes the initial great-circle bearing and distance from the user's
 * coordinates to the Kaaba using the standard spherical formulas
 * (WGS-84 mean Earth radius). All values are computed; nothing is hardcoded.
 *
 * Kaaba coordinates per brief: 21.4225° N, 39.8262° E.
 */
object QiblaEngine {

    const val KAABA_LAT = 21.4225
    const val KAABA_LON = 39.8262

    /** Mean Earth radius (km), IUGG recommended mean radius. */
    private const val EARTH_RADIUS_KM = 6371.0088

    /**
     * Initial great-circle bearing from (lat, lon) to the Kaaba, in degrees
     * clockwise from true north, normalized to [0, 360).
     *
     * Returns 0.0 when standing on the Kaaba itself (bearing undefined).
     */
    fun bearingToKaaba(lat: Double, lon: Double): Double {
        val phi1 = Math.toRadians(lat)
        val phi2 = Math.toRadians(KAABA_LAT)
        val dLon = Math.toRadians(KAABA_LON - lon)
        if (phi1 == phi2 && dLon == 0.0) return 0.0
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        val deg = Math.toDegrees(atan2(y, x))
        return (deg + 360.0) % 360.0
    }

    /** Great-circle distance from (lat, lon) to the Kaaba in kilometres. */
    fun distanceToKaabaKm(lat: Double, lon: Double): Double {
        val phi1 = Math.toRadians(lat)
        val phi2 = Math.toRadians(KAABA_LAT)
        val dPhi = Math.toRadians(KAABA_LAT - lat)
        val dLon = Math.toRadians(KAABA_LON - lon)
        val a = sin(dPhi / 2) * sin(dPhi / 2) +
            cos(phi1) * cos(phi2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    /**
     * Shortest signed difference between [target] and [current], both in
     * degrees — used by the compass view to rotate the needle the easy way.
     * Result is in (-180, 180].
     */
    fun shortestDelta(current: Double, target: Double): Double {
        var d = (target - current) % 360.0
        if (d > 180.0) d -= 360.0
        if (d <= -180.0) d += 360.0
        return d
    }
}
