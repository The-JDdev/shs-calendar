package com.shs.calendar.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Note / journal entry (VJOURNAL where practical). Tags are comma-separated
 * for simple substring search; rich text arrives Phase 3 (markdown subset).
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val tags: String = "",
    /** Optional linked date (epoch millis UTC); 0 = timeless note. */
    val linkedDateUtcMillis: Long = 0L,
    val pinned: Boolean = false,
    val createdAtUtcMillis: Long = 0L,
    val updatedAtUtcMillis: Long = 0L
) {
    fun tagList(): List<String> =
        tags.split(',').mapNotNull { t -> t.trim().takeIf { it.isNotEmpty() } }
}
