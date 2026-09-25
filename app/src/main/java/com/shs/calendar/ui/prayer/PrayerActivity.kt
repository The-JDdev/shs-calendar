package com.shs.calendar.ui.prayer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.shs.calendar.R
import com.shs.calendar.data.CalendarDatabase
import com.shs.calendar.data.entity.SettingsEntity
import com.shs.calendar.data.repository.SettingsRepository
import com.shs.calendar.location.LocationPickerActivity
import com.shs.calendar.prayer.PrayerEngine
import com.shs.calendar.prayer.PrayerSettings
import com.shs.calendar.prayer.PrayerTimes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * M2 prayer screen: next-prayer countdown plus the full timetable, all
 * computed by [PrayerEngine] from the persisted location, method, madhab,
 * high-latitude rule and manual offsets. Null times render as
 * "Unavailable" (polar day/night) — never fabricated.
 */
class PrayerActivity : AppCompatActivity() {

    private val repo: SettingsRepository by lazy {
        SettingsRepository(CalendarDatabase.get(this))
    }

    private lateinit var locationLabel: TextView
    private lateinit var methodSummary: TextView
    private lateinit var nextName: TextView
    private lateinit var nextTime: TextView
    private lateinit var countdown: TextView
    private lateinit var emptyState: LinearLayout
    private lateinit var timesList: LinearLayout

    private var settings = SettingsEntity()
    private var times: PrayerTimes? = null
    private var nextKey: String = "dhuhr"
    private var nextAt: LocalTime? = null
    private var zone: ZoneId = ZoneId.systemDefault()
    private var ticker: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prayer)

        findViewById<MaterialToolbar>(R.id.prayer_toolbar)?.apply {
            setNavigationIcon(R.drawable.ic_back)
            setNavigationOnClickListener { finish() }
        }

        locationLabel = findViewById(R.id.prayer_location_label)
        methodSummary = findViewById(R.id.prayer_method_summary)
        nextName = findViewById(R.id.prayer_next_name)
        nextTime = findViewById(R.id.prayer_next_time)
        countdown = findViewById(R.id.prayer_countdown)
        emptyState = findViewById(R.id.prayer_empty_state)
        timesList = findViewById(R.id.prayer_times_list)

        findViewById<View>(R.id.prayer_set_location)?.setOnClickListener {
            startActivity(Intent(this, LocationPickerActivity::class.java))
        }

        lifecycleScope.launch {
            repo.observe().collectLatest { s ->
                settings = s
                render()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Recompute on return (e.g. after picking a location or crossing midnight).
        if (::locationLabel.isInitialized) render()
    }

    // ---- rendering ----------------------------------------------------------

    private fun zoneOf(s: SettingsEntity): ZoneId =
        if (s.useDeviceTimezone) ZoneId.systemDefault()
        else runCatching { ZoneId.of(s.timezone) }.getOrElse { ZoneId.systemDefault() }

    private fun render() {
        zone = zoneOf(settings)
        val lat = settings.latitude
        val lon = settings.longitude
        if (lat == null || lon == null) {
            locationLabel.setText(R.string.prayer_no_location)
            methodSummary.text = ""
            emptyState.visibility = View.VISIBLE
            timesList.removeAllViews()
            times = null
            ticker?.cancel()
            return
        }
        emptyState.visibility = View.GONE
        locationLabel.text = settings.locationName.ifBlank {
            String.format(Locale.US, "%.2f\u00B0, %.2f\u00B0", lat, lon)
        }
        methodSummary.text = methodSummaryText()

        val today = LocalDate.now(zone)
        val t = PrayerEngine.compute(
            today, lat, lon, zone, PrayerSettings.configOf(settings)
        )
        times = t

        val next = PrayerEngine.nextPrayer(t, LocalTime.now(zone), today)
        nextKey = next.first
        nextAt = next.second
        renderRows()
        updateHeader()

        ticker?.cancel()
        ticker = lifecycleScope.launch {
            while (true) {
                delay(1000)
                tick()
            }
        }
    }

    private fun renderRows() {
        timesList.removeAllViews()
        val t = times ?: return
        for ((key, time) in PrayerEngine.orderedEntries(t)) {
            val row = layoutInflater.inflate(R.layout.prayer_row, timesList, false)
            val name = row.findViewById<TextView>(R.id.prayer_row_name)
            val note = row.findViewById<TextView>(R.id.prayer_row_note)
            val timeView = row.findViewById<TextView>(R.id.prayer_row_time)

            name.setText(labelId(key))
            val noteId = noteId(key)
            if (noteId != null) {
                note.setText(noteId)
                note.visibility = View.VISIBLE
            } else {
                note.visibility = View.GONE
            }
            timeView.text = formatTime(time)

            val isNext = key == nextKey
            name.setTextColor(
                ContextCompat.getColor(
                    this, if (isNext) R.color.shs_cyan else R.color.shs_text_primary
                )
            )
            timeView.setTextColor(
                ContextCompat.getColor(
                    this, if (isNext) R.color.shs_cyan else R.color.shs_text_secondary
                )
            )
            name.typeface = if (isNext) android.graphics.Typeface.DEFAULT_BOLD else null
            timesList.addView(row)
        }
    }

    private fun updateHeader() {
        nextName.setText(labelId(nextKey))
        nextTime.text = formatTime(nextAt)
        tick()
    }

    /** Per-second countdown; re-selects the next prayer as time passes. */
    private fun tick() {
        val t = times ?: return
        val now = LocalTime.now(zone)
        val next = PrayerEngine.nextPrayer(t, now, LocalDate.now(zone))
        if (next.first != nextKey || next.second != nextAt) {
            nextKey = next.first
            nextAt = next.second
            updateHeader()
            renderRows()
            return
        }
        val target = nextAt ?: return
        var d = Duration.between(now, target)
        if (d.isNegative) d = d.plusDays(1)
        countdown.text = getString(R.string.prayer_countdown_in, formatDuration(d))
    }

    // ---- helpers ------------------------------------------------------------

    private fun methodSummaryText(): String {
        val methods = resources.getStringArray(R.array.prayer_methods)
        val madhabs = resources.getStringArray(R.array.prayer_madhabs)
        val m = methods.getOrElse(PrayerSettings.methodOf(settings).ordinal) { methods[0] }
        val h = madhabs.getOrElse(PrayerSettings.madhabOf(settings).ordinal) { madhabs[0] }
        return "$m \u00B7 $h"
    }

    private fun labelId(key: String): Int = when (key) {
        "imsak" -> R.string.prayer_imsak
        "fajr" -> R.string.prayer_fajr
        "sunrise" -> R.string.prayer_sunrise
        "dhuhr" -> R.string.prayer_dhuhr
        "asr" -> R.string.prayer_asr
        "sunset" -> R.string.prayer_sunset
        "maghrib" -> R.string.prayer_maghrib
        "isha" -> R.string.prayer_isha
        "midnight" -> R.string.prayer_midnight
        else -> R.string.prayer_tahajjud
    }

    private fun noteId(key: String): Int? = when (key) {
        "imsak" -> R.string.prayer_note_suhoor
        "maghrib" -> R.string.prayer_note_iftar
        else -> null
    }

    private fun formatTime(t: LocalTime?): String {
        t ?: return getString(R.string.prayer_unavailable)
        val pattern = if (settings.clock24Hour) "HH:mm" else "h:mm a"
        return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(t)
    }

    private fun formatDuration(d: Duration): String {
        val h = d.toHours()
        val m = d.toMinutes() % 60
        val s = d.seconds % 60
        return when {
            h > 0 -> String.format(Locale.getDefault(), "%dh %dm", h, m)
            m > 0 -> String.format(Locale.getDefault(), "%dm %ds", m, s)
            else -> "<1m"
        }
    }
}
