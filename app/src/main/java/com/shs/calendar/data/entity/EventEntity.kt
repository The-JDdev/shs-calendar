package com.shs.calendar.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Calendar event row — field set per SPEC section 12.
 *
 * @param startUtcMillis epoch millis (UTC) of the first occurrence start
 * @param endUtcMillis   epoch millis (UTC) of the first occurrence end
 * @param allDay         true when the event ignores time-of-day (rendered in
 *                       the device zone of [timezone] for display only)
 * @param reminders      comma-separated minutes-before-start offsets, e.g. "0,10,1440"
 * @param rrule          RFC 5545 RRULE subset string, e.g. "FREQ=WEEKLY;INTERVAL=1;COUNT=10"
 *                       (null = single occurrence)
 * @param privacy        RFC 5545 PARTSTAT-style flag: PUBLIC | PRIVATE | CONFIDENTIAL
 */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val startUtcMillis: Long,
    val endUtcMillis: Long,
    val allDay: Boolean = false,
    val location: String = "",
    val url: String = "",
    val category: String = "",
    val color: String = "",
    val participants: String = "",
    val organizer: String = "",
    val reminders: String = "",
    val rrule: String? = null,
    val privacy: String = "PUBLIC",
    val notes: String = "",
    val timezone: String = "Asia/Dhaka",
    val createdAtUtcMillis: Long = 0L,
    val updatedAtUtcMillis: Long = 0L
) {
    /** Parsed reminder offsets in minutes-before-start, sorted ascending. */
    fun reminderMinutes(): List<Int> =
        reminders.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .sorted()

    val isRecurring: Boolean get() = !rrule.isNullOrBlank()
}
