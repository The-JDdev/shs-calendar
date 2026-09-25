package com.shs.calendar.location

import java.time.ZoneId
import java.time.ZoneOffset

/**
 * IANA timezone helpers for the location layer (M1).
 *
 * Pure Kotlin/JVM — unit-testable without an Android device.
 */
object TimeZones {

    /** True when [id] is a valid IANA timezone available on this runtime. */
    fun isValid(id: String?): Boolean {
        if (id.isNullOrBlank()) return false
        return try {
            ZoneId.of(id)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Best-effort timezone for a coordinate: matches the UTC offset observed
     * at [latitude]/[longitude] against all supported zone rules, preferring
     * the zone whose current offset AND DST offset both agree with the spot.
     *
     * Returns [fallback] (typically the device zone) when nothing matches.
     */
    fun detect(latitude: Double, longitude: Double, fallback: String): String {
        val targetOffset = offsetAt(latitude, longitude)
        val now = java.time.Instant.now()
        var best: String? = null
        for (zone in ZoneId.getAvailableZoneIds()) {
            val id = try { ZoneId.of(zone) } catch (e: Exception) { continue }
            if (id.rules.isFixedOffset) continue
            val jan = id.rules.getOffset(java.time.LocalDate.of(2020, 1, 1)
                .atStartOfDay(id).toInstant())
            val jul = id.rules.getOffset(java.time.LocalDate.of(2020, 7, 1)
                .atStartOfDay(id).toInstant())
            if (jan.totalSeconds == targetOffset && jul.totalSeconds == targetOffset) {
                // Prefer a zone already consistent "now" as a tie-breaker.
                val current = id.rules.getOffset(now).totalSeconds
                if (current == targetOffset) return zone
                if (best == null) best = zone
            }
        }
        return best ?: fallback
    }

    /** UTC offset in seconds at a coordinate, estimated by longitude. */
    fun offsetAt(latitude: Double, longitude: Double): Int {
        val hours = Math.round(longitude / 15.0).toInt()
        return hours * 3600
    }

    /** Device zone id, falling back to UTC when unavailable. */
    fun deviceZone(): String =
        runCatching { ZoneId.systemDefault().id }.getOrDefault(ZoneOffset.UTC.id)
}
