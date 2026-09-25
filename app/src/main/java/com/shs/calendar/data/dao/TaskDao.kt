package com.shs.calendar.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.shs.calendar.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks ORDER BY completed ASC, priority ASC, dueUtcMillis ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    /** Open tasks due before [nowUtcMillis] (0 = undated excluded by SQL range). */
    @Query(
        """
        SELECT * FROM tasks
        WHERE completed = 0 AND dueUtcMillis BETWEEN 1 AND :nowUtcMillis
        ORDER BY dueUtcMillis ASC
        """
    )
    suspend fun overdue(nowUtcMillis: Long): List<TaskEntity>

    @Query(
        """
        SELECT * FROM tasks
        WHERE completed = 0 AND dueUtcMillis BETWEEN :fromUtcMillis AND :toUtcMillis
        ORDER BY dueUtcMillis ASC
        """
    )
    suspend fun dueInWindow(fromUtcMillis: Long, toUtcMillis: Long): List<TaskEntity>

    @Query(
        """
        SELECT * FROM tasks
        WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'
           OR category LIKE '%' || :query || '%'
        ORDER BY completed ASC, dueUtcMillis ASC
        """
    )
    fun search(query: String): Flow<List<TaskEntity>>
}
