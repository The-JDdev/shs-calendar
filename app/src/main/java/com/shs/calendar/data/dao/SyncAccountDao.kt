package com.shs.calendar.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.shs.calendar.data.entity.SyncAccountEntity
import kotlinx.coroutines.flow.Flow

/**
 * Persistence for CalDAV accounts.
 *
 * Reactive reads back the accounts UI list, so adding or removing an account
 * updates the screen without an explicit refresh. The unique index on
 * (serverUrl, username) means re-adding the same server replaces the row
 * rather than creating a duplicate the sync engine would then double-push.
 */
@Dao
interface SyncAccountDao {

    @Query("SELECT * FROM sync_accounts ORDER BY label ASC")
    fun observeAll(): Flow<List<SyncAccountEntity>>

    @Query("SELECT * FROM sync_accounts WHERE enabled = 1 ORDER BY label ASC")
    suspend fun enabled(): List<SyncAccountEntity>

    @Query("SELECT * FROM sync_accounts WHERE id = :id")
    suspend fun getById(id: Long): SyncAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: SyncAccountEntity): Long

    @Update
    suspend fun update(account: SyncAccountEntity)

    @Delete
    suspend fun delete(account: SyncAccountEntity)

    @Query("DELETE FROM sync_accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Records a successful sync. lastSyncMillis and the cached collection tag
     * are written in one statement so a crash can never persist a new token
     * with a stale timestamp, or vice versa.
     */
    @Query(
        "UPDATE sync_accounts SET lastSyncMillis = :millis, lastErrorMessage = '', " +
            "syncToken = COALESCE(:token, syncToken) WHERE id = :id"
    )
    suspend fun markSynced(id: Long, millis: Long, token: String?)

    @Query("UPDATE sync_accounts SET lastErrorMessage = :message WHERE id = :id")
    suspend fun markFailed(id: Long, message: String)

    @Query("UPDATE sync_accounts SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
