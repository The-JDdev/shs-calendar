package com.shs.calendar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shs.calendar.data.dao.EventDao
import com.shs.calendar.data.dao.NoteDao
import com.shs.calendar.data.dao.SettingsDao
import com.shs.calendar.data.dao.TaskDao
import com.shs.calendar.data.entity.EventEntity
import com.shs.calendar.data.entity.NoteEntity
import com.shs.calendar.data.entity.SettingsEntity
import com.shs.calendar.data.entity.TaskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Room database for SHS Calendar (offline-first local store).
 * Settings row is seeded on first open so reads never observe null.
 */
@Database(
    entities = [EventEntity::class, TaskEntity::class, NoteEntity::class, SettingsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CalendarDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao
    abstract fun taskDao(): TaskDao
    abstract fun noteDao(): NoteDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var instance: CalendarDatabase? = null

        private val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun get(context: Context): CalendarDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): CalendarDatabase =
            Room.databaseBuilder(context.applicationContext, CalendarDatabase::class.java, "shs_calendar.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        seedScope.launch {
                            get(context).settingsDao()
                                .insertIfAbsent(SettingsEntity())
                        }
                    }
                })
                .build()

        /** Test hook: close and forget the cached instance. */
        fun destroyInstance() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }
    }
}
