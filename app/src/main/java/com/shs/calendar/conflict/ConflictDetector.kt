package com.shs.calendar.conflict

/**
 * Pure, deterministic conflict rules for event save and event list.
 *
 * Deliberately free of Room/Android types: callers map their entities onto
 * [EventSlot] so the rules stay unit-testable on a plain JVM.
 */
object ConflictDetector {

    /**
     * The minimum shape of an event the rules need. [reminders] follows the
     * [com.shs.calendar.data.entity.EventEntity] convention: comma-separated
     * minutes-before-start offsets, e.g. "0,10,1440".
     */
    data class EventSlot(
        val id: Long,
        val title: String,
        val startUtcMillis: Long,
        val endUtcMillis: Long,
        val allDay: Boolean = false,
        val location: String = "",
        val participants: String = "",
        val reminders: String = ""
    )

    enum class Type { OVERLAP, DOUBLE_BOOKING, REMINDER_STORM }

    /**
     * A user-facing warning. [message] is already display-ready and carries
     * the "⚠️ 30-minute overlap" phrasing required by the spec.
     */
    data class Conflict(
        val type: Type,
        val message: String,
        /** Id of the *other* event involved; -1 for non-pairwise rules. */
        val withEventId: Long = -1L,
        val severity: Int = 1,
        /**
         * For [Type.REMINDER_STORM] this is the fire-minute bucket (epoch
         * millis) that the storm covers, so callers can tell whether a
         * particular event participates without parsing the message.
         * 0 for pairwise conflicts, which are not time-bucketed.
         */
        val atUtcMillis: Long = 0L
    )

    /**
     * Overlap and double-booking between [candidate] and [others].
     *
     * Intervals are half-open: an event ending exactly when another starts is
     * NOT an overlap. Zero-length events still count at their own instant, so
     * a reminder-style marker never silently disappears from the comparison.
     */
    fun pairwise(candidate: EventSlot, others: List<EventSlot>): List<Conflict> {
        val out = ArrayList<Conflict>()
        for (other in others) {
            if (other.id == candidate.id) continue
            if (!candidate.allDay && !other.allDay && !overlaps(candidate, other)) continue
            if (candidate.allDay && other.allDay) {
                // Two all-day events on the same day double-book it, even
                // though their millisecond ranges are set by convention.
                if (sameUtcDay(candidate, other)) {
                    out += Conflict(
                        Type.DOUBLE_BOOKING,
                        "⚠️ Double-booked: '${other.title}' is also on this day",
                        other.id,
                        2
                    )
                }
                continue
            }
            val minutes = overlapMinutes(candidate, other)
            if (minutes > 0) {
                out += Conflict(
                    Type.OVERLAP,
                    "⚠️ $minutes-minute overlap with '${other.title}'",
                    other.id,
                    2
                )
            }
            if (sharesResource(candidate, other)) {
                out += Conflict(
                    Type.DOUBLE_BOOKING,
                    "⚠️ Double-booked: '${other.title}' at ${sharedResource(candidate, other)}",
                    other.id,
                    2
                )
            }
        }
        return out
    }

    /** Half-open overlap test: touching endpoints do not overlap. */
    private fun overlaps(a: EventSlot, b: EventSlot): Boolean {
        val az = a.endUtcMillis <= a.startUtcMillis
        val bz = b.endUtcMillis <= b.startUtcMillis
        if (az && bz) return a.startUtcMillis == b.startUtcMillis
        if (az) return a.startUtcMillis >= b.startUtcMillis && a.startUtcMillis < b.endUtcMillis
        if (bz) return b.startUtcMillis >= a.startUtcMillis && b.startUtcMillis < a.endUtcMillis
        return a.startUtcMillis < b.endUtcMillis && b.startUtcMillis < a.endUtcMillis
    }

    /** Overlap in whole minutes, floored, for the spec's "30-minute" wording. */
    fun overlapMinutes(a: EventSlot, b: EventSlot): Long {
        if (!overlaps(a, b)) return 0L
        val start = maxOf(a.startUtcMillis, b.startUtcMillis)
        val end = minOf(a.endUtcMillis, b.endUtcMillis)
        val ms = maxOf(0L, end - start)
        return if (ms == 0L) 1L else (ms / 60_000L)
    }

    /**
     * A "reminder storm" is three or more reminders across the same event set
     * that land within the same one-minute bucket — the spec's "≥3 reminders
     * same minute" rule. Grouping by minute (not by exact instant) is what
     * makes the rule useful: three alarms 40 seconds apart still annoy.
     */
    fun reminderStorms(events: List<EventSlot>, zone: java.time.ZoneId): List<Conflict> {
        val byMinute = HashMap<Long, MutableList<EventSlot>>()
        for (e in events) {
            for (offset in parseReminderOffsets(e.reminders)) {
                // Subtract the minutes-before-start offset to get fire time.
                // Must use the same minuteBucket() as detect(), or the two
                // sides of the participation check would disagree.
                val bucket = minuteBucket(e.startUtcMillis - offset * 60_000L, zone)
                byMinute.getOrPut(bucket) { ArrayList() }.add(e)
            }
        }
        val out = ArrayList<Conflict>()
        for ((bucket, group) in byMinute) {
            if (group.size < REMINDER_STORM_THRESHOLD) continue
            val titles = group.map { "'${it.title}'" }.sorted().joinToString(", ")
            val when0 = java.time.Instant.ofEpochMilli(bucket).atZone(zone)
            out += Conflict(
                Type.REMINDER_STORM,
                "⚠️ ${group.size} reminders at once (${formatMinute(when0)}): $titles",
                -1L,
                1,
                bucket
            )
        }
        return out.sortedBy { it.message }
    }

    /**
     * All rules at once, most severe first then by message so the order is
     * stable for a given input set.
     */
    fun detect(candidate: EventSlot, others: List<EventSlot>, zone: java.time.ZoneId): List<Conflict> {
        // A storm only concerns the candidate if the candidate itself fires a
        // reminder in that minute. Comparing against withEventId == -1 alone
        // would match every storm and make this guard a no-op.
        val candidateMinutes = reminderFireMinutes(candidate, zone)
        val storms = reminderStorms(others + candidate, zone)
            .filter { it.atUtcMillis in candidateMinutes }
        return (pairwise(candidate, others) + storms)
            .distinct()
            .sortedWith(compareByDescending<Conflict> { it.severity }.thenBy { it.message })
    }

    /** Distinct fire-minute buckets (epoch millis) contributed by one event. */
    private fun reminderFireMinutes(e: EventSlot, zone: java.time.ZoneId): Set<Long> {
        val out = HashSet<Long>()
        for (offset in parseReminderOffsets(e.reminders)) {
            out += minuteBucket(e.startUtcMillis - offset * 60_000L, zone)
        }
        return out
    }

    /** Truncates an instant to the containing wall-clock minute in [zone]. */
    private fun minuteBucket(utcMillis: Long, zone: java.time.ZoneId): Long =
        java.time.Instant.ofEpochMilli(utcMillis)
            .atZone(zone)
            .truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
            .toInstant()
            .toEpochMilli()

    private fun formatMinute(z: java.time.ZonedDateTime): String =
        "%02d:%02d".format(z.hour, z.minute)

    /**
     * Parses "0,10,1440" into [0, 10, 1440]. Blank input yields an empty list,
     * and unparseable entries are skipped rather than throwing, so one bad
     * value cannot break the editor screen.
     */
    fun parseReminderOffsets(raw: String): List<Long> {
        if (raw.isBlank()) return emptyList()
        return raw.split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { it >= 0 }
    }

    private fun sharedResource(a: EventSlot, b: EventSlot): String {
        val la = a.location.trim()
        return if (la.isNotEmpty() && la.equals(b.location.trim(), ignoreCase = true)) la else ""
    }

    private fun sharesResource(a: EventSlot, b: EventSlot): Boolean {
        if (sharedResource(a, b).isNotEmpty()) return true
        val pa = participantSet(a.participants)
        return pa.isNotEmpty() && pa.intersect(participantSet(b.participants)).isNotEmpty()
    }

    private fun participantSet(raw: String): Set<String> =
        raw.split(';', ',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    private fun sameUtcDay(a: EventSlot, b: EventSlot): Boolean {
        val za = java.time.Instant.ofEpochMilli(a.startUtcMillis).atZone(java.time.ZoneOffset.UTC)
        val zb = java.time.Instant.ofEpochMilli(b.startUtcMillis).atZone(java.time.ZoneOffset.UTC)
        return za.toLocalDate() == zb.toLocalDate()
    }

    /** Spec: "≥3 reminders same minute" is a storm. */
    const val REMINDER_STORM_THRESHOLD = 3
}
