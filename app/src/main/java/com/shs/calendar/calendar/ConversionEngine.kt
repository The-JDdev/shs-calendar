package com.shs.calendar.calendar

import java.time.LocalDate

/**
 * Bidirectional conversion hub: Gregorian <-> Bengali <-> Hijri.
 * All conversions route through the Gregorian [LocalDate] as the canonical
 * instant — no tables, no network, fully deterministic.
 *
 * @param hijriAdjustment moon-sighting offset in days, −3..+3 (settings-driven).
 */
data class ConversionResult(
    val gregorian: LocalDate,
    val bengali: BengaliEngine.BengaliDate,
    val hijri: HijriEngine.HijriDate
)

object ConversionEngine {

    /**
     * M11: bounded memo-cache for the hot path. Rendering a month grid converts
     * every visible cell to Bengali + Hijri, and scrolling a year touches
     * thousands of dates; the underlying conversions are pure and deterministic,
     * so a memo is safe. Bounded (simple FIFO eviction) so a year-jump spree
     * cannot grow it without limit. The hijriAdjustment is part of the key.
     */
    private const val CACHE_LIMIT = 4096
    private val cache = LinkedHashMap<String, ConversionResult>(256)

    private fun cached(date: LocalDate, hijriAdjustment: Int): ConversionResult? =
        synchronized(cache) { cache["${date.toEpochDay()}:$hijriAdjustment"] }

    private fun putInCache(date: LocalDate, hijriAdjustment: Int, result: ConversionResult) {
        synchronized(cache) {
            if (cache.size >= CACHE_LIMIT) {
                val eldest = cache.keys.firstOrNull() ?: return
                cache.remove(eldest)
            }
            cache["${date.toEpochDay()}:$hijriAdjustment"] = result
        }
    }

    /** Gregorian anchor date -> full trilingual result (memoized). */
    fun fromGregorian(date: LocalDate, hijriAdjustment: Int = 0): ConversionResult =
        cached(date, hijriAdjustment) ?: fromGregorianUncached(date, hijriAdjustment)
            .also { putInCache(date, hijriAdjustment, it) }

    private fun fromGregorianUncached(date: LocalDate, hijriAdjustment: Int): ConversionResult =
        ConversionResult(
            gregorian = date,
            bengali = BengaliEngine.fromGregorian(date),
            hijri = HijriEngine.fromGregorian(date, hijriAdjustment)
        )

    /** Bengali date -> Gregorian -> everything else. */
    fun fromBengali(b: BengaliEngine.BengaliDate, hijriAdjustment: Int = 0): ConversionResult {
        val g = BengaliEngine.toGregorian(b)
        return ConversionResult(g, b, HijriEngine.fromGregorian(g, hijriAdjustment))
    }

    /** Hijri date -> Gregorian -> everything else. */
    fun fromHijri(h: HijriEngine.HijriDate, hijriAdjustment: Int = 0): ConversionResult {
        val g = HijriEngine.toGregorian(h.copy(adjustment = hijriAdjustment))
        return ConversionResult(g, BengaliEngine.fromGregorian(g), h.copy(adjustment = hijriAdjustment))
    }

    fun bengaliToHijri(b: BengaliEngine.BengaliDate, hijriAdjustment: Int = 0): HijriEngine.HijriDate =
        HijriEngine.fromGregorian(BengaliEngine.toGregorian(b), hijriAdjustment)

    fun hijriToBengali(h: HijriEngine.HijriDate, hijriAdjustment: Int = 0): BengaliEngine.BengaliDate =
        BengaliEngine.fromGregorian(HijriEngine.toGregorian(h.copy(adjustment = hijriAdjustment)))

    fun gregorianToBengali(date: LocalDate): BengaliEngine.BengaliDate =
        BengaliEngine.fromGregorian(date)

    fun gregorianToHijri(date: LocalDate, hijriAdjustment: Int = 0): HijriEngine.HijriDate =
        HijriEngine.fromGregorian(date, hijriAdjustment)

    /**
     * Human-readable one-line summary of a conversion, Western digits:
     * "25 September 2026 — 10 Ashshin 1433 — 12 Rabiʿ al-Awwal 1448"
     */
    fun format(result: ConversionResult): String {
        val g = result.gregorian
        val b = result.bengali
        val h = result.hijri
        return "%02d %s %04d — %d %s %d — %d %s %d".format(
            g.dayOfMonth, g.month.name.lowercase().replaceFirstChar { it.uppercase() }, g.year,
            b.day, b.monthNameEn, b.year,
            h.day, h.monthName, h.year
        )
    }
}
