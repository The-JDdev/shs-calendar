package com.shs.calendar.ui.clock

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.shs.calendar.ui.SHSBaseActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shs.calendar.R
import com.shs.calendar.clock.WorldClock
import com.shs.calendar.data.CalendarDatabase
import com.shs.calendar.data.repository.SettingsRepository
import com.shs.calendar.location.TimeZones
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * M4: world clock screen.
 *
 * Shows live local times for a set of watched zones. The list is seeded with
 * the device zone so the screen is never empty on first launch, and a
 * lightweight handler ticks once a minute to keep the times current.
 */
class WorldClockActivity : SHSBaseActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val settingsRepo: SettingsRepository by lazy {
        SettingsRepository(CalendarDatabase.get(this))
    }
    private lateinit var adapter: WorldClockAdapter
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            adapter.refresh()
            if (running) handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_world_clock)

        findViewById<View>(R.id.clock_toolbar).setOnClickListener { finish() }

        // Long-press a row to drop that zone. RecyclerView has no
        // setOnItemLongClickListener, so the callback is owned by the row.
        adapter = WorldClockAdapter(
            use24Hour = true,
            onLongPress = { zoneId ->
                adapter.removeZone(zoneId)
                Toast.makeText(
                    this,
                    getString(R.string.clock_removed, zoneId.substringAfterLast('/')),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
        val list = findViewById<RecyclerView>(R.id.clock_list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        // Seed with the device zone so the list is never empty.
        adapter.setZones(listOf(TimeZones.deviceZone()))

        // Honour the user's 12/24h preference from the settings screen.
        lifecycleScope.launch {
            settingsRepo.observe().collectLatest { s ->
                adapter.setUse24Hour(s.clock24Hour)
            }
        }

        findViewById<View>(R.id.clock_add).setOnClickListener { promptForZone() }
    }

    /** Prompts for a time zone id and adds it, or reports an unknown zone. */
    private fun promptForZone() {
        val input = EditText(this).apply {
            hint = getString(R.string.clock_add_hint)
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.clock_add_zone)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val zoneId = input.text.toString().trim()
                if (zoneId.isEmpty() || !WorldClock.isValid(zoneId)) {
                    Toast.makeText(
                        this, R.string.clock_zone_invalid, Toast.LENGTH_SHORT
                    ).show()
                } else {
                    adapter.addZone(zoneId)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        running = true
        handler.post(tick)
    }

    override fun onPause() {
        running = false
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private companion object {
        const val TICK_MS = 60_000L
    }
}
