package com.shs.calendar.ui.task

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.shs.calendar.R
import com.shs.calendar.data.entity.TaskEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Renders one [TaskEntity] per row: title, due/priority line, checklist
 * progress and an overdue flag. Overdue rows are tinted via
 * [R.color.shs_danger] so they read differently from open work.
 */
class TasksAdapter(
    private val onToggleDone: (TaskEntity) -> Unit,
    private val onOpen: (TaskEntity) -> Unit
) : RecyclerView.Adapter<TasksAdapter.Holder>() {

    private var rows: List<TaskEntity> = emptyList()

    fun submit(tasks: List<TaskEntity>) {
        rows = tasks
        notifyDataSetChanged()
    }

    fun itemAt(position: Int): TaskEntity? = rows.getOrNull(position)

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val done: CheckBox = view.findViewById(R.id.task_done)
        val title: TextView = view.findViewById(R.id.task_title)
        val meta: TextView = view.findViewById(R.id.task_meta)
        val checklist: TextView = view.findViewById(R.id.task_checklist)
        val overdue: TextView = view.findViewById(R.id.task_overdue)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false))

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val task = rows[position]
        val now = System.currentTimeMillis()
        val isOverdue = task.isOverdue(now)

        holder.title.text = task.title
        holder.title.alpha = if (task.completed) 0.5f else 1f

        holder.done.setOnCheckedChangeListener(null)
        holder.done.isChecked = task.completed
        holder.done.setOnCheckedChangeListener { _, _ -> onToggleDone(task) }

        holder.meta.text = metaLine(task)
        holder.meta.visibility = if (holder.meta.text.isEmpty()) View.GONE else View.VISIBLE

        val items = task.checklistItems()
        if (items.isEmpty()) {
            holder.checklist.visibility = View.GONE
        } else {
            val doneCount = items.count { it.first }
            holder.checklist.text = "$doneCount/${items.size}"
            holder.checklist.visibility = View.VISIBLE
        }

        holder.overdue.visibility = if (isOverdue) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener { onOpen(task) }
    }

    /** "Due Dec 25, 10:00 · P1 · weekly" — only the populated parts. */
    private fun metaLine(task: TaskEntity): String {
        val parts = mutableListOf<String>()
        if (task.dueUtcMillis > 0) {
            val zdt = Instant.ofEpochMilli(task.dueUtcMillis).atZone(ZoneId.systemDefault())
            val pattern = if (task.allDay) "MMM d" else "MMM d, HH:mm"
            parts += zdt.format(DateTimeFormatter.ofPattern(pattern))
        }
        if (task.priority in 1..9) parts += "P${task.priority}"
        if (!task.category.isBlank()) parts += task.category
        if (!task.rrule.isNullOrBlank()) parts += task.rrule.substringBefore(';')
        return parts.joinToString(" · ")
    }
}
