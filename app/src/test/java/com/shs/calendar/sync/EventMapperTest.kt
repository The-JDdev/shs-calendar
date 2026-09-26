package com.shs.calendar.sync

import com.shs.calendar.data.entity.EventEntity
import com.shs.calendar.io.IcsCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ICS <-> model round trip.
 *
 * The remote server is unreachable in the sandbox, so the mapping is verified
 * the only way it can be honestly verified: event -> ICS text -> event.
 */
class EventMapperTest {

    private val start = 1_773_000_000_000L // 2026-03-01T09:00:00Z
    private val end = start + 90 * 60 * 1000L

    private val sample = EventEntity(
        id = 7,
        title = "Standup",
        description = "Daily sync; bring notes",
        startUtcMillis = start,
        endUtcMillis = end,
        allDay = false,
        location = "Room 3",
        url = "https://example.org/standup",
        category = "work,team",
        color = "#112233",
        participants = "a@example.org,b@example.org",
        organizer = "lead@example.org",
        rrule = "FREQ=WEEKLY;COUNT=10",
        davUid = "uid-123",
        davEtag = "\"etag-9\""
    )

    @Test
    fun `full round trip preserves the event`() {
        val ics = IcsCodec.write(listOf(EventMapper.toComponent(sample)))
        val back = IcsCodec.parse(ics)
        assertEquals(1, back.size)
        val c = back.single()
        assertEquals(IcsCodec.Kind.VEVENT, c.kind)
        assertEquals("uid-123", c.uid)
        assertEquals("Standup", c.summary)
        assertEquals("Daily sync; bring notes", c.description)
        assertEquals("Room 3", c.location)
        assertEquals("https://example.org/standup", c.url)
        assertEquals(listOf("work", "team"), c.categories)
        assertEquals(start, c.startUtcMillis)
        assertEquals(end, c.endUtcMillis)
        assertEquals("FREQ=WEEKLY;COUNT=10", c.rrule)
        assertEquals(listOf("a@example.org", "b@example.org"), c.participants)
    }

    @Test
    fun `separators inside text survive the round trip`() {
        // Semicolons and commas are the escape-sensitive characters; a codec
        // that forgets RFC 5545 3.3.11 silently corrupts this event.
        val tricky = sample.copy(
            title = "Budget, Q3; review",
            description = "line1\nline2, still line2"
        )
        val c = IcsCodec.parse(IcsCodec.write(listOf(EventMapper.toComponent(tricky)))).single()
        assertEquals("Budget, Q3; review", c.summary)
        assertEquals("line1\nline2, still line2", c.description)
    }

    @Test
    fun `all day events stay all day`() {
        val allDay = sample.copy(allDay = true)
        val c = IcsCodec.parse(IcsCodec.write(listOf(EventMapper.toComponent(allDay)))).single()
        assertTrue(c.allDay)
    }

    @Test
    fun `an unsynced event gets a deterministic derived uid`() {
        val local = sample.copy(davUid = null)
        val first = EventMapper.toComponent(local).uid
        val second = EventMapper.toComponent(local).uid
        // Stability matters: the same row pushed twice must not create two
        // remote events.
        assertEquals(first, second)
        assertTrue(first.startsWith("shs-local-"))
    }

    @Test
    fun `the etag is never written into the ics body`() {
        val ics = IcsCodec.write(listOf(EventMapper.toComponent(sample)))
        // An ETag in the payload is meaningless to a server and would shadow
        // the real validator from the DAV response.
        assertTrue(!ics.contains("etag-9"))
    }

    @Test
    fun `fromComponent threads the etag and account from the dav response`() {
        val c = EventMapper.toComponent(sample)
        val row = EventMapper.fromComponent(c, etag = "\"etag-9\"", accountId = "acct-1", calendarHref = "/dav/w/")
        assertNotNull(row)
        assertEquals("\"etag-9\"", row!!.davEtag)
        assertEquals("acct-1", row.davAccountId)
        assertEquals("/dav/w/", row.davCalendarHref)
        assertEquals("uid-123", row.davUid)
        // A freshly pulled event is clean: nothing local is pending.
        assertTrue(!row.davDirty)
    }

    @Test
    fun `a component with no start is rejected rather than persisted`() {
        val noStart = IcsCodec.Component(kind = IcsCodec.Kind.VEVENT, uid = "u", summary = "ghost")
        assertNull(EventMapper.fromComponent(noStart))
    }

    @Test
    fun `a missing end falls back to the start`() {
        val c = IcsCodec.Component(
            kind = IcsCodec.Kind.VEVENT, uid = "u", startUtcMillis = start, endUtcMillis = null
        )
        val row = EventMapper.fromComponent(c)
        assertEquals(start, row!!.startUtcMillis)
        assertEquals(start, row.endUtcMillis)
    }

    @Test
    fun `empty category and participant lists stay empty`() {
        val bare = sample.copy(category = "", participants = "")
        val c = EventMapper.toComponent(bare)
        assertTrue(c.categories.isEmpty())
        assertTrue(c.participants.isEmpty())
    }

    @Test
    fun `round trip is stable across two full cycles`() {
        var row: EventEntity = sample.copy(davUid = null, davEtag = null, id = 0)
        repeat(2) {
            val c = IcsCodec.parse(IcsCodec.write(listOf(EventMapper.toComponent(row)))).single()
            row = EventMapper.fromComponent(c)!!
        }
        assertEquals("Standup", row.title)
        assertEquals(start, row.startUtcMillis)
        assertEquals("FREQ=WEEKLY;COUNT=10", row.rrule)
    }
}
