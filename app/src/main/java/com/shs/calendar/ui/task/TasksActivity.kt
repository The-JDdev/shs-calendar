package com.shs.calendar.ui.task

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.shs.calendar.ui.SHSBaseActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.shs.calendar.R
import com.shs.calendar.data.CalendarDatabase
import com.shs.calendar.data.entity.EventEntity
import com.shs.calendar.data.entity.TaskEntity
import com.shs.calendar.data.repository.EventRepository
import com.shs.calendar.data.repository.TaskRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Tasks screen for the VTODO model. Shows open or completed work from
 * [TaskRepository.observeAll], toggles completion, and converts a task into a
 * real calendar event (sharing the task's due moment) when the user asks.
 */
class TasksActivity : SHSBaseActivity() {

    private val taskRepo: TaskRepository by lazy { TaskRepository(CalendarDatabase.get(this)) }
    private val eventRepo: EventRepository by lazy { EventRepository(CalendarDatabase.get(this)) }
    private lateinit var adapter: TasksAdapter
    private var showCompleted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasks)

        findViewById<MaterialToolbar>(R.id.tasks_toolbar).apply {
            setNavigationIcon(R.drawable.ic_back)
            setNavigationOnClickListener { finish() }
        }

        adapter = TasksAdapter(
            onToggleDone = { task -> toggleDone(task) },
            onOpen = { task -> confirmConvert(task) }
        )

        findViewById<RecyclerView>(R.id.tasks_list).apply {
            layoutManager = LinearLayoutManager(this@TasksActivity)
            adapter = this@TasksActivity.adapter
        }

        val empty = findViewById<android.widget.TextView>(R.id.tasks_empty)
        findViewById<FloatingActionButton>(R.id.tasks_add).setOnClickListener { createTask() }
        findViewById<MaterialButton>(R.id.tasks_filter_open).setOnClickListener {
            showCompleted = false
            refreshFilterLabels()
        }
        findViewById<MaterialButton>(R.id.tasks_filter_done).setOnClickListener {
            showCompleted = true
            refreshFilterLabels()
        }
        findViewById<MaterialButton>(R.id.tasks_convert).setOnClickListener {
            convertPicked()
        }

        observeTasks(empty)
    }

    private fun observeTasks(empty: android.widget.TextView) {
        lifecycleScope.launch {
            taskRepo.observeAll().collectLatest { all ->
                val visible = all.filter { it.completed == showCompleted }
                adapter.submit(visible)
                empty.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
                findViewById<RecyclerView>(R.id.tasks_list).visibility =
                    if (visible.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun refreshFilterLabels() {
        val open = findViewById<MaterialButton>(R.id.tasks_filter_open)
        val done = findViewById<MaterialButton>(R.id.tasks_filter_done)
        open.alpha = if (showCompleted) 0.5f else 1f
        done.alpha = if (showCompleted) 1f else 0.5f
    }

    private fun toggleDone(task: TaskEntity) {
        val now = System.currentTimeMillis()
        lifecycleScope.launch {
            taskRepo.save(
                task.copy(
                    completed = !task.completed,
                    completedUtcMillis = if (!task.completed) now else 0L,
                    updatedAtUtcMillis = now
                )
            )
        }
    }

    /** Prompts for a title, then saves a real task (never a placeholder row). */
    private fun createTask() {
        val input = android.widget.EditText(this).apply {            hint = getString(R.string.task_new)
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.task_new)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.event_save) { _, _ ->
                val title = input.text.toString().trim()
                if (title.isEmpty()) return@setPositiveButton
                val now = System.currentTimeMillis()
                lifecycleScope.launch {
                    taskRepo.save(
                        TaskEntity(
                            title = title,
                            createdAtUtcMillis = now,
                            updatedAtUtcMillis = now
                        )
                    )
                }
            }
            .show()
    }

    /** Converts the first open task, or asks when several are open. */
    private fun convertPicked() {
        val list = findViewById<RecyclerView>(R.id.tasks_list)
        // findFirstVisibleItemPosition() lives on LinearLayoutManager, not the
        // base LayoutManager type, so narrow it before calling.
        val pos = (list.layoutManager as? LinearLayoutManager)
            ?.findFirstVisibleItemPosition() ?: -1
        val picked = adapter.itemAt(pos)
        if (picked == null) {
            Toast.makeText(this, R.string.tasks_empty, Toast.LENGTH_SHORT).show()
        } else {
            confirmConvert(picked)
        }
    }

    private fun confirmConvert(task: TaskEntity) {
        AlertDialog.Builder(this)
            .setTitle(R.string.task_convert)
            .setMessage(task.title)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.task_convert) { _, _ -> convert(task) }
            .show()
    }

    /**
     * Copies the task into an event at its due moment (1h long by default) and
     * marks the task complete so the two never drift apart.
     */
    private fun convert(task: TaskEntity) {
        val now = System.currentTimeMillis()
        val start = if (task.dueUtcMillis > 0) task.dueUtcMillis else now
        val end = if (task.allDay) start + 86_400_000L else start + 3_600_000L
        lifecycleScope.launch {
            eventRepo.save(
                EventEntity(
                    title = task.title,
                    description = task.description,
                    startUtcMillis = start,
                    endUtcMillis = end,
                    allDay = task.allDay,
                    category = task.category,
                    participants = "",
                    reminders = task.reminders,
                    rrule = task.rrule,
                    createdAtUtcMillis = now,
                    updatedAtUtcMillis = now
                )
            )
            taskRepo.save(
                task.copy(
                    completed = true,
                    completedUtcMillis = now,
                    updatedAtUtcMillis = now
                )
            )
            Toast.makeText(this@TasksActivity, R.string.task_converted, Toast.LENGTH_SHORT)
                .show()
        }
    }
}
