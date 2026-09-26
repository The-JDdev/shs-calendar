package com.shs.calendar.sync

/**
 * Pure construction of CalDAV/WebDAV HTTP requests.
 *
 * Deliberately separate from [DavHttp], which performs the socket I/O. Keeping
 * the two apart means every method, URL, header and body this app sends can be
 * unit-tested on the JVM with no server, no network and no Android classes.
 */
object DavRequest {

    const val PROPFIND_BODY: String = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:" xmlns:cs="http://calendarserver.org/ns/">
  <d:prop>
    <d:getetag/>
    <d:getctag/>
    <cs:getctag/>
  </d:prop>
</d:propfind>"""

    const val PROPFIND_METHOD: String = "PROPFIND"

    /**
     * Discovery request bodies, one per hop of the RFC 6764 walk:
     * server root -> principal -> calendar home set -> calendar list.
     *
     * They are separate constants rather than one templated body because each
     * hop asks for a different single property, and a test can assert the
     * exact property name present in each request.
     */
    const val PRINCIPAL_BODY: String = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop><d:current-user-principal/></d:prop>
</d:propfind>"""

    const val HOME_SET_BODY: String = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav">
  <d:prop><cal:calendar-home-set/></d:prop>
</d:propfind>"""

    /**
     * Calendar listing. supported-calendar-component-set tells us whether the
     * collection holds VEVENTs, VTODOs or both; calendar-color is the
     * server-set #RRGGBB we show in the accounts list.
     */
    const val CALENDAR_LIST_BODY: String = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav"
            xmlns:cs="http://calendarserver.org/ns/">
  <d:prop>
    <d:resourcetype/>
    <d:displayname/>
    <cal:supported-calendar-component-set/>
    <cs:getctag/>
    <cal:calendar-color/>
  </d:prop>
</d:propfind>"""

    /**
     * Depth 1 on a collection returns the collection plus its members, which is
     * exactly the change set we want. Depth 0 would hide changed items and
     * "infinity" invites servers to reject the request.
     */
    const val PROPFIND_DEPTH: String = "1"

    /**
     * Absolute hrefs are required: hrefs inside a multistatus body are
     * relative to the request URL, so a relative href cannot be resolved
     * without re-implementing base-URL joining on every response.
     */
    /**
     * The scheme + authority of [url], with no trailing slash.
     *
     * Must not be derived by chopping at the last '/': for a bare origin like
     * "https://dav.example.com/" that slices through the "//" of the scheme
     * and yields "https:/", so a root-relative href resolves to
     * "https://dav.example.com" -> "https:/dav/..." and the request goes to a
     * host that does not exist. The third '/' after the scheme is the first
     * that actually begins the path.
     */
    private fun origin(url: String): String {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return url.trimEnd('/')
        val afterScheme = schemeEnd + 3
        val pathStart = url.indexOf('/', afterScheme)
        return if (pathStart < 0) url.trimEnd('/') else url.substring(0, pathStart)
    }

    /**
     * [url] with its last path segment removed, so a relative href resolves
     * against the containing collection rather than against the resource.
     *
     * For "https://host/dav/cal/work/" this is "https://host/dav/cal/work".
     * The last '/' of the whole string is the only safe split point: the
     * scheme's "//" is always further left, so lastIndexOf can never land
     * inside it, which is precisely what substringBeforeLast over the full
     * URL could not guarantee.
     */
    private fun base(url: String): String {
        val schemeEnd = url.indexOf("://")
        val afterScheme = if (schemeEnd < 0) 0 else schemeEnd + 3
        val lastSlash = url.lastIndexOf('/')
        return if (lastSlash < afterScheme) url.trimEnd('/') else url.substring(0, lastSlash)
    }

    fun absolute(url: String, href: String): String {
        if (href.startsWith("http://", ignoreCase = true) ||
            href.startsWith("https://", ignoreCase = true)
        ) {
            return href
        }
        if (href.startsWith("/")) return origin(url) + href
        return base(url).trimEnd('/') + "/" + href.trimStart('/')
    }

    fun propfindHeaders(): Map<String, String> = propfindHeaders(PROPFIND_DEPTH)

    /**
     * Headers for a PROPFIND at an explicit Depth.
     *
     * Discovery needs this: the principal and calendar-home-set hops ask for
     * a single property on one resource, so Depth 0 keeps the reply small,
     * while the calendar listing needs Depth 1 to enumerate members. Defaulting
     * to 1 for those would return the whole server.
     */
    fun propfindHeaders(depth: String): Map<String, String> = linkedMapOf(
        "Depth" to depth,
        "Content-Type" to "application/xml; charset=utf-8"
    )

    fun putHeaders(localEtag: String?): Map<String, String> =
        ETagPolicy.plan(localEtag, null).headers() + mapOf(
            "Content-Type" to "text/calendar; charset=utf-8"
        )

    fun deleteHeaders(localEtag: String?): Map<String, String> =
        ETagPolicy.plan(localEtag, null).headers()

    /**
     * Characters that need no escaping in a URL path segment (RFC 3986
     * unreserved + sub-delims, minus the delimiters that change path meaning).
     */
    private const val SAFE_PUNCT: String = "-_.~!\$&'()*+,;=:@"

    /** URL-encodes one path segment without breaking on an existing slash. */
    fun encodeUid(uid: String): String = buildString {
        for (byte in uid.toByteArray(Charsets.UTF_8)) {
            val v = byte.toInt() and 0xFF
            // Test the byte value, not the widened char: isLetterOrDigit()
            // returns true for e.g. 0xE2 ('a-circumflex'), which is the lead
            // byte of a multi-byte UTF-8 sequence and must be percent-encoded.
            val alnum = v in 0x41..0x5A || v in 0x61..0x7A || v in 0x30..0x39
            if (alnum || v.toChar() in SAFE_PUNCT) {
                append(v.toChar())
            } else {
                append('%').append("%02X".format(v))
            }
        }
    }

    fun eventUrl(base: String, uid: String): String =
        absolute(base, encodeUid(uid) + ".ics")
}
