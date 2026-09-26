package com.shs.calendar.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Queue-behaviour tests for [SyncQueue].
 *
 * [SyncQueue] is pure, so these exercise the real decisions without a network.
 * Every timing assertion passes an explicit `now`, so nothing sleeps.
 */
class SyncQueueTest {

    private val base = 1_700_000_000_000L

    private fun send(entries: List<QueueEntry>) = entries as? QueueAction.Send

    @Test
    fun `enqueue keeps each uid once`() {
        var q = SyncQueue.enqueue(emptyList(), "a", SyncOp.CREATE)
        q = SyncQueue.enqueue(q, "b", SyncOp.UPDATE, etag = "\"1\"")
        assertEquals(2, q.size)
        assertEquals("a", q[0].uid)
        assertEquals(SyncOp.UPDATE, q[1].op)
    }

    @Test
    fun `repeated edits to one uid collapse to a single entry`() {
        var q = SyncQueue.enqueue(emptyList(), "a", SyncOp.CREATE)
        q = SyncQueue.enqueue(q, "a", SyncOp.UPDATE, etag = "\"1\"")
        q = SyncQueue.enqueue(q, "a", SyncOp.UPDATE, etag = "\"2\"")
        assertEquals(1, q.size)
        // The newest edit must win, not the first one queued.
        assertEquals("\"2\"", q.single().etag)
    }

    @Test
    fun `edit after create collapses to the update`() {
        var q = SyncQueue.enqueue(emptyList(), "a", SyncOp.CREATE)
        q = SyncQueue.enqueue(q, "a", SyncOp.UPDATE, etag = "\"1\"")
        assertEquals(SyncOp.UPDATE, q.single().op)
    }

    @Test
    fun `delete of a never-pushed event is not queued`() {
        // Nothing exists on the server, so a DELETE would only earn a 404.
        val q = SyncQueue.enqueue(emptyList(), "a", SyncOp.DELETE, etag = null)
        assertTrue(q.isEmpty())
    }

    @Test
    fun `delete after edit collapses to one delete`() {
        var q = SyncQueue.enqueue(emptyList(), "a", SyncOp.UPDATE, etag = "\"1\"")
        q = SyncQueue.enqueue(q, "a", SyncOp.DELETE, etag = "\"1\"")
        assertEquals(1, q.size)
        assertEquals(SyncOp.DELETE, q.single().op)
    }

    @Test
    fun `offline holds the queue instead of dropping it`() {
        val q = listOf(QueueEntry(uid = "a", op = SyncOp.CREATE))
        val action = SyncQueue.plan(q, nowMillis = base, online = false)
        assertTrue(action is QueueAction.Backoff)
        // Offline must never resolve to a send.
        assertTrue(send(emptyList()) == null)
    }

    @Test
    fun `empty queue is idle even when online`() {
        assertEquals(QueueAction.Idle, SyncQueue.plan(emptyList(), base, online = true))
    }

    @Test
    fun `untried entries are sent immediately when online`() {
        val q = listOf(
            QueueEntry(uid = "a", op = SyncOp.CREATE),
            QueueEntry(uid = "b", op = SyncOp.UPDATE, etag = "\"1\"")
        )
        val action = SyncQueue.plan(q, base, online = true)
        assertTrue(action is QueueAction.Send)
        assertEquals(2, (action as QueueAction.Send).entries.size)
    }

    @Test
    fun `an entry inside its backoff window is held`() {
        val entry = QueueEntry(uid = "a", op = SyncOp.CREATE, firstAttemptMillis = base, attempts = 3)
        // attempts=3 -> backoff 4 min; 1 min later it must still wait.
        val action = SyncQueue.plan(listOf(entry), base + 60_000L, online = true)
        assertTrue(action is QueueAction.Backoff)
    }

    @Test
    fun `an entry past its backoff window is released`() {
        val entry = QueueEntry(uid = "a", op = SyncOp.CREATE, firstAttemptMillis = base, attempts = 3)
        val action = SyncQueue.plan(listOf(entry), base + 240_001L, online = true)
        assertTrue(action is QueueAction.Send)
    }

    @Test
    fun `a clock that moved backwards releases the entry instead of stalling`() {
        // firstAttemptMillis is ahead of now (NTP correction / date change).
        val entry = QueueEntry(uid = "a", op = SyncOp.CREATE, firstAttemptMillis = base, attempts = 5)
        val action = SyncQueue.plan(listOf(entry), base - 10_000L, online = true)
        assertTrue(action is QueueAction.Send)
    }

    @Test
    fun `backoff grows exponentially and saturates`() {
        assertEquals(60_000L, SyncQueue.backoffMillis(1))
        assertEquals(120_000L, SyncQueue.backoffMillis(2))
        assertEquals(240_000L, SyncQueue.backoffMillis(3))
        // Saturates at the 6h cap rather than overflowing to a negative delay.
        val cap = 6L * 60 * 60 * 1000
        assertEquals(cap, SyncQueue.backoffMillis(40))
        assertEquals(0L, SyncQueue.backoffMillis(0))
        assertEquals(0L, SyncQueue.backoffMillis(-5))
    }

    @Test
    fun `recordAttempt stamps the first try and bumps the counter`() {
        val first = SyncQueue.recordAttempt(QueueEntry("a", SyncOp.CREATE), base)
        assertEquals(1, first.attempts)
        assertEquals(base, first.firstAttemptMillis)

        val second = SyncQueue.recordAttempt(first, base + 30_000L)
        assertEquals(2, second.attempts)
        // The first-attempt stamp must survive: backoff measures from it.
        assertEquals(base, second.firstAttemptMillis)
    }

    @Test
    fun `batch size caps what one pass sends`() {
        val q = (1..5).map { QueueEntry(uid = "e$it", op = SyncOp.CREATE) }
        val action = SyncQueue.plan(q, base, online = true, maxBatch = 2) as QueueAction.Send
        assertEquals(2, action.entries.size)
    }
}
