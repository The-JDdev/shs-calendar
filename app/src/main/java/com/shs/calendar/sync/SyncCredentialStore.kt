package com.shs.calendar.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Account credentials, kept out of the database.
 *
 * M9 requires passwords in EncryptedSharedPreferences. The account row holds
 * only the server URL and username; the secret lives here, under a key derived
 * from (serverUrl, username) so two accounts on one device never collide and
 * removing an account can drop exactly its own entry.
 *
 * The store is created lazily and falls back to an in-memory map if the
 * keystore is unavailable — a corrupt or uninitialised keystore must not stop
 * the user reaching their calendars, and a degraded session is better than a
 * crash on startup. The fallback is deliberately not persisted.
 */
class SyncCredentialStore(context: Context) {

    private val appContext = context.applicationContext

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var degraded: MutableMap<String, String> = mutableMapOf()

    /** True when encryption is unavailable and secrets live only in memory. */
    val isDegraded: Boolean get() = prefs == null

    private fun key(serverUrl: String, username: String): String =
        DavRequest.encodeUid("$serverUrl|$username")

    private fun open(): SharedPreferences? {
        prefs?.let { return it }
        synchronized(this) {
            prefs?.let { return it }
            return try {
                val masterKey = MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    appContext,
                    FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                ).also { prefs = it }
            } catch (t: Throwable) {
                // Keystore locked, corrupted, or absent on this device.
                null
            }
        }
    }

    fun putPassword(serverUrl: String, username: String, password: String) {
        val k = key(serverUrl, username)
        val store = open()
        if (store != null) store.edit().putString(k, password).apply()
        else synchronized(this) { degraded[k] = password }
    }

    /** Null when the account has no stored secret, e.g. a public calendar. */
    fun getPassword(serverUrl: String, username: String): String? {
        val k = key(serverUrl, username)
        open()?.let { return it.getString(k, null) }
        return synchronized(this) { degraded[k] }
    }

    fun remove(serverUrl: String, username: String) {
        val k = key(serverUrl, username)
        open()?.edit()?.remove(k)?.apply()
        synchronized(this) { degraded.remove(k) }
    }

    /** Basic auth header value, or null when no password is stored. */
    fun authHeader(serverUrl: String, username: String): String? =
        DavAuth.basicHeader(username, getPassword(serverUrl, username))

    private companion object {
        const val FILE_NAME = "shs_sync_credentials"
    }
}
