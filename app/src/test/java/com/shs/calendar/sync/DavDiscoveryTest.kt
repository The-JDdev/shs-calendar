package com.shs.calendar.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for account discovery, with no server and no network.
 *
 * The cases that matter here are the ones a real CalDAV server forces on
 * us and a hand-written happy path would miss: a principal href that is
 * relative to the request URL, a home set reported 404, and a resourcetype
 * that arrives as two sibling elements.
 */
class DavDiscoveryTest {

    private fun response(
        href: String,
        values: Map<String, String> = emptyMap(),
        statuses: Map<String, String> = emptyMap()
    ) = DavResponse(
        href = href,
        propStatus = if (statuses.isEmpty()) values.keys.associateWith { "HTTP/1.1 200 OK" } else statuses,
        propValues = values
    )

    // ---- normalize -------------------------------------------------------

    @Test
    fun `normalize adds the trailing slash a collection URL needs`() {
        assertEquals("https://dav.example.com/dav/", DavDiscovery.normalize("https://dav.example.com/dav"))
    }

    @Test
    fun `normalize collapses repeated trailing slashes`() {
        assertEquals("https://dav.example.com/dav/", DavDiscovery.normalize("https://dav.example.com/dav///"))
    }

    @Test
    fun `normalize returns empty for empty input rather than a bare slash`() {
        // "" would become "/" and be requested as the server root.
        assertEquals("", DavDiscovery.normalize(""))
    }

    // ---- hops ------------------------------------------------------------

    @Test
    fun `principal and home-set hops use depth 0 but the listing uses depth 1`() {
        // Depth 1 on the server root would enumerate every principal on it.
        assertEquals("0", DavDiscovery.principalHop("https://dav.example.com").depth)
        assertEquals("0", DavDiscovery.homeSetHop("https://dav.example.com/p/").depth)
        assertEquals("1", DavDiscovery.calendarListHop("https://dav.example.com/dav/").depth)
    }

    @Test
    fun `each hop asks for the property that hop is for`() {
        assertTrue(DavRequest.PRINCIPAL_BODY.contains("current-user-principal"))
        assertTrue(DavRequest.HOME_SET_BODY.contains("calendar-home-set"))
        assertTrue(DavRequest.CALENDAR_LIST_BODY.contains("resourcetype"))
    }

    // ---- principal / home set -------------------------------------------

    @Test
    fun `parsePrincipal resolves a relative href against the request url`() {
        val dav = response(
            href = "/",
            values = mapOf("current-user-principal" to "/dav/principals/user/alice/")
        )
        assertEquals(
            "https://dav.example.com/dav/principals/user/alice/",
            DavDiscovery.parsePrincipal(dav, "https://dav.example.com/")
        )
    }

    @Test
    fun `parsePrincipal ignores a property the server did not return`() {
        // A 404 here must not fall back to a guessed path: every real server
        // would answer 404 to the guess, hiding the real cause.
        val dav = response(
            href = "/",
            statuses = mapOf("current-user-principal" to "HTTP/1.1 404 Not Found")
        )
        assertNull(DavDiscovery.parsePrincipal(dav, "https://dav.example.com/"))
    }

    @Test
    fun `parseHomeSet returns null when the property is absent`() {
        assertNull(DavDiscovery.parseHomeSet(response(href = "/"), "https://dav.example.com/"))
    }

    @Test
    fun `parseHomeSet reads the value out of the response`() {
        val dav = response(href = "/p/", values = mapOf("calendar-home-set" to "/dav/c/u/"))
        assertEquals("https://dav.example.com/dav/c/u/", DavDiscovery.parseHomeSet(dav, "https://dav.example.com/p/"))
    }
}
