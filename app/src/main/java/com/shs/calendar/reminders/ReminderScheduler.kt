package com.shs.calendar.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.shs.calendar.data.CalendarDatabase
import com.shs.calendar.data.entity.EventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * AlarmManager-backed reminder scheduling.
 *
 * - [scheduleEventReminders] schedules every reminder offset of an event.
 * - [rescheduleAll] is the BOOT_COMPLETED / TIME_SET restore path: it walks
 *   upcoming events in the DB and re-arms every future alarm.
 * - All scheduling is best-effort: exact alarms degrade gracefully on
 *   API 31+ when SCHEDULE_EXACT_ALARM is not granted (inexact still fires).
 */
class ReminderScheduler(private val context: Context) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun pendingIntentFor(eventId: Long, minuteOffset: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(ReminderReceiver.EXTRA_MINUTE_OFFSET, minuteOffset)
        }
        val requestCode = (eventId.toInt() * 31 + minuteOffset)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Schedule all reminder offsets for [event] whose fire time is in the future. */
    fun scheduleEventReminders(event: EventEntity) {
        cancelEventReminders(event)
        val offsets = event.reminderMinutes().ifEmpty { listOf(0) }
        val now = System.currentTimeMillis()
        for (offset in offsets) {
            val fireAt = event.startUtcMillis - offset * 60_000L
            if (fireAt > now) scheduleAt(fireAt, pendingIntentFor(event.id, offset))
        }
    }

    /** Cancel every reminder previously armed for [eventId]. */
    fun cancelEventReminders(eventId: Long) {
        for (offset in COMMON_OFFSETS) {
            alarmManager.cancel(pendingIntentFor(eventId, offset))
        }
    }

    /** Cancel only the offsets this event actually used (best effort + common set). */
    fun cancelEventReminders(event: EventEntity) {
        val offsets = (event.reminderMinutes() + COMMON_OFFSETS).distinct()
        for (offset in offsets) {
            alarmManager.cancel(pendingIntentFor(event.id, offset))
        }
    }

    private fun scheduleAt(triggerAtMillis: Long, pi: PendingIntent) {
        if (canScheduleExactAlarms()) {
            runCatching {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pi
                )
            }.onFailure {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /**
     * Restore path (boot / time change / app update): re-arm alarms for all
     * events with start times in the near future window.
     */
    fun rescheduleAll(lookaheadMillis: Long = DEFAULT_LOOKAHEAD, onDone: (() -> Unit)? = null) {
        val db = CalendarDatabase.get(context)
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                val events = db.eventDao().eventsInWindow(now, now + lookaheadMillis) +
                    db.eventDao().recurringStartingBefore(now + lookaheadMillis)
                for (event in events.distinctBy { it.id }) {
                    scheduleEventReminders(event)
                }
            } finally {
                onDone?.invoke()
            }
        }
    }

    companion object {
        /** Reminder offsets commonly present in events — used for cancellation sweeps. */
        private val COMMON_OFFSETS = intArrayOf(0, 5, 10, 15, 30, 60, 120, 1440)

        /** Restore window: 30 days of upcoming events. */
        const val DEFAULT_LOOKAHEAD = 30L * 24 * 60 * 60 * 1000
    }
}
