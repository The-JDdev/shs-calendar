package com.shs.calendar.sync

import com.shs.calendar.data.entity.EventEntity
import com.shs.calendar.data.entity.SyncAccountEntity
import com.shs.calendar.io.IcsCodec

/**
 * Two-way CalDAV sync for one account: pull remote changes, push local ones.
 *
 * Split in two halves that are independently testable:
 *   - [planPush] decides what to send, with no I/O at all.
 *   - [push] / [pull] do the actual HTTP through [DavHttp].
 *
 * All request shapes come from [DavRequest] and all response parsing from
 * [DavXml], so the protocol rules stay unit-testable without a server.
 */
class CalDavEngine(
    private val http: DavHttp = DavHttp(),
    /**
     * Resolves the Authorization header for one account, or null for a public
     * calendar. A function rather than a SyncCredentialStore so this class
     * stays constructible in a unit test with no Context and no keystore —
     * [planPush] is the half that is tested without a server, and the HTTP
     * half is injected via [http]. Production wiring passes
     * { store.authHeader(it.serverUrl, it.username) }.
     */
    private val authFor: (SyncAccountEntity) -> String? = { null }
) {

    /** Outcome of one account pass, reported back to the accounts UI. */
    data class Result(
        val pushed: Int = 0,
        val pulled: Int = 0,
        val failed: Int = 0,
        val errorMessage: String = "",
        val ctag: String? = null
    ) {
        val ok: Boolean get() = errorMessage.isEmpty()
    }

    /**
     * VEVENT body for one local event. Exposed so the wire format can be
     * asserted in a unit test without opening a socket.
     */
    fun buildBody(event: EventEntity): String =
        IcsCodec.write(listOf(EventMapper.toComponent(event)), "SHS Calendar")

    /**
     * Everything one PROPFIND told us, still unmapped to storage.
     *
     * [events] are remote rows ready to compare against local ones. Remote
     * hrefs whose status is 404 are reported in [deletedHrefs] so the caller
     * can drop the matching local rows; they are deliberately not folded into
     * [events], because "absent" and "present but empty" must not be conflated.
     * [ctag] is the collection tag to cache for the next conditional pull.
     */
    data class PullResult(
        val events: List<EventEntity> = emptyList(),
        val deletedHrefs: List<String> = emptyList(),
        val conflicts: Int = 0,
        val ctag: String? = null,
        val errorMessage: String = ""
    ) {
        val ok: Boolean get() = errorMessage.isEmpty()
    }

    /**
     * The entries that are due to be sent right now.
     *
     * [account] is not consulted for the decision itself — the batch comes from
     * the dirty rows — but it scopes which collection the hrefs are built
     * against, so the caller cannot accidentally send one account's events to
     * another's server.
     */
    fun planPush(
        account: SyncAccountEntity,
        dirty: List<EventEntity>,
        nowMillis: Long,
        online: Boolean
    ): QueueAction = SyncQueue.plan(
        queue = dirty.filter { it.davAccountId == null || it.davAccountId == account.id.toString() }
            .map { event ->
                QueueEntry(
                    uid = EventMapper.uidOrDerived(event),
                    op = if (event.davEtag == null) SyncOp.CREATE else SyncOp.UPDATE,
                    etag = event.davEtag
                )
            },
        nowMillis = nowMillis,
        online = online
    )

    /**
     * Sends the due batch. A 412 Precondition Failed means the server copy
     * moved underneath us, so the event is left dirty for the next pass to
     * re-resolve rather than being force-overwritten.
     */
    fun push(account: SyncAccountEntity, dirty: List<EventEntity>, nowMillis: Long): Result {
        val send = planPush(account, dirty, nowMillis, online = true) as? QueueAction.Send
            ?: return Result()
        var pushed = 0
        var failed = 0
        var lastError = ""
        for (entry in send.entries) {
            val event = dirty.firstOrNull { EventMapper.uidOrDerived(it) == entry.uid } ?: continue
            val url = DavRequest.eventUrl(account.calendarUrl, entry.uid)
            val res = http.put(url, buildBody(event), entry.etag, authFor(account))
            when {
                res.status in 200..299 -> pushed++
                res.status == 412 -> failed++
                else -> {
                    failed++
                    lastError = "HTTP ${res.status}"
                }
            }
        }
        return Result(pushed = pushed, failed = failed, errorMessage = lastError)
    }

    /**
     * Reads the remote collection and converts each VEVENT into a local row.
     *
     * Returns parsed entities rather than writing them, so the caller decides
     * what to persist and a unit test can assert the mapping with no database.
     * A 207 that is not a multistatus, or a 404, yields an empty list with the
     * reason in [Result.errorMessage] instead of throwing.
     */
    fun pull(account: SyncAccountEntity): PullResult {
        val res = http.propfind(account.calendarUrl, authFor(account))
        if (res.status == 404) {
            return PullResult(errorMessage = "Calendar not found: ${account.calendarUrl}")
        }
        if (res.status !in 200..299) {
            return PullResult(errorMessage = "HTTP ${res.status}")
        }

        val events = ArrayList<EventEntity>()
        val deleted = ArrayList<String>()
        var conflicts = 0
        for (dav in DavXml.parseMultistatus(res.body)) {
            val href = DavRequest.absolute(account.calendarUrl, dav.href)
            // A 404 in a propstat block is how a server reports "this href is
            // gone". Treating it as a normal response would resurrect a
            // deleted event, so it is routed to deletedHrefs instead.
            //
            // The status is read from the *response*, never from a literal:
            // statusCode() extracts the leading numeric token of whatever
            // string it is handed, so passing a constant here would compare a
            // hardcoded "404" with itself and match every href. It is a member
            // of DavResponse, so it is called on `dav`, not on DavXml.
            val gone = dav.propStatus.values.any { dav.statusCode(it) == 404 }
            if (gone) {
                deleted += href
                continue
            }
            val ics = dav.calendarData ?: continue
            for (component in IcsCodec.parse(ics)) {
                val entity = EventMapper.fromComponent(
                    component = component,
                    etag = dav.propValues["getetag"],
                    accountId = account.id.toString(),
                    calendarHref = href
                )
                if (entity == null) {
                    // No start in the VEVENT: it cannot be placed on a
                    // calendar, so count it as a conflict rather than a row.
                    conflicts++
                    continue
                }
                events += entity
            }
        }
        return PullResult(
            events = events,
            deletedHrefs = deleted,
            conflicts = conflicts,
            ctag = res.ctag
        )
    }
}
