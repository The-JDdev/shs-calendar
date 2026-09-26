package com.shs.calendar.ui.caldav

import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.shs.calendar.ui.SHSBaseActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.shs.calendar.R
import com.shs.calendar.data.CalendarDatabase
import com.shs.calendar.data.entity.SyncAccountEntity
import com.shs.calendar.sync.SyncCoordinator
import com.shs.calendar.sync.SyncCredentialStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Sync accounts screen: the list of CalDAV accounts, the state of each one,
 * and a manual "Sync now".
 *
 * The screen owns no sync logic of its own. It observes
 * [com.shs.calendar.data.dao.SyncAccountDao.observeAll] and calls
 * [SyncCoordinator.syncAll]; the coordinator writes `lastSyncMillis` and
 * `lastErrorMessage` back onto each row, and this screen re-renders from that
 * flow. Keeping the outcome in the database rather than in a local field is
 * what lets the same result show up after a process death or on the widget.
 */
class CalDavAccountsActivity : SHSBaseActivity() {

    private val db: CalendarDatabase by lazy { CalendarDatabase.get(this) }
    private val accountDao by lazy { db.syncAccountDao() }
    private val credentials by lazy { SyncCredentialStore(this) }

    private val coordinator: SyncCoordinator by lazy {
        SyncCoordinator(
            accountDao = accountDao,
            eventDao = db.eventDao(),
            // A factory, not a shared engine: CalDavEngine binds the
            // Authorization header at construction, so one engine instance
            // would send the first account's password to the second server.
            engineFor = { auth ->
                com.shs.calendar.sync.CalDavEngine(authFor = { auth })
            },
            authFor = { account -> credentials.authHeader(account.serverUrl, account.username) }
        )
    }

    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private lateinit var summary: TextView
    private lateinit var syncNow: MaterialButton
    private val adapter = CalDavAccountsAdapter(onToggle = ::setEnabled)

    /** Guards against a second pass starting while one is already running. */
    private var syncing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_caldav_accounts)

        findViewById<MaterialToolbar>(R.id.caldav_accounts_toolbar)?.apply {
            setNavigationOnClickListener { finish() }
        }

        list = findViewById(R.id.caldav_accounts_list)
        empty = findViewById(R.id.caldav_accounts_empty)
        summary = findViewById(R.id.caldav_sync_summary)
        syncNow = findViewById(R.id.caldav_sync_now)

        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<FloatingActionButton>(R.id.caldav_accounts_add)?.setOnClickListener {
            // The form exists and is registered; discovery is the only network
            // call and it is behind its own button, so opening this screen
            // always succeeds even with no connection.
            startActivity(
                android.content.Intent(
                    this,
                    CalDavAddAccountActivity::class.java
                )
            )
        }

        syncNow.setOnClickListener { runSync() }

        lifecycleScope.launch {
            accountDao.observeAll().collectLatest { accounts ->
                adapter.submit(accounts)
                empty.visibility = if (accounts.isEmpty()) View.VISIBLE else View.GONE
                list.visibility = if (accounts.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun setEnabled(account: SyncAccountEntity, enabled: Boolean) {
        lifecycleScope.launch {
            accountDao.setEnabled(account.id, enabled)
        }
    }

    /**
     * Runs a pass and reports what happened, including failure.
     *
     * The button is disabled for the duration and the coroutine is tied to the
     * Activity scope, so a pass is cancelled if the user leaves rather than
     * writing to a database the screen no longer owns.
     */
    private fun runSync() {
        if (syncing) return
        syncing = true
        syncNow.isEnabled = false
        summary.text = getString(R.string.caldav_sync_running)

        lifecycleScope.launch {
            val result = runCatching { coordinator.syncAll(System.currentTimeMillis()) }
            syncing = false
            syncNow.isEnabled = true
            summary.text = result.fold(
                onSuccess = { describe(it) },
                onFailure = { getString(R.string.caldav_sync_error, it.message ?: it.javaClass.simpleName) }
            )
        }
    }

    private fun describe(result: SyncCoordinator.Summary): String = when {
        result.accountsSynced == 0 && result.offline ->
            getString(R.string.caldav_offline)
        result.failed > 0 ->
            getString(R.string.caldav_partial, result.accountsSynced, result.failed)
        else ->
            getString(R.string.caldav_sync_done, result.pushed, result.pulled)
    }
}
