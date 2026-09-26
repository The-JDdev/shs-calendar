package com.shs.calendar.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shs.calendar.data.CalendarDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives the periodic sync alarm and runs one pass.
 *
 * The alarm may outlive the process, so the database and the credential store
 * are opened here rather than read from any Activity — the same reason
 * [com.shs.calendar.reminders.ReminderReceiver] reloads its event.
 *
 * KNOWN LIMITATION: goAsync() gives roughly 10 seconds before the system may
 * consider the receiver finished and reclaim the process. A pass over several
 * accounts on a slow connection can exceed that, in which case the process is
 * killed mid-pass. The pass is safe to interrupt — mergePulled writes only
 * after each account's pull returns, and the next alarm re-arms from a finally
 * block — so an interrupted pass loses at most one account's work and is
 * retried rather than lost. Moving to WorkManager would remove the ceiling;
 * that is tracked in PROGRESS.md rather than silently left unremarked.
 */
class SyncReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Respect the user's switch. The alarm can still fire after
        // setEnabled(false) races an in-flight schedule().
        if (!SyncScheduler(context).isEnabled()) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = CalendarDatabase.get(appContext)
                val credentials = SyncCredentialStore(appContext)
                val coordinator = SyncCoordinator(
                    accountDao = db.syncAccountDao(),
                    eventDao = db.eventDao(),
                    // Per-account engine, or the first account's password would
                    // be sent to every other server.
                    engineFor = { auth -> CalDavEngine(authFor = { auth }) },
                    authFor = { account -> credentials.authHeader(account.serverUrl, account.username) }
                )
                // Errors are recorded per account by the coordinator, which is
                // what the accounts screen shows. Nothing is thrown.
                coordinator.syncAll(System.currentTimeMillis())
            } catch (t: Throwable) {
                // A crash here would be silent: no UI is on screen at 3am.
                android.util.Log.w("SyncReceiver", "periodic sync pass failed", t)
            } finally {
                // Re-arm even on failure, or one bad night ends background
                // sync until the user next opens the app.
                if (SyncScheduler(appContext).isEnabled()) {
                    SyncScheduler(appContext).schedule()
                }
                pending.finish()
            }
        }
    }
}
