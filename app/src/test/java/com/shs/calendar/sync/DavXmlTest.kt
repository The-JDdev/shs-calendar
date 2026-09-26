package com.shs.calendar.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parser tests against captured-shaped multistatus samples.
 *
 * The sample mirrors what Nextcloud/Radicale return for a PROPFIND on
 * calendar-home-set and for a calendar-query REPORT: a <d:multistatus>
 * of <d:response>, each with <d:href> and one or more <d:propstat>.
 */
class DavXmlTest {

    /** A PROPFIND listing two collections and the current-user-principal. */
    private val propfindSample = """
        <?xml version="1.0" encoding="utf-8"?>
        <d:multistatus xmlns:d="DAV:" xmlns:cs="http://calendarserver.org/ns/">
          <d:response>
            <d:href>/remote.php/dav/calendars/user/</d:href>
            <d:propstat>
              <d:prop>
                <d:resourcetype><d:collection/></d:resourcetype>
                <d:displayname>calendars</d:displayname>
              </d:prop>
              <d:status>HTTP/1.1 200 OK</d:status>
            </d:propstat>
          </d:response>
          <d:response>
            <d:href>/remote.php/dav/calendars/user/work/</d:href>
            <d:propstat>
              <d:prop>
                <d:resourcetype><d:collection/></d:resourcetype>
                <d:displayname>Work</d:displayname>
                <cs:getctag>ctag-1234</cs:getctag>
              </d:prop>
              <d:status>HTTP/1.1 200 OK</d:status>
            </d:propstat>
            <d:propstat>
              <d:prop><d:owner/></d:prop>
              <d:status>HTTP/1.1 404 Not Found</d:status>
            </d:propstat>
          </d:response>
        </d:multistatus>
    """.trimIndent()

    @Test
    fun `parses one response per href`() {
        val parsed = DavXml.parseMultistatus(propfindSample)
        assertEquals(2, parsed.size)
        assertEquals("/remote.php/dav/calendars/user/", parsed[0].href)
        assertEquals("/remote.php/dav/calendars/user/work/", parsed[1].href)
    }

    @Test
    fun `extracts leaf property values through the prop container`() {
        // Regression: a dot-all body let <d:prop> pair with </d:prop> first and
        // swallow every child, so every map came back empty.
        val work = DavXml.parseMultistatus(propfindSample)[1]
        assertEquals("Work", work.propValues["displayname"])
        assertEquals("ctag-1234", work.propValues["getctag"])
        assertTrue(work.has("displayname"))
    }

    @Test
    fun `property status is recorded per propstat block`() {
        val work = DavXml.parseMultistatus(propfindSample)[1]
        assertTrue(work.has("displayname"))
        // The owner property came back 404, so it must not count as present.
        assertTrue(!work.has("owner"))
        assertEquals("HTTP/1.1 404 Not Found", work.propStatus["owner"])
    }

    @Test
    fun `stripped namespace prefixes are matched by local name`() {
        // cs:getctag must be reachable as "getctag"; d:href as "href".
        val first = DavXml.parseMultistatus(propfindSample)[0]
        assertTrue(first.propValues.containsKey("displayname"))
    }

    @Test
    fun `parses calendar data from a report response`() {
        val ics = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:abc\r\nSUMMARY:Standup\r\n" +
            "DTSTART:20260301T090000Z\r\nDTEND:20260301T091500Z\r\nEND:VEVENT\r\nEND:VCALENDAR"
        val report = """
            <d:multistatus xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav"
                            xmlns:cs="http://calendarserver.org/ns/">
              <d:response>
                <d:href>/dav/calendars/user/work/abc.ics</d:href>
                <d:propstat>
                  <d:prop>
                    <d:getetag>"etag-1"</d:getetag>
                    <cal:calendar-data version="2.0">$ics</cal:calendar-data>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val parsed = DavXml.parseMultistatus(report)
        assertEquals(1, parsed.size)
        val r = parsed[0]
        assertEquals("\"etag-1\"", r.propValues["getetag"])
        assertTrue(r.calendarData!!.contains("SUMMARY:Standup"))
        assertTrue(r.calendarData!!.contains("DTSTART:20260301T090000Z"))
    }

    @Test
    fun `blank or non-multistatus bodies yield no responses instead of throwing`() {
        assertTrue(DavXml.parseMultistatus("").isEmpty())
        assertTrue(DavXml.parseMultistatus("   ").isEmpty())
        assertTrue(DavXml.parseMultistatus("<html><body>502</body></html>").isEmpty())
        assertTrue(DavXml.parseMultistatus("not xml at all").isEmpty())
    }

    @Test
    fun `response without an href is skipped`() {
        val xml = """
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:propstat><d:prop><d:displayname>x</d:displayname></d:prop>
                <d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()
        assertTrue(DavXml.parseMultistatus(xml).isEmpty())
    }

    @Test
    fun `absent calendar data reads as null`() {
        val first = DavXml.parseMultistatus(propfindSample)[0]
        assertNull(first.calendarData)
    }
}
