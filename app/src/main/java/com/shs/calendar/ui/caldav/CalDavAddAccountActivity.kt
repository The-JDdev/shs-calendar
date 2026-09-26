package com.shs.calendar.ui.caldav

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.shs.calendar.ui.SHSBaseActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.shs.calendar.R
import com.shs.calendar.data.CalendarDatabase
import com.shs.calendar.data.entity.SyncAccountEntity
import com.shs.calendar.sync.AccountInput
import com.shs.calendar.sync.DavAuth
import com.shs.calendar.sync.DavDiscovery
import com.shs.calendar.sync.SyncCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Add-account form: address, credentials, calendar picker, save.
 *
 * Discovery is the only network call and it runs behind an explicit
 * "Find calendars" button, never on text change — a PROPFIND per keystroke
 * would hammer the user's server and would leak a password to a host the
 * user has not finished typing. The password is read only at the moment the
 * button is pressed, is never written to a log, and goes straight to
 * [SyncCredentialStore]; the account row keeps only the server URL, username
 * and chosen collection.
 *
 * Offline is a supported path: the status line says what is wrong instead of
 * a spinner that never stops, and a failure here changes nothing already on
 * the device.
 */
class CalDavAddAccountActivity : SHSBaseActivity() {

    private val db: CalendarDatabase by lazy { CalendarDatabase.get(this) }
    private val accountDao by lazy { db.syncAccountDao() }
    private val credentials by lazy { SyncCredentialStore(this) }

    private lateinit var serverLayout: TextInputLayout
    private lateinit var server: TextInputEditText
    private lateinit var usernameLayout: TextInputLayout
    private lateinit var username: TextInputEditText
    private lateinit var password: TextInputEditText
    private lateinit var status: TextView
    private lateinit var pickLabel: TextView
    private lateinit var calendar: Spinner
    private lateinit var labelLayout: TextInputLayout
    private lateinit var label: TextInputEditText
    private lateinit var discover: MaterialButton
    private lateinit var save: MaterialButton

    /** Calendars returned by the last successful discovery. */
    private var found: List<DavDiscovery.CalendarRef> = emptyList()

    /** Guards a second discovery starting while one is in flight. */
    private var searching = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_caldav_add_account)

        findViewById<MaterialToolbar>(R.id.caldav_add_toolbar)?.apply {
            setNavigationOnClickListener { finish() }
        }

        serverLayout = findViewById(R.id.caldav_add_server_layout)
        server = findViewById(R.id.caldav_add_server)
        usernameLayout = findViewById(R.id.caldav_add_username_layout)
        username = findViewById(R.id.caldav_add_username)
        password = findViewById(R.id.caldav_add_password)
        status = findViewById(R.id.caldav_add_status)
        pickLabel = findViewById(R.id.caldav_add_pick_label)
        calendar = findViewById(R.id.caldav_add_calendar)
        labelLayout = findViewById(R.id.caldav_add_label_layout)
        label = findViewById(R.id.caldav_add_label)
        discover = findViewById(R.id.caldav_add_discover)
        save = findViewById(R.id.caldav_add_save)

        discover.setOnClickListener { runDiscovery() }
        save.setOnClickListener { saveAccount() }
    }
    // MARK: - discovery

    /**
     * PROPFIND the typed address and fill the picker with what came back.
     *
     * The header is built from the fields as typed and handed to the
     * transport per request, so nothing here caches a credential. Runs on IO
     * because it is three sequential round trips; the UI only ever sees the
     * finished result or a message.
     */
    private fun runDiscovery() {
        if (searching) return
        val address = server.text?.toString().orEmpty()
        val normalized = AccountInput.normalizeServerUrl(address)
        if (normalized == null) {
            showProblem(if (address.isBlank()) AccountInput.Problem.EMPTY_SERVER
            else AccountInput.Problem.MALFORMED_SERVER)
            return
        }
        val user = username.text?.toString().orEmpty()
        if (user.isBlank()) {
            showProblem(AccountInput.Problem.EMPTY_USERNAME)
            return
        }

        searching = true
        discover.isEnabled = false
        status.setTextColor(getColor(R.color.shs_text_muted))
        status.setText(R.string.caldav_add_discovering)

        lifecycleScope.launch {
            // withContext(IO): lifecycleScope defaults to Main, and a PROPFIND
            // there would throw NetworkOnMainThreadException.
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    DavDiscovery.discover(
                        serverUrl = AccountInput.originOf(normalized),
                        auth = DavAuth.basicHeader(user, password.text?.toString().orEmpty())
                    )
                }
            }
            searching = false
            discover.isEnabled = true
            render(result.getOrElse { DavDiscovery.DiscoveryResult(errorMessage = it.message.orEmpty()) })
        }
    }

    /** Shows either the calendar picker or the reason there is nothing to show. */
    private fun render(result: DavDiscovery.DiscoveryResult) {
        if (!result.ok) {
            found = emptyList()
            save.isEnabled = false
            status.setTextColor(getColor(R.color.shs_danger))
            status.text = result.errorMessage
            return
        }
        if (result.calendars.isEmpty()) {
            found = emptyList()
            save.isEnabled = false
            status.setTextColor(getColor(R.color.shs_text_muted))
            status.setText(R.string.caldav_add_no_calendars)
            return
        }
        found = result.calendars
        calendar.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            result.calendars.map { it.displayName }
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        calendar.setSelection(0)
        pickLabel.visibility = View.VISIBLE
        calendar.visibility = View.VISIBLE
        labelLayout.visibility = View.VISIBLE
        findViewById<TextView>(R.id.caldav_add_label_hint).visibility = View.VISIBLE
        status.setTextColor(getColor(R.color.shs_text_muted))
        status.text = getString(R.string.caldav_add_pick_calendar)
        save.isEnabled = true
    }

    private fun showProblem(problem: AccountInput.Problem) {
        val res = when (problem) {
            AccountInput.Problem.EMPTY_SERVER -> R.string.caldav_problem_empty_server
            AccountInput.Problem.MALFORMED_SERVER -> R.string.caldav_problem_malformed
            // normalizeServerUrl rejects a non-http(s) scheme and a scheme-less
            // host the same way — both are null returns — so both map here.
            AccountInput.Problem.UNSUPPORTED_SCHEME -> R.string.caldav_problem_malformed
            AccountInput.Problem.EMPTY_USERNAME -> R.string.caldav_problem_empty_username
            AccountInput.Problem.NO_CALENDAR -> R.string.caldav_problem_no_calendar
        }
        // Each message lands on the field it is about, not in one generic
        // banner: the user needs to know which box to fix.
        serverLayout.error = when (problem) {
            AccountInput.Problem.EMPTY_SERVER, AccountInput.Problem.MALFORMED_SERVER ->
                getString(res)
            else -> null
        }
        usernameLayout.error =
            if (problem == AccountInput.Problem.EMPTY_USERNAME) getString(res) else null
        status.setTextColor(getColor(R.color.shs_danger))
        status.setText(res)
        save.isEnabled = false
    }
    // MARK: - save

    /**
     * Validates, stores the secret, writes the row.
     *
     * The password is written to the encrypted store *before* the row exists,
     * because the store is keyed by (serverUrl, username) and not by row id.
     * If the insert then fails the secret is removed again, so a failed save
     * cannot leave a dangling credential no account refers to.
     */
    private fun saveAccount() {
        val checked = AccountInput.validate(
            serverRaw = server.text?.toString().orEmpty(),
            username = username.text?.toString().orEmpty(),
            labelRaw = label.text?.toString().orEmpty(),
            calendar = found.getOrNull(calendar.selectedItemPosition)
        )
        val ok = checked.getOrNull()
        if (ok == null) {
            val problem = (checked.exceptionOrNull() as? AccountInput.FormException)?.problem
            showProblem(problem ?: AccountInput.Problem.NO_CALENDAR)
            return
        }

        save.isEnabled = false
        lifecycleScope.launch {
            val address = ok.serverUrl
            val user = ok.username
            val secret = password.text?.toString().orEmpty()
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    if (secret.isNotEmpty()) {
                        credentials.putPassword(address, user, secret)
                    }
                    accountDao.upsert(
                        SyncAccountEntity(
                            label = ok.label,
                            calendarUrl = ok.calendar.href,
                            serverUrl = address,
                            username = user,
                            colorHex = ok.calendar.colorHex
                        )
                    )
                }
            }
            // Roll the secret back if the row did not land, so the store never
            // holds a credential for an account that does not exist.
            if (written.isFailure && secret.isNotEmpty()) {
                credentials.remove(address, user)
            }
            save.isEnabled = written.isSuccess
            if (written.isSuccess) {
                Toast.makeText(this@CalDavAddAccountActivity, R.string.caldav_add_saved, Toast.LENGTH_SHORT).show()
                finish()
            } else {
                status.setTextColor(getColor(R.color.shs_danger))
                status.text = written.exceptionOrNull()?.message ?: getString(R.string.caldav_sync_error, "")
                save.isEnabled = true
            }
        }
    }
}
