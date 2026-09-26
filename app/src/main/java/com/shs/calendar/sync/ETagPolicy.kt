package com.shs.calendar.sync

/**
 * Conditional-request policy for CalDAV PUT.
 *
 * RFC 7232 defines the conditional headers, and RFC 4791 section 5.3.2 requires
 * CalDAV clients to use them so two devices cannot silently clobber each other:
 *
 *  - If-None-Match: *  -> create, fails with 412 if the resource already exists.
 *  - If-Match: <etag>  -> replace, fails with 412 if the remote copy changed.
 *
 * Deciding which header (if any) to send is pure logic with no I/O, so it lives
 * here as a total function and is unit-tested directly.
 */

/** What a client should do with a single event push. */
enum class PushDecision {
    /** No remote copy: create with If-None-Match: *. */
    CREATE,

    /** Remote copy matches our local copy: safe replace with If-Match. */
    REPLACE,

    /** Remote copy changed under us: do not push, surface a conflict. */
    CONFLICT
}

/**
 * A ready-to-send conditional push.
 *
 * @param header  either "If-None-Match" or "If-Match"; null when [PushDecision.CONFLICT]
 * @param value   the header value, e.g. "*" or a quoted ETag; null on conflict
 */
data class PushPlan(
    val decision: PushDecision,
    val header: String? = null,
    val value: String? = null
) {
    /** Convenience for building a request map; empty means "send unconditionally". */
    fun headers(): Map<String, String> =
        if (header == null || value == null) emptyMap() else mapOf(header to value)
}

/**
 * Decides the conditional headers for one push.
 *
 * ETag comparison is a plain string compare on the *weak* validator with its
 * quotes and W/ prefix preserved by the caller. CalDAV servers are not uniform
 * about quoting, so a mismatch is treated as a conflict rather than guessed at.
 */
object ETagPolicy {

    /**
     * @param localEtag  ETag of the copy we last saw, null if we never synced it
     * @param remoteEtag ETag reported by the server, null if the resource is absent
     */
    fun plan(localEtag: String?, remoteEtag: String?): PushPlan {
        // A blank ETag is not a usable validator. Treat it as absent rather than
        // comparing "" == "" and authorising an overwrite on a meaningless value.
        val local = localEtag?.takeIf { it.isNotBlank() }
        val remote = remoteEtag?.takeIf { it.isNotBlank() }
        return when {
            // Server has no such resource: create-if-absent.
            remote == null -> PushPlan(PushDecision.CREATE, "If-None-Match", "*")

            // Remote is unchanged since our last sync: safe to overwrite.
            local != null && local == remote ->
                PushPlan(PushDecision.REPLACE, "If-Match", remote)

            // We have a local copy but no usable ETag for it, and one exists
            // remotely. We cannot prove the remote is unchanged, so refuse to
            // clobber it.
            else -> PushPlan(PushDecision.CONFLICT)
        }
    }
}
