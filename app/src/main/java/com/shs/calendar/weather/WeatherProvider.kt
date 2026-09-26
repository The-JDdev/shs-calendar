package com.shs.calendar.weather

/**
 * Weather provider abstraction (M5, SPEC line 99-100).
 *
 * Implementations MUST NOT throw on network/parse failure: they return
 * [WeatherResult.Failure] so the UI can degrade to cache or an honest
 * "unavailable" state. No credentials/API keys are involved (Open-Meteo is keyless).
 */
interface WeatherProvider {

    /** Stable id used for settings persistence and the "provider selectable" UI. */
    val id: String

    /** Human label for the settings spinner. */
    val displayName: String

    /**
     * Fetch current + 24h hourly + 7d daily weather for a location.
     * Implementations run on the caller's dispatcher (the repository uses IO).
     */
    suspend fun fetch(latitude: Double, longitude: Double): WeatherResult
}

/**
 * Outcome of a provider call. [Cached] is produced by the caching layer, not by
 * raw providers, so the UI can always tell a live read from a stored one.
 */
sealed class WeatherResult {

    data class Success(val snapshot: WeatherSnapshot) : WeatherResult()

    /** Served from the offline cache because the network attempt failed. */
    data class Cached(val snapshot: WeatherSnapshot, val staleMillis: Long) : WeatherResult()

    /**
     * No data at all. [reason] is safe to show to the user (no stack traces,
     * no URLs with secrets — there are none, but keep the habit).
     */
    data class Failure(val reason: WeatherFailureReason) : WeatherResult()
}

enum class WeatherFailureReason {
    NO_NETWORK,
    TIMEOUT,
    SERVER_ERROR,
    UNPARSEABLE,
    NOT_ENABLED,
    NO_LOCATION
}
