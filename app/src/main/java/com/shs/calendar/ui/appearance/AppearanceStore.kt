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

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

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
    }
}
