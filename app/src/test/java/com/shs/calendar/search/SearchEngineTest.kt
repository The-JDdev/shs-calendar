package com.shs.calendar.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

// SearchResult is nested inside the SearchEngine object, so it must be
// imported explicitly — a bare `SearchResult` does not resolve.
import com.shs.calendar.search.SearchEngine.SearchResult

/**
 * Regression tests for [SearchEngine].
 *
 * The Bengali cases here are not decorative: they pin the Mc-vs-Mn defect
 * where Bengali vowel signs (U+09BE, U+0982) are SPACING_COMBINING_MARK
 * and were silently *not* folded by an Mn-only filter.
 */
class SearchEngineTest {

    private val dhaka = ZoneId.of("Asia/Dhaka")

    private fun res(
        kind: SearchResult.Kind,
        id: Long,
        title: String,
        subtitle: String = "",
        sortKey: Long = 0L
    ) = SearchResult(kind, id, title, subtitle, 0, sortKey)

    // ---------- normalize ----------

    @Test
    fun normalize_is_case_and_whitespace_insensitive() {
        assertEquals("exam march", SearchEngine.normalize("  Exam   MARCH "))
    }

    @Test
    fun normalize_folds_bangla_digits_to_ascii() {
        assertEquals("25 december 2026", SearchEngine.normalize("২৫ December ২০২৬"))
    }

    @Test
    fun normalize_drops_bengali_spacing_combining_marks() {
        // ঢাকা spelled with a hasanta must equal the same word without it.
        assertEquals(
            SearchEngine.normalize("ঢাকা"),
            SearchEngine.normalize("ঢাকা")
        )
    }

    @Test
    fun normalize_keeps_genuinely_different_words_apart() {
        // বাংলা vs বাঙলা differ by a CONSONANT (ঙ), not a mark: must not collide.
        assertTrue(SearchEngine.normalize("বাংলা") != SearchEngine.normalize("বাঙলা"))
    }

    @Test
    fun normalize_preserves_bengali_consonants() {
        // 2 base letters in, 2 out — folding must not eat the skeleton.
        assertEquals("বল", SearchEngine.normalize("বাংলা"))
    }

    @Test
    fun normalize_drops_arabic_harakat() {
        assertEquals(SearchEngine.normalize("محمد"), SearchEngine.normalize("مُحَمَّد"))
    }

    @Test
    fun normalize_keeps_latin_accents_foldable() {
        assertEquals("cafe", SearchEngine.normalize("Café"))
    }

    // ---------- matches ----------

    @Test
    fun matches_ands_multiple_tokens() {
        assertTrue(SearchEngine.matches("Final exam in March", "exam march"))
        assertFalse(SearchEngine.matches("Final exam in March", "exam june"))
    }

    @Test
    fun marks_only_query_matches_nothing() {
        // Regression: normalizing to "" must NOT mean "match every row".
        assertFalse(SearchEngine.matches("anything", "া"))
    }

    @Test
    fun blank_query_matches_everything() {
        assertTrue(SearchEngine.matches("anything", "   "))
    }

    // ---------- parseDateQuery ----------

    @Test
    fun parses_english_long_date() {
        assertEquals(LocalDate.of(2026, 12, 25), SearchEngine.parseDateQuery("25 December 2026"))
    }

    @Test
    fun parses_abbreviated_month() {
        assertEquals(LocalDate.of(2026, 12, 25), SearchEngine.parseDateQuery("Dec 25 2026"))
    }

    @Test
    fun parses_slash_form() {
        assertEquals(LocalDate.of(2026, 12, 25), SearchEngine.parseDateQuery("25/12/2026"))
    }

    @Test
    fun parses_bengali_date_with_bangla_digits() {
        assertEquals(
            LocalDate.of(2026, 12, 25),
            SearchEngine.parseDateQuery("২৫ ডিসেম্বর ২০২৬")
        )
    }

    @Test
    fun rejects_impossible_day() {
        assertNull(SearchEngine.parseDateQuery("30 February 2026"))
    }

    @Test
    fun accepts_leap_day() {
        assertEquals(LocalDate.of(2024, 2, 29), SearchEngine.parseDateQuery("29 February 2024"))
    }

    @Test
    fun rejects_non_leap_day() {
        assertNull(SearchEngine.parseDateQuery("29 February 2026"))
    }

    @Test
    fun rejects_query_without_year() {
        assertNull(SearchEngine.parseDateQuery("25 december"))
    }

    @Test
    fun rejects_plain_word() {
        assertNull(SearchEngine.parseDateQuery("prayer"))
    }

    // ---------- ranking ----------

    @Test
    fun date_query_yields_a_date_result_first() {
        val out = SearchEngine.search(
            "25 december 2026",
            events = listOf(res(SearchResult.Kind.EVENT, 1, "December 2026", "misc")),
            zone = dhaka
        )
        assertEquals(SearchResult.Kind.DATE, out.first().kind)
    }

    @Test
    fun title_prefix_outranks_body_match() {
        val out = SearchEngine.search(
            "exam",
            events = listOf(
                res(SearchResult.Kind.EVENT, 2, "Physics", "final exam prep"),
                res(SearchResult.Kind.EVENT, 1, "Exam timetable", "")
            ),
            zone = dhaka
        )
        assertEquals("Exam timetable", out.first().title)
    }

    @Test
    fun results_are_deterministic_regardless_of_input_order() {
        val corpus = listOf(
            res(SearchResult.Kind.TASK, 3, "a", "x", 300),
            res(SearchResult.Kind.TASK, 1, "a", "x", 100),
            res(SearchResult.Kind.TASK, 2, "a", "x", 200)
        )
        val fwd = SearchEngine.search("a", events = corpus, zone = dhaka)
        val rev = SearchEngine.search("a", events = corpus.reversed(), zone = dhaka)
        assertEquals(fwd.map { it.id }, rev.map { it.id })
        assertEquals(listOf(1L, 2L, 3L), fwd.map { it.id })
    }

    @Test
    fun non_matching_rows_are_excluded() {
        val out = SearchEngine.search(
            "exam",
            events = listOf(res(SearchResult.Kind.EVENT, 1, "Groceries", "milk")),
            zone = dhaka
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun date_result_carries_zone_correct_start() {
        val r = SearchEngine.dateResult("25 december 2026", dhaka)
        assertNotNull(r)
        assertEquals(
            LocalDate.of(2026, 12, 25).atStartOfDay(dhaka).toInstant().toEpochMilli(),
            r!!.sortKey
        )
    }
}
