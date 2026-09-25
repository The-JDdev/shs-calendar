package com.shs.calendar.reminders

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.shs.calendar.R
import com.shs.calendar.ShsCalendarApp
import com.shs.calendar.data.CalendarDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives armed reminder alarms and posts the notification.
 * Loads the event from the DB (alarm may outlive process death) and guards
 * against stale fires (event deleted or start already passed the grace window).
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        val minuteOffset = intent.getIntExtra(EXTRA_MINUTE_OFFSET, 0)
        if (eventId < 0) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = CalendarDatabase.get(context)
                val event = db.eventDao().getById(eventId) ?: return@launch
                val now = System.currentTimeMillis()
                // Guard: drop fires for deleted events or long-stale alarms.
                if (event.startUtcMillis < now - STALE_GRACE_MILLIS) return@launch

                notify(context, event.title, event.startUtcMillis, minuteOffset, eventId)
            } finally {
                pending.finish()
            }
        }
    }

    private fun notify(
        context: Context,
        title: String,
        startUtcMillis: Long,
        minuteOffset: Int,
        notificationId: Long
    ) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val startText = java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT
        ).format(java.util.Date(startUtcMillis))
        val text = if (minuteOffset > 0) {
            context.getString(R.string.reminder_notification_text_offset, minuteOffset, startText)
        } else {
            context.getString(R.string.reminder_notification_text, startText)
        }

        val notification = NotificationCompat.Builder(context, ShsCalendarApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        runCatching {
            manager.notify(notificationId.toInt(), notification)
        }
    }

    companion object {
        const val EXTRA_EVENT_ID = "shs.calendar.EXTRA_EVENT_ID"
        const val EXTRA_MINUTE_OFFSET = "shs.calendar.EXTRA_MINUTE_OFFSET"

        /** Alarms older than this past the start are ignored (reboot duplicates). */
        private const val STALE_GRACE_MILLIS = 10 * 60 * 1000L
    }
}
