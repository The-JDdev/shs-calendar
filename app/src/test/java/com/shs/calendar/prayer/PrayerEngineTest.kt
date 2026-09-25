package com.shs.calendar.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** M2 prayer engine: method windows, madhab effect, high-latitude safety. */
class PrayerEngineTest {

    private val dhaka = ZoneId.of("Asia/Dhaka")
    private val date = LocalDate.of(2026, 9, 26)

    private fun minutes(t: LocalTime?): Int = t?.toSecondOfDay()?.div(60) ?: -1

    @Test
    fun dhakaPlausibleWindow() {
        val t = PrayerEngine.compute(date, 23.8103, 90.4125, dhaka, PrayerConfig(method = PrayerMethod.MWL))
        // Fajr (18 deg) window for late September in Dhaka. Ground truth (independent
        // NOAA re-derivation): Fajr ≈ 04:41, spec's "~04:45" is approximate.
        assertTrue("fajr in window, was ${t.fajr}", minutes(t.fajr) in (4 * 60 + 35)..(5 * 60 + 15))
        assertTrue("maghrib in window, was ${t.maghrib}", minutes(t.maghrib) in (17 * 60 + 45)..(18 * 60 + 15))
        // Sunrise after Fajr, Dhuhr at solar noon, Isha after Maghrib.
        assertTrue("sunrise after fajr", minutes(t.sunrise) > minutes(t.fajr))
        assertTrue("dhuhr near noon", minutes(t.dhuhr) in 11 * 60 + 30..12 * 60 + 30)
        assertTrue("isha after maghrib", minutes(t.isha) > minutes(t.maghrib))
        // Imsak leads Fajr by 10 minutes.
        assertEquals(minutes(t.fajr) - 10, minutes(t.imsak))
        // Derived times present.
        assertNotNull("midnight", t.midnight)
        assertNotNull("tahajjud", t.tahajjud)
        assertEquals(minutes(t.fajr), minutes(t.suhoorEnd))
        assertEquals(minutes(t.maghrib), minutes(t.iftar))
    }

    @Test
    fun makkahAnchors() {
        val makkah = ZoneId.of("Asia/Riyadh")
        val t = PrayerEngine.compute(date, 21.4225, 39.8262, makkah, PrayerConfig(method = PrayerMethod.MAKKAH))
        assertTrue("fajr in window, was ${t.fajr}", minutes(t.fajr) in 4 * 60 + 30..5 * 60 + 30)
        assertTrue("sunrise in window, was ${t.sunrise}", minutes(t.sunrise) in 5 * 60 + 30..6 * 60 + 30)
        assertTrue("maghrib in window, was ${t.maghrib}", minutes(t.maghrib) in 17 * 60 + 50..18 * 60 + 40)
        // Umm al-Qura: Isha = Maghrib + 90 minutes.
        val expectedIsha = minutes(t.maghrib) + 90
        assertEquals(expectedIsha, minutes(t.isha) % 1440)
    }

    @Test
    fun hanafiAsrLaterThanStandard() {
        val standard = PrayerEngine.compute(date, 23.8103, 90.4125, dhaka, PrayerConfig(madhab = Madhab.STANDARD))
        val hanafi = PrayerEngine.compute(date, 23.8103, 90.4125, dhaka, PrayerConfig(madhab = Madhab.HANAFI))
        assertTrue(
            "hanafi asr (${hanafi.asr}) must be later than standard (${standard.asr})",
            minutes(hanafi.asr) > minutes(standard.asr)
        )
    }

    @Test
    fun highLatitudeNoCrashAt65North() {
        val june = LocalDate.of(2026, 6, 21)
        val december = LocalDate.of(2026, 12, 21)
        val tz = ZoneId.of("Europe/Stockholm")
        for (rule in HighLatitudeRule.values()) {
            for (d in listOf(june, december)) {
                val t = PrayerEngine.compute(d, 65.0, 17.6, tz, PrayerConfig(highLatitudeRule = rule))
                assertNotNull("dhuhr always exists", t.dhuhr)
                assertNotNull("midnight derived", t.midnight)
                assertNotNull("tahajjud derived", t.tahajjud)
            }
        }
    }

    @Test
    fun manualOffsetsShiftTimes() {
        val base = PrayerEngine.compute(date, 23.8103, 90.4125, dhaka, PrayerConfig())
        val shifted = PrayerEngine.compute(
            date, 23.8103, 90.4125, dhaka,
            PrayerConfig(offsets = PrayerOffsets(fajr = 3, dhuhr = -2, isha = 5))
        )
        assertEquals(minutes(base.fajr) + 3, minutes(shifted.fajr))
        assertEquals(minutes(base.dhuhr) - 2, minutes(shifted.dhuhr))
        assertEquals(minutes(base.isha) + 5, minutes(shifted.isha))
        assertEquals(minutes(base.asr), minutes(shifted.asr))
        // Imsak tracks the shifted Fajr.
        assertEquals(minutes(shifted.fajr) - 10, minutes(shifted.imsak))
    }

    @Test
    fun allMethodsProduceTimesWithoutCrashing() {
        for (method in PrayerMethod.values()) {
            val t = PrayerEngine.compute(date, 23.8103, 90.4125, dhaka, PrayerConfig(method = method))
            assertNotNull("dhuhr for $method", t.dhuhr)
            assertNotNull("fajr for $method", t.fajr)
            assertNotNull("isha for $method", t.isha)
        }
    }

    @Test
    fun nextPrayerWrapsAfterIsha() {
        val t = PrayerEngine.compute(date, 23.8103, 90.4125, dhaka, PrayerConfig())
        val afterIsha = PrayerEngine.nextPrayer(t, LocalTime.of(23, 0), date)
        assertEquals("fajr", afterIsha.first)
        val beforeDhuhr = PrayerEngine.nextPrayer(t, LocalTime.of(10, 0), date)
        assertEquals("dhuhr", beforeDhuhr.first)
    }
}
