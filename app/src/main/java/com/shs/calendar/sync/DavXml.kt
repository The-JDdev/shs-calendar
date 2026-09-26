package com.shs.calendar.sync

/**
 * Minimal DAV multistatus reader.
 *
 * Deliberately not a general XML library: the app ships no XML dependency beyond
 * what the platform provides, and CalDAV responses are a small, known shape.
 *
 * Two response shapes matter to the client:
 *  - PROPFIND -> a <d:multistatus> of <d:response>, each carrying <d:href> and
 *    zero or more <d:propstat> with <d:prop> and <d:status>.
 *  - REPORT  -> the same envelope, but each response additionally carries the
 *    calendar data in a <d:prop><cal:calendar-data> element.
 *
 * Everything here is pure string/XML handling with no I/O, so it is unit-tested
 * against captured samples.
 */
data class DavResponse(
    val href: String,
    /** HTTP status per property, keyed by property name, e.g. "200 OK" / "404 Not Found". */
    val propStatus: Map<String, String> = emptyMap(),
    /** Text content of each returned property, keyed by property name. */
    val propValues: Map<String, String> = emptyMap(),
    /** iCalendar payload from cal:calendar-data, when the response carried one. */
    val calendarData: String? = null
) {
    /**
     * Extracts the numeric HTTP status from a DAV status line.
     *
     * RFC 4918 status values are a full status line ("HTTP/1.1 200 OK"), not a
     * bare code, so a naive startsWith("2") test is false for every real
     * response and every property reads as absent. Scanning for the first
     * all-digit token also tolerates the HTTP/2 form, which has no version.
     */
    fun statusCode(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        return raw.trim()
            .split(Regex("\\s+"))
            .firstOrNull { it.isNotEmpty() && it.all { ch -> ch.isDigit() } }
            ?.toIntOrNull()
    }

    /** True when the named property was returned with a 2xx status. */
    fun has(propertyName: String): Boolean =
        statusCode(propStatus[propertyName])?.let { it in 200..299 } == true
}

object DavXml {

    /**
     * Strips namespace prefixes from element names: "d:href" -> "href",
     * "cal:calendar-data" -> "calendar-data". Element names are compared
     * case-sensitively after this, matching the XML spec.
     */
    private fun localName(raw: String): String {
        val noPrefix = raw.substringAfterLast(':')
        return noPrefix.trim()
    }

    /**
     * Splits a <response> body into (propertyName -> text) and (propertyName -> status).
     *
     * Hand-rolled rather than regex-scanning the whole document so that nested
     * elements (notably cal:calendar-data, whose body contains the .ics text with
     * its own angle-bracket-free but newline-heavy content) are captured whole.
     */
    private fun parseProps(body: String): Pair<Map<String, String>, Map<String, String>> {
        val values = LinkedHashMap<String, String>()
        val statuses = LinkedHashMap<String, String>()

        // Each <propstat> groups properties that share one HTTP status.
        val propstatBlocks = Regex("""<[A-Za-z0-9_.:-]*:?propstat[^>]*>(.*?)</[A-Za-z0-9_.:-]*:?propstat>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .findAll(body)
            .map { it.groupValues[1] }
            .toList()
            .ifEmpty { listOf(body) }

        for (block in propstatBlocks) {
            val status = Regex("""<[A-Za-z0-9_.:-]*:?status[^>]*>(.*?)</[A-Za-z0-9_.:-]*:?status>""",
                RegexOption.DOT_MATCHES_ALL)
                .find(block)?.groupValues?.get(1)?.trim().orEmpty()

            // A property element may carry its own attributes (e.g. calendar-data
            // has a version and content-type); only the name is significant here.
            // A property may legitimately carry no value, in which case the
            // server writes it self-closing: <d:owner/>. The leaf regex below
            // needs a closing tag, so those must be matched separately or the
            // property is dropped along with the HTTP status that proves the
            // server was asked about it and found nothing.
            Regex("""<([A-Za-z0-9_.:-]+)((?:\s[^>]*)?)/>""")
                .findAll(block)
                .forEach { m ->
                    val name = localName(m.groupValues[1])
                    if (name.isEmpty() || name.equals("status", true) || name.equals("prop", true)) return@forEach
                    statuses[name] = status
                    values.putIfAbsent(name, "")
                }

            // The body is [^<]* so that only LEAF elements match: with a dot-all
            // body the enclosing <prop> would pair with </prop> first, swallow
            // every child, and be skipped by the guard below -- losing all values.
            Regex("""<([A-Za-z0-9_.:-]+)((?:\s[^>]*)?)>([^<]*)</\1>""")
                .findAll(block)
                .forEach { m ->
                    val name = localName(m.groupValues[1])
                    if (name.isEmpty() || name.equals("status", true) || name.equals("prop", true)) return@forEach
                    statuses[name] = status
                    values[name] = m.groupValues[3].trim()
                }
        }
        return values to statuses
    }

    /**
     * Parses a multistatus document into responses.
     *
     * Returns an empty list for an empty or non-multistatus body rather than
     * throwing: a server that answers 207 with nothing useful is a sync state,
     * not a crash.
     */
    fun parseMultistatus(xml: String): List<DavResponse> {
        if (xml.isBlank()) return emptyList()
        if (!xml.contains("multistatus", ignoreCase = true)) return emptyList()

        val out = ArrayList<DavResponse>()
        Regex("""<[A-Za-z0-9_.:-]*:?response[^>]*>(.*?)</[A-Za-z0-9_.:-]*:?response>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .findAll(xml)
            .forEach { m ->
                val body = m.groupValues[1]
                val href = Regex("""<[A-Za-z0-9_.:-]*:?href[^>]*>(.*?)</[A-Za-z0-9_.:-]*:?href>""",
                    RegexOption.DOT_MATCHES_ALL)
                    .find(body)?.groupValues?.get(1)?.trim().orEmpty()
                if (href.isEmpty()) return@forEach

                val (values, statuses) = parseProps(body)
                out.add(
                    DavResponse(
                        href = href,
                        propStatus = statuses,
                        propValues = values,
                        calendarData = values["calendar-data"]
                    )
                )
            }
        return out
    }
}
