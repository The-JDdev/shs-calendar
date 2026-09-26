package com.shs.calendar.widget

import com.shs.calendar.prayer.PrayerTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * JVM tests for the widget presentation layer.
 *
 * WidgetPresenter is deliberately Android-free, so its formatting decisions can
 * be asserted here without Robolectric or an instrumented device.
 */
class WidgetPresenterTest {

    @Test
    fun `formatTime renders 24h and falls back when null`() {
        assertEquals("05:30", WidgetPresenter.formatTime(LocalTime.of(5, 30)))
        assertEquals("00:00", WidgetPresenter.formatTime(LocalTime.MIDNIGHT))
        assertEquals(WidgetPresenter.DASH_TIME, WidgetPresenter.formatTime(null))
    }

    @Test
    fun `formatTodayLong includes weekday day month and year`() {
        val text = WidgetPresenter.formatTodayLong(LocalDate.of(2026, 12, 26))
        assertEquals("Saturday, 26 December 2026", text)
    }

    @Test
    fun `formatWeekdayShort is abbreviated`() {
        assertEquals("Sat", WidgetPresenter.formatWeekdayShort(LocalDate.of(2026, 12, 26)))
    }

    @Test
    fun `bengali conversion round-trips through the presenter`() {
        val b = com.shs.calendar.calendar.ConversionEngine.gregorianToBengali(LocalDate.of(2026, 12, 26))
        val text = WidgetPresenter.formatBengali(b)
        assertEquals("${b.day} ${b.monthName} ${b.year}", text)
    }

    @Test
    fun `moon phase is computed and illumination is a sane percentage`() {
        val text = WidgetPresenter.formatMoonPhase(LocalDate.of(2026, 12, 26))
        val percent = text.substringAfter("\u00b7 ").removeSuffix("%").toInt()
        assertTrue("illumination in 0..100 but was $percent", percent in 0..100)
    }

    @Test
    fun `nextPrayer picks the first prayer strictly after now`() {
        val times = times()
        assertEquals("Fajr" to "05:00", WidgetPresenter.nextPrayer(times, LocalTime.of(4, 55)))
        assertEquals("Asr" to "16:00", WidgetPresenter.nextPrayer(times, LocalTime.of(13, 0)))
    }

    @Test
    fun `nextPrayer returns null once the day is over rather than inventing a time`() {
        val times = times()
        assertNull(WidgetPresenter.nextPrayer(times, LocalTime.of(23, 59)))
    }

    @Test
    fun `prayerRows keeps canonical order and tolerates missing times`() {
        val times = times(imsak = null, sunrise = null, isha = null)
        val rows = WidgetPresenter.prayerRows(times)
        assertEquals(listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"), rows.map { it.first })
        assertEquals(WidgetPresenter.DASH_TIME, rows.first { it.first == "Sunrise" }.second)
    }

    /**
     * Fills every field of [PrayerTimes] so a future field addition breaks
     * compilation in exactly one place instead of three.
     */
    private fun times(
        imsak: LocalTime? = LocalTime.of(4, 40),
        fajr: LocalTime? = LocalTime.of(5, 0),
        sunrise: LocalTime? = LocalTime.of(6, 15),
        dhuhr: LocalTime? = LocalTime.of(12, 5),
        asr: LocalTime? = LocalTime.of(16, 0),
        sunset: LocalTime? = LocalTime.of(17, 45),
        maghrib: LocalTime? = LocalTime.of(17, 50),
        isha: LocalTime? = LocalTime.of(19, 10)
    ) = PrayerTimes(
        imsak = imsak, fajr = fajr, sunrise = sunrise, dhuhr = dhuhr, asr = asr,
        sunset = sunset, maghrib = maghrib, isha = isha,
        midnight = LocalTime.of(0, 0), tahajjud = LocalTime.of(3, 30),
        suhoorEnd = imsak, iftar = maghrib
    )
}
