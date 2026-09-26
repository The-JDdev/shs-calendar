package com.shs.calendar.ui.note

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.shs.calendar.R
import com.shs.calendar.data.entity.NoteEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Renders [NoteEntity] rows: title, body preview, tag chips and the optional
 * linked date. Pinned notes sort first (the DAO already orders them that way),
 * so the adapter preserves input order and only formats.
 */
class NotesAdapter(
    private val onOpen: (NoteEntity) -> Unit
) : RecyclerView.Adapter<NotesAdapter.Holder>() {

    private var rows: List<NoteEntity> = emptyList()

    fun submit(notes: List<NoteEntity>) {
        rows = notes
        notifyDataSetChanged()
    }

    fun itemAt(position: Int): NoteEntity? = rows.getOrNull(position)

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.note_title)
        val body: TextView = view.findViewById(R.id.note_body)
        val tags: TextView = view.findViewById(R.id.note_tags)
        val linked: TextView = view.findViewById(R.id.note_linked)
        val pin: TextView = view.findViewById(R.id.note_pin)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false))

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val note = rows[position]

        holder.title.text = note.title.ifBlank { holder.itemView.context.getString(R.string.note_untitled) }
        holder.title.alpha = if (note.pinned) 1f else 0.85f

        holder.body.text = note.body
        holder.body.visibility = if (note.body.isBlank()) View.GONE else View.VISIBLE

        val tags = note.tagList()
        holder.tags.text = tags.joinToString("  ") { "#$it" }
        holder.tags.visibility = if (tags.isEmpty()) View.GONE else View.VISIBLE

        holder.linked.visibility = if (note.linkedDateUtcMillis > 0) View.VISIBLE else View.GONE
        if (note.linkedDateUtcMillis > 0) {
            val zdt = Instant.ofEpochMilli(note.linkedDateUtcMillis).atZone(ZoneId.systemDefault())
            holder.linked.text = zdt.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        }

        holder.pin.visibility = if (note.pinned) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener { onOpen(note) }
    }
}
