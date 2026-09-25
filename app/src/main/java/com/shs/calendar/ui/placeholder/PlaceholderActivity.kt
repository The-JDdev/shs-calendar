package com.shs.calendar.ui.placeholder

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.shs.calendar.R

/**
 * Phase-2/3 placeholder for screens outside the Phase 1 dashboard scope
 * (prayer times, accounts, advanced tools). Shows a title, explanatory body
 * and the phase badge so navigation never dead-ends on an empty screen.
 */
class PlaceholderActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_placeholder)

        val key = intent.getStringExtra(EXTRA_KEY) ?: KEY_DEFAULT
        val (titleRes, bodyRes) = when (key) {
            KEY_PRAYER -> R.string.placeholder_prayer_title to R.string.placeholder_prayer_body
            KEY_ACCOUNTS -> R.string.placeholder_accounts_title to R.string.placeholder_accounts_body
            else -> R.string.placeholder_phase2 to R.string.placeholder_phase3
        }
        findViewById<TextView>(R.id.placeholder_title).setText(titleRes)
        findViewById<TextView>(R.id.placeholder_body).setText(bodyRes)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.placeholder_toolbar)
            ?.setNavigationOnClickListener { finish() }
    }

    companion object {
        const val EXTRA_KEY = "placeholder_key"
        const val KEY_PRAYER = "prayer"
        const val KEY_ACCOUNTS = "accounts"
        private const val KEY_DEFAULT = "default"
    }
}
