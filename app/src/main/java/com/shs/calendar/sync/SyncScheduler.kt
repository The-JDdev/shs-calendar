package com.shs.calendar.sync

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Periodic background sync, on the app's existing AlarmManager convention.
 *
 * Deliberately not WorkManager. The app already schedules background work this
 * way for reminders (see reminders.ReminderScheduler) and restores those alarms
 * from BootCompletedReceiver; introducing a second scheduling idiom for the one
 * job that shares a database with the first would mean two lifecycles to reason
 * about, not one fewer.
 *
 * Interval is a floor, not a promise. Android batches and defers inexact
 * alarms aggressively under battery pressure, so a 6-hour request may land
 * hours later. Every job therefore re-arms for the *next* window instead of
 * trusting one alarm to repeat, and always reschedules itself in a finally
 * block: an alarm lost to a failed or cancelled pass must not silently end
 * background sync for the lifetime of the install.
 */
class SyncScheduler(private val context: Context) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * One fixed request code, so scheduling is an update of the same alarm
     * rather than a second one. Distinct accounts are not separate jobs — a
     * pass covers all enabled accounts, and running them concurrently would let
     * two passes write the same account rows.
     */
    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, SyncReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** (Re)arms the next pass. Safe to call repeatedly; it never stacks alarms. */
    fun schedule(intervalMillis: Long = DEFAULT_INTERVAL_MILLIS) {
        val triggerAt = System.currentTimeMillis() + intervalMillis
        // Inexact, and no wakeup: background sync that wakes the device to run
        // minutes early is a battery cost the user did not ask for. In a
        // window this large, a few minutes of drift changes nothing.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent())
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent())
        }
    }

    fun cancel() {
        alarmManager.cancel(pendingIntent())
    }

    /**
     * Whether the user has background sync switched on, in a form the boot
     * receiver can consult without a database read on the main thread.
     */
    fun isEnabled(): Boolean = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        if (enabled) schedule() else cancel()
    }

    companion object {
        /** Six hours: enough to stay useful, rare enough to be cheap. */
        const val DEFAULT_INTERVAL_MILLIS = 6 * 60 * 60 * 1000L

        private const val REQUEST_CODE = 0x5_59_01
        private const val PREFS = "shs_sync_scheduler"
        private const val KEY_ENABLED = "periodic_enabled"
    }
}
