package com.shs.calendar.weather

/**
 * Weather domain models (M5). Pure Kotlin — no Android imports — so every field
 * and formatter is unit-testable on the JVM.
 *
 * Values are always stored in the units the provider returned (Open-Meteo default
 * metric); [WeatherRepository] converts at render time based on user preference.
 */

data class CurrentWeather(
    val temperatureC: Double,
    val apparentTemperatureC: Double,
    val humidityPercent: Int,
    val windSpeedKmh: Double,
    val windDirectionDegrees: Int,
    val weatherCode: Int,
    val isDay: Boolean,
    /** Epoch millis, UTC. */
    val observedAtUtcMillis: Long
) {
    /** Open-Meteo WMO weather code -> human label. */
    val conditionLabel: String get() = WeatherCodes.describe(weatherCode)

    val isRaining: Boolean get() = WeatherCodes.isRaining(weatherCode)
}

data class HourlyPoint(
    val epochMillisUtc: Long,
    val temperatureC: Double,
    val apparentTemperatureC: Double,
    val humidityPercent: Int,
    /** Chance of precipitation, 0..100. */
    val precipitationChancePercent: Int,
    val windSpeedKmh: Double,
    val weatherCode: Int,
    /** Metres; may be negative-free but clamped at render. */
    val visibilityMetres: Double,
    val uvIndex: Double,
    val isDay: Boolean
) {
    val conditionLabel: String get() = WeatherCodes.describe(weatherCode)

    /** True when the WMO code denotes rain, drizzle or a rain shower. */
    val isRaining: Boolean get() = WeatherCodes.isRaining(weatherCode)
}

data class DailyPoint(
    val dateIso: String,
    val minTempC: Double,
    val maxTempC: Double,
    /** Chance of the most precipitation-prone day, 0..100. */
    val precipitationChancePercent: Int,
    val precipitationSumMm: Double,
    val windSpeedMaxKmh: Double,
    val uvIndexMax: Double,
    val sunriseUtcMillis: Long,
    val sunsetUtcMillis: Long,
    val weatherCode: Int
) {
    val conditionLabel: String get() = WeatherCodes.describe(weatherCode)
}

/**
 * One complete successful provider response, plus the metadata the UI needs to
 * be honest about freshness ([fetchedAtUtcMillis]) and location ([latitude]/[longitude]).
 */
data class WeatherSnapshot(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val current: CurrentWeather,
    val hourly: List<HourlyPoint>,
    val daily: List<DailyPoint>,
    val fetchedAtUtcMillis: Long
)

/**
 * WMO 4677 weather interpretation codes as used by Open-Meteo.
 * Deterministic mapping — no fabricated conditions.
 */
object WeatherCodes {

    fun describe(code: Int): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61 -> "Light rain"
        63 -> "Rain"
        65 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        71 -> "Light snow"
        73 -> "Snow"
        75 -> "Heavy snow"
        77 -> "Snow grains"
        80 -> "Rain showers"
        81 -> "Rain showers"
        82 -> "Violent rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "Unknown"
    }

    fun isRaining(code: Int): Boolean = when (code) {
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99 -> true
        else -> false
    }

    /** True when the icon set should draw sun rather than cloud. */
    fun isClear(code: Int): Boolean = code == 0 || code == 1
}
