package com.shs.calendar.weather

import com.shs.calendar.data.entity.SettingsEntity
import com.shs.calendar.data.entity.isWeatherEnabled
import com.shs.calendar.data.entity.weatherProviderOrDefault
import java.util.Locale

/**
 * Coordinates the weather layer (M5).
 *
 * Responsibilities: honour the enable flag, pick the configured provider, hit
 * the network, and fall back to the last good cached snapshot when that fails
 * so the dashboard is never blank. Unit conversion happens here (at the edge of
 * the system) so the models and providers stay in one canonical unit set.
 */
class WeatherRepository(
    private val settings: suspend () -> SettingsEntity,
    private val cache: WeatherCache = WeatherCache(),
    private val providers: List<WeatherProvider> = listOf(OpenMeteoProvider()),
    private val now: () -> Long = System::currentTimeMillis
) {

    /** Providers offered in settings; ids are persisted in the settings row. */
    fun availableProviders(): List<WeatherProvider> = providers

    fun providerFor(id: String?): WeatherProvider =
        providers.firstOrNull { it.id == id } ?: providers.first()

    fun cachedSnapshot(): WeatherSnapshot? = cache.peek()

    fun clearCache() = cache.clear()

    /**
     * Refresh for the configured location. Always returns a result; never
     * throws. When [force] is false and a fresh-enough cache exists, the cache
     * is served without touching the network.
     */
    suspend fun refresh(force: Boolean = false): WeatherResult {
        val config = settings()

        if (!config.isWeatherEnabled()) {
            return WeatherResult.Failure(WeatherFailureReason.NOT_ENABLED)
        }

        val latitude = config.latitude
        val longitude = config.longitude
        if (latitude == null || longitude == null) {
            return WeatherResult.Failure(WeatherFailureReason.NO_LOCATION)
        }

        val cached = cache.peek()
        if (!force && cached != null && !WeatherCache.isStale(cached, now())) {
            return WeatherResult.Cached(cached, staleMillis = now() - cached.fetchedAtUtcMillis)
        }

        val provider = providerFor(config.weatherProviderOrDefault())
        val result = provider.fetch(latitude, longitude)

        return when (result) {
            is WeatherResult.Success -> {
                cache.put(result.snapshot)
                result
            }
            // Any failure degrades to the last good response, honestly labelled.
            is WeatherResult.Cached -> result
            is WeatherResult.Failure -> {
                val fallback = cache.peek()
                if (fallback != null) {
                    WeatherResult.Cached(fallback, staleMillis = now() - fallback.fetchedAtUtcMillis)
                } else {
                    result
                }
            }
        }
    }

    companion object {

        const val UNITS_METRIC = "METRIC"
        const val UNITS_IMPERIAL = "IMPERIAL"

        fun cToF(celsius: Double): Double = celsius * 9.0 / 5.0 + 32.0

        fun kmhToMph(kmh: Double): Double = kmh * 0.621371

        fun kmhToMs(kmh: Double): Double = kmh / 3.6

        fun metresToKm(metres: Double): Double = metres / 1000.0

        /** "27°" / "81°", sign-prefixed for negatives. */
        fun formatTemp(value: Double, units: String, withDegree: Boolean = true): String {
            val converted = if (units == UNITS_IMPERIAL) cToF(value) else value
            val rounded = Math.round(converted)
            val body = if (rounded < 0) "-${-rounded}" else "$rounded"
            return if (withDegree) "$body°" else body
        }

        fun formatTempWithUnit(value: Double, units: String): String {
            val converted = if (units == UNITS_IMPERIAL) cToF(value) else value
            val body = formatTemp(value, units, withDegree = false)
            return if (units == UNITS_IMPERIAL) "$body°F" else "$body°C"
        }

        fun formatWind(kmh: Double, units: String): String {
            return when (units) {
                UNITS_IMPERIAL -> String.format(Locale.US, "%.0f mph", kmhToMph(kmh))
                else -> String.format(Locale.US, "%.0f km/h", kmh)
            }
        }

        fun formatVisibility(metres: Double, units: String): String {
            return when (units) {
                UNITS_IMPERIAL -> String.format(Locale.US, "%.1f mi", metresToKm(metres) * 0.621371)
                else -> String.format(Locale.US, "%.1f km", metresToKm(metres))
            }
        }
    }
}
