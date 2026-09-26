package com.shs.calendar.sync

/**
 * Validation and normalisation for the add-account form, kept free of Android
 * so it is unit-testable on the JVM.
 *
 * The project has no Robolectric, so anything left inside the Activity cannot
 * be tested. Everything a field can be wrong about — an empty server, a URL
 * with no scheme, a server whose collection path is embedded, a label of pure
 * whitespace — is decided here, and the Activity only renders the messages.
 *
 * No credential value is ever returned or logged from this file: the password
 * is passed straight through to [SyncCredentialStore] by the caller.
 */
object AccountInput {

    /** Why the form cannot be submitted, as a value the UI can map to a string. */
    enum class Problem {
        EMPTY_SERVER,
        MALFORMED_SERVER,
        UNSUPPORTED_SCHEME,
        EMPTY_USERNAME,
        NO_CALENDAR
    }

    data class Validated(
        val serverUrl: String,
        val username: String,
        val label: String,
        /** The calendar the user picked from the discovery listing. */
        val calendar: DavDiscovery.CalendarRef
    )

    /**
     * Normalises a typed server URL into something [DavRequest] can use.
     *
     * A bare host is the common case (Nextcloud, Radicale, most self-hosted
     * servers) and people type it without a scheme. `https://` is assumed
     * rather than `http://`, because an https PROPFIND that fails is honest
     * and fixable while a silent downgrade to plaintext would send a Basic
     * credential over the wire in the clear. An explicit `http://` is
     * respected, since LAN-only servers legitimately have no TLS.
     *
     * Returns null when the text is not a URL at all; the caller distinguishes
     * that from an unsupported scheme via [Problem].
     */
    fun normalizeServerUrl(raw: String): String? {
        var text = raw.trim()
        if (text.isEmpty()) return null
        if (!text.contains("://")) text = "https://$text"
        val scheme = text.substringBefore("://").lowercase()
        if (scheme != "http" && scheme != "https") return null
        // Require an authority: "https://" alone has no host, and every
        // downstream PROPFIND would fail with a confusing error.
        val host = text.substringAfter("://").substringBefore('/')
        if (host.isBlank() || !host.contains('.')) return null
        return text.trimEnd('/')
    }

    /**
     * The origin used for discovery: scheme, host and port only, no path.
     *
     * The accounts row keeps the collection URL for sync, but discovery
     * re-walks principal -> home set from the root, so it must start from an
     * origin. Stripping the path here is what lets a user paste either a bare
     * host or a deep collection URL and have both work.
     */
    fun originOf(serverUrl: String): String =
        serverUrl.substringBefore("://") + "://" +
            serverUrl.substringAfter("://").substringBefore('/')

    /** Fills in a display label from the calendar, or from the host. */
    fun defaultLabel(serverUrl: String, calendar: DavDiscovery.CalendarRef): String =
        calendar.displayName.ifBlank { originOf(serverUrl).substringAfter("://") }

    /**
     * The full check, so the form's Save button can be enabled from one call
     * rather than re-deriving three rules in the Activity.
     */
    fun validate(
        serverRaw: String,
        username: String,
        labelRaw: String,
        calendar: DavDiscovery.CalendarRef?
    ): Result<Validated> = when {
        serverRaw.isBlank() -> Result.failure(FormException(Problem.EMPTY_SERVER))
        normalizeServerUrl(serverRaw) == null ->
            Result.failure(FormException(Problem.MALFORMED_SERVER))
        username.isBlank() -> Result.failure(FormException(Problem.EMPTY_USERNAME))
        calendar == null -> Result.failure(FormException(Problem.NO_CALENDAR))
        else -> Result.success(
            Validated(
                serverUrl = normalizeServerUrl(serverRaw)!!,
                username = username.trim(),
                label = labelRaw.trim().ifBlank { defaultLabel(normalizeServerUrl(serverRaw)!!, calendar) },
                calendar = calendar
            )
        )
    }

    /**
     * Distinct type rather than reusing a message string, so a test asserts on
     * the cause and the Activity maps it to a localised resource in one place.
     */
    class FormException(val problem: Problem) : IllegalArgumentException(problem.name)
}
