package com.shs.calendar.ui.event

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.shs.calendar.ui.SHSBaseActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
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

        // The M10 layout uses a RecyclerView for the agenda and a FAB for the
        // add action. The previous ListView/ArrayAdapter + MaterialButton code
        // threw ClassCastException the moment this screen opened from the
        // dashboard, so the whole wiring is matched to the layout now.
        val list = findViewById<RecyclerView>(R.id.agenda_list)
        val empty = findViewById<TextView>(R.id.agenda_empty)
        list.layoutManager = LinearLayoutManager(this)
        val adapter = AgendaAdapter()
        list.adapter = adapter

        findViewById<FloatingActionButton>(R.id.agenda_add).setOnClickListener { openEditor(0L) }

        lifecycleScope.launch {
            repo.observeAll().collectLatest { events ->
                rows = events.sortedBy { it.startUtcMillis }
                adapter.submit(rows.map { formatRow(it) })
                empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
                list.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    /** Holder for [AgendaAdapter]; top-level nested so AgendaAdapter can stay inner. */
    private class VH(v: View) : RecyclerView.ViewHolder(v)

    /** Minimal string-row adapter over the agenda; rows open the editor. */
    private inner class AgendaAdapter : RecyclerView.Adapter<VH>() {

        private val items = mutableListOf<String>()

        fun submit(newItems: List<String>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            (holder.itemView as TextView).text = items[position]
            holder.itemView.setOnClickListener {
                rows.getOrNull(position)?.let { openEditor(it.id) }
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
