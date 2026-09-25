package com.shs.calendar.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row settings store (Phase 1 subset per SPEC, M2 extends prayer fields).
 *
 * @param firstDayOfWeek   1 = Sunday … 7 = Saturday (ISO)
 * @param clock24Hour      true = 24h, false = 12h
 * @param defaultSystem    GREGORIAN | BENGALI | HIJRI
 * @param bengaliNumerals  render Bengali digits (০-৯) in date surfaces
 * @param hijriAdjustment  −3..+3 moon-sighting offset (clamped by engine)
 * @param bengaliVariant   CLASSIC | REFORM_2019 (hook only, reform NOT implemented)
 * @param latitude/longitude/locationName nullable pair — unset = "Set location"
 * @param prayerMethod     PrayerMethod name (MWL, ISNA, …); null = MWL default
 * @param prayerMadhab     Madhab name (STANDARD, HANAFI); null = HANAFI default
 * @param highLatitudeRule HighLatitudeRule name; null = NONE
 * @param prayerOffsetsCsv "f;sr;dhuhr;asr;maghrib;isha" minute offsets; null = all 0
 *
 * M2 columns are nullable with null = documented default so the Room schema
 * (ALTER TABLE ADD COLUMN with no default) validates exactly.
 */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val firstDayOfWeek: Int = 1,
    val clock24Hour: Boolean = true,
    val defaultSystem: String = "GREGORIAN",
    val bengaliNumerals: Boolean = false,
    val hijriAdjustment: Int = 0,
    val bengaliVariant: String = "CLASSIC",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationName: String = "",
    val timezone: String = "Asia/Dhaka",
    val useDeviceTimezone: Boolean = true,
    val prayerMethod: String? = null,
    val prayerMadhab: String? = null,
    val highLatitudeRule: String? = null,
    val prayerOffsetsCsv: String? = null
) {
    companion object {
        const val SINGLE_ROW_ID = 1
    }
}
