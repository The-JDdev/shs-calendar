package com.shs.calendar.io

/**
 * CSV reader/writer for event and task export (RFC 4180).
 *
 * Deliberately minimal: a header row of column names, quoted values, and no
 * type information. Pure Kotlin so it is unit-testable on the JVM.
 *
 * Values are written as ISO-8601 (or epoch millis) so a round trip through a
 * spreadsheet stays sortable. A UTF-8 BOM is emitted because Excel misreads
 * non-ASCII CSV without one, and stripped on read if present.
 */
object CsvCodec {

    const val BOM = "﻿"

    /** Column order for the export. Stable so diffs of exported files are readable. */
    val EVENT_COLUMNS = listOf(
        "uid", "summary", "description", "location", "start", "end", "allDay", "rrule"
    )
    val TASK_COLUMNS = listOf("uid", "title", "notes", "due", "done", "priority")

    // ---------------- writing ----------------

    fun writeRows(headers: List<String>, rows: List<List<String?>>): String {
        val sb = StringBuilder()
        sb.append(BOM)
        sb.append(headers.joinToString(",") { quote(it) }).append("\r\n")
        for (row in rows) {
            sb.append(row.joinToString(",") { quote(it ?: "") }).append("\r\n")
        }
        return sb.toString()
    }

    /** Wraps in quotes when the value contains a delimiter, quote, CR or LF. */
    fun quote(raw: String): String {
        val needs = raw.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needs) return raw
        return "\"" + raw.replace("\"", "\"\"") + "\""
    }

    // ---------------- reading ----------------

    /**
     * Parses CSV into a header row plus data rows. Tolerates a UTF-8 BOM,
     * both CRLF and LF line endings, quoted fields containing newlines, and a
     * missing final newline.
     */
    fun parse(raw: String): Pair<List<String>, List<List<String>>> {
        val text = raw.removePrefix(BOM)
        val records = splitRecords(text)
        if (records.isEmpty()) return emptyList<String>() to emptyList()
        val headers = records.first()
        val rows = records.drop(1).filter { r -> r.any { it.isNotBlank() } }
        return headers to rows
    }

    /** Splits on newlines and commas, honouring quoted fields and doubled quotes. */
    private fun splitRecords(text: String): List<List<String>> {
        val out = ArrayList<List<String>>()
        var field = StringBuilder()
        var record = ArrayList<String>()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                inQuotes && ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"'); i++ // escaped quote
                }
                ch == '"' -> inQuotes = !inQuotes
                inQuotes -> field.append(ch)
                ch == ',' -> {
                    record += field.toString(); field = StringBuilder()
                }
                ch == '\r' -> {
                    // Swallow CR; the following LF (if any) ends the record.
                    if (i + 1 < text.length && text[i + 1] == '\n') i++
                    record += field.toString(); field = StringBuilder()
                    out += record; record = ArrayList()
                }
                ch == '\n' -> {
                    record += field.toString(); field = StringBuilder()
                    out += record; record = ArrayList()
                }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || record.isNotEmpty()) {
            record += field.toString()
            out += record
        }
        return out
    }
}
