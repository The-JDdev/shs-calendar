package com.shs.calendar.ui.search

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.shs.calendar.R
import com.shs.calendar.data.CalendarDatabase
import com.shs.calendar.data.repository.EventRepository
import com.shs.calendar.data.repository.NoteRepository
import com.shs.calendar.data.repository.TaskRepository
import com.shs.calendar.dua.DuaCollection
import com.shs.calendar.holidays.HolidayDatabase
import com.shs.calendar.search.SearchEngine
import com.shs.calendar.search.SearchEngine.SearchResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.Year

/**
 * Unified search across events, tasks, notes, holidays and duas.
 *
 * [SearchEngine] owns ranking, date-query detection and the result cap, so
 * this activity's only real job is assembling the five candidate lists. It
 * reads one snapshot of each table with `first()` rather than observing,
 * because a search is a one-shot question — re-querying the database on every
 * keystroke would be wasteful and would make results flicker.
 */
class SearchActivity : AppCompatActivity() {

    private val eventRepo: EventRepository by lazy { EventRepository(CalendarDatabase.get(this)) }
    private val taskRepo: TaskRepository by lazy { TaskRepository(CalendarDatabase.get(this)) }
    private val noteRepo: NoteRepository by lazy { NoteRepository(CalendarDatabase.get(this)) }

    private lateinit var adapter: SearchResultsAdapter
    private val zone: ZoneId by lazy { ZoneId.systemDefault() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        findViewById<MaterialToolbar>(R.id.search_toolbar).apply {
            setNavigationIcon(R.drawable.ic_back)
            setNavigationOnClickListener { finish() }
        }

        adapter = SearchResultsAdapter(onOpen = { result -> openResult(result) })

        findViewById<RecyclerView>(R.id.search_list).apply {
            layoutManager = LinearLayoutManager(this@SearchActivity)
            adapter = this@SearchActivity.adapter
        }

        findViewById<EditText>(R.id.search_input).addTextChangedListener(
            afterTextChanged = { text -> runSearch(text.orEmpty()) }
        )
    }

    private fun runSearch(raw: String) {
        val query = raw.trim()
        if (query.isEmpty()) {
            adapter.submit(emptyList())
            findViewById<TextView>(R.id.search_empty).visibility = View.GONE
            return
        }

        lifecycleScope.launch {
            // Static sources are cheap and synchronous, so only the database
            // snapshots need coroutines.
            val events = eventRepo.observeAll().first()
            val tasks = taskRepo.observeAll().first()
            val notes = noteRepo.observeAll().first()
            val year = Year.now(zone).value

            val results = SearchEngine.search(
                query = query,
                events = events.map {
                    SearchResult(SearchResult.Kind.EVENT, it.id, it.title, it.description, 0, it.startUtcMillis)
                },
                tasks = tasks.map {
                    SearchResult(SearchResult.Kind.TASK, it.id, it.title, it.description, 0, it.dueUtcMillis)
                },
                notes = notes.map {
                    SearchResult(SearchResult.Kind.NOTE, it.id, it.title, it.body, 0, it.updatedAtUtcMillis)
                },
                holidays = HolidayDatabase().forYear(year).map {
                    SearchResult(SearchResult.Kind.HOLIDAY, it.date.toEpochDay(), it.localizedName(false), it.category.name, 0, 0L)
                },
                duas = DuaCollection.all.mapIndexed { index, d ->
                    SearchResult(SearchResult.Kind.DUA, index.toLong(), d.title, d.meaningEnglish, 0, 0L)
                },
                zone = zone
            )

            adapter.submit(results)
            findViewById<TextView>(R.id.search_empty).visibility =
                if (results.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /**
     * Search is a navigation surface, not an editor: opening a hit navigates to
     * the owning screen, leaving this activity behind.
     *
     * Events route to the agenda list rather than [EventEditorActivity]:
     * no screen in this project accepts a pre-selected event id yet, and
     * launching the editor with an unread extra would show a blank event.
     */
    private fun openResult(result: SearchResult) {
        val target = when (result.kind) {
            SearchResult.Kind.EVENT -> "com.shs.calendar.ui.event.EventsAgendaActivity"
            SearchResult.Kind.TASK -> "com.shs.calendar.ui.task.TasksActivity"
            SearchResult.Kind.NOTE -> "com.shs.calendar.ui.note.NotesActivity"
            SearchResult.Kind.DATE,
            SearchResult.Kind.HOLIDAY,
            SearchResult.Kind.DUA -> "com.shs.calendar.MainActivity"
        }
        startActivity(android.content.Intent().apply {
            setClassName(this@SearchActivity, target)
            putExtra("result_kind", result.kind.name)
            putExtra("result_id", result.id)
        })
    }
}

/** Minimal TextWatcher that only cares about the post-edit text. */
private fun EditText.addTextChangedListener(afterTextChanged: (String?) -> Unit) {
    addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        override fun afterTextChanged(s: android.text.Editable?) = afterTextChanged(s?.toString())
    })
}
