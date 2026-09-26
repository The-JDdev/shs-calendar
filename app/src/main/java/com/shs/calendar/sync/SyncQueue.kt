package com.shs.calendar.sync

/**
 * Offline-first push queue.
 *
 * Every local change is recorded here before it is attempted against a server,
 * so a sync attempt that fails on a train leaves no data stranded. The queue is
 * deliberately pure: no clock, no network, no Android types. [nowMillis] is
 * always passed in, which makes retry timing testable without sleeping.
 */

/** Why an entry is waiting. */
enum class SyncOp { CREATE, UPDATE, DELETE }

/** One pending remote change. */
data class QueueEntry(
    val uid: String,
    val op: SyncOp,
    /** ETag of the copy we last saw; null when we have never synced this UID. */
    val etag: String? = null,
    /** Epoch millis of the first attempt; null until it is first tried. */
    val firstAttemptMillis: Long? = null,
    val attempts: Int = 0
)

/** What the scheduler should do with the queue right now. */
sealed class QueueAction {
    /** Send [entries] now. */
    data class Send(val entries: List<QueueEntry>) : QueueAction()

    /** Nothing to send. */
    object Idle : QueueAction()

    /**
     * Entries were seen but must wait for [retryAfterMillis] because the
     * transport is unreachable — this is the offline queue, not a drop.
     */
    data class Backoff(val retryAfterMillis: Long) : QueueAction()
}

object SyncQueue {

    /** Exponential backoff: base * 2^(attempts-1), capped at [maxBackoffMillis]. */
    fun backoffMillis(
        attempts: Int,
        baseBackoffMillis: Long = 60_000L,
        maxBackoffMillis: Long = 6L * 60 * 60 * 1000
    ): Long {
        if (attempts <= 0) return 0L
        // Shift in a Long, clamping well before attempt 63 would overflow into
        // a negative delay — an overflowed backoff would retry immediately.
        val shift = (attempts - 1).coerceAtMost(32)
        val scaled = baseBackoffMillis shl shift
        return if (scaled < 0 || scaled > maxBackoffMillis) maxBackoffMillis else scaled
    }

    /**
     * Records a local change, coalescing per UID.
     *
     * Only the newest operation per UID is kept: editing an event three times
     * offline must produce one PUT, not three, and a delete after an edit must
     * collapse to a single DELETE rather than replaying a create.
     */
    fun enqueue(
        queue: List<QueueEntry>,
        uid: String,
        op: SyncOp,
        etag: String? = null
    ): List<QueueEntry> {
        val kept = queue.filterNot { it.uid == uid }
        if (op == SyncOp.DELETE) {
            // A deletion of a copy we never pushed is a local-only event: there
            // is nothing on the server to remove, so queueing it would 404.
            return if (etag.isNullOrBlank()) kept
            else kept + QueueEntry(uid = uid, op = SyncOp.DELETE, etag = etag)
        }
        return kept + QueueEntry(uid = uid, op = op, etag = etag)
    }


    /**
     * Decides what to do with [queue] at [nowMillis].
     *
     * Entries still inside their backoff window are held, not dropped, and
     * [online] false is what makes this the offline queue: entries wait rather
     * than being discarded, so no local change is ever lost to a dead network.
     */
    fun plan(
        queue: List<QueueEntry>,
        nowMillis: Long,
        online: Boolean,
        maxBatch: Int = 50,
        baseBackoffMillis: Long = 60_000L,
        maxBackoffMillis: Long = 6L * 60 * 60 * 1000
    ): QueueAction {
        if (queue.isEmpty()) return QueueAction.Idle
        if (!online) return QueueAction.Backoff(backoffMillis(1, baseBackoffMillis, maxBackoffMillis))

        val ready = queue.filter { entry ->
            val since = entry.firstAttemptMillis ?: return@filter true
            // Clock moved backwards (NTP correction, user changing the date):
            // treat the entry as due rather than waiting a negative interval.
            nowMillis < since || nowMillis - since >= backoffMillis(
                entry.attempts, baseBackoffMillis, maxBackoffMillis
            )
        }
        if (ready.isEmpty()) {
            val soonest = queue.mapNotNull { it.firstAttemptMillis }
                .minOrNull() ?: return QueueAction.Backoff(baseBackoffMillis)
            return QueueAction.Backoff((soonest + baseBackoffMillis - nowMillis).coerceAtLeast(0L))
        }
        return QueueAction.Send(ready.take(maxBatch))
    }

    /** Marks an attempt, stamping the first try and bumping the counter. */
    fun recordAttempt(entry: QueueEntry, nowMillis: Long): QueueEntry =
        entry.copy(
            attempts = entry.attempts + 1,
            firstAttemptMillis = entry.firstAttemptMillis ?: nowMillis
        )
}

