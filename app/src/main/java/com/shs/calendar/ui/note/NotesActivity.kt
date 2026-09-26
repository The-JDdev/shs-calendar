package com.shs.calendar.ui.note

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.shs.calendar.ui.SHSBaseActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.shs.calendar.R
import com.shs.calendar.data.CalendarDatabase
import com.shs.calendar.data.entity.NoteEntity
import com.shs.calendar.data.repository.NoteRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Notes / journal screen (VJOURNAL). Lists notes newest-updated-first with
 * pinned notes on top, and lets the user create, edit, pin and delete them.
 *
 * This is deliberately a plain-text editor rather than a rich-text canvas:
 * the data model stores body as a String, so styled spans (Phase 3) will layer
 * on top of this without a schema change.
 */
class NotesActivity : SHSBaseActivity() {

    private val noteRepo: NoteRepository by lazy { NoteRepository(CalendarDatabase.get(this)) }
    private lateinit var adapter: NotesAdapter
    private var query: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notes)

        findViewById<MaterialToolbar>(R.id.notes_toolbar).apply {
            setNavigationIcon(R.drawable.ic_back)
            setNavigationOnClickListener { finish() }
        }

        adapter = NotesAdapter(onOpen = { note -> showEditor(note) })

        findViewById<RecyclerView>(R.id.notes_list).apply {
            layoutManager = LinearLayoutManager(this@NotesActivity)
            adapter = this@NotesActivity.adapter
        }

        findViewById<FloatingActionButton>(R.id.notes_add).setOnClickListener { showEditor(null) }

        findViewById<EditText>(R.id.notes_search).addTextChangedListener(
            afterTextChanged = { s ->
                query = s?.toString().orEmpty()
                observe()
            }
        )

        observe()
    }

    /**
     * Re-observes the repository. When [query] is blank this is the full
     * pinned-first list; otherwise it is the DAO's substring match, which also
     * covers tags.
     */
    private fun observe() {
        val flow = if (query.isBlank()) noteRepo.observeAll() else noteRepo.search(query.trim())
        lifecycleScope.launch {
            flow.collectLatest { notes ->
                adapter.submit(notes)
                findViewById<TextView>(R.id.notes_empty).visibility =
                    if (notes.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    /** Create when [existing] is null, otherwise edit in place. */
    private fun showEditor(existing: NoteEntity?) {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextInputEditText(this).apply { hint = getString(R.string.note_title_hint) }
        val body = TextInputEditText(this).apply {
            hint = getString(R.string.note_body_hint)
            minLines = 4
        }
        val tags = TextInputEditText(this).apply { hint = getString(R.string.note_tags_hint) }
        listOf(title, body, tags).forEach { field ->
            container.addView(field, LinearLayout.LayoutParams(-1, -2).apply { setMargins(pad, pad, pad, 0) })
        }

        existing?.let {
            title.setText(it.title)
            body.setText(it.body)
            tags.setText(it.tags)
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.note_new else R.string.note_edit)
            .setView(container)

        builder.setPositiveButton(R.string.note_save) { _, _ ->
            val now = System.currentTimeMillis()
            val note = (existing ?: NoteEntity()).copy(
                title = title.text.toString().trim(),
                body = body.text.toString().trim(),
                tags = tags.text.toString(),
                createdAtUtcMillis = existing?.createdAtUtcMillis ?: now,
                updatedAtUtcMillis = now
            )
            lifecycleScope.launch {
                // save() dispatches on id: insert when 0, update otherwise.
                noteRepo.save(note)
            }
        }

        builder.setNeutralButton(R.string.note_pin) { _, _ ->
            // Pinning is a one-tap action, so it must not require editing the body.
            val src = existing ?: return@setNeutralButton
            lifecycleScope.launch { noteRepo.save(src.copy(pinned = !src.pinned, updatedAtUtcMillis = System.currentTimeMillis())) }
        }

        if (existing != null) {
            builder.setNegativeButton(R.string.note_delete) { _, _ -> confirmDelete(existing) }
        }
        builder.setCancelable(true)
        builder.show()
    }

    private fun confirmDelete(note: NoteEntity) {
        AlertDialog.Builder(this)
            .setTitle(R.string.note_delete)
            .setMessage(note.title.ifBlank { getString(R.string.note_untitled) })
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.note_delete) { _, _ ->
                lifecycleScope.launch {
                    noteRepo.delete(note)
                    Toast.makeText(this@NotesActivity, R.string.note_deleted, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
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
