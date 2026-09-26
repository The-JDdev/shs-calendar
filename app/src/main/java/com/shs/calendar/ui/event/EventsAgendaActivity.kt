package com.shs.calendar.ui.event

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import com.shs.calendar.ui.SHSBaseActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.shs.calendar.R
import com.shs.calendar.data.CalendarDatabase
import com.shs.calendar.data.entity.EventEntity
import com.shs.calendar.data.repository.EventRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Upcoming-events agenda: observes [EventRepository.observeAll], shows a
 * chronological list (empty-state until the first event), and opens the
 * editor on tap or via the "new" action.
 */
class EventsAgendaActivity : SHSBaseActivity() {

    private val repo: EventRepository by lazy { EventRepository(CalendarDatabase.get(this)) }
    private var rows: List<EventEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_events_agenda)

        findViewById<MaterialToolbar>(R.id.agenda_toolbar).apply {
            setNavigationIcon(R.drawable.ic_back)
            setNavigationOnClickListener { finish() }
        }

        val list = findViewById<ListView>(R.id.agenda_list)
        val empty = findViewById<TextView>(R.id.agenda_empty)
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
        list.adapter = adapter

        list.setOnItemClickListener { _, _, position, _ ->
            rows.getOrNull(position)?.let { openEditor(it.id) }
        }
        findViewById<MaterialButton>(R.id.agenda_add).setOnClickListener { openEditor(0L) }

        lifecycleScope.launch {
            repo.observeAll().collectLatest { events ->
                rows = events.sortedBy { it.startUtcMillis }
                adapter.clear()
                adapter.addAll(rows.map { formatRow(it) })
                adapter.notifyDataSetChanged()
                empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
                list.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun openEditor(id: Long) {
        startActivity(
            Intent(this, EventEditorActivity::class.java)
                .putExtra(EventEditorActivity.EXTRA_EVENT_ID, id)
        )
    }

    private fun formatRow(e: EventEntity): String {
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(e.startUtcMillis).atZone(zone)
        val stamp = start.format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))
        return if (e.allDay) "${start.toLocalDate()} • ${e.title}" else "$stamp • ${e.title}"
    }
}
