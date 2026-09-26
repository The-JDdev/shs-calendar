package com.shs.calendar.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.Locale
import java.util.TimeZone

/**
 * Open-Meteo provider (M5). Keyless by design — no API token anywhere in this
 * file, and none is required by the service. Plain [HttpURLConnection] + org.json
 * keeps the dependency set unchanged.
 *
 * The two interesting halves are deliberately static and pure so they can be
 * unit-tested on the JVM without a network:
 *  - [buildUrl] request construction
 *  - [parseSnapshot] org.json -> [WeatherSnapshot]
 */
class OpenMeteoProvider(
    private val now: () -> Long = System::currentTimeMillis
) : WeatherProvider {

    override val id: String = ID
    override val displayName: String = "Open-Meteo"

    override suspend fun fetch(latitude: Double, longitude: Double): WeatherResult =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(buildUrl(latitude, longitude)).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("Accept", "application/json")
                }

                val code = connection.responseCode
                if (code !in 200..299) {
                    return@withContext WeatherResult.Failure(WeatherFailureReason.SERVER_ERROR)
                }

                val body = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                val snapshot = parseSnapshot(body, latitude, longitude, now())
                if (snapshot == null) WeatherResult.Failure(WeatherFailureReason.UNPARSEABLE)
                else WeatherResult.Success(snapshot)

            } catch (e: UnknownHostException) {
                WeatherResult.Failure(WeatherFailureReason.NO_NETWORK)
            } catch (e: SocketTimeoutException) {
                WeatherResult.Failure(WeatherFailureReason.TIMEOUT)
            } catch (e: Exception) {
                // Never let a weather problem crash the dashboard.
                WeatherResult.Failure(WeatherFailureReason.UNPARSEABLE)
            } finally {
                connection?.disconnect()
            }
        }

    companion object {
        const val ID = "open_meteo"
        const val BASE_URL = "https://api.open-meteo.com/v1/forecast"

        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000

        private const val HOURLY_FIELDS =
            "temperature_2m,apparent_temperature,relative_humidity_2m,precipitation_probability," +
                "wind_speed_10m,weather_code,visibility,uv_index,is_day"
        private const val DAILY_FIELDS =
            "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum," +
                "precipitation_probability_max,wind_speed_10m_max,uv_index_max,sunrise,sunset"

        /**
         * Metric, keyless, current + 24 hourly + 7 daily, UTC timestamps so no
         * timezone arithmetic leaks into the parser.
         */
        fun buildUrl(latitude: Double, longitude: Double): String = String.format(
            Locale.US,
            "%s?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m," +
                "wind_speed_10m,wind_direction_10m,weather_code,is_day" +
                "&hourly=%s&daily=%s&forecast_days=7&timezone=UTC",
            BASE_URL, latitude, longitude, HOURLY_FIELDS, DAILY_FIELDS
        )

        /**
         * Parse an Open-Meteo forecast payload. Returns null when required blocks
         * are missing, so callers can report "unavailable" rather than crash.
         * Arrays are parallel-indexed; short arrays simply truncate the list.
         */
        fun parseSnapshot(
            body: String,
            latitude: Double,
            longitude: Double,
            fetchedAtUtcMillis: Long
        ): WeatherSnapshot? {
            // org.json throws on malformed input; the contract is null, never an
            // exception, so callers can render an honest "unavailable" state.
            val root = try {
                JSONObject(body)
            } catch (e: org.json.JSONException) {
                return null
            }

            val currentBlock = root.optJSONObject("current") ?: return null
            val hourlyBlock = root.optJSONObject("hourly") ?: return null
            val dailyBlock = root.optJSONObject("daily") ?: return null

            val current = CurrentWeather(
                temperatureC = currentBlock.optDouble("temperature_2m", Double.NaN),
                apparentTemperatureC = currentBlock.optDouble("apparent_temperature", Double.NaN),
                humidityPercent = currentBlock.optInt("relative_humidity_2m", 0),
                windSpeedKmh = currentBlock.optDouble("wind_speed_10m", 0.0),
                windDirectionDegrees = currentBlock.optInt("wind_direction_10m", 0),
                weatherCode = currentBlock.optInt("weather_code", -1),
                isDay = currentBlock.optInt("is_day", 1) == 1,
                observedAtUtcMillis = parseIsoUtcMillis(currentBlock.optString("time", ""))
                    ?: fetchedAtUtcMillis
            )

            val times = hourlyBlock.optJSONArray("time")
            if (times == null || times.length() == 0) return null

            val hourly = ArrayList<HourlyPoint>(minOf(24, times.length()))
            for (i in 0 until minOf(24, times.length())) {
                val millis = parseIsoUtcMillis(times.optString(i, "")) ?: continue
                hourly += HourlyPoint(
                    epochMillisUtc = millis,
                    temperatureC = hourlyBlock.optDoubleAt("temperature_2m", i, Double.NaN),
                    apparentTemperatureC = hourlyBlock.optDoubleAt("apparent_temperature", i, Double.NaN),
                    humidityPercent = hourlyBlock.optIntAt("relative_humidity_2m", i, 0),
                    precipitationChancePercent = hourlyBlock.optIntAt("precipitation_probability", i, 0),
                    windSpeedKmh = hourlyBlock.optDoubleAt("wind_speed_10m", i, 0.0),
                    weatherCode = hourlyBlock.optIntAt("weather_code", i, -1),
                    visibilityMetres = hourlyBlock.optDoubleAt("visibility", i, 0.0),
                    uvIndex = hourlyBlock.optDoubleAt("uv_index", i, 0.0),
                    isDay = hourlyBlock.optIntAt("is_day", i, 1) == 1
                )
            }
            if (hourly.isEmpty()) return null

            val dailyTimes = dailyBlock.optJSONArray("time")
            if (dailyTimes == null || dailyTimes.length() == 0) return null

            val daily = ArrayList<DailyPoint>(dailyTimes.length())
            for (i in 0 until dailyTimes.length()) {
                val dateIso = dailyTimes.optString(i, "")
                if (dateIso.isEmpty()) continue
                daily += DailyPoint(
                    dateIso = dateIso,
                    minTempC = dailyBlock.optDoubleAt("temperature_2m_min", i, Double.NaN),
                    maxTempC = dailyBlock.optDoubleAt("temperature_2m_max", i, Double.NaN),
                    precipitationChancePercent = dailyBlock.optIntAt("precipitation_probability_max", i, 0),
                    precipitationSumMm = dailyBlock.optDoubleAt("precipitation_sum", i, 0.0),
                    windSpeedMaxKmh = dailyBlock.optDoubleAt("wind_speed_10m_max", i, 0.0),
                    uvIndexMax = dailyBlock.optDoubleAt("uv_index_max", i, 0.0),
                    // Open-Meteo returns sunrise/sunset as local-to-timezone strings;
                    // we requested UTC, so the same ISO parser applies.
                    sunriseUtcMillis = parseIsoUtcMillis(dailyBlock.optStringAt("sunrise", i, "")) ?: 0L,
                    sunsetUtcMillis = parseIsoUtcMillis(dailyBlock.optStringAt("sunset", i, "")) ?: 0L,
                    weatherCode = dailyBlock.optIntAt("weather_code", i, -1)
                )
            }

            return WeatherSnapshot(
                latitude = latitude,
                longitude = longitude,
                timezone = root.optString("timezone", "UTC"),
                current = current,
                hourly = hourly,
                daily = daily,
                fetchedAtUtcMillis = fetchedAtUtcMillis
            )
        }

        /** "2026-09-25T14:00" (and with seconds/offset) -> epoch millis UTC. */
        fun parseIsoUtcMillis(iso: String): Long? {
            if (iso.isEmpty()) return null
            val patterns = arrayOf(
                "yyyy-MM-dd'T'HH:mm",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ssXXX"
            )
            for (pattern in patterns) {
                val parsed = runCatching {
                    java.text.SimpleDateFormat(pattern, Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                        isLenient = false
                    }.parse(iso)
                }.getOrNull()
                if (parsed != null) return parsed.time
            }
            return null
        }

        // --- small org.json helpers: null-safe indexed accessors -----------------

        private fun JSONObject.optDoubleAt(key: String, index: Int, fallback: Double): Double {
            val arr = optJSONArray(key) ?: return fallback
            if (index >= arr.length()) return fallback
            val v = arr.optDouble(index, Double.NaN)
            return if (v.isNaN()) fallback else v
        }

        private fun JSONObject.optIntAt(key: String, index: Int, fallback: Int): Int {
            val arr = optJSONArray(key) ?: return fallback
            if (index >= arr.length()) return fallback
            return arr.optInt(index, fallback)
        }

        private fun JSONObject.optStringAt(key: String, index: Int, fallback: String): String {
            val arr = optJSONArray(key) ?: return fallback
            if (index >= arr.length()) return fallback
            return arr.optString(index, fallback)
        }
    }
}
