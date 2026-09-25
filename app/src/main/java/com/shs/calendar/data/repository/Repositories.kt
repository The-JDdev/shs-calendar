package com.shs.calendar.data.repository

import com.shs.calendar.data.CalendarDatabase
import com.shs.calendar.data.entity.EventEntity
import com.shs.calendar.data.entity.NoteEntity
import com.shs.calendar.data.entity.SettingsEntity
import com.shs.calendar.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository over the Room store. Entities pass through as-is in Phase 1;
 * domain mappers arrive with the `domain` layer (Phase 3 search/sync).
 */
class EventRepository(private val db: CalendarDatabase) {

    private val dao get() = db.eventDao()

    suspend fun save(event: EventEntity): Long =
        if (event.id == 0L) dao.insert(event) else {
            dao.update(event); event.id
        }

    suspend fun delete(event: EventEntity) = dao.delete(event)
    suspend fun deleteById(id: Long) = dao.deleteById(id)
    suspend fun getById(id: Long): EventEntity? = dao.getById(id)
    fun observeById(id: Long): Flow<EventEntity?> = dao.observeById(id)
    fun observeAll(): Flow<List<EventEntity>> = dao.observeAll()
    fun search(query: String): Flow<List<EventEntity>> = dao.search(query.trim())
    suspend fun upcoming(fromUtcMillis: Long, limit: Int = 20): List<EventEntity> =
        dao.upcoming(fromUtcMillis, limit)

    /** Non-recurring events overlapping the window plus recurring masters. */
    suspend fun eventsInWindow(fromUtcMillis: Long, toUtcMillis: Long): List<EventEntity> {
        val direct = dao.eventsInWindow(fromUtcMillis, toUtcMillis)
        val recurring = dao.recurringStartingBefore(toUtcMillis)
        return (direct + recurring).distinctBy { it.id }
            .sortedBy { it.startUtcMillis }
    }
}

class TaskRepository(private val db: CalendarDatabase) {

    private val dao get() = db.taskDao()

    suspend fun save(task: TaskEntity): Long =
        if (task.id == 0L) dao.insert(task) else {
            dao.update(task); task.id
        }

    suspend fun delete(task: TaskEntity) = dao.delete(task)
    suspend fun getById(id: Long) = dao.getById(id)
    fun observeAll(): Flow<List<TaskEntity>> = dao.observeAll()
    suspend fun overdue(nowUtcMillis: Long) = dao.overdue(nowUtcMillis)
    suspend fun dueInWindow(from: Long, to: Long) = dao.dueInWindow(from, to)
    fun search(query: String) = dao.search(query.trim())
}

class NoteRepository(private val db: CalendarDatabase) {

    private val dao get() = db.noteDao()

    suspend fun save(note: NoteEntity): Long =
        if (note.id == 0L) dao.insert(note) else {
            dao.update(note); note.id
        }

    suspend fun delete(note: NoteEntity) = dao.delete(note)
    suspend fun getById(id: Long) = dao.getById(id)
    fun observeAll(): Flow<List<NoteEntity>> = dao.observeAll()
    fun search(query: String) = dao.search(query.trim())
}

class SettingsRepository(private val db: CalendarDatabase) {

    private val dao get() = db.settingsDao()

    /** Settings row, falling back to compiled defaults if seeding is pending. */
    fun observe(): Flow<SettingsEntity> =
        dao.observe().map { it ?: SettingsEntity() }

    suspend fun get(): SettingsEntity = dao.get() ?: SettingsEntity()

    suspend fun save(settings: SettingsEntity) = dao.upsert(settings)

    suspend fun update(transform: (SettingsEntity) -> SettingsEntity) {
        dao.upsert(transform(get()))
    }
}
