package com.shs.calendar.ui.appearance

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Persistence for [AppearanceOptions].
 *
 * Appearance deliberately does NOT live in [com.shs.calendar.data.entity.SettingsEntity]:
 * it must be readable synchronously from [android.content.Context.attachBaseContext]
 * (to scale fonts and pick the locale before the first inflation) and that happens
 * long before the Room database is open. SharedPreferences gives a cheap
 * synchronous read, and letting the OS hold it in memory keeps the pre-inflation
 * lookup off the disk path.
 *
 * Values are stored as enum *names*, never ordinals, so reordering an enum
 * later cannot silently reinterpret an existing user's choice. Reads tolerate
 * missing or unrecognised names by falling back through [Theme.from] and
 * friends rather than throwing — a corrupt preference must not stop the app
 * launching.
 */
class AppearanceStore(context: Context) {

    // Fall back to [context] itself: when AppearanceStore is built from
    // Application.attachBaseContext (the pre-inflation locale/font-scale
    // read), the application context is NOT yet attached and
    // Context.getApplicationContext() returns null on a real device —
    // dereferencing it there crashed the process before any Activity could
    // exist (the "app installs but never opens" crash). The Application
    // instance itself is already an app-scoped Context, so
    // getSharedPreferences on it returns exactly the same file.
    private val prefs: SharedPreferences =
        (context.applicationContext ?: context).getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): AppearanceOptions = AppearanceOptions(
        theme = Theme.from(prefs.getString(KEY_THEME, null)),
        accent = Accent.from(prefs.getString(KEY_ACCENT, null)),
        fontScale = FontScale.from(prefs.getString(KEY_FONT_SCALE, null)),
        localeTag = prefs.getString(KEY_LOCALE, null).orEmpty()
            .ifEmpty { AppearanceOptions.TAG_SYSTEM }
    )

    fun saveTheme(theme: Theme) = prefs.edit { putString(KEY_THEME, theme.name) }

    fun saveAccent(accent: Accent) = prefs.edit { putString(KEY_ACCENT, accent.name) }

    fun saveFontScale(scale: FontScale) = prefs.edit { putString(KEY_FONT_SCALE, scale.name) }

    /** An empty tag means "follow the device language". */
    fun saveLocale(tag: String) = prefs.edit { putString(KEY_LOCALE, tag) }

    companion object {
        private const val FILE = "appearance"
        private const val KEY_THEME = "theme"
        private const val KEY_ACCENT = "accent"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_LOCALE = "locale_tag"

        @Volatile
        private var instance: AppearanceStore? = null

        /**
         * Process-wide singleton. attachBaseContext runs before any Activity
         * exists, so an instance cannot be threaded through a constructor.
         */
        fun get(context: Context): AppearanceStore =
            instance ?: synchronized(this) {
                instance ?: AppearanceStore(context).also { instance = it }
            }

        /**
         * Test hook: forget the cached instance, exactly like
         * [com.shs.calendar.data.CalendarDatabase.destroyInstance]. Robolectric
         * builds a fresh Application per test, so a stale singleton would keep
         * reading the previous test's preference file and silently ignore the
         * appearance the test just saved.
         */
        fun resetForTest() {
            synchronized(this) { instance = null }
        }
    }
}
