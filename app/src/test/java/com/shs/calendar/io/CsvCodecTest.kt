package com.shs.calendar.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** RFC 4180 behaviour for [CsvCodec]. Pure JVM. */
class CsvCodecTest {

    // ---------------- quoting ----------------

    @Test
    fun plain_values_are_not_quoted() {
        assertEquals("hello", CsvCodec.quote("hello"))
        assertEquals("", CsvCodec.quote(""))
    }

    @Test
    fun values_with_specials_are_quoted() {
        assertEquals("\"a,b\"", CsvCodec.quote("a,b"))
        assertEquals("\"line1\nline2\"", CsvCodec.quote("line1\nline2"))
        assertEquals("\"say \"\"hi\"\"\"", CsvCodec.quote("say \"hi\""))
    }

    @Test
    fun a_value_that_is_only_a_quote_is_still_quoted() {
        assertEquals("\"\"\"\"", CsvCodec.quote("\""))
    }

    // ---------------- writing ----------------

    @Test
    fun write_emits_a_bom_then_the_header() {
        val out = CsvCodec.writeRows(listOf("a", "b"), listOf(listOf("1", "2")))
        assertTrue(out.startsWith(CsvCodec.BOM))
        assertTrue(out.contains("a,b"))
    }

    @Test
    fun write_uses_crlf_line_endings() {
        val out = CsvCodec.writeRows(listOf("a"), listOf(listOf("1")))
        assertTrue(out.contains("\r\n"))
    }

    @Test
    fun null_cells_are_written_as_empty_not_the_text_null() {
        val out = CsvCodec.writeRows(listOf("a", "b"), listOf(listOf("1", null)))
        assertEquals(CsvCodec.BOM + "a,b\r\n1,\r\n", out)
    }

    // ---------------- parsing ----------------

    @Test
    fun parses_headers_and_rows() {
        val (h, rows) = CsvCodec.parse("a,b\r\n1,2\r\n3,4\r\n")
        assertEquals(listOf("a", "b"), h)
        assertEquals(listOf(listOf("1", "2"), listOf("3", "4")), rows)
    }

    @Test
    fun a_bom_is_stripped_from_the_first_header() {
        val (h, _) = CsvCodec.parse(CsvCodec.BOM + "uid,summary\r\n1,x\r\n")
        assertEquals(listOf("uid", "summary"), h)
    }

    @Test
    fun a_missing_final_newline_still_yields_the_last_row() {
        val (_, rows) = CsvCodec.parse("a,b\r\n1,2")
        assertEquals(listOf(listOf("1", "2")), rows)
    }

    @Test
    fun quoted_fields_containing_commas_and_newlines_survive() {
        val (_, rows) = CsvCodec.parse("a,b\r\n\"x,1\",\"line1\nline2\"\r\n")
        assertEquals("x,1", rows[0][0])
        assertEquals("line1\nline2", rows[0][1])
    }

    @Test
    fun doubled_quotes_unescape_to_one() {
        val (_, rows) = CsvCodec.parse("a\r\n\"say \"\"hi\"\"\"\r\n")
        assertEquals("say \"hi\"", rows[0][0])
    }

    @Test
    fun crlf_and_lf_line_endings_both_parse() {
        val (_, crlf) = CsvCodec.parse("a,b\r\n1,2\r\n")
        val (_, lf) = CsvCodec.parse("a,b\n1,2\n")
        assertEquals(crlf, lf)
    }

    @Test
    fun lone_cr_line_endings_parse() {
        // Classic Mac exports: no LF at all. A naive split("\n") returns one
        // row containing every CR; the record splitter must treat CR as a
        // record terminator in its own right.
        val (h, rows) = CsvCodec.parse("a,b\r1,2\r3,4\r")
        assertEquals(listOf("a", "b"), h)
        assertEquals(2, rows.size)
        assertEquals(listOf("1", "2"), rows[0])
        assertEquals(listOf("3", "4"), rows[1])
    }

    @Test
    fun blank_trailing_lines_are_ignored() {
        val (h, rows) = CsvCodec.parse("a,b\r\n1,2\r\n\r\n\r\n")
        assertEquals(listOf("a", "b"), h)
        assertEquals(1, rows.size)
    }

    @Test
    fun empty_input_yields_nothing() {
        val (h, rows) = CsvCodec.parse("")
        assertTrue(h.isEmpty())
        assertTrue(rows.isEmpty())
    }

    @Test
    fun an_unbalanced_quote_does_not_hang_or_lose_later_rows() {
        // Lenient: treat the rest as one quoted field rather than failing.
        val (_, rows) = CsvCodec.parse("a,b\r\n\"unclosed,2\r\n")
        assertEquals(1, rows.size)
    }

    // ---------------- round trip ----------------

    @Test
    fun a_value_with_everything_hostile_survives_a_round_trip() {
        val nasty = "Meeting, \"Q3\"\nsecond line; with\tsemicolon"
        val out = CsvCodec.writeRows(CsvCodec.EVENT_COLUMNS, listOf(CsvCodec.EVENT_COLUMNS.map { nasty }))
        val (h, rows) = CsvCodec.parse(out)
        assertEquals(CsvCodec.EVENT_COLUMNS, h)
        assertEquals(nasty, rows[0][0])
    }

    @Test
    fun column_order_is_stable_for_diffable_exports() {
        assertEquals(listOf("uid", "summary", "description", "location", "start", "end", "allDay", "rrule"), CsvCodec.EVENT_COLUMNS)
    }
}
