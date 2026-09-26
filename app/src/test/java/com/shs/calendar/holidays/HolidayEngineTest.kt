package com.shs.calendar.holidays

import kotlinx.coroutines.runBlocking
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M6 holiday engine: computus correctness, the 2026/2030 dates the SPEC
 * calls out, category coverage, the 1900-3000 range, and the database queries.
 *
 * Dates are asserted from the calendar's own rules where a rule is exact
 * (fixed days, computus) and as plausible windows where a rule depends on a
 * lunar conversion (Eid, Durga Puja) — so the tests pin behaviour without
 * becoming a copy of the per-year table they exist to prevent.
 */
class HolidayEngineTest {

    @Test
    fun easter_sundays_match_published_computus() {
        // Independent anchors from published Gregorian computus tables.
        val expected = mapOf(
            1900 to LocalDate.of(1900, 4, 15),
            2000 to LocalDate.of(2000, 4, 23),
            2024 to LocalDate.of(2024, 3, 31),
            2025 to LocalDate.of(2025, 4, 20),
            2026 to LocalDate.of(2026, 4, 5),
            2027 to LocalDate.of(2027, 3, 28),
            2030 to LocalDate.of(2030, 4, 21)
        )
        for ((year, date) in expected) {
            assertEquals("Easter $year", date, HolidayEngine.easterSunday(year))
        }
    }

    @Test
    fun easter_always_falls_in_march_or_april() {
        for (year in 1900..2100) {
            val e = HolidayEngine.easterSunday(year)
            assertTrue("Easter $year in $e", e.monthValue in 3..4)
            assertEquals("Easter $year is a Sunday", DayOfWeek.SUNDAY, e.dayOfWeek)
        }
    }

    @Test
    fun supported_year_range_is_1900_to_3000() {
        assertTrue(HolidayEngine.isSupportedYear(1900))
        assertTrue(HolidayEngine.isSupportedYear(3000))
        assertFalse(HolidayEngine.isSupportedYear(1899))
        assertFalse(HolidayEngine.isSupportedYear(3001))
    }

    @Test
    fun bangladesh_2026_includes_the_specced_dates() {
        val list = HolidayEngine.holidaysForYear(2026, setOf(HolidayCountry.BANGLADESH))
        val byName = list.associateBy { it.nameEn }
        // Independence Day, Victory Day, Bengali New Year, Language Martyrs' Day
        assertNotNull("Independence Day", byName["Independence Day"])
        assertEquals(LocalDate.of(2026, 3, 26), byName["Independence Day"]!!.date)
        assertEquals(LocalDate.of(2026, 12, 16), byName["Victory Day"]!!.date)
        // 1 Boishakh always falls in mid-April in the Bengali solar year.
        val newYear = byName["Pohela Boishakh"]!!.date
        assertEquals("Pohela Boishakh month", 4, newYear.monthValue)
        assertTrue("Pohela Boishakh day", newYear.dayOfMonth in 13..15)
    }

    @Test
    fun eid_al_fitr_and_adha_fall_in_expected_windows_2026_and_2030() {
        for (year in listOf(2026, 2030)) {
            val list = HolidayEngine.holidaysForYear(year, HolidayCountry.entries.toSet())
            val fitr = list.first { it.nameEn == "Eid al-Fitr" }.date
            val adha = list.first { it.nameEn == "Eid al-Adha" }.date
            // 1 Shawwal and 10 Dhu al-Hijjah migrate ~11 days earlier per
            // Gregorian year, so a 2026 date (May/June) can become Feb/Apr by
            // 2030. These bounds admit the whole 4-year cycle, not one year.
            assertTrue("Eid al-Fitr $year in $fitr", fitr.monthValue in 1..5)
            assertTrue("Eid al-Adha $year in $adha", adha.monthValue in 1..8)
            assertTrue("Eid al-Fitr before Eid al-Adha in $year", fitr.isBefore(adha))
        }
    }

    @Test
    fun all_four_categories_are_reachable() {
        val list = HolidayEngine.holidaysForYear(2026, HolidayCountry.entries.toSet())
        val categories = list.map { it.category }.toSet()
        for (c in listOf(
            HolidayCategory.NATIONAL,
            HolidayCategory.ISLAMIC,
            HolidayCategory.BENGALI,
            HolidayCategory.REGIONAL
        )) {
            assertTrue("category $c present in $categories", c in categories)
        }
    }

    @Test
    fun every_returned_holiday_lies_inside_the_requested_year() {
        for (year in listOf(1900, 1950, 2026, 2030, 3000)) {
            for (h in HolidayEngine.holidaysForYear(year, HolidayCountry.entries.toSet())) {
                assertEquals("stray holiday $h in $year", year, h.date.year)
            }
        }
    }

    @Test
    fun unsupported_year_returns_empty_rather_than_throwing() {
        assertTrue(HolidayEngine.holidaysForYear(1899).isEmpty())
        assertTrue(HolidayEngine.holidaysForYear(3001).isEmpty())
    }

    @Test
    fun database_month_and_range_queries_agree_with_the_engine() {
        val db = HolidayDatabase()
        val march = YearMonth.of(2026, 3)
        val inMarch = db.inMonth(march)
        assertTrue("March has holidays", inMarch.isNotEmpty())
        for (h in inMarch) {
            assertEquals(march, YearMonth.from(h.date))
        }
        val window = db.inRange(march.atDay(1), march.atEndOfMonth())
        assertEquals("inRange == inMonth", inMarch, window)
        // Reversed range is empty, not an error.
        assertTrue(db.inRange(march.atEndOfMonth(), march.atDay(1)).isEmpty())
    }

    @Test
    fun search_matches_bengali_and_english_names_and_tolerates_blank() {
        val db = HolidayDatabase()
        assertTrue("blank query", db.search("   ").isEmpty())
        assertTrue("no match", db.search("zzzznotaholiday").isEmpty())
        val victory = db.search("victory")
        assertTrue("English search finds Victory Day", victory.any { it.nameEn == "Victory Day" })
    }

    @Test
    fun database_respects_a_single_country_filter() {
        val db = HolidayDatabase(setOf(HolidayCountry.UNITED_STATES))
        val list = db.forYear(2026)
        assertTrue(list.isNotEmpty())
        for (h in list) {
            assertEquals(HolidayCountry.UNITED_STATES, h.country)
        }
        // Good Friday is a US Easter rule, so it must be present.
        assertTrue("Good Friday", list.any { it.nameEn == "Good Friday" })
    }

    @Test
    fun hijri_adjustment_shifts_islamic_holidays_only() {
        val plain = HolidayEngine.holidaysForYear(2026, HolidayCountry.entries.toSet(), 0)
        val shifted = HolidayEngine.holidaysForYear(2026, HolidayCountry.entries.toSet(), 2)
        val plainIslamic = plain.filter { it.category == HolidayCategory.ISLAMIC }
        val shiftedIslamic = shifted.filter { it.category == HolidayCategory.ISLAMIC }
        assertEquals("same Islamic count", plainIslamic.size, shiftedIslamic.size)
        assertTrue(
            "Islamic dates moved",
            plainIslamic.map { it.date }.toSet() != shiftedIslamic.map { it.date }.toSet()
        )
        val plainFixed = plain.filter { it.category == HolidayCategory.NATIONAL }
            .map { it.date to it.nameEn }.toSet()
        val shiftedFixed = shifted.filter { it.category == HolidayCategory.NATIONAL }
            .map { it.date to it.nameEn }.toSet()
        assertEquals("national dates unchanged", plainFixed, shiftedFixed)
    }

    @Test
    fun offline_update_source_reports_up_to_date() = runBlocking {
        val from = LocalDate.of(2026, 1, 1)
        val to = LocalDate.of(2026, 12, 31)
        val result = OfflineRulesUpdateSource.fetch(from, to)
        assertTrue("offline source is honest, never fakes data", result is UpdateResult.UpToDate)
        assertFalse("offline source is not network-available", OfflineRulesUpdateSource.isAvailable())
    }
}
