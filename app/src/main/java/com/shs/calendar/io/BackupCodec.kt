package com.shs.calendar.io

import org.json.JSONArray
import org.json.JSONObject

/**
 * Full backup/restore as plain JSON.
 *
 * Per the milestone spec: a user-chosen file (SAF), and "no data locked in
 * private formats" — so this is human-readable JSON, not a zip, not encrypted,
 * not a bespoke binary container. Anyone can open a backup in a text editor.
 *
 * The envelope carries a [FORMAT_VERSION] so a future schema change can migrate
 * old backups instead of failing them. Bump it and add a branch in [parse].
 *
 * Written against org.json, which ships inside the Android platform; the JVM
 * unit tests get the same API via the test-only org.json dependency.
 *
 * [parse] is deliberately lenient about missing arrays (an older or partial
 * backup yields empty lists rather than throwing) but strict about the version,
 * because restoring a file we cannot interpret risks silently losing data.
 */
object BackupCodec {

    const val FORMAT_VERSION = 1
    const val KEY_VERSION = "version"
    const val KEY_EXPORTED_AT = "exportedAt"
    const val KEY_EVENTS = "events"
    const val KEY_TASKS = "tasks"
    const val KEY_NOTES = "notes"
    const val KEY_SETTINGS = "settings"

    /** One backed-up row. Nullable fields are omitted rather than written null. */
    data class Row(val values: Map<String, Any?>)

    /** The whole backup: a version plus flat tables, each a list of [Row]. */
    data class Backup(
        val version: Int,
        val exportedAtMillis: Long,
        val events: List<Row> = emptyList(),
        val tasks: List<Row> = emptyList(),
        val notes: List<Row> = emptyList(),
        val settings: List<Row> = emptyList()
    ) {
        val totalRows: Int get() = events.size + tasks.size + notes.size + settings.size
    }

    class FormatException(message: String) : IllegalArgumentException(message)

    // ---------------- writing ----------------

    fun write(b: Backup): String {
        val root = JSONObject()
        root.put(KEY_VERSION, if (b.version > 0) b.version else FORMAT_VERSION)
        root.put(KEY_EXPORTED_AT, b.exportedAtMillis)
        root.put(KEY_EVENTS, rowsToJson(b.events))
        root.put(KEY_TASKS, rowsToJson(b.tasks))
        root.put(KEY_NOTES, rowsToJson(b.notes))
        root.put(KEY_SETTINGS, rowsToJson(b.settings))
        return root.toString(2)
    }

    private fun rowsToJson(rows: List<Row>): JSONArray {
        val arr = JSONArray()
        for (row in rows) {
            val obj = JSONObject()
            for ((k, v) in row.values) {
                if (v == null) continue // omit, so absence and null read back alike
                obj.put(k, v)
            }
            arr.put(obj)
        }
        return arr
    }

    // ---------------- reading ----------------

    fun parse(raw: String): Backup {
        if (raw.isBlank()) throw FormatException("backup file is empty")
        val root = try {
            JSONObject(raw)
        } catch (e: Exception) {
            throw FormatException("not valid JSON: ${e.message}")
        }
        val version = root.optInt(KEY_VERSION, -1)
        if (version < 1) {
            throw FormatException("missing or invalid '$KEY_VERSION' (got $version)")
        }
        if (version > FORMAT_VERSION) {
            // Refusing beats restoring a file we only half understand: a newer
            // schema may have moved fields we would silently drop.
            throw FormatException("backup format v$version is newer than supported v$FORMAT_VERSION")
        }
        return Backup(
            version = version,
            exportedAtMillis = root.optLong(KEY_EXPORTED_AT, 0L),
            events = jsonToRows(root.optJSONArray(KEY_EVENTS)),
            tasks = jsonToRows(root.optJSONArray(KEY_TASKS)),
            notes = jsonToRows(root.optJSONArray(KEY_NOTES)),
            settings = jsonToRows(root.optJSONArray(KEY_SETTINGS))
        )
    }

    private fun jsonToRows(arr: JSONArray?): List<Row> {
        if (arr == null) return emptyList()
        val out = ArrayList<Row>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val map = LinkedHashMap<String, Any?>(obj.length())
            for (key in obj.keys()) map[key] = obj.get(key)
            out += Row(map)
        }
        return out
    }
}
