package com.shs.calendar.ui.clock

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.shs.calendar.R
import com.shs.calendar.clock.WorldClock

/**
 * M4: adapter for the world clock list.
 *
 * Holds zone ids only and asks [WorldClock] to resolve the displayed time,
 * so all time-zone maths stays in the testable model rather than in the view.
 */
class WorldClockAdapter(
    private var use24Hour: Boolean,
    /** Invoked with the zone id on a row long-press. */
    private val onLongPress: ((String) -> Unit)? = null
) : RecyclerView.Adapter<WorldClockAdapter.Holder>() {

    private val zones = mutableListOf<String>()

    /** Adds a zone at the end; ignored if already present or invalid. */
    fun addZone(zoneId: String) {
        if (WorldClock.isValid(zoneId) && zoneId !in zones) zones.add(zoneId)
        notifyDataSetChanged()
    }

    /**
     * Removes a zone, returning false if it was not in the list.
     *
     * Removing the last zone is allowed: the screen simply shows an empty
     * state and the add button stays usable.
     */
    fun removeZone(zoneId: String): Boolean {
        val removed = zones.remove(zoneId)
        if (removed) notifyDataSetChanged()
        return removed
    }

    /** Replaces the whole list, dropping invalid ids. */
    fun setZones(ids: List<String>) {
        zones.clear()
        zones.addAll(ids.filter { WorldClock.isValid(it) }.distinct())
        notifyDataSetChanged()
    }

    /**
     * Applies the user's 12/24h preference, rebinding rows if it changed.
     *
     * The setting lives in the settings table rather than the adapter, so the
     * screen pushes it in from its settings observer.
     */
    fun setUse24Hour(value: Boolean) {
        if (use24Hour == value) return
        use24Hour = value
        notifyItemRangeChanged(0, zones.size)
    }

    /**
     * Rebinds the visible rows so their times advance.
     *
     * Deliberately leaves the zone list untouched: this is called on every
     * timer tick, and rebuilding the list would discard scroll state.
     */
    fun refresh() = notifyItemRangeChanged(0, zones.size)

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val city: TextView = view.findViewById(R.id.clock_row_city)
        val date: TextView = view.findViewById(R.id.clock_row_date)
        val time: TextView = view.findViewById(R.id.clock_row_time)
        val offset: TextView = view.findViewById(R.id.clock_row_offset)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_world_clock, parent, false))

    override fun getItemCount(): Int = zones.size

    /** Zone id at [position], or null if the position is out of range. */
    fun zoneAt(position: Int): String? = zones.getOrNull(position)

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val zoneId = zones[position]
        val entry = WorldClock.entryAt(zoneId)
        holder.city.text = zoneId.substringAfterLast('/').replace('_', ' ')
        holder.time.text = WorldClock.formatTime(entry.time, use24Hour)
        holder.date.text = WorldClock.formatDate(entry.time)
        holder.offset.text = entry.offsetLabel

        // RecyclerView has no setOnItemLongClickListener; the row owns it.
        holder.itemView.setOnLongClickListener {
            onLongPress?.invoke(zoneId)
            true
        }
    }
}
