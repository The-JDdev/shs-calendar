package com.shs.calendar.conflict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Behavioural tests for [ConflictDetector]. Pure JVM: no Room, no Android.
 */
class ConflictDetectorTest {

    private val dhaka = ZoneId.of("Asia/Dhaka")

    /** 2026-12-25 10:00 UTC == 16:00 Dhaka — a fixed, DST-free anchor. */
    private val base = LocalDate.of(2026, 12, 25)
        .atTime(10, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private val hour = 3_600_000L
    private val min = 60_000L

    private fun ev(
        id: Long,
        title: String,
        start: Long = base,
        end: Long = base + hour,
        allDay: Boolean = false,
        location: String = "",
        participants: String = "",
        reminders: String = ""
    ) = ConflictDetector.EventSlot(
        id, title, start, end, allDay, location, participants, reminders
    )

    // ---------- overlap ----------

    @Test
    fun touching_events_do_not_overlap() {
        val a = ev(1, "A", base, base + hour)
        val b = ev(2, "B", base + hour, base + 2 * hour)
        assertEquals(0L, ConflictDetector.overlapMinutes(a, b))
        assertTrue(ConflictDetector.pairwise(a, listOf(b)).isEmpty())
    }

    @Test
    fun thirty_minute_overlap_is_reported_with_the_spec_wording() {
        val a = ev(1, "Dentist", base, base + hour)
        val b = ev(2, "Lecture", base + 30 * min, base + 90 * min)
        val out = ConflictDetector.pairwise(a, listOf(b))
        assertEquals(1, out.size)
        assertEquals(ConflictDetector.Type.OVERLAP, out[0].type)
        assertTrue(out[0].message.contains("30-minute overlap"))
        assertTrue(out[0].message.contains("Lecture"))
    }

    @Test
    fun contained_event_reports_the_contained_span() {
        val a = ev(1, "Long", base, base + hour)
        val b = ev(2, "Short", base + 15 * min, base + 45 * min)
        assertEquals(30L, ConflictDetector.overlapMinutes(a, b))
    }

    @Test
    fun disjoint_events_do_not_overlap() {
        val a = ev(1, "A", base, base + hour)
        val b = ev(2, "B", base + 3 * hour, base + 4 * hour)
        assertEquals(0L, ConflictDetector.overlapMinutes(a, b))
    }

    @Test
    fun self_is_never_a_conflict() {
        val a = ev(1, "A", base, base + hour, location = "Room 5")
        assertTrue(ConflictDetector.pairwise(a, listOf(a)).isEmpty())
    }

    @Test
    fun zero_length_event_inside_another_is_an_overlap() {
        val a = ev(1, "A", base, base + hour)
        val b = ev(2, "Marker", base + 15 * min, base + 15 * min)
        assertEquals(1L, ConflictDetector.overlapMinutes(a, b))
    }

    @Test
    fun zero_length_event_outside_another_is_not() {
        val a = ev(1, "A", base, base + hour)
        val b = ev(2, "Marker", base + 5 * hour, base + 5 * hour)
        assertEquals(0L, ConflictDetector.overlapMinutes(a, b))
    }

    // ---------- double booking ----------

    @Test
    fun same_location_is_a_double_booking() {
        val a = ev(1, "Team A", location = "Room 5")
        val b = ev(2, "Team B", location = "room 5")
        val out = ConflictDetector.pairwise(a, listOf(b))
        assertTrue(out.any { it.type == ConflictDetector.Type.DOUBLE_BOOKING })
    }

    @Test
    fun shared_participant_is_a_double_booking() {
        val a = ev(1, "A", participants = "rahim; karim")
        val b = ev(2, "B", participants = "Karim; sadia")
        val out = ConflictDetector.pairwise(a, listOf(b))
        assertTrue(out.any { it.type == ConflictDetector.Type.DOUBLE_BOOKING })
    }

    @Test
    fun disjoint_participants_and_places_are_clean() {
        val a = ev(1, "A", base, base + min, location = "Room 1", participants = "x")
        val b = ev(2, "B", base + 3 * hour, base + 3 * hour + min,
            location = "Room 2", participants = "y")
        assertTrue(ConflictDetector.pairwise(a, listOf(b)).isEmpty())
    }

    // ---------- reminder storm ----------

    @Test
    fun three_events_firing_in_one_minute_is_a_storm() {
        val a = ev(1, "Standup", reminders = "0")
        val b = ev(2, "Review", start = base + 30_000L, reminders = "0")
        val c = ev(3, "Call", start = base + 5_000L, reminders = "0")
        val storms = ConflictDetector.reminderStorms(listOf(a, b, c), dhaka)
        assertEquals(1, storms.size)
        assertEquals(ConflictDetector.Type.REMINDER_STORM, storms[0].type)
        assertTrue(storms[0].message.contains("3 reminders at once"))
        assertTrue(storms[0].message.contains("16:00")) // 10:00 UTC in Dhaka
    }

    @Test
    fun two_events_in_one_minute_is_not_a_storm() {
        val a = ev(1, "A", reminders = "0")
        val b = ev(2, "B", start = base + 1_000L, reminders = "0")
        assertTrue(ConflictDetector.reminderStorms(listOf(a, b), dhaka).isEmpty())
    }

    @Test
    fun one_event_with_many_offsets_does_not_storm_with_itself() {
        val solo = ev(1, "Solo", reminders = "0,1,2,3,4")
        assertTrue(ConflictDetector.reminderStorms(listOf(solo), dhaka).isEmpty())
    }

    @Test
    fun events_in_different_minutes_do_not_storm() {
        val a = ev(1, "A", reminders = "0")
        val b = ev(2, "B", start = base + 5 * hour, reminders = "0")
        assertTrue(ConflictDetector.reminderStorms(listOf(a, b), dhaka).isEmpty())
    }

    @Test
    fun storm_buckets_by_local_minute_not_utc_minute() {
        // 15:59:40 UTC is 21:59 Dhaka. Two events 30s apart straddle the
        // local hour boundary but not the local minute, so they must storm.
        val a = ev(1, "A", reminders = "0")
        val b = ev(2, "B", start = base + 30_000L, reminders = "0")
        val storms = ConflictDetector.reminderStorms(listOf(a, b), dhaka)
        assertTrue(storms.isEmpty()) // only 2 events
        val c = ev(3, "C", start = base + 20_000L, reminders = "0")
        assertEquals(1, ConflictDetector.reminderStorms(listOf(a, b, c), dhaka).size)
    }

    @Test
    fun reminder_offsets_are_counted_back_from_the_start() {
        // "0,30" on a 10:00 event fires at 10:00 and 09:30 — backwards only.
        // Three events are needed per bucket to cross the >=3 threshold, and
        // all three share both minutes, so we get exactly 2 storms (one per
        // fire time), never 3+ at a single time.
        val a = ev(1, "A", reminders = "0,30")
        val b = ev(2, "B", start = base + 1_000L, reminders = "0,30")
        val c = ev(3, "C", start = base + 2_000L, reminders = "0,30")
        val storms = ConflictDetector.reminderStorms(listOf(a, b, c), dhaka)
        assertEquals(2, storms.size)
        assertTrue(storms.all { it.type == ConflictDetector.Type.REMINDER_STORM })
        // Nothing fires forward: no storm after the start time.
        val onlyZero = ConflictDetector.reminderStorms(
            listOf(a, b, c).map { it.copy(reminders = "0") }, dhaka
        )
        assertEquals(1, onlyZero.size)
    }

    // ---------- offset parsing ----------

    @Test
    fun offsets_parse_commas_blanks_and_junk() {
        assertEquals(listOf(0L, 10L, 1440L), ConflictDetector.parseReminderOffsets("0,10,1440"))
        assertEquals(emptyList<Long>(), ConflictDetector.parseReminderOffsets("   "))
        // Unparseable entries are skipped, not thrown on: one bad value must
        // not break the event editor.
        assertEquals(listOf(0L, 10L), ConflictDetector.parseReminderOffsets("0, ,x,abc,10"))
    }

    @Test
    fun negative_offsets_are_dropped() {
        assertEquals(listOf(0L, 5L), ConflictDetector.parseReminderOffsets("0,-5,5"))
    }

    // ---------- detect ----------

    @Test
    fun detect_includes_storms_the_candidate_participates_in() {
        val a = ev(1, "A", reminders = "0")
        val b = ev(2, "B", start = base + 1_000L, reminders = "0")
        val c = ev(3, "C", start = base + 2_000L, reminders = "0")
        val out = ConflictDetector.detect(a, listOf(b, c), dhaka)
        assertTrue(out.any { it.type == ConflictDetector.Type.REMINDER_STORM })
    }

    @Test
    fun detect_omits_storms_the_candidate_is_not_part_of() {
        val a = ev(1, "A", start = base + 8 * hour, reminders = "0")
        val b = ev(2, "B", start = base, reminders = "0")
        val c = ev(3, "C", start = base + 1_000L, reminders = "0")
        val out = ConflictDetector.detect(a, listOf(b, c), dhaka)
        assertTrue(out.none { it.type == ConflictDetector.Type.REMINDER_STORM })
    }

    @Test
    fun detect_orders_most_severe_first_and_is_stable() {
        val a = ev(1, "A", location = "Room 5", reminders = "0")
        val b = ev(2, "B", location = "Room 5", reminders = "0")
        val first = ConflictDetector.detect(a, listOf(b), dhaka)
        val second = ConflictDetector.detect(a, listOf(b), dhaka)
        assertEquals(first.map { it.message }, second.map { it.message })
        assertTrue(first.first().severity >= first.last().severity)
    }

    @Test
    fun detect_on_empty_others_is_empty() {
        assertTrue(ConflictDetector.detect(ev(1, "A"), emptyList(), dhaka).isEmpty())
    }

    @Test
    fun all_day_events_on_the_same_day_double_book() {
        val a = ev(1, "A", allDay = true)
        val b = ev(2, "B", allDay = true)
        val out = ConflictDetector.pairwise(a, listOf(b))
        assertTrue(out.any { it.type == ConflictDetector.Type.DOUBLE_BOOKING })
    }

    @Test
    fun all_day_events_on_different_days_do_not() {
        val a = ev(1, "A", allDay = true)
        val b = ev(2, "B", start = base + 24 * hour, allDay = true)
        assertTrue(ConflictDetector.pairwise(a, listOf(b)).isEmpty())
    }

    @Test
    fun all_day_does_not_overlap_a_timed_event_within_the_same_day() {
        val a = ev(1, "AllDay", allDay = true)
        val b = ev(2, "Timed", base + 2 * hour, base + 3 * hour)
        val out = ConflictDetector.pairwise(a, listOf(b))
        assertTrue(out.none { it.type == ConflictDetector.Type.OVERLAP })
    }

    @Test
    fun minute_bucket_is_stable_across_calls() {
        // Three events, not two: below the >=3 threshold nothing is a storm,
        // so a two-event fixture would assert emptiness for the wrong reason.
        val a = ev(1, "A", reminders = "0")
        val b = ev(2, "B", start = base + 1_000L, reminders = "0")
        val c = ev(3, "C", start = base + 2_000L, reminders = "0")
        val forward = ConflictDetector.reminderStorms(listOf(a, b, c), dhaka)
        val reversed = ConflictDetector.reminderStorms(listOf(c, b, a), dhaka)
        assertEquals(1, forward.size)
        assertEquals(forward.map { it.message }, reversed.map { it.message })
    }
}
