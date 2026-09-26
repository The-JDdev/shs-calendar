package com.shs.calendar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
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
    version = 4,
    exportSchema = false
)
abstract class CalendarDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao
    abstract fun taskDao(): TaskDao
    abstract fun noteDao(): NoteDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        /**
         * 1 -> 2 (M2): prayer settings columns. Nullable with NULL = documented
         * default, so no data rewrite is needed and schema validation matches.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN prayerMethod TEXT")
                db.execSQL("ALTER TABLE settings ADD COLUMN prayerMadhab TEXT")
                db.execSQL("ALTER TABLE settings ADD COLUMN highLatitudeRule TEXT")
                db.execSQL("ALTER TABLE settings ADD COLUMN prayerOffsetsCsv TEXT")
            }
        }

        /**
         * 2 -> 3 (M5): weather settings columns. Nullable with NULL = documented
         * default (enabled / metric / Open-Meteo), so no data rewrite is needed
         * and schema validation matches the entity exactly.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN weatherEnabled INTEGER")
                db.execSQL("ALTER TABLE settings ADD COLUMN weatherUnits TEXT")
                db.execSQL("ALTER TABLE settings ADD COLUMN weatherProvider TEXT")
            }
        }

        /**
         * 3 -> 4 (M9): CalDAV sync bookkeeping on events.
         *
         * The DAV columns are nullable except davDirty, which is declared
         * NOT NULL DEFAULT 0 so pre-existing local-only rows get a concrete
         * value. davUid carries an index because the sync engine upserts by it
         * on every pass and a table scan per remote event would not scale.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN davUid TEXT")
                db.execSQL("ALTER TABLE events ADD COLUMN davEtag TEXT")
                db.execSQL("ALTER TABLE events ADD COLUMN davAccountId TEXT")
                db.execSQL("ALTER TABLE events ADD COLUMN davCalendarHref TEXT")
                db.execSQL("ALTER TABLE events ADD COLUMN davDirty INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_dav_uid ON events(davUid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_dav_dirty ON events(davDirty)")
            }
        }

        @Volatile
        private var instance: CalendarDatabase? = null

        private val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun get(context: Context): CalendarDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): CalendarDatabase =
            Room.databaseBuilder(context.applicationContext, CalendarDatabase::class.java, "shs_calendar.db")
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
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
