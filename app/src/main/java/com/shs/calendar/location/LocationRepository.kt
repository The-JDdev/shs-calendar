package com.shs.calendar.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Play-services-free location layer (M1): plain [LocationManager] GPS/NETWORK
 * with passive last-known fallback.
 *
 * Location is always OPTIONAL: callers must handle a null result and fall
 * back to the manual picker. No crash when providers are off or permission
 * is denied.
 */
data class ResolvedLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String,
    val timeZone: String
)

class LocationRepository(context: Context) {

    private val appContext = context.applicationContext

    private val manager: LocationManager
        get() = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasPermission(): Boolean = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ).any {
        ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
    }

    fun providersEnabled(): Boolean =
        runCatching {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)

    /** Best-effort last known fix from any enabled provider. */
    @SuppressLint("MissingPermission")
    fun lastKnown(): ResolvedLocation? {
        if (!hasPermission()) return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var best: Location? = null
        for (p in providers) {
            val loc = runCatching { manager.getLastKnownLocation(p) }.getOrNull() ?: continue
            if (best == null || loc.time > best.time) best = loc
        }
        return best?.toResolved()
    }

    /**
     * One-shot current location: waits for the first fresh fix from any
     * enabled provider, or returns null after [timeoutMillis].
     */
    @SuppressLint("MissingPermission")
    suspend fun current(timeoutMillis: Long = 20_000L): ResolvedLocation? {
        if (!hasPermission() || !providersEnabled()) return lastKnown()
        return suspendCancellableCoroutine { cont ->
            val handler = Handler(Looper.getMainLooper())
            var resumed = false
            var listener: LocationListener? = null
            fun finish(value: ResolvedLocation?) {
                if (resumed) return
                resumed = true
                listener?.let { runCatching { manager.removeUpdates(it) } }
                handler.removeCallbacksAndMessages(null)
                if (cont.isActive) cont.resume(value)
            }
            listener = object : LocationListener {
                override fun onLocationChanged(location: Location) = finish(location.toResolved())
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }
            handler.postDelayed({ finish(lastKnown()) }, timeoutMillis)
            val gpsOn = runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
            val networkOn = runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
            try {
                if (gpsOn) manager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 0L, 0f, listener, Looper.getMainLooper()
                )
                if (networkOn) manager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 0L, 0f, listener, Looper.getMainLooper()
                )
                if (!gpsOn && !networkOn) finish(lastKnown())
            } catch (e: SecurityException) {
                finish(null)
            }
            cont.invokeOnCancellation {
                listener?.let { runCatching { manager.removeUpdates(it) } }
                handler.removeCallbacksAndMessages(null)
            }
        }
    }

    /** Label a coordinate with the nearest catalog city, or coordinates. */
    fun labelFor(latitude: Double, longitude: Double): String {
        val near = CityCatalog.nearest(latitude, longitude)
        if (near != null && CityCatalog.distanceKm(latitude, longitude, near.latitude, near.longitude) <= 150.0) {
            return "${near.name}, ${near.country}"
        }
        return "%.4f, %.4f".format(latitude, longitude)
    }

    /** Resolve a coordinate into a [ResolvedLocation] with tz detection. */
    fun resolve(latitude: Double, longitude: Double): ResolvedLocation {
        val nearest = CityCatalog.nearest(latitude, longitude)
        val zone = if (nearest != null &&
            CityCatalog.distanceKm(latitude, longitude, nearest.latitude, nearest.longitude) <= 500.0
        ) nearest.timeZone else TimeZones.detect(latitude, longitude, TimeZones.deviceZone())
        return ResolvedLocation(latitude, longitude, labelFor(latitude, longitude), zone)
    }

    private fun Location.toResolved(): ResolvedLocation = resolve(latitude, longitude)
}
