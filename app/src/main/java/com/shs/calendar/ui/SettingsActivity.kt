package com.shs.calendar.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.shs.calendar.R
import com.shs.calendar.data.CalendarDatabase
import com.shs.calendar.data.repository.SettingsRepository
import java.time.DayOfWeek
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Settings screen: first day of week, time format, default calendar system,
 * Bengali numerals toggle, Hijri moon-sighting adjustment (−3..+3).
 *
 * Every change is persisted through [SettingsRepository] and takes effect
 * immediately on the dashboard via its settings Flow.
 */
class SettingsActivity : AppCompatActivity() {

    private val repo: SettingsRepository by lazy {
        SettingsRepository(CalendarDatabase.get(this))
    }

    private lateinit var firstDaySpinner: Spinner
    private lateinit var timeFormatGroup: RadioGroup
    private lateinit var defaultSystemSpinner: Spinner
    private lateinit var switchBengaliNumerals: SwitchMaterial
    private lateinit var hijriAdjustText: TextView
    private lateinit var travelModeSwitch: SwitchMaterial
    private var updatingUi = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<MaterialToolbar>(R.id.settings_toolbar)?.apply {
            setNavigationIcon(R.drawable.ic_back)
            setNavigationOnClickListener { finish() }
        }

        firstDaySpinner = findViewById(R.id.settings_first_day_spinner)
        timeFormatGroup = findViewById(R.id.settings_time_format_group)
        defaultSystemSpinner = findViewById(R.id.settings_calendar_spinner)
        switchBengaliNumerals = findViewById(R.id.settings_bengali_numerals_switch)
        hijriAdjustText = findViewById(R.id.settings_hijri_value)
        travelModeSwitch = findViewById(R.id.settings_travel_mode_switch)

        firstDaySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            dayNames()
        )
        defaultSystemSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            resources.getStringArray(R.array.calendar_systems)
        )

        bindControls()

        lifecycleScope.launch {
            repo.observe().collectLatest { s ->
                updatingUi = true
                firstDaySpinner.setSelection(
                    (s.firstDayOfWeek.coerceIn(1, 7) - 1 + 7) % 7
                )
                timeFormatGroup.check(
                    if (s.clock24Hour) R.id.settings_time_24 else R.id.settings_time_12
                )
                defaultSystemSpinner.setSelection(
                    when (s.defaultSystem) {
                        "BENGALI" -> 1
                        "HIJRI" -> 2
                        else -> 0
                    }
                )
                switchBengaliNumerals.isChecked = s.bengaliNumerals
                travelModeSwitch.isChecked = s.useDeviceTimezone
                hijriAdjustText.text = (s.hijriAdjustment.coerceIn(-3, 3)).toString()
                updatingUi = false
            }
        }
    }

    private fun bindControls() {
        firstDaySpinner.onItemSelectedListener = simpleSelection { pos ->
            persist { it.copy(firstDayOfWeek = ((pos + 6) % 7) + 1) }
        }
        timeFormatGroup.setOnCheckedChangeListener { _, checkedId ->
            if (!updatingUi) persist { it.copy(clock24Hour = checkedId == R.id.settings_time_24) }
        }
        defaultSystemSpinner.onItemSelectedListener = simpleSelection { pos ->
            persist {
                it.copy(
                    defaultSystem = when (pos) {
                        1 -> "BENGALI"
                        2 -> "HIJRI"
                        else -> "GREGORIAN"
                    }
                )
            }
        }
        switchBengaliNumerals.setOnCheckedChangeListener { _, checked ->
            if (!updatingUi) persist { it.copy(bengaliNumerals = checked) }
        }
        travelModeSwitch.setOnCheckedChangeListener { _, checked ->
            if (!updatingUi) persist { it.copy(useDeviceTimezone = checked) }
        }
        findViewById<android.view.View>(R.id.settings_hijri_minus)?.setOnClickListener {
            adjustHijri(-1)
        }
        findViewById<android.view.View>(R.id.settings_hijri_plus)?.setOnClickListener {
            adjustHijri(+1)
        }
    }

    private fun adjustHijri(delta: Int) {
        persist { s ->
            s.copy(hijriAdjustment = (s.hijriAdjustment + delta).coerceIn(-3, 3))
        }
    }

    private fun persist(transform: (com.shs.calendar.data.entity.SettingsEntity) -> com.shs.calendar.data.entity.SettingsEntity) {
        lifecycleScope.launch {
            runCatching { repo.update(transform) }
        }
    }

    private fun simpleSelection(onSelected: (Int) -> Unit) =
        object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                if (!updatingUi) onSelected(position)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }

    private fun dayNames(): List<String> {
        val names = DayOfWeek.values().map {
            it.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
        }
        return names.drop(6) + names.take(6) // Sunday-first display order
    }
}
