package com.shs.calendar.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms reminder alarms after reboot, app update, clock changes or
 * timezone changes — restores from the DB (SPEC requirement: BOOT_COMPLETED).
 *
 * Registered in AndroidManifest with BOOT_COMPLETED, MY_PACKAGE_REPLACED,
 * TIME_SET and TIMEZONE_CHANGED actions.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in RESTORE_ACTIONS) return

        // Alarms do not survive reboot; goAsync keeps the process alive until
        // the DB-driven reschedule has actually completed.
        val pending = goAsync()
        ReminderScheduler(context).rescheduleAll(onDone = { pending.finish() })
    }

    companion object {
        private val RESTORE_ACTIONS = setOf(
            "android.intent.action.BOOT_COMPLETED",
            "android.intent.action.MY_PACKAGE_REPLACED",
            "android.intent.action.TIME_SET",
            "android.intent.action.TIMEZONE_CHANGED",
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
    }
}
