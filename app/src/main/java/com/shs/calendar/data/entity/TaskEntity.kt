package com.shs.calendar.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Task (VTODO where practical) — due date/time, priority, category, checklist,
 * recurrence, reminders, overdue flag derivation, convert-to-event (Phase 3).
 *
 * @param checklist newline-separated items, each "1|text" (done) or "0|text",
 *                  empty when unused. Newline separates items so '|' stays
 *                  unambiguous inside an item.
 * @param priority   0 = none, 1 = highest … 9 = lowest (RFC 5545 scale)
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    /** Epoch millis (UTC) of the due moment; 0 = undated. */
    val dueUtcMillis: Long = 0L,
    /** True when the due date carries no time-of-day. */
    val allDay: Boolean = false,
    val completed: Boolean = false,
    /** Epoch millis (UTC) when marked complete; 0 = not completed. */
    val completedUtcMillis: Long = 0L,
    val priority: Int = 0,
    val category: String = "",
    val checklist: String = "",
    val reminders: String = "",
    val rrule: String? = null,
    val timezone: String = "Asia/Dhaka",
    val createdAtUtcMillis: Long = 0L,
    val updatedAtUtcMillis: Long = 0L
) {
    fun reminderMinutes(): List<Int> =
        reminders.split(',').mapNotNull { it.trim().toIntOrNull() }.sorted()

    /** Parses [checklist] into (done, text) pairs; malformed lines are skipped. */
    fun checklistItems(): List<Pair<Boolean, String>> =
        checklist.split('\n').mapNotNull { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@mapNotNull null
            val sep = line.indexOf('|')
            if (sep <= 0) null
            else (line[0] == '1') to line.substring(sep + 1).trim()
        }

    /** Overdue = due in the past and not completed (undated tasks never overdue). */
    fun isOverdue(nowUtcMillis: Long): Boolean =
        !completed && dueUtcMillis in 1..nowUtcMillis
}
