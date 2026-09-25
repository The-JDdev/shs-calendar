package com.shs.calendar.prayer

import com.shs.calendar.data.entity.SettingsEntity

/**
 * Maps the persisted [SettingsEntity] prayer fields to [PrayerConfig] and
 * back. The stored columns are nullable with NULL = documented default, so
 * every reader falls back deterministically instead of crashing on old rows.
 *
 * Offsets CSV format: "fajr;sunrise;dhuhr;asr;maghrib;isha" (signed minutes).
 */
object PrayerSettings {

    private const val OFFSET_SEPARATOR = ";"

    fun methodOf(s: SettingsEntity): PrayerMethod =
        PrayerMethod.values().find { it.name == s.prayerMethod } ?: PrayerMethod.MWL

    fun madhabOf(s: SettingsEntity): Madhab =
        Madhab.values().find { it.name == s.prayerMadhab } ?: Madhab.HANAFI

    fun highLatitudeRuleOf(s: SettingsEntity): HighLatitudeRule =
        HighLatitudeRule.values().find { it.name == s.highLatitudeRule } ?: HighLatitudeRule.NONE

    fun offsetsOf(s: SettingsEntity): PrayerOffsets {
        val parts = s.prayerOffsetsCsv?.split(OFFSET_SEPARATOR) ?: return PrayerOffsets()
        if (parts.size != 6) return PrayerOffsets()
        val n = parts.map { it.trim().toIntOrNull() ?: return PrayerOffsets() }
        return PrayerOffsets(n[0], n[1], n[2], n[3], n[4], n[5])
    }

    fun encodeOffsets(o: PrayerOffsets): String = listOf(
        o.fajr, o.sunrise, o.dhuhr, o.asr, o.maghrib, o.isha
    ).joinToString(OFFSET_SEPARATOR)

    fun configOf(s: SettingsEntity): PrayerConfig = PrayerConfig(
        method = methodOf(s),
        madhab = madhabOf(s),
        highLatitudeRule = highLatitudeRuleOf(s),
        offsets = offsetsOf(s)
    )

    /** Persisted copy of [s] with all prayer fields set from [config]. */
    fun applyTo(s: SettingsEntity, config: PrayerConfig): SettingsEntity = s.copy(
        prayerMethod = config.method.name,
        prayerMadhab = config.madhab.name,
        highLatitudeRule = config.highLatitudeRule.name,
        prayerOffsetsCsv = encodeOffsets(config.offsets)
    )
}
