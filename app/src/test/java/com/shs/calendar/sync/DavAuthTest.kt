package com.shs.calendar.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

/**
 * [DavAuth] is JVM-testable precisely because it takes no Context, so this
 * covers the one piece of credential handling that can run in a unit test.
 */
class DavAuthTest {

    private fun header(username: String, password: String?): String? =
        DavAuth.basicHeader(username, password)

    private fun decode(value: String): String =
        String(Base64.getDecoder().decode(value.removePrefix("Basic ")), Charsets.UTF_8)

    @Test
    fun encodesUsernameAndPasswordAsBasic() {
        val value = header("alice", "s3cret")
        assertEquals("Basic ", value?.take(6))
        assertEquals("alice:s3cret", decode(value!!))
    }

    @Test
    fun noHeaderWhenNoPasswordStored() {
        // A public calendar must send no header at all, not an empty one —
        // "Basic " + base64("alice:") would be a malformed credential.
        assertNull(header("alice", null))
    }

    @Test
    fun noHeaderForEmptyPassword() {
        assertNull(header("alice", ""))
    }

    @Test
    fun colonInPasswordIsNotAmbiguous() {
        // RFC 7617 splits on the FIRST colon only, so a password containing
        // one must survive the round trip intact.
        assertEquals("alice:pa:ss:word", decode(header("alice", "pa:ss:word")!!))
    }

    @Test
    fun nonAsciiPasswordIsEncodedAsUtf8() {
        assertEquals("alice:pässwörd", decode(header("alice", "pässwörd")!!))
    }

    @Test
    fun emptyUsernameStillProducesValidHeader() {
        assertEquals(":tok", decode(header("", "tok")!!))
    }
}
