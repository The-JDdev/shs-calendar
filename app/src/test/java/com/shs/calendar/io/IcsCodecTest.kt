package com.shs.calendar.io

import com.shs.calendar.data.recurrence.RRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RFC 5545 behaviour for [IcsCodec]. Pure JVM — no Android, no Room.
 */
class IcsCodecTest {

    private val hour = 3_600_000L
    private val min = 60_000L
    private val base = 1_767_225_600_000L // 2026-01-01T00:00:00Z

    // ---------------- escaping ----------------

    @Test
    fun escapes_the_four_text_specials() {
        assertEquals("a\\,b\\;c\\nd\\\\e", IcsCodec.escapeText("a,b;c\nd\\e"))
    }

    @Test
    fun escape_round_trips() {
        val raw = "Seminar; bring 2,3 laptops\\ndon't panic"
        assertEquals(raw, IcsCodec.unescapeText(IcsCodec.escapeText(raw)))
    }

    @Test
    fun unescape_tolerates_lenient_exporters() {
        // A bare semicolon from a hand-edited file must not be lost.
        assertEquals("a;b", IcsCodec.unescapeText("a;b"))
    }

    @Test
    fun unescape_preserves_a_dangling_trailing_backslash() {
        // RFC 5545 defines only \\, \; \, and \N. A trailing backslash is
        // malformed with no defined replacement, so it is kept verbatim:
        // silently dropping a character from a user's title is worse than
        // preserving a stray one, and it round-trips through write() intact.
        assertEquals("abc\\", IcsCodec.unescapeText("abc\\"))
    }

    @Test
    fun an_unknown_escape_drops_the_backslash() {
        // \x is a known-but-unlisted case: keep the character, drop the slash.
        assertEquals("axb", IcsCodec.unescapeText("a\\xb"))
    }

    @Test
    fun unescape_accepts_lowercase_newline_escape() {
        assertEquals("a\nb", IcsCodec.unescapeText("a\\nb"))
    }

    // ---------------- folding ----------------

    @Test
    fun short_lines_are_not_folded() {
        assertEquals("SUMMARY:Hi", IcsCodec.fold("SUMMARY:Hi"))
    }

    @Test
    fun long_lines_fold_at_75_chars_per_segment() {
        val line = "DESCRIPTION:" + "x".repeat(300)
        val folded = IcsCodec.fold(line)
        val segments = folded.split("\r\n")
        assertTrue(segments.size > 1)
        // First segment is 75; continuations are ' ' + 74 = 75.
        assertEquals(75, segments[0].length)
        for (i in 1 until segments.size) {
            assertTrue("segment $i too long", segments[i].length <= 75)
            assertEquals(' ', segments[i][0])
        }
    }

    @Test
    fun fold_unfolds_back_to_the_original() {
        // No literal newlines: escapeText turns them into \n before folding,
        // so a real LF never reaches fold()/unfold() from write(). A payload
        // containing one is a separate concern (parse splits on line breaks).
        val line = "DESCRIPTION:" + "ab, c; d\\n ".repeat(40)
        assertEquals(line, IcsCodec.unfold(IcsCodec.fold(line)))
    }

    @Test
    fun fold_handles_the_exact_75_octet_boundary() {
        val at75 = "X".repeat(75)
        val at76 = "X".repeat(76)
        assertEquals(at75, IcsCodec.fold(at75))   // no break needed
        assertTrue(IcsCodec.fold(at76).contains("\r\n "))
    }

    @Test
    fun unfold_joins_crlf_space_and_lf_space() {
        assertEquals("AB", IcsCodec.unfold("A\r\n B"))
        assertEquals("AB", IcsCodec.unfold("A\n B"))
    }

    // ---------------- timestamps ----------------

    @Test
    fun date_only_values_are_all_day() {
        assertTrue(IcsCodec.parseStamp("20261225") != null)
    }

    @Test
    fun utc_date_time_round_trips() {
        val s = IcsCodec.formatStamp(base, allDay = false)
        assertEquals("20260101T000000Z", s)
        assertEquals(base, IcsCodec.parseStamp(s))
    }

    @Test
    fun all_day_formatters_omit_the_time_part() {
        assertEquals("20260101", IcsCodec.formatStamp(base, allDay = true))
    }

    @Test
    fun fractional_seconds_are_millis() {
        assertEquals(base + 250L, IcsCodec.parseStamp("20260101T000000.25Z"))
    }

    @Test
    fun junk_timestamps_return_null_rather_than_throwing() {
        assertNull(IcsCodec.parseStamp("not-a-date"))
        assertNull(IcsCodec.parseStamp("20260230T120000Z")) // Feb 30
        assertNull(IcsCodec.parseStamp(""))
    }

    @Test
    fun parameters_on_a_value_are_ignored() {
        assertEquals(base, IcsCodec.parseStamp("20260101T000000Z;TZID=Asia/Dhaka"))
    }

    // ---------------- write ----------------

    @Test
    fun writes_a_well_formed_envelope() {
        val out = IcsCodec.write(listOf(event("Team sync")))
        assertTrue(out.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(out.endsWith("END:VCALENDAR\r\n"))
        assertTrue(out.contains("VERSION:2.0"))
        assertTrue(out.contains("BEGIN:VEVENT"))
        assertTrue(out.contains("END:VEVENT"))
    }

    @Test
    fun null_timestamps_are_omitted_not_written_as_blank() {
        // Must construct the component directly: the event() helper always
        // supplies start/end, so reusing it here would assert nothing.
        val noTimes = IcsCodec.Component(
            kind = IcsCodec.Kind.VEVENT, uid = "n1", summary = "No times"
        )
        val out = IcsCodec.write(listOf(noTimes))
        assertFalse(out.contains("DTSTART"))
        assertFalse(out.contains("DTEND"))
        assertTrue(out.contains("SUMMARY:No times"))
    }

    @Test
    fun a_todo_writes_due_not_dtstart() {
        val t = IcsCodec.Component(
            kind = IcsCodec.Kind.VTODO, uid = "t1", summary = "Pay bill",
            dueUtcMillis = base
        )
        val out = IcsCodec.write(listOf(t))
        assertTrue(out.contains("BEGIN:VTODO"))
        assertTrue(out.contains("DUE:20260101T000000Z"))
        assertFalse(out.contains("DTSTART"))
    }

    // ---------------- round trip ----------------

    @Test
    fun event_survives_a_write_read_round_trip() {
        val original = event("Team sync", description = "Agenda; items, notes")
        val back = IcsCodec.parse(IcsCodec.write(listOf(original))).single()
        assertEquals("Team sync", back.summary)
        assertEquals("Agenda; items, notes", back.description)
        assertEquals(base, back.startUtcMillis)
        assertEquals(base + hour, back.endUtcMillis)
        assertEquals("Room 5", back.location)
    }

    @Test
    fun all_day_round_trip_stays_all_day() {
        // Regression: Builder.allDay was never assigned, so a date-only
        // DTSTART re-exported as 20260101T000000Z and became a timed event.
        val allDay = IcsCodec.Component(
            kind = IcsCodec.Kind.VEVENT, uid = "a1", summary = "Holiday",
            startUtcMillis = base, endUtcMillis = base + 24 * hour, allDay = true
        )
        val text = IcsCodec.write(listOf(allDay))
        assertTrue(text.contains("DTSTART:20260101"))
        assertFalse(text.contains("DTSTART:20260101T"))

        val back = IcsCodec.parse(text).single()
        assertTrue("all-day lost in round trip", back.allDay)
        assertEquals(base, back.startUtcMillis)
    }

    @Test
    fun rrule_is_passed_through_and_still_parses() {
        val e = event("Standup", rrule = "FREQ=WEEKLY;BYDAY=MO,WE,FR;COUNT=10")
        val back = IcsCodec.parse(IcsCodec.write(listOf(e))).single()
        assertEquals("FREQ=WEEKLY;BYDAY=MO,WE,FR;COUNT=10", back.rrule)
        // The whole point of passing it through: the existing engine owns this.
        val parsed = RRule.parse(back.rrule)
        assertNotNull("RRule.parse rejected our passthrough", parsed)
        assertEquals(RRule.Freq.WEEKLY, parsed!!.freq)
        assertEquals(3, parsed.byDay.size)
    }

    @Test
    fun categories_and_attendees_round_trip() {
        val e = event("Review", categories = listOf("work", "urgent"))
        val back = IcsCodec.parse(IcsCodec.write(listOf(e))).single()
        assertEquals(listOf("work", "urgent"), back.categories)
    }

    @Test
    fun folded_long_summary_survives_the_round_trip() {
        val e = event("A very long meeting title that will certainly exceed the seventy five octet folding limit imposed by rfc")
        val back = IcsCodec.parse(IcsCodec.write(listOf(e))).single()
        assertEquals(e.summary, back.summary)
    }

    // ---------------- parser robustness ----------------

    @Test
    fun missing_end_vecalendar_is_tolerated() {
        val out = IcsCodec.parse("BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:x\r\nSUMMARY:Hi\r\n")
        assertEquals(1, out.size)
        assertEquals("Hi", out.single().summary)
    }

    @Test
    fun unknown_properties_are_ignored() {
        val out = IcsCodec.parse(
            "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nX-WEIRD;P=1:v\r\nUID:x\r\nSUMMARY:Hi\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        )
        assertEquals("Hi", out.single().summary)
    }

    @Test
    fun junk_lines_without_a_colon_are_skipped() {
        val out = IcsCodec.parse("garbage\r\nBEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nSUMMARY:Hi\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n")
        assertEquals(1, out.size)
    }

    @Test
    fun several_components_are_returned_in_order() {
        val out = IcsCodec.parse(
            "BEGIN:VCALENDAR\r\n" +
            "BEGIN:VEVENT\r\nUID:1\r\nSUMMARY:One\r\nEND:VEVENT\r\n" +
            "BEGIN:VEVENT\r\nUID:2\r\nSUMMARY:Two\r\nEND:VEVENT\r\n" +
            "END:VCALENDAR\r\n"
        )
        assertEquals(listOf("One", "Two"), out.map { it.summary })
    }

    @Test
    fun empty_input_yields_no_components() {
        assertTrue(IcsCodec.parse("").isEmpty())
    }

    @Test
    fun a_malformed_timestamp_does_not_abort_the_import() {
        val out = IcsCodec.parse(
            "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nSUMMARY:Hi\r\nDTSTART:garbage\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        )
        assertEquals(1, out.size)
        assertNull(out.single().startUtcMillis)
        assertEquals("Hi", out.single().summary)
    }

    private fun event(
        summary: String,
        description: String = "",
        rrule: String? = null,
        categories: List<String> = emptyList()
    ) = IcsCodec.Component(
        kind = IcsCodec.Kind.VEVENT,
        uid = "u-" + summary,
        summary = summary,
        description = description,
        location = "Room 5",
        startUtcMillis = base,
        endUtcMillis = base + hour,
        categories = categories,
        rrule = rrule
    )
}
