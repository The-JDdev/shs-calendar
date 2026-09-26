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

    // ---------------------------------------------------------------------
    // CalDAV (M9)
    //
    // The five dav* columns on EventEntity are what separate a purely local
    // event from a mirrored one, so every sync query keys off davUid or
    // davDirty. davUid is deliberately NULLABLE: a local event has no
    // davUid at all, and "davUid IS NULL" is therefore the entire
    // never-synced population.
    // ---------------------------------------------------------------------

    /**
     * Local edits waiting to be pushed.
     *
     * Scoped to one account so a failure on one server cannot stall another's
     * queue. A null [accountId] row is claimed by [claimOrphanDirty] instead:
     * a user can mark an event dirty before any account exists.
     */
    @Query(
        """
        SELECT * FROM events
        WHERE davDirty = 1 AND davAccountId = :accountId
        ORDER BY startUtcMillis ASC
        """
    )
    suspend fun dirtyForAccount(accountId: String): List<EventEntity>

    /** Local edits not yet attached to any account. */
    @Query("SELECT * FROM events WHERE davDirty = 1 AND (davAccountId IS NULL OR davAccountId = '')")
    suspend fun dirtyWithoutAccount(): List<EventEntity>

    /** The already-mirrored copy of one remote event, matched by UID + account. */
    @Query("SELECT * FROM events WHERE davUid = :uid AND davAccountId = :accountId LIMIT 1")
    suspend fun findByDavUid(uid: String, accountId: String): EventEntity?

    /**
     * Every local copy of one remote event across all accounts.
     *
     * A delete has to remove the row in each subscribed account, since the
     * same UID is mirrored once per server. Matching on UID alone would
     * delete the user's copy on an unrelated server.
     */
    @Query("SELECT * FROM events WHERE davUid = :uid")
    suspend fun findAllByDavUid(uid: String): List<EventEntity>

    /** Rows whose remote href the server reported as gone, for this account. */
    @Query("SELECT * FROM events WHERE davAccountId = :accountId AND davCalendarHref = :href LIMIT 1")
    suspend fun findByCalendarHref(href: String, accountId: String): EventEntity?

    @Query("DELETE FROM events WHERE davAccountId = :accountId")
    suspend fun deleteAllForAccount(accountId: String)
}
