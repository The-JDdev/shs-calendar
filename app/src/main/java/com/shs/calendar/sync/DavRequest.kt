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
    fun absolute(url: String, href: String): String {
        if (href.startsWith("http://", ignoreCase = true) ||
            href.startsWith("https://", ignoreCase = true)
        ) {
            return href
        }
        val base = url.substringBeforeLast('/', url)
        val origin = base.substringBeforeLast('/', url)
        if (href.startsWith("/")) return origin + href
        return base.trimEnd('/') + "/" + href.trimStart('/')
    }

    fun propfindHeaders(): Map<String, String> = linkedMapOf(
        "Depth" to PROPFIND_DEPTH,
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
