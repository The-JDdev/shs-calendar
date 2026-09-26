package com.shs.calendar.weather

import org.json.JSONArray
import org.json.JSONObject

/**
 * Last-good-response cache for weather (M5, offline-first rule #5).
 *
 * Holds exactly one snapshot — the most recent successful response for the
 * configured location — so a device that is offline still shows real data with
 * an honest "cached" label instead of a blank card. Serialization is pure
 * (snapshot <-> JSON) so it is unit-testable without Android SharedPreferences.
 */
class WeatherCache {

    private var entry: Entry? = null

    data class Entry(val snapshot: WeatherSnapshot, val storedAtUtcMillis: Long)

    fun put(snapshot: WeatherSnapshot) {
        entry = Entry(snapshot, snapshot.fetchedAtUtcMillis)
    }

    fun peek(): WeatherSnapshot? = entry?.snapshot

    fun entry(): Entry? = entry

    fun clear() {
        entry = null
    }

    /** Age of the cached response in millis, or null when nothing is cached. */
    fun ageMillis(now: Long): Long? = entry?.let { now - it.snapshot.fetchedAtUtcMillis }

    companion object {

        /** Cache older than this is labelled stale in the UI. */
        const val STALE_AFTER_MILLIS = 60L * 60L * 1000L

        fun isStale(snapshot: WeatherSnapshot, now: Long): Boolean =
            now - snapshot.fetchedAtUtcMillis > STALE_AFTER_MILLIS

        fun serialize(snapshot: WeatherSnapshot): String {
            val root = JSONObject()
            root.put("latitude", snapshot.latitude)
            root.put("longitude", snapshot.longitude)
            root.put("timezone", snapshot.timezone)
            root.put("fetchedAt", snapshot.fetchedAtUtcMillis)

            val current = JSONObject()
            current.put("t", snapshot.current.temperatureC)
            current.put("a", snapshot.current.apparentTemperatureC)
            current.put("h", snapshot.current.humidityPercent)
            current.put("w", snapshot.current.windSpeedKmh)
            current.put("d", snapshot.current.windDirectionDegrees)
            current.put("c", snapshot.current.weatherCode)
            current.put("day", if (snapshot.current.isDay) 1 else 0)
            current.put("t_utc", snapshot.current.observedAtUtcMillis)
            root.put("current", current)

            val hourly = JSONArray()
            for (point in snapshot.hourly) {
                val o = JSONObject()
                o.put("t_utc", point.epochMillisUtc)
                o.put("t", point.temperatureC)
                o.put("a", point.apparentTemperatureC)
                o.put("h", point.humidityPercent)
                o.put("p", point.precipitationChancePercent)
                o.put("w", point.windSpeedKmh)
                o.put("c", point.weatherCode)
                o.put("v", point.visibilityMetres)
                o.put("u", point.uvIndex)
                o.put("day", if (point.isDay) 1 else 0)
                hourly.put(o)
            }
            root.put("hourly", hourly)

            val daily = JSONArray()
            for (point in snapshot.daily) {
                val o = JSONObject()
                o.put("date", point.dateIso)
                o.put("min", point.minTempC)
                o.put("max", point.maxTempC)
                o.put("p", point.precipitationChancePercent)
                o.put("s", point.precipitationSumMm)
                o.put("w", point.windSpeedMaxKmh)
                o.put("u", point.uvIndexMax)
                o.put("sr", point.sunriseUtcMillis)
                o.put("ss", point.sunsetUtcMillis)
                o.put("c", point.weatherCode)
                daily.put(o)
            }
            root.put("daily", daily)

            return root.toString()
        }

        /** Inverse of [serialize]; returns null for unreadable payloads. */
        fun deserialize(raw: String): WeatherSnapshot? {
            val root = try {
                JSONObject(raw)
            } catch (e: org.json.JSONException) {
                return null
            }

            val currentObj = root.optJSONObject("current") ?: return null
            val current = CurrentWeather(
                temperatureC = currentObj.optDouble("t", Double.NaN),
                apparentTemperatureC = currentObj.optDouble("a", Double.NaN),
                humidityPercent = currentObj.optInt("h", 0),
                windSpeedKmh = currentObj.optDouble("w", 0.0),
                windDirectionDegrees = currentObj.optInt("d", 0),
                weatherCode = currentObj.optInt("c", -1),
                isDay = currentObj.optInt("day", 1) == 1,
                observedAtUtcMillis = currentObj.optLong("t_utc", 0L)
            )

            val hourlyList = ArrayList<HourlyPoint>()
            root.optJSONArray("hourly")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    hourlyList += HourlyPoint(
                        epochMillisUtc = o.optLong("t_utc", 0L),
                        temperatureC = o.optDouble("t", Double.NaN),
                        apparentTemperatureC = o.optDouble("a", Double.NaN),
                        humidityPercent = o.optInt("h", 0),
                        precipitationChancePercent = o.optInt("p", 0),
                        windSpeedKmh = o.optDouble("w", 0.0),
                        weatherCode = o.optInt("c", -1),
                        visibilityMetres = o.optDouble("v", 0.0),
                        uvIndex = o.optDouble("u", 0.0),
                        isDay = o.optInt("day", 1) == 1
                    )
                }
            }

            val dailyList = ArrayList<DailyPoint>()
            root.optJSONArray("daily")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    dailyList += DailyPoint(
                        dateIso = o.optString("date", ""),
                        minTempC = o.optDouble("min", Double.NaN),
                        maxTempC = o.optDouble("max", Double.NaN),
                        precipitationChancePercent = o.optInt("p", 0),
                        precipitationSumMm = o.optDouble("s", 0.0),
                        windSpeedMaxKmh = o.optDouble("w", 0.0),
                        uvIndexMax = o.optDouble("u", 0.0),
                        sunriseUtcMillis = o.optLong("sr", 0L),
                        sunsetUtcMillis = o.optLong("ss", 0L),
                        weatherCode = o.optInt("c", -1)
                    )
                }
            }

            return WeatherSnapshot(
                latitude = root.optDouble("latitude", 0.0),
                longitude = root.optDouble("longitude", 0.0),
                timezone = root.optString("timezone", "UTC"),
                current = current,
                hourly = hourlyList,
                daily = dailyList,
                fetchedAtUtcMillis = root.optLong("fetchedAt", 0L)
            )
        }
    }
}
