package com.shs.calendar.io

/**
 * RFC 5545 iCalendar reader/writer for VEVENT, VTODO and VJOURNAL.
 *
 * Pure Kotlin with no Android or Room types so it is unit-testable on the JVM.
 * Recurrence is handled by passing the raw RRULE line straight through to
 * [com.shs.calendar.data.recurrence.RRule] — this codec deliberately does not
 * re-implement RFC 5545 recurrence parsing.
 *
 * Folding (RFC 5545 3.1) and text escaping (3.3.11) are implemented because
 * real-world exports from Outlook and Google Calendar both rely on them.
 */
object IcsCodec {

    const val PRODID = "-//SHS Calendar//shs-calendar//EN"
    private const val CRLF = "\r\n"

    // ---------------- escaping ----------------

    /** Escapes a value for a TEXT property (RFC 5545 3.3.11). */
    fun escapeText(raw: String): String {
        val sb = StringBuilder(raw.length + 8)
        for (ch in raw) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                ';' -> sb.append("\\;")
                ',' -> sb.append("\\,")
                '\n' -> sb.append("\\n")
                '\r' -> {}
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** Reverses [escapeText], tolerating unescaped semicolons from lenient exporters. */
    fun unescapeText(raw: String): String {
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (ch == '\\' && i + 1 < raw.length) {
                when (val next = raw[i + 1]) {
                    'n', 'N' -> sb.append('\n')
                    '\\' -> sb.append('\\')
                    ';' -> sb.append(';')
                    ',' -> sb.append(',')
                    else -> sb.append(next)
                }
                i += 2
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }

    /** Undoes RFC 5545 line folding: a CRLF followed by a space or tab continues the line. */
    fun unfold(raw: String): String =
        raw.replace("\r\n ", "").replace("\r\n\t", "")
            .replace("\n ", "").replace("\n\t", "")
            .replace("\r ", "")

    /**
     * Folds a content line to 75 octets, continuing with a single space.
     *
     * Counts characters rather than octets: iCalendar payloads are largely
     * ASCII, and folding a multibyte line slightly early is RFC-conformant,
     * whereas overflowing 75 octets is not.
     */
    fun fold(line: String): String {
        if (line.length <= 75) return line
        val out = StringBuilder(line.length + 8)
        out.append(line[0])
        var count = 1
        for (i in 1 until line.length) {
            if (count == 75) {
                // Break before writing the next char; the leading space of the
                // continuation line occupies one of the 75.
                out.append(CRLF).append(' ')
                count = 1
            }
            out.append(line[i])
            count++
        }
        return out.toString()
    }

    // ---------------- component model ----------------

    enum class Kind { VEVENT, VTODO, VJOURNAL }

    /**
     * A parsed or to-be-written iCalendar component. One flat shape covers all
     * three kinds: only the properties present in the source are populated, so
     * a round trip does not invent fields the exporter never had.
     *
     * [rrule] holds the raw value only (no `RRULE:` prefix), matching how
     * [com.shs.calendar.data.entity.EventEntity] stores it.
     */
    data class Component(
        val kind: Kind,
        val uid: String = "",
        val summary: String = "",
        val description: String = "",
        val location: String = "",
        val url: String = "",
        val categories: List<String> = emptyList(),
        val startUtcMillis: Long? = null,
        val endUtcMillis: Long? = null,
        val dueUtcMillis: Long? = null,
        val allDay: Boolean = false,
        val status: String = "",
        val priority: Int? = null,
        val organizer: String = "",
        val participants: List<String> = emptyList(),
        val reminders: String = "",
        val rrule: String? = null,
        val createdAtUtcMillis: Long = 0L,
        val updatedAtUtcMillis: Long = 0L
    )

    // ---------------- writing ----------------

    /** Serialises components into a single VCALENDAR, one CRLF-terminated line per property. */
    fun write(components: List<Component>, calendarName: String = "SHS Calendar"): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR").append(CRLF)
        sb.append("VERSION:2.0").append(CRLF)
        sb.append("PRODID:").append(PRODID).append(CRLF)
        sb.append("CALSCALE:GREGORIAN").append(CRLF)
        if (calendarName.isNotBlank()) {
            sb.append("X-WR-CALNAME:").append(escapeText(calendarName)).append(CRLF)
        }
        for (c in components) writeComponent(sb, c)
        sb.append("END:VCALENDAR").append(CRLF)
        return sb.toString()
    }

    private fun writeComponent(sb: StringBuilder, c: Component) {
        sb.append("BEGIN:").append(c.kind.name).append(CRLF)
        prop(sb, "UID", c.uid)
        prop(sb, "SUMMARY", escapeText(c.summary))
        if (c.description.isNotEmpty()) prop(sb, "DESCRIPTION", escapeText(c.description))
        if (c.location.isNotEmpty()) prop(sb, "LOCATION", escapeText(c.location))
        if (c.url.isNotEmpty()) prop(sb, "URL", c.url)
        if (c.categories.isNotEmpty()) {
            prop(sb, "CATEGORIES", c.categories.joinToString(",") { escapeText(it) })
        }
        if (c.status.isNotEmpty()) prop(sb, "STATUS", c.status)
        c.priority?.let { prop(sb, "PRIORITY", it.toString()) }
        if (c.organizer.isNotEmpty()) prop(sb, "ORGANIZER", c.organizer)
        for (p in c.participants) prop(sb, "ATTENDEE", p)
        if (c.kind == Kind.VEVENT) {
            stamp(sb, "DTSTART", c.startUtcMillis, c.allDay)
            stamp(sb, "DTEND", c.endUtcMillis, c.allDay)
        } else if (c.kind == Kind.VTODO) {
            stamp(sb, "DUE", c.dueUtcMillis, c.allDay)
        }
        if (c.createdAtUtcMillis > 0) prop(sb, "CREATED", formatUtc(c.createdAtUtcMillis))
        if (c.updatedAtUtcMillis > 0) prop(sb, "LAST-MODIFIED", formatUtc(c.updatedAtUtcMillis))
        // Recurrence is passed through verbatim: RRule.parse() owns RFC 5545
        // recurrence semantics, so this codec must not re-interpret the value.
        c.rrule?.takeIf { it.isNotBlank() }?.let { prop(sb, "RRULE", it) }
        sb.append("END:").append(c.kind.name).append(CRLF)
    }

    private fun prop(sb: StringBuilder, name: String, value: String) {
        // Fold the whole content line: the property name and colon count
        // toward the 75-octet limit, so folding the value alone is wrong.
        sb.append(fold("$name:$value")).append(CRLF)
    }

    private fun stamp(sb: StringBuilder, name: String, millis: Long?, allDay: Boolean) {
        if (millis == null) return
        prop(sb, name, formatStamp(millis, allDay))
    }

    /** `20261225T160000Z` for timed, `20261225` for all-day (RFC 5545 3.3.4/3.3.5). */
    fun formatStamp(millis: Long, allDay: Boolean): String {
        val t = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC)
        return if (allDay) {
            "%04d%02d%02d".format(t.year, t.monthValue, t.dayOfMonth)
        } else {
            "%04d%02d%02dT%02d%02d%02dZ".format(
                t.year, t.monthValue, t.dayOfMonth, t.hour, t.minute, t.second
            )
        }
    }

    private fun formatUtc(millis: Long): String = formatStamp(millis, allDay = false)

    // ---------------- reading ----------------

    /**
     * Parses a VCALENDAR payload into components.
     *
     * Defensive by design, because real exports are messy: unknown properties
     * are ignored, a missing END:VCALENDAR is tolerated, and a malformed
     * timestamp yields null rather than aborting the whole import.
     */
    fun parse(raw: String): List<Component> {
        val lines = unfold(raw).split("\r\n", "\n")
        val out = ArrayList<Component>()
        var current: Builder? = null
        fun flush() {
            current?.build()?.let { out += it }
            current = null
        }
        for (line in lines) {
            if (line.isBlank()) continue
            val sep = line.indexOf(':')
            if (sep < 0) continue // not a content line; ignore
            // Parameters ("DTSTART;TZID=Asia/Dhaka") precede the colon; keep
            // the name but note that a parametrised value is still UTC below.
            val name = line.substring(0, sep).substringBefore(';').uppercase()
            val value = line.substring(sep + 1)
            when {
                name == "BEGIN" -> {
                    // A component missing its END: would otherwise be dropped.
                    flush()
                    val kind = runCatching { Kind.valueOf(value.trim().uppercase()) }.getOrNull()
                    if (kind != null) current = Builder(kind)
                }
                name == "END" -> flush()
                // Bind to a val first: `current` is captured by flush(), so the
                // compiler cannot smart-cast it inside this when branch.
                else -> current?.let { it.put(name, value) }
            }
        }
        // A truncated file (no END:VCALENDAR, or a final component with no
        // END:) must still yield what was parsed rather than nothing.
        flush()
        return out
    }

    /** Accumulates properties for one component while parsing. */
    private class Builder(val kind: Kind) {
        private var uid = ""
        private var summary = ""
        private var description = ""
        private var location = ""
        private var url = ""
        private var categories = emptyList<String>()
        private var start: Long? = null
        private var end: Long? = null
        private var due: Long? = null
        private var allDay = false
        private var status = ""
        private var priority: Int? = null
        private var organizer = ""
        private var participants = ArrayList<String>()
        private var rrule: String? = null
        private var created = 0L
        private var updated = 0L

        fun put(name: String, value: String) {
            when (name) {
                "UID" -> uid = value.trim()
                "SUMMARY" -> summary = unescapeText(value)
                "DESCRIPTION" -> description = unescapeText(value)
                "LOCATION" -> location = unescapeText(value)
                "URL" -> url = value.trim()
                "CATEGORIES" -> categories = value.split(',').map { unescapeText(it.trim()) }
                "STATUS" -> status = value.trim().uppercase()
                "PRIORITY" -> priority = value.trim().toIntOrNull()
                "ORGANIZER" -> organizer = value.trim()
                "ATTENDEE" -> participants += value.trim()
                "RRULE" -> rrule = value.trim()
                "CREATED" -> created = parseStamp(value) ?: 0L
                "LAST-MODIFIED" -> updated = parseStamp(value) ?: 0L
                "DTSTART" -> {
                    start = parseStamp(value)
                    // A date-only DTSTART means an all-day event; without this
                    // the re-export would turn 20261225 into 20261225T000000Z.
                    if (isDateOnly(value)) allDay = true
                }
                "DTEND" -> end = parseStamp(value)
                "DUE" -> due = parseStamp(value)
            }
        }

        fun build() = Component(
            kind = kind,
            uid = uid,
            summary = summary,
            description = description,
            location = location,
            url = url,
            categories = categories,
            startUtcMillis = start,
            endUtcMillis = end,
            dueUtcMillis = due,
            allDay = allDay,
            status = status,
            priority = priority,
            organizer = organizer,
            participants = participants,
            rrule = rrule,
            createdAtUtcMillis = created,
            updatedAtUtcMillis = updated
        )
    }

    /**
     * True for the `VALUE=DATE` form (`20261225`) as opposed to a date-time
     * (`20261225T160000Z`). Leading parameters such as `;TZID=` are ignored.
     */
    private fun isDateOnly(raw: String): Boolean {
        val v = raw.trim().substringBefore(';')
        return v.length == 8 && v.all { it.isDigit() }
    }

    /**
     * Parses `20261225T160000Z`, `20261225` or the extended `20261225T160000`
     * form, optionally with fractional seconds. Returns null on junk so one
     * bad property cannot abort a whole import.
     */
    fun parseStamp(raw: String): Long? {
        val v = raw.trim().substringBefore(';')
        return try {
            when {
                isDateOnly(raw) ->
                    java.time.LocalDate.of(
                        v.substring(0, 4).toInt(),
                        v.substring(4, 6).toInt(),
                        v.substring(6, 8).toInt()
                    ).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

                v.length >= 15 && v[8] == 'T' && v.substring(9, 15).all { it.isDigit() } -> {
                    val base = java.time.LocalDateTime.of(
                        v.substring(0, 4).toInt(),
                        v.substring(4, 6).toInt(),
                        v.substring(6, 8).toInt(),
                        v.substring(9, 11).toInt(),
                        v.substring(11, 13).toInt(),
                        v.substring(13, 15).toInt()
                    ).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
                    // Optional fractional seconds: ".25" or ".250" -> millis.
                    if (v.length > 15 && v[15] == '.') {
                        val digits = v.drop(16).takeWhile { it.isDigit() }.padEnd(3, '0')
                        base + digits.take(3).toLong()
                    } else {
                        base
                    }
                }

                else -> null
            }
        } catch (e: java.time.DateTimeException) {
            null // malformed date (e.g. Feb 30) — skip rather than fail the import
        }
    }
}
