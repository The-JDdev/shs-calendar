package com.shs.calendar.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [AccountInput] — the part of the add-account form that is not the
 * Activity.
 *
 * The Activity itself needs a Context and an instrumented test, but every rule
 * it enforces about a typed address is pure, so the rules are tested here
 * rather than left to be discovered by a user on a phone. The project has no
 * Robolectric, which is exactly why this logic was pushed out of the screen.
 */
class AccountInputTest {

    private val work = DavDiscovery.CalendarRef(
        href = "https://cloud.example.com/dav/calendars/u/work/",
        displayName = "Work"
    )

    private val unnamed = DavDiscovery.CalendarRef(
        href = "https://cloud.example.com/dav/calendars/u/x/",
        displayName = ""
    )

    // ---- normalizeServerUrl ----

    @Test
    fun `bare host gains an https scheme`() {
        // The overwhelmingly common case: people paste "cloud.example.com".
        assertEquals("https://cloud.example.com", AccountInput.normalizeServerUrl("cloud.example.com"))
    }

    @Test
    fun `trailing slash is dropped so discovery starts from one root`() {
        assertEquals("https://cloud.example.com", AccountInput.normalizeServerUrl("https://cloud.example.com/"))
    }

    @Test
    fun `explicit http is respected for a LAN server with no tls`() {
        // Silently upgrading to https would break a legitimate plain-HTTP
        // server; the downgrade in the other direction is the user's choice.
        assertEquals("http://nas.local:8080", AccountInput.normalizeServerUrl("http://nas.local:8080"))
    }

    @Test
    fun `port and deep path survive normalisation`() {
        assertEquals(
            "https://dav.example.com:8443/remote.php/dav",
            AccountInput.normalizeServerUrl("  https://dav.example.com:8443/remote.php/dav/  ")
        )
    }

    @Test
    fun `blank input is not a url`() {
        assertNull(AccountInput.normalizeServerUrl(""))
        assertNull(AccountInput.normalizeServerUrl("   "))
    }

    @Test
    fun `scheme without a host is rejected`() {
        // "https://" alone would fail later with a confusing transport error.
        assertNull(AccountInput.normalizeServerUrl("https://"))
    }

    @Test
    fun `host without a dot is rejected`() {
        // localhost:8080 is a real and common CalDAV target, so this is a
        // judgement call; but a bare word with no dot is far more often a typo
        // than a host, and the error message tells the user what to fix.
        assertNull(AccountInput.normalizeServerUrl("notahost"))
    }

    @Test
    fun `non-http scheme is rejected rather than passed to the transport`() {
        // A "file://" or "ftp://" address must not reach HttpURLConnection.
        assertNull(AccountInput.normalizeServerUrl("ftp://example.com"))
        assertNull(AccountInput.normalizeServerUrl("javascript:alert(1)"))
    }

    // ---- originOf ----

    @Test
    fun `origin strips the path so discovery can re-walk from the root`() {
        // Pasting a deep collection URL must still discover: principal and home
        // set are found from the origin, not from the collection.
        assertEquals(
            "https://dav.example.com:8443",
            AccountInput.originOf("https://dav.example.com:8443/remote.php/dav/calendars/u/work/")
        )
    }

    @Test
    fun `origin of a bare host round-trips`() {
        assertEquals("https://cloud.example.com", AccountInput.originOf(AccountInput.normalizeServerUrl("cloud.example.com")!!))
    }

    // ---- validate ----

    @Test
    fun `a complete form validates`() {
        val ok = AccountInput.validate("cloud.example.com", "user", "Work stuff", work)
        assertEquals("https://cloud.example.com", ok.getOrNull()?.serverUrl)
        assertEquals("user", ok.getOrNull()?.username)
        assertEquals("Work stuff", ok.getOrNull()?.label)
    }

    @Test
    fun `blank label falls back to the calendar name`() {
        val ok = AccountInput.validate("cloud.example.com", "user", "   ", work)
        assertEquals("Work", ok.getOrNull()?.label)
    }

    @Test
    fun `blank label falls back to the host when the calendar is unnamed`() {
        val ok = AccountInput.validate("cloud.example.com", "user", "", unnamed)
        assertEquals("cloud.example.com", ok.getOrNull()?.label)
    }

    @Test
    fun `empty server is reported as its own problem`() {
        val problem = (AccountInput.validate("", "user", "L", work).exceptionOrNull() as AccountInput.FormException).problem
        assertEquals(AccountInput.Problem.EMPTY_SERVER, problem)
    }

    @Test
    fun `malformed server is reported as its own problem`() {
        val problem = (AccountInput.validate("nope", "user", "L", work).exceptionOrNull() as AccountInput.FormException).problem
        assertEquals(AccountInput.Problem.MALFORMED_SERVER, problem)
    }

    @Test
    fun `blank username is rejected`() {
        val problem = (AccountInput.validate("cloud.example.com", "  ", "L", work).exceptionOrNull() as AccountInput.FormException).problem
        assertEquals(AccountInput.Problem.EMPTY_USERNAME, problem)
    }

    @Test
    fun `no calendar chosen is rejected`() {
        val problem = (AccountInput.validate("cloud.example.com", "user", "L", null).exceptionOrNull() as AccountInput.FormException).problem
        assertEquals(AccountInput.Problem.NO_CALENDAR, problem)
    }

    @Test
    fun `username is trimmed before it is stored`() {
        // Whitespace from a paste would break the Basic credential pair, which
        // is built as "user:password".
        val ok = AccountInput.validate("cloud.example.com", "  user  ", "L", work)
        assertEquals("user", ok.getOrNull()?.username)
    }

    @Test
    fun `validation never carries a password`() {
        // The password is not an argument to validate() at all, so it cannot
        // end up in a Result that a caller might log. Asserted as a shape
        // property: Validated has no field that could hold one.
        val ok = AccountInput.validate("cloud.example.com", "user", "L", work).getOrNull()!!
        val fields = ok::class.java.declaredFields.map { it.name }
        assertTrue(fields.none { it.contains("pass", ignoreCase = true) || it.contains("secret", ignoreCase = true) })
        assertTrue(fields.containsAll(listOf("serverUrl", "username", "label", "calendar")))
    }
}
