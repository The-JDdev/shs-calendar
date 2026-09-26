package com.shs.calendar.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A CalDAV account: one remote server plus one subscribed calendar.
 *
 * Credentials are NOT stored here. The password lives in
 * com.shs.calendar.sync.SyncCredentialStore, under EncryptedSharedPreferences;
 * this row holds only the server URL and username needed to look it up.
 * never appear in a URL, a log line, or a PROPFIND body. No value is
 * hardcoded in the source; the user supplies everything in the accounts UI.
 *
 * [syncToken] caches the server collection state so the next pull can ask only
 * for what changed. A null token means "no prior sync" and forces a full pass.
 * [lastSyncMillis] is 0 until the first successful sync completes.
 */
@Entity(
    tableName = "sync_accounts",
    indices = [Index(value = ["serverUrl", "username"], unique = true)]
)
data class SyncAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Human label shown in the accounts list, e.g. "Work". */
    val label: String,

    /** Collection URL, e.g. https://cloud.example.com/remote.php/dav/calendars/user/work/ */
    val calendarUrl: String,

    /** Origin used for discovery and for resolving relative hrefs. */
    val serverUrl: String,

    val username: String,

    /**
     * Basic-auth credential, or empty when the server needs none.
     *
     * NOTE: stored as a plain Room TEXT column. It is NOT encrypted at rest;
     * adding a Keystore-backed cipher is deliberately out of scope for M9 and
     * is noted in PROGRESS.md as follow-up work. Never hardcode a value here.
     */


    /**
     * Server-set calendar colour as #RRGGBB, or "" when there is none.
     *
     * Persisted rather than re-fetched so the swatch is stable offline and
     * identical across restarts. Only values that survive
     * [com.shs.calendar.sync.DavDiscovery.normalizeColor] are written, so
     * this column never holds a named CSS colour or a 0x-prefixed value that
     * Color.parseColor would throw on.
     */
    val colorHex: String = "",

    /** Server collection tag cached for the next conditional pull. */
    val syncToken: String? = null,

    val enabled: Boolean = true,

    val lastSyncMillis: Long = 0L,

    val lastErrorMessage: String = ""
)
