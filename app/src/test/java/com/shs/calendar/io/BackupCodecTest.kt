package com.shs.calendar.io

import com.shs.calendar.io.BackupCodec.FormatException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** JSON backup envelope behaviour. Pure JVM (org.json test dependency). */
class BackupCodecTest {

    private fun backup(
        events: List<BackupCodec.Row> = emptyList(),
        tasks: List<BackupCodec.Row> = emptyList(),
        notes: List<BackupCodec.Row> = emptyList(),
        settings: List<BackupCodec.Row> = emptyList(),
        version: Int = BackupCodec.FORMAT_VERSION
    ) = BackupCodec.Backup(version, 1_700_000_000_000L, events, tasks, notes, settings)

    private fun row(vararg pairs: Pair<String, Any?>) =
        BackupCodec.Row(linkedMapOf(*pairs))

    // ---------------- writing ----------------

    @Test
    fun write_produces_readable_json_with_the_version() {
        val out = BackupCodec.write(backup(events = listOf(row("uid" to "a"))))
        assertTrue(out.contains("\"version\": ${BackupCodec.FORMAT_VERSION}"))
        assertTrue("should be pretty-printed, not a single line", out.contains("\n"))
        assertTrue(out.contains("uid"))
    }

    @Test
    fun null_fields_are_omitted_rather_than_written_as_null() {
        val out = BackupCodec.write(backup(events = listOf(row("uid" to "a", "summary" to null))))
        assertTrue(out.contains("uid"))
        assertFalse("null should be omitted, not written", out.contains("summary"))
    }

    @Test
    fun a_zero_version_is_replaced_with_the_current_one() {
        val out = BackupCodec.write(backup(version = 0))
        assertTrue(out.contains("\"version\": ${BackupCodec.FORMAT_VERSION}"))
    }

    // ---------------- round trip ----------------

    @Test
    fun a_full_backup_survives_a_round_trip() {
        val b = backup(
            events = listOf(row("uid" to "e1", "summary" to "বৈশাখী", "start" to 1_700_000_000_000L)),
            tasks = listOf(row("uid" to "t1", "done" to false)),
            notes = listOf(row("uid" to "n1", "tags" to "a")),
            settings = listOf(row("key" to "theme", "value" to "dark"))
        )
        val out = BackupCodec.parse(BackupCodec.write(b))
        assertEquals(b.version, out.version)
        assertEquals(1_700_000_000_000L, out.exportedAtMillis)
        assertEquals("e1", out.events[0].values["uid"])
        assertEquals("বৈশাখী", out.events[0].values["summary"])
        assertEquals(1_700_000_000_000L, out.events[0].values["start"])
        assertEquals(false, out.tasks[0].values["done"])
        assertEquals("dark", out.settings[0].values["value"])
    }

    @Test
    fun a_round_trip_preserves_the_row_count() {
        val b = backup(events = List(50) { row("uid" to "e$it") })
        assertEquals(50, BackupCodec.parse(BackupCodec.write(b)).events.size)
    }

    @Test
    fun an_empty_backup_round_trips() {
        val out = BackupCodec.parse(BackupCodec.write(backup()))
        assertEquals(0, out.totalRows)
    }

    // ---------------- version guard ----------------

    @Test
    fun a_newer_format_version_is_refused_rather_than_half_restored() {
        val raw = """{"version": ${BackupCodec.FORMAT_VERSION + 1}, "events": []}"""
        val e = assertThrows(FormatException::class.java) { BackupCodec.parse(raw) }
        assertTrue(e.message!!.contains("newer"))
    }

    @Test
    fun a_missing_version_is_refused() {
        val e = assertThrows(FormatException::class.java) { BackupCodec.parse("""{"events": []}""") }
        assertTrue(e.message!!.contains("version"))
    }

    // ---------------- leniency ----------------

    @Test
    fun a_missing_table_yields_an_empty_list_not_an_exception() {
        val out = BackupCodec.parse("""{"version": 1, "events": [{"uid":"e1"}]}""")
        assertEquals(1, out.events.size)
        assertTrue(out.tasks.isEmpty())
        assertTrue(out.notes.isEmpty())
    }

    @Test
    fun a_missing_export_timestamp_defaults_to_zero() {
        assertEquals(0L, BackupCodec.parse("""{"version": 1}""").exportedAtMillis)
    }

    @Test
    fun blank_input_is_rejected_with_a_clear_message() {
        val e = assertThrows(FormatException::class.java) { BackupCodec.parse("   ") }
        assertTrue(e.message!!.contains("empty"))
    }

    @Test
    fun malformed_json_is_rejected_not_silently_empty() {
        val e = assertThrows(FormatException::class.java) { BackupCodec.parse("{not json") }
        assertTrue(e.message!!.contains("valid JSON"))
    }

    @Test
    fun non_object_entries_in_a_table_are_skipped_not_fatal() {
        val out = BackupCodec.parse("""{"version": 1, "events": [{"uid":"e1"}, 7]}""")
        assertEquals(1, out.events.size)
    }
}
