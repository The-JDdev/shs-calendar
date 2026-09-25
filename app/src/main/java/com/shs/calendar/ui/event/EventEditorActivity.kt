package com.shs.calendar.ui.event

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.shs.calendar.R
import com.shs.calendar.data.CalendarDatabase
import com.shs.calendar.data.entity.EventEntity
import com.shs.calendar.data.repository.EventRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * Event editor: create or edit one calendar event (title, description,
 * location, start/end, all-day). Start defaults to "now", end to start +1h.
 * Saving persists through [EventRepository]; editing an existing id loads it.
 */
class EventEditorActivity : AppCompatActivity() {

    private val repo: EventRepository by lazy { EventRepository(CalendarDatabase.get(this)) }

    private var eventId: Long = 0L
    private var start: LocalDateTime = LocalDateTime.now().withSecond(0).withNano(0)
    private var end: LocalDateTime = start.plusHours(1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_editor)

        eventId = intent.getLongExtra(EXTRA_EVENT_ID, 0L)

        findViewById<MaterialToolbar>(R.id.event_toolbar).apply {
            setNavigationIcon(R.drawable.ic_back)
            setNavigationOnClickListener { finish() }
            title = getString(if (eventId == 0L) R.string.event_new else R.string.event_editor_title)
        }

        val titleField = findViewById<EditText>(R.id.event_title)
        val descriptionField = findViewById<EditText>(R.id.event_description)
        val locationField = findViewById<EditText>(R.id.event_location)
        val startValue = findViewById<TextView>(R.id.event_start_value)
        val endValue = findViewById<TextView>(R.id.event_end_value)
        val allDay = findViewById<SwitchMaterial>(R.id.event_all_day_switch)
        val error = findViewById<TextView>(R.id.event_error)
        val deleteButton = findViewById<MaterialButton>(R.id.event_delete)

        fun renderTimes() {
            startValue.text = formatDateTime(start)
            endValue.text = formatDateTime(end)
        }

        startValue.setOnClickListener {
            pickDateTime(start) { picked ->
                val shift = java.time.Duration.between(start, picked)
                start = picked
                end = end.plus(shift)
                renderTimes()
            }
        }
        endValue.setOnClickListener {
            pickDateTime(end) { picked ->
                end = if (picked.isBefore(start)) start else picked
                renderTimes()
            }
        }

        if (eventId != 0L) {
            deleteButton.visibility = View.VISIBLE
            lifecycleScope.launch {
                val existing = repo.getById(eventId) ?: return@launch
                titleField.setText(existing.title)
                descriptionField.setText(existing.description)
                locationField.setText(existing.location)
                allDay.isChecked = existing.allDay
                start = Instant.ofEpochMilli(existing.startUtcMillis)
                    .atZone(ZoneId.systemDefault()).toLocalDateTime()
                end = Instant.ofEpochMilli(existing.endUtcMillis)
                    .atZone(ZoneId.systemDefault()).toLocalDateTime()
                renderTimes()
            }
        } else {
            deleteButton.visibility = View.GONE
            renderTimes()
        }

        allDay.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                start = start.with(LocalTime.MIDNIGHT)
                end = start.plusDays(1)
            }
            renderTimes()
        }

        deleteButton.setOnClickListener {
            lifecycleScope.launch {
                repo.getById(eventId)?.let { repo.delete(it) }
                toast(R.string.event_deleted)
                finish()
            }
        }

        findViewById<MaterialButton>(R.id.event_save).setOnClickListener {
            val title = titleField.text.toString().trim()
            if (title.isEmpty()) {
                error.setText(R.string.event_title_required)
                error.visibility = View.VISIBLE
                return@setOnClickListener
            }
            error.visibility = View.GONE
            lifecycleScope.launch {
                val zone = ZoneId.systemDefault()
                repo.save(
                    EventEntity(
                        id = eventId,
                        title = title,
                        description = descriptionField.text.toString().trim(),
                        location = locationField.text.toString().trim(),
                        startUtcMillis = start.atZone(zone).toInstant().toEpochMilli(),
                        endUtcMillis = end.atZone(zone).toInstant().toEpochMilli(),
                        allDay = allDay.isChecked,
                        createdAtUtcMillis = System.currentTimeMillis(),
                        updatedAtUtcMillis = System.currentTimeMillis()
                    )
                )
                toast(R.string.event_saved)
                finish()
            }
        }
    }

    private fun pickDateTime(current: LocalDateTime, onPicked: (LocalDateTime) -> Unit) {
        val dateMs = current.toLocalDate()
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setSelection(dateMs)
            .build()
        datePicker.addOnPositiveButtonClickListener { selection ->
            val date = Instant.ofEpochMilli(selection as Long)
                .atZone(ZoneId.systemDefault()).toLocalDate()
            val timePicker = MaterialTimePicker.Builder()
                .setHour(current.hour)
                .setMinute(current.minute)
                .setTimeFormat(
                    if (hour24()) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
                )
                .build()
            timePicker.addOnPositiveButtonClickListener { _view ->
                onPicked(LocalDateTime.of(date, LocalTime.of(timePicker.hour, timePicker.minute)))
            }
            timePicker.show(supportFragmentManager, "event_time")
        }
        datePicker.show(supportFragmentManager, "event_date")
    }

    private fun hour24(): Boolean = android.text.format.DateFormat.is24HourFormat(this)

    private fun formatDateTime(dt: LocalDateTime): String {
        val time = if (allDayChecked()) "" else " " + dt.toLocalTime().toString().substring(0, 5)
        return "${dt.toLocalDate()}$time"
    }

    private fun allDayChecked(): Boolean =
        findViewById<SwitchMaterial>(R.id.event_all_day_switch)?.isChecked == true

    private fun toast(res: Int) =
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    companion object {
        /** Optional pre-filled start date (ISO-YYYY-MM-DD) from the dashboard grid. */
        const val EXTRA_DATE = "extra_date"
        const val EXTRA_EVENT_ID = "event_id"
    }
}
