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
import com.shs.calendar.data.dao.SyncAccountDao
import com.shs.calendar.data.dao.TaskDao
import com.shs.calendar.data.entity.EventEntity
import com.shs.calendar.data.entity.NoteEntity
import com.shs.calendar.data.entity.SettingsEntity
import com.shs.calendar.data.entity.SyncAccountEntity
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
    entities = [
        EventEntity::class,
        TaskEntity::class,
        NoteEntity::class,
        SettingsEntity::class,
        SyncAccountEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class CalendarDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao
    abstract fun taskDao(): TaskDao
    abstract fun noteDao(): NoteDao
    abstract fun settingsDao(): SettingsDao
    abstract fun syncAccountDao(): SyncAccountDao

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

        /**
         * v5 → v6: move account passwords out of the database.
         *
         * Credentials now live in EncryptedSharedPreferences (see
         * SyncCredentialStore), so the plaintext column is dropped. Any secret
         * still present is discarded rather than migrated: it cannot be read
         * back by the new store, and copying it into a fresh plaintext table
         * would defeat the point of the change. The user re-enters it once
         * after upgrading.
         *
         * SQLite has no DROP COLUMN before 3.35, so the table is rebuilt:
         * create, copy, drop, recreate the index. MIGRATION_4_5 is left
         * untouched — a shipped migration is immutable.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_accounts_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "label TEXT NOT NULL, " +
                        "calendarUrl TEXT NOT NULL, " +
                        "serverUrl TEXT NOT NULL, " +
                        "username TEXT NOT NULL, " +
                        "syncToken TEXT, " +
                        "enabled INTEGER NOT NULL, " +
                        "lastSyncMillis INTEGER NOT NULL, " +
                        "lastErrorMessage TEXT NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO sync_accounts_new " +
                        "(id, label, calendarUrl, serverUrl, username, syncToken, " +
                        "enabled, lastSyncMillis, lastErrorMessage) " +
                        "SELECT id, label, calendarUrl, serverUrl, username, syncToken, " +
                        "enabled, lastSyncMillis, lastErrorMessage FROM sync_accounts"
                )
                db.execSQL("DROP TABLE sync_accounts")
                db.execSQL(
                    "ALTER TABLE sync_accounts_new RENAME TO sync_accounts"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_sync_accounts_serverUrl_username " +
                        "ON sync_accounts(serverUrl, username)"
                )
            }
        }

        /**
         * 4 -> 5 (M9): CalDAV account storage.
         *
         * A new table only, so no existing rows are touched and the schema
         * matches [SyncAccountEntity] exactly. Types map to SQLite affinities:
         * Long/Boolean -> INTEGER, String -> TEXT, String? -> TEXT (nullable).
         * The (serverUrl, username) index is declared UNIQUE on the entity and
         * so is created here too — Room's identity hash covers the index, and a
         * mismatch fails validation at runtime, not at compile time.
         */
        /**
         * 6 -> 7 (M9): per-calendar colour.
         *
         * A plain ADD COLUMN, not the create/copy/drop rebuild MIGRATION_5_6
         * needed: SQLite supports ADD COLUMN from 3.2, and adding a column
         * touches no existing row, so there is nothing to copy and nothing
         * that can be lost. The rebuild is only required when dropping or
         * retyping a column, which no longer happens here.
         *
         * NOT NULL DEFAULT '' matters twice over: the column is declared
         * non-null on the entity, and an existing account has no server colour,
         * so it falls back to the app's own palette rather than to null — the
         * same "honest empty" rule the syncToken column follows.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE sync_accounts " +
                        "ADD COLUMN colorHex TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        // M11: index the agenda/range query column. IF NOT EXISTS keeps this
        // idempotent for fresh installs where Room already created the index
        // from the entity annotation.
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_events_startUtcMillis` " +
                        "ON `events` (`startUtcMillis`)"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_accounts (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "label TEXT NOT NULL, " +
                        "calendarUrl TEXT NOT NULL, " +
                        "serverUrl TEXT NOT NULL, " +
                        "username TEXT NOT NULL, " +
                        "password TEXT NOT NULL, " +
                        "syncToken TEXT, " +
                        "enabled INTEGER NOT NULL, " +
                        "lastSyncMillis INTEGER NOT NULL, " +
                        "lastErrorMessage TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_sync_accounts_serverUrl_username " +
                        "ON sync_accounts(serverUrl, username)"
                )
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
                .addMigrations(MIGRATION_4_5)
                .addMigrations(MIGRATION_5_6)
                // Registered, not merely defined: an unregistered migration is
                // dead code that still changes the entity's expected schema, so
                // Room would fail validation on the first open of an existing
                // install rather than at compile time.
                .addMigrations(MIGRATION_6_7)
                .addMigrations(MIGRATION_7_8)
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
