package com.shs.calendar

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.shs.calendar.ui.appearance.AppearanceOptions
import com.shs.calendar.ui.appearance.AppearanceStore

/**
 * Application entry point: creates the notification channel once so reminder
 * scheduling never races a missing channel (API 26+ requires the channel to
 * exist before any notification can post).
 *
 * It also applies the M10 appearance choices here, before any Activity exists.
 * attachBaseContext is the only hook that runs early enough: an in-app text
 * size has to be in the Configuration before the first view is inflated, and
 * the accent has to be resolvable before the theme is applied.
 */
class ShsCalendarApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(applyFontScale(base, AppearanceStore.get(base).load()))
    }

    override fun onCreate() {
        super.onCreate()
        // Theme is applied here rather than per-Activity so it survives a
        // process restart without each screen remembering to set it.
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            AppearanceStore.get(this).load().nightMode
        )
        createNotificationChannels()
    }

    /**
     * Multiplies the system font scale by the user's in-app choice.
     *
     * NORMAL returns [Configuration.fontScale] untouched rather than 1.0f, so
     * a user who has already set a large system font is never overridden by an
     * app default — see AppearanceOptions.respectsSystemFontScale.
     */
    private fun applyFontScale(base: Context, options: AppearanceOptions): Context {
        if (options.respectsSystemFontScale) return base
        val config = Configuration(base.resources.configuration)
        config.fontScale = base.resources.configuration.fontScale * options.fontScaleFactor
        return base.createConfigurationContext(config)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS,
                getString(R.string.channel_reminders_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_reminders_desc)
                enableVibration(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALARMS,
                getString(R.string.channel_alarms_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.channel_alarms_desc)
            }
        )
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_ALARMS = "calendar_alarms"
    }
}
