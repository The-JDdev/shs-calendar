package com.shs.calendar.search

import java.time.LocalDate
import java.util.Locale

/**
 * Deterministic global search over events, tasks, notes, holidays and duas,
 * plus natural date queries such as "25 december 2026".
 *
 * Two design rules from SPEC section 14:
 *
 * 1. **Deterministic** — results are ranked by an explicit [SearchResult.score]
 *    then by a stable tiebreak (title, then id). The same corpus and query
 *    always produce the same list, regardless of iteration order upstream.
 * 2. **Diacritic/mark insensitive** — [normalize] folds the marks that vary
 *    between Bengali spellings so that a query still matches when the user
 *    omits a sign, a zero-width joiner, or types ASCII digits for
 *    Bangla numerals. See [normalize].
 *
 * Pure Kotlin: no Android, no Room, so it is directly unit-testable.
 */
object SearchEngine {

    /** Longest query we try to read as a calendar date. */
    private const val MAX_DATE_QUERY_CHARS = 40

    /** Ranking constants — higher wins. Kept in one place so tuning is obvious. */
    private const val SCORE_TITLE_PREFIX = 1000
    private const val SCORE_TITLE = 600
    private const val SCORE_BODY = 200
    private const val SCORE_SUBSTRING = 100

    private val MONTHS = listOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december"
    )

    private val MONTHS_BN = listOf(
        "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
        "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
    )

    // Precomputed once: matching a token must compare normalized text, and
    // normalizing twelve month names inside the per-token loop made the
    // parser O(tokens x 12) NFD passes on every keystroke of the query box.
    private val MONTHS_NORM: List<String> = MONTHS.map { normalize(it) }
    private val MONTHS_BN_NORM: List<String> = MONTHS_BN.map { normalize(it) }

    /**
     * Folds a string to its comparison form:
     *
     * - lowercased (locale-independent),
     * - Bengali/Bangla digits (U+09E6..U+09EF) folded to ASCII 0..9,
     * - zero-width joiner/non-joiner (U+200C/U+200D) and ZWNJ/ZWJ removed,
     * - decomposed via NFD so that vowel signs and nukta are dropped
     *   (canonical reordering) — this is what makes "বাংলা" match a query
     *   typed without the hasanta, and "স্কুল" match "স্কুল".
     * - Arabic-Indic digits folded the same way for parity with the AR locale.
     *
     * Note that NFD is applied *after* case folding but *before* mark removal
     * so that decomposed sequences collapse predictably. Whitespace is
     * collapsed to single spaces and trimmed so that "a  b" == "a b".
     */
    fun normalize(input: String): String {
        if (input.isEmpty()) return ""
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when {
                ch == '\u200C' || ch == '\u200D' || ch == '\uFEFF' -> Unit // joiners
                ch in '\u09E6'..'\u09EF' -> sb.append('0' + (ch - '\u09E6')) // ০-৯
                ch in '\u0660'..'\u0669' -> sb.append('0' + (ch - '\u0660')) // ٠-٩
                else -> sb.append(ch)
            }
        }
        val decomposed = java.text.Normalizer.normalize(sb, java.text.Normalizer.Form.NFD)
        return decomposed
            .lowercase(Locale.ROOT)
            // Drop combining marks — BOTH categories are required:
            // Mn (NON_SPACING_MARK) covers Latin accents and Arabic harakat,
            // but Bengali vowel signs are Mc (SPACING_COMBINING_MARK):
            // U+09BE, U+0982, U+09CD and friends. Filtering on Mn alone
            // folds nothing at all in Bengali — the case this exists for.
            // The constants are Byte while getType() returns Int, hence toInt().
            .filterNot {
                val t = java.lang.Character.getType(it)
                t == java.lang.Character.NON_SPACING_MARK.toInt() ||
                    t == java.lang.Character.COMBINING_SPACING_MARK.toInt()
            }
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * True when every normalized token of [query] occurs in [haystack].
     * Multi-token queries are ANDed, which behaves far better than substring
     * for a user typing "exam march" expecting both words.
     */
    fun matches(haystack: String, query: String): Boolean {
        val q = normalize(query)
        // A query that is entirely marks ("া", "ঁ") normalizes to the empty
        // string. Returning true for that would match EVERY row, so an
        // empty result of normalization means "no match" whenever the user
        // actually typed something.
        if (q.isEmpty()) return query.isBlank()
        val h = normalize(haystack)
        return q.split(' ').filter { it.isNotEmpty() }.all { h.contains(it) }
    }

    /**
     * Parses a natural-language date such as "25 december 2026",
     * "dec 25 2026", "25/12/2026" or the Bengali "২৫ ডিসেম্বর ২০২৬".
     *
     * Returns null when the text is not a date we can read. A year is
     * required — guessing a year would make every bare "5" a date, which is
     * worse than returning no date results at all.
     *
     * Digit folding happens in [normalize] first, so Bangla numerals arrive
     * here already as ASCII and this parser is script-agnostic.
     */
    fun parseDateQuery(query: String): LocalDate? {
        val text = normalize(query)
        if (text.isEmpty() || text.length > MAX_DATE_QUERY_CHARS) return null

        // Split on any non-alphanumeric run; order is the *normalized* order,
        // which is what both Latin and Bengali month names survive.
        val tokens = text.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        if (tokens.size < 3) return null

        var day: Int? = null
        var month: Int? = null
        var year: Int? = null
        val numbers = mutableListOf<Int>()

        for (token in tokens) {
            val monthIndex = MONTHS_NORM.indexOfFirst { it == token } + 1
            val monthBnIndex = MONTHS_BN_NORM.indexOfFirst { it == token } + 1
            when {
                monthIndex > 0 -> month = monthIndex
                monthBnIndex > 0 -> month = monthBnIndex
                // Abbreviations: "jan", "dec", "sept" …
                token.length in 3..4 && token.all { it.isLetter() } -> {
                    val hit = MONTHS_NORM.indexOfFirst { it.startsWith(token) } + 1
                    if (hit > 0) month = hit
                }
                token.all { it.isDigit() } -> {
                    val n = token.toIntOrNull() ?: return null
                    numbers += n
                    // A bare number that is not day-sized is the year.
                    if (n > 31) year = n
                }
            }
        }

        if (year == null) return null

        val small = numbers.filter { it != year && it in 1..31 }
        if (month == null && small.size >= 2) {
            // Slash/dash form: "25/12/2026" is day-first, "2026-12-25" is
            // month-first. Take day-first, then swap if that yields an
            // impossible month — which is exactly the ISO case.
            val d0 = small[0]
            val m0 = small[1]
            if (m0 in 1..12) {
                day = d0
                month = m0
            } else if (d0 in 1..12) {
                day = m0
                month = d0
            }
        }
        if (day == null) day = small.firstOrNull()
        val d = day ?: return null
        val m = month ?: return null
        if (d !in 1..31 || m !in 1..12) return null

        // Reject impossible dates instead of silently rolling over: 30 Feb
        // must not become 2 Mar. LocalDate.of normalizes in SMART mode, so
        // we build it and then verify the fields survived unchanged.
        return try {
            val ld = LocalDate.of(year, m, d)
            if (ld.year == year && ld.monthValue == m && ld.dayOfMonth == d) ld else null
        } catch (e: java.time.DateTimeException) {
            null
        }
    }

    /** A single search hit. [kind] tells the UI which icon/detail row to show. */
    data class SearchResult(
        val kind: Kind,
        val id: Long,
        val title: String,
        val subtitle: String,
        val score: Int,
        /** Sort key: start/due/linked instant for time-bound hits, else 0. */
        val sortKey: Long = 0L
    ) {
        enum class Kind { EVENT, TASK, NOTE, DATE, HOLIDAY, DUA }
    }

    /**
     * Builds the single DATE hit for a query that parses as a calendar date,
     * or null when the query is not a date. The caller prepends it to the
     * other results so an exact date always outranks incidental text hits.
     */
    fun dateResult(query: String, zone: java.time.ZoneId): SearchResult? {
        val d = parseDateQuery(query) ?: return null
        val start = d.atStartOfDay(zone).toInstant().toEpochMilli()
        return SearchResult(
            kind = SearchResult.Kind.DATE,
            id = start,
            title = d.toString(),
            subtitle = "",
            score = SCORE_TITLE_PREFIX,
            sortKey = start
        )
    }

    /**
     * Ranks a mixed corpus. Every input list is scanned fully and the output is
     * ordered by score desc, then [SearchResult.sortKey] asc, then title, then
     * id — a total order, so the same inputs always yield the same output.
     */
    fun search(
        query: String,
        events: List<SearchResult> = emptyList(),
        tasks: List<SearchResult> = emptyList(),
        notes: List<SearchResult> = emptyList(),
        holidays: List<SearchResult> = emptyList(),
        duas: List<SearchResult> = emptyList(),
        zone: java.time.ZoneId = java.time.ZoneId.of("Asia/Dhaka"),
        limit: Int = 200
    ): List<SearchResult> {
        val out = ArrayList<SearchResult>()
        // An exact date read is the strongest possible signal — it is what the
        // user typed, not an incidental substring hit.
        dateResult(query, zone)?.let { out += it }
        for (r in events + tasks + notes + holidays + duas) {
            val s = scoreOf(query, r) ?: continue
            out += r.copy(score = s)
        }
        return out.sortedWith(
            compareByDescending<SearchResult> { it.score }
                .thenBy { it.sortKey }
                .thenBy { it.title }
                .thenBy { it.id }
        ).take(limit)
    }

    /** Returns the score for one hit, or null when it does not match at all. */
    private fun scoreOf(query: String, r: SearchResult): Int? {
        val q = normalize(query)
        if (q.isEmpty()) return 0
        val title = normalize(r.title)
        val subtitle = normalize(r.subtitle)
        if (title.isEmpty() && subtitle.isEmpty()) return null
        if (!matches(r.title, query) && !matches(r.subtitle, query)) return null
        return when {
            title.startsWith(q) -> SCORE_TITLE_PREFIX
            title.contains(q) -> SCORE_TITLE
            subtitle.contains(q) -> SCORE_BODY
            else -> SCORE_SUBSTRING
        }
    }
}
