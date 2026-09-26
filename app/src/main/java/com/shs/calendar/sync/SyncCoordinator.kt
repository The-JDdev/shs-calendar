package com.shs.calendar.sync

import com.shs.calendar.data.dao.EventDao
import com.shs.calendar.data.dao.SyncAccountDao
import com.shs.calendar.data.entity.EventEntity
import com.shs.calendar.data.entity.SyncAccountEntity

/**
 * Runs one sync pass over the subscribed accounts and records the outcome.
 *
 * This is the layer that makes CalDAV actually work: [CalDavEngine] can push
 * and pull, but nothing told it which rows are dirty, resolved the password,
 * or wrote the result back. Splitting it this way keeps every decision here
 * testable without a socket:
 *
 *   - [mergePulled] is pure: it decides which pulled rows are new and which
 *     overwrite a local copy, and it is the half that is unit-tested.
 *   - [syncAll] does the I/O and the database writes.
 *
 * [authFor] is injected rather than constructing a [SyncCredentialStore]
 * here, for the same reason [CalDavEngine] takes one: a unit test has no
 * Context and no keystore. Production wiring passes
 * { store.authHeader(it.serverUrl, it.username) }.
 */
class SyncCoordinator(
    private val accountDao: SyncAccountDao,
    private val eventDao: EventDao,
    /**
     * Builds the engine for one account's credentials.
     *
     * A factory rather than a single injected engine: [CalDavEngine] binds
     * the Authorization header at construction, and a per-call `auth` is the
     * only way to serve several accounts without one account's password
     * reaching another's server. An injected engine instance would silently
     * ignore the second account.
     */
    private val engineFor: (String?) -> CalDavEngine = { auth -> CalDavEngine(authFor = { auth }) },
    private val authFor: (SyncAccountEntity) -> String? = { null }
) {

    /** Aggregate of a full pass, surfaced by the accounts UI and the worker. */
    data class Summary(
        val accountsSynced: Int = 0,
        val pushed: Int = 0,
        val pulled: Int = 0,
        val deleted: Int = 0,
        val conflicts: Int = 0,
        val failed: Int = 0,
        /** True when every account failed, i.e. almost certainly no network. */
        val offline: Boolean = false
    )

    /**
     * What [mergePulled] decided to write.
     *
     * Kept as a plan rather than performed inside the function so the
     * decision is assertable in a test: insert-vs-update is the part of sync
     * that silently loses user data when it is wrong.
     */
    data class MergePlan(
        val inserts: List<EventEntity> = emptyList(),
        val updates: List<EventEntity> = emptyList(),
        val localIdsToDelete: List<Long> = emptyList()
    )

    /**
     * Decides how pulled rows merge into the local table. Pure; no I/O.
     *
     * Three rules, each earning its place:
     *
     * 1. An href the server reported as 404 deletes the local row. Skipping
     *    this resurrects every event the user deleted on the server.
     * 2. A pulled row whose davUid already exists locally overwrites the
     *    local copy. [localByUid] must already be scoped to the account being
     *    synced, so this is (davUid, account) in effect; keying on davUid
     *    alone would let two subscribed servers holding the same event
     *    overwrite each other.
     * 3. A local row that is dirty is NOT overwritten. The user's edit is
     *    newer than the server's; clobbering it here is data loss, and the
     *    push path is the only thing allowed to win that race.
     *
     * [localByHref] must likewise be scoped to the account: an href is
     * relative to one collection, and matching globally could delete a row
     * belonging to a different server that happens to share a path.
     */
    fun mergePulled(
        pulled: List<EventEntity>,
        deletedHrefs: List<String>,
        localByUid: Map<String, EventEntity>,
        localByHref: Map<String, EventEntity>
    ): MergePlan {
        val deletes = deletedHrefs
            .mapNotNull { localByHref[it]?.id }
            .distinct()
            .toMutableList()

        val inserts = ArrayList<EventEntity>()
        val updates = ArrayList<EventEntity>()
        for (event in pulled) {
            val key = event.davUid
            val existing = if (key != null) localByUid[key] else null
            when {
                existing == null -> inserts += event
                // Rule 3: the local copy carries an unsent edit.
                existing.davDirty -> Unit
                else -> updates += event.copy(id = existing.id)
            }
        }
        return MergePlan(inserts, updates, deletes)
    }

    /**
     * Syncs every enabled account and records the outcome on each row.
     *
     * Push runs before pull on purpose. A pull brings the server's copy of an
     * event the user has just edited locally, and [mergePulled] refuses to
     * overwrite a dirty row, so the local edit survives and is then pushed.
     * The reverse order would still be safe, but it would do a pointless
     * pull-then-push round trip for every event.
     *
     * A failure on one account never aborts the pass: the remaining accounts
     * still get their chance, because one unreachable server is not a reason
     * to leave every other calendar stale.
     */
    suspend fun syncAll(nowMillis: Long): Summary {
        val accounts = accountDao.enabled()
        if (accounts.isEmpty()) return Summary()

        var pushed = 0
        var pulled = 0
        var deleted = 0
        var conflicts = 0
        var failed = 0

        for (account in accounts) {
            val auth = authFor(account)
            val engineForAccount = engineFor(auth)

            val pushResult = engineForAccount.push(
                account = account,
                dirty = eventDao.dirtyForAccount(account.id.toString()),
                nowMillis = nowMillis
            )
            if (!pushResult.ok) {
                failed++
                accountDao.markFailed(account.id, pushResult.errorMessage)
                continue
            }
            pushed += pushResult.pushed

            val pullResult = engineForAccount.pull(account)
            if (!pullResult.ok) {
                failed++
                accountDao.markFailed(account.id, pullResult.errorMessage)
                continue
            }

            val accountId = account.id.toString()
            val localByUid = pullResult.events
                .mapNotNull { it.davUid }
                .distinct()
                .mapNotNull { uid -> eventDao.findByDavUid(uid, accountId)?.let { uid to it } }
                .toMap()
            val localByHref = pullResult.deletedHrefs
                .mapNotNull { href -> eventDao.findByCalendarHref(href, accountId)?.let { href to it } }
                .toMap()
            val plan = mergePulled(
                pulled = pullResult.events,
                deletedHrefs = pullResult.deletedHrefs,
                // Scoped to this account; see mergePulled.
                localByUid = localByUid,
                localByHref = localByHref
            )
            for (event in plan.inserts) eventDao.insert(event)
            for (event in plan.updates) eventDao.update(event)
            for (id in plan.localIdsToDelete) eventDao.deleteById(id)

            pulled += plan.inserts.size + plan.updates.size
            deleted += plan.localIdsToDelete.size
            conflicts += pullResult.conflicts
            accountDao.markSynced(account.id, nowMillis, pullResult.ctag)
        }

        return Summary(
            accountsSynced = accounts.size - failed,
            pushed = pushed,
            pulled = pulled,
            deleted = deleted,
            conflicts = conflicts,
            failed = failed,
            // Only "every account failed" implies the user is offline. One
            // broken server among three working ones is not an offline state.
            offline = failed > 0 && failed == accounts.size
        )
    }
}
