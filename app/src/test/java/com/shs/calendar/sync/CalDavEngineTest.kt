package com.shs.calendar.sync

import com.shs.calendar.data.entity.EventEntity
import com.shs.calendar.data.entity.SyncAccountEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the parts of the sync stack that need no server.
 *
 * These deliberately cover the two bugs found in review: a UID containing
 * non-ASCII (whose lead byte isLetterOrDigit() reports as a letter, so it
 * must still be percent-encoded) and the account filter in [CalDavEngine.planPush].
 */
class CalDavEngineTest {

    private fun account(id: Long = 1L) = SyncAccountEntity(
        id = id,
        label = "Work",
        calendarUrl = "https://dav.example.com/cal/work/",
        serverUrl = "https://dav.example.com",
        username = "u"
    )

    private fun event(id: Long, etag: String? = null, accountId: String? = null) = EventEntity(
        title = "Standup",
        startUtcMillis = 1_700_000_000_000L,
        endUtcMillis = 1_700_000_060_000L,
        davUid = "uid-$id",
        davEtag = etag,
        davAccountId = accountId
    )

    @Test
    fun `uid segment percent-encodes non-ascii lead bytes`() {
        // "é" is 0xC3 0xA9; the old code saw 0xC3.isLetterOrDigit() == true
        // and passed a mangled char straight through, corrupting the href.
        assertEquals("%C3%A9", DavRequest.encodeUid("é"))
    }

    @Test
    fun `uid segment leaves unreserved characters alone`() {
        assertEquals("abc-123_x.y~z", DavRequest.encodeUid("abc-123_x.y~z"))
    }

    @Test
    fun `uid segment encodes a slash so it cannot split the path`() {
        assertEquals("a%2Fb", DavRequest.encodeUid("a/b"))
    }

    @Test
    fun `planPush excludes events belonging to another account`() {
        val mine = event(1, accountId = "1")
        val theirs = event(2, accountId = "2")
        val unowned = event(3, accountId = null)
        val action = CalDavEngine().planPush(
            account = account(id = 1L),
            dirty = listOf(mine, theirs, unowned),
            nowMillis = 0L,
            online = true
        )
        // Compare against uidOrDerived(), not a literal: planPush keys the
        // queue on the mapper's derived UID, and hardcoding "uid-1" here would
        // test the wrong string.
        val uids = (action as QueueAction.Send).entries.map { it.uid }.toSet()
        assertEquals(
            setOf(EventMapper.uidOrDerived(mine), EventMapper.uidOrDerived(unowned)),
            uids
        )
    }

    @Test
    fun `planPush backoffs instead of dropping when offline`() {
        val action = CalDavEngine().planPush(
            account = account(),
            dirty = listOf(event(1, accountId = "1")),
            nowMillis = 0L,
            online = false
        )
        // Offline is the queue's reason to exist: a local edit made without
        // connectivity must wait, never be discarded.
        assertTrue(action is QueueAction.Backoff)
    }

    @Test
    fun `planPush marks an event with no etag as a create`() {
        val action = CalDavEngine().planPush(
            account = account(),
            dirty = listOf(event(1, etag = null, accountId = "1")),
            nowMillis = 0L,
            online = true
        )
        assertEquals(SyncOp.CREATE, (action as QueueAction.Send).entries.single().op)
    }

    @Test
    fun `planPush marks an event with a known etag as an update`() {
        val action = CalDavEngine().planPush(
            account = account(),
            dirty = listOf(event(1, etag = "\"abc\"", accountId = "1")),
            nowMillis = 0L,
            online = true
        )
        assertEquals(SyncOp.UPDATE, (action as QueueAction.Send).entries.single().op)
    }

    @Test
    fun `planPush is idle with nothing dirty`() {
        assertEquals(
            QueueAction.Idle,
            CalDavEngine().planPush(account(), emptyList(), 0L, online = true)
        )
    }
}
