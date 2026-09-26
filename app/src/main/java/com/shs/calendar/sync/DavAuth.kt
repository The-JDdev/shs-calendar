package com.shs.calendar.sync

/**
 * Basic-auth header construction.
 *
 * Split out from [SyncCredentialStore] because it is the security-critical
 * part and needs no Android: header building is pure, so it is unit-testable
 * on the JVM, whereas the store itself needs a real Context and keystore.
 * The project's test setup has no Robolectric, so anything left inside the
 * store would be untestable.
 */
internal object DavAuth {

    /**
     * RFC 7617 credentials, or null when there is no password — a public
     * calendar must get no header at all rather than an empty one.
     */
    fun basicHeader(username: String, password: String?): String? {
        if (password.isNullOrEmpty()) return null
        val pair = "$username:$password"
        return "Basic " + java.util.Base64.getEncoder()
            .encodeToString(pair.toByteArray(Charsets.UTF_8))
    }
}
