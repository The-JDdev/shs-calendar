package com.shs.calendar.sync

/**
 * CalDAV account discovery: server URL in, list of subscribable calendars out.
 *
 * RFC 6764 defines the walk:
 *   1. PROPFIND the server root for `current-user-principal` -> /dav/principals/user/
 *   2. PROPFIND that principal for `calendar-home-set`      -> /dav/calendars/user/
 *   3. PROPFIND the home set (Depth: 1) for `resourcetype`,
 *      `displayname` and `supported-calendar-component-set`
 *
 * Step 3 is a listing, so it is separated from steps 1-2: [parsePrincipal] and
 * [parseHomeSet] are pure string parsing and are unit-tested without a server,
 * exactly like the rest of this package. [discover] does the I/O.
 */
object DavDiscovery {

    /** One remote calendar offered for subscription. */
    data class CalendarRef(
        /** Absolute collection URL, normalised to end with a single '/'. */
        val href: String,
        val displayName: String,
        /** Server-set colour as #RRGGBB, or "" when the server sent none. */
        val colorHex: String = "",
        val supportsTasks: Boolean = false
    )

    /** Outcome of a discovery attempt; [calendars] is empty on failure. */
    data class DiscoveryResult(
        val calendars: List<CalendarRef> = emptyList(),
        val errorMessage: String = ""
    ) {
        val ok: Boolean get() = errorMessage.isEmpty()
    }

    /**
     * Steps 1-2 as a plan: given a server root, return the PROPFIND body and
     * depth needed for each hop, so the sequence is testable without I/O.
     */
    data class Hop(val url: String, val body: String, val depth: String)

    fun principalHop(serverUrl: String): Hop =
        Hop(normalize(serverUrl), DavRequest.PRINCIPAL_BODY, "0")

    fun homeSetHop(principalUrl: String): Hop =
        Hop(normalize(principalUrl), DavRequest.HOME_SET_BODY, "0")

    fun calendarListHop(homeSetUrl: String): Hop =
        Hop(normalize(homeSetUrl), DavRequest.CALENDAR_LIST_BODY, "1")

    /** Trailing '/' is mandatory on a DAV collection URL; collapses doubles. */
    fun normalize(url: String): String {
        val trimmed = url.trim().trimEnd('/')
        return if (trimmed.isEmpty()) trimmed else "$trimmed/"
    }

    /**
     * Reads the principal URL out of a `current-user-principal` response.
     *
     * Returns null when the property is absent or did not come back 2xx:
     * guessing a principal path would produce a 404 on every real server.
     */
    fun parsePrincipal(dav: DavResponse, baseUrl: String): String? {
        if (!dav.has("current-user-principal")) return null
        val href = dav.propValues["current-user-principal"]?.trim()
        if (href.isNullOrEmpty()) return null
        return normalize(DavRequest.absolute(baseUrl, href))
    }

    /** Reads the calendar home set, or null when the server did not return it. */
    fun parseHomeSet(dav: DavResponse, baseUrl: String): String? {
        if (!dav.has("calendar-home-set")) return null
        val href = dav.propValues["calendar-home-set"]?.trim()
        if (href.isNullOrEmpty()) return null
        return normalize(DavRequest.absolute(baseUrl, href))
    }

    /**
     * Turns a calendar-home-set multistatus into subscribable calendars.
     *
     * Two filters are needed conceptually, but only one survives as code:
     *  - the collection must be a *calendar*. The home set also holds the
     *    address books and the principal itself, which answer 200 with a
     *    resourcetype that is not a calendar.
     *  - resourcetype arrives as two sibling elements, <d:collection/> and
     *    <cal:calendar/>, and DavXml stores each as its own value with "" for
     *    the self-closing one. So "resourcetype" is absent from the map
     *    entirely; the presence of the separate "calendar" key is the signal,
     *    and that single test covers the first point too.
     */
    fun parseCalendarList(
        responses: List<DavResponse>,
        homeSetUrl: String
    ): List<CalendarRef> =
        responses
            .filter { it.propStatus.containsKey("calendar") }
            .map { dav ->
                val href = normalize(DavRequest.absolute(homeSetUrl, dav.href))
                val name = dav.propValues["displayname"]?.trim().orEmpty()
                CalendarRef(
                    href = href,
                    // Fall back to the last path segment so a calendar is
                    // still identifiable when the server sent no displayname.
                    displayName = name.ifEmpty { href.trimEnd('/').substringAfterLast('/') },
                    colorHex = normalizeColor(dav.propValues["calendar-color"]),
                    supportsTasks = dav.propValues["supported-calendar-component-set"]
                        ?.contains("VTODO", ignoreCase = true) == true
                )
            }

    /**
     * Server colours are advisory and arrive in several dialects: #RRGGBB,
     * 0xRRGGBB, an #AARRGGBB from a few clients, or a named CSS colour. Only a
     * plain #RRGGBB is accepted; anything else yields "" and the UI falls back
     * to its own palette. Guessing a hex out of a named colour would be
     * inventing data the server never sent.
     */
    fun normalizeColor(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (!value.startsWith("#") || value.length != 7) return ""
        if (!value.drop(1).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return ""
        return value.lowercase()
    }

    /**
     * Walks all three hops and returns the calendars on the account.
     *
     * The only part that touches the network. [auth] is the Basic-auth header
     * value, built by the caller from what the user just typed, so this object
     * needs no Context, no keystore and no SyncCredentialStore.
     *
     * A 401 is reported as a distinct message rather than a bare "HTTP 401":
     * the overwhelmingly common cause is a wrong password, and the user needs
     * to know that to act. No credential value ever reaches this string.
     */
    fun discover(
        serverUrl: String,
        auth: String? = null,
        http: DavHttp = DavHttp()
    ): DiscoveryResult {
        val principalHop = principalHop(serverUrl)
        val principalRes = http.propfind(principalHop.url, auth, principalHop.body, principalHop.depth)
        if (principalRes.status == 401) return DiscoveryResult(errorMessage = unauthorizedMessage)
        if (principalRes.status !in 200..299) {
            return DiscoveryResult(errorMessage = "HTTP ${principalRes.status} discovering principal")
        }
        val principalUrl = DavXml.parseMultistatus(principalRes.body)
            .firstNotNullOfOrNull { parsePrincipal(it, principalHop.url) }
            ?: return DiscoveryResult(errorMessage = "No current-user-principal returned by the server")

        val homeHop = homeSetHop(principalUrl)
        val homeRes = http.propfind(homeHop.url, auth, homeHop.body, homeHop.depth)
        if (homeRes.status == 401) return DiscoveryResult(errorMessage = unauthorizedMessage)
        if (homeRes.status !in 200..299) {
            return DiscoveryResult(errorMessage = "HTTP ${homeRes.status} reading calendar home set")
        }
        val homeUrl = DavXml.parseMultistatus(homeRes.body)
            .firstNotNullOfOrNull { parseHomeSet(it, homeHop.url) }
            ?: return DiscoveryResult(errorMessage = "No calendar-home-set returned by the server")

        val listHop = calendarListHop(homeUrl)
        val listRes = http.propfind(listHop.url, auth, listHop.body, listHop.depth)
        if (listRes.status == 401) return DiscoveryResult(errorMessage = unauthorizedMessage)
        if (listRes.status !in 200..299) {
            return DiscoveryResult(errorMessage = "HTTP ${listRes.status} listing calendars")
        }
        val calendars = parseCalendarList(DavXml.parseMultistatus(listRes.body), listHop.url)
        return DiscoveryResult(calendars = calendars)
    }

    internal const val unauthorizedMessage: String = "Authentication failed: check username and password"
}
