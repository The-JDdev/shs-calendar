package com.shs.calendar.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.shs.calendar.data.entity.EventEntity
import kotlinx.coroutines.flow.Flow

/**
 * Event access. Window queries take UTC epoch millis so the DAO stays
 * timezone-agnostic (timezone handling belongs to the domain layer).
 */
@Dao
interface EventDao {

    @Insert
    suspend fun insert(event: EventEntity): Long

    @Update
    suspend fun update(event: EventEntity)

    @Delete
    suspend fun delete(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: Long): EventEntity?

    @Query("SELECT * FROM events WHERE id = :id")
    fun observeById(id: Long): Flow<EventEntity?>

    @Query("SELECT * FROM events ORDER BY startUtcMillis ASC")
    fun observeAll(): Flow<List<EventEntity>>

    /**
     * Events overlapping [fromUtcMillis, toUtcMillis]: non-recurring rows whose
     * first occurrence intersects the window, plus recurring masters (their
     * occurrences are expanded by the recurrence engine).
     */
    @Query(
        """
        SELECT * FROM events
        WHERE (rrule IS NULL OR rrule = '')
          AND startUtcMillis < :toUtcMillis AND endUtcMillis > :fromUtcMillis
        ORDER BY startUtcMillis ASC
        """
    )
    suspend fun eventsInWindow(fromUtcMillis: Long, toUtcMillis: Long): List<EventEntity>

    /** Recurring masters that start at or before the window end (expansion seeds). */
    @Query(
        """
        SELECT * FROM events
        WHERE rrule IS NOT NULL AND rrule != '' AND startUtcMillis <= :toUtcMillis
        ORDER BY startUtcMillis ASC
        """
    )
    suspend fun recurringStartingBefore(toUtcMillis: Long): List<EventEntity>

    @Query(
        """
        SELECT * FROM events
        WHERE title LIKE '%' || :query || '%' OR location LIKE '%' || :query || '%'
           OR description LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%'
        ORDER BY startUtcMillis ASC
        """
    )
    fun search(query: String): Flow<List<EventEntity>>

    /** Upcoming single events from [fromUtcMillis] onward — dashboard agenda. */
    @Query(
        """
        SELECT * FROM events
        WHERE (rrule IS NULL OR rrule = '') AND startUtcMillis >= :fromUtcMillis
        ORDER BY startUtcMillis ASC LIMIT :limit
        """
    )
    suspend fun upcoming(fromUtcMillis: Long, limit: Int): List<EventEntity>
}
