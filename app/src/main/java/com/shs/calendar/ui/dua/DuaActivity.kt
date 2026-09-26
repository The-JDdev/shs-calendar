package com.shs.calendar.ui.dua

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.shs.calendar.ui.SHSBaseActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shs.calendar.R
import com.shs.calendar.dua.Dua
import com.shs.calendar.dua.DuaCollection

/**
 * M3 Dua collection screen: browse the curated duas, filter by category,
 * and copy or share any entry. Fully offline - all content ships in-app.
 */
class DuaActivity : SHSBaseActivity() {

    private lateinit var listView: RecyclerView
    private lateinit var emptyHint: TextView
    private lateinit var adapter: DuaAdapter
    private var duas: List<Dua> = DuaCollection.all

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dua)

        findViewById<View>(R.id.dua_toolbar).setOnClickListener { finish() }
        listView = findViewById(R.id.dua_list)
        emptyHint = findViewById(R.id.dua_empty)
        listView.layoutManager = LinearLayoutManager(this)

        adapter = DuaAdapter(duas)
        listView.adapter = adapter

        val labels = listOf(getString(R.string.dua_category_all)) + DuaCollection.categories
        val spinner = findViewById<android.widget.Spinner>(R.id.dua_category_spinner)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val category = DuaCollection.categories.getOrNull(pos - 1)
                applyFilter(if (category == null) DuaCollection.all else DuaCollection.byCategory(category))
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = applyFilter(DuaCollection.all)
        }
    }

    private fun applyFilter(filtered: List<Dua>) {
        duas = filtered
        adapter.replaceAll(filtered)
        val isEmpty = filtered.isEmpty()
        emptyHint.visibility = if (isEmpty) View.VISIBLE else View.GONE
        listView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    /** Plain-text rendering used for both clipboard copy and share. */
    private fun formatDua(dua: Dua): String = buildString {
        appendLine(dua.title)
        appendLine()
        appendLine(dua.arabic)
        appendLine(dua.transliteration)
        appendLine()
        appendLine(dua.meaningBengali)
        appendLine(dua.meaningEnglish)
        appendLine()
        append("Reference: ").append(dua.reference)
    }

    private fun copyToClipboard(dua: Dua) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            Toast.makeText(this, R.string.dua_copy_failed, Toast.LENGTH_SHORT).show()
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(dua.title, formatDua(dua)))
        Toast.makeText(this, R.string.dua_copied, Toast.LENGTH_SHORT).show()
    }

    private fun shareDua(dua: Dua) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, dua.title)
            putExtra(Intent.EXTRA_TEXT, formatDua(dua))
        }
        startActivity(Intent.createChooser(intent, getString(R.string.dua_share)))
    }

    /** Renders one dua per row and wires its copy/share buttons. */
    private inner class DuaAdapter(private var items: List<Dua>) :
        RecyclerView.Adapter<DuaAdapter.Holder>() {

        private val inflater = LayoutInflater.from(this@DuaActivity)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(inflater.inflate(R.layout.dua_row, parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

        fun replaceAll(newItems: List<Dua>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            private val title: TextView = view.findViewById(R.id.dua_row_title)
            private val arabic: TextView = view.findViewById(R.id.dua_row_arabic)
            private val transliteration: TextView = view.findViewById(R.id.dua_row_transliteration)
            private val bengali: TextView = view.findViewById(R.id.dua_row_bengali)
            private val english: TextView = view.findViewById(R.id.dua_row_english)
            private val reference: TextView = view.findViewById(R.id.dua_row_reference)
            private val copy: Button = view.findViewById(R.id.dua_row_copy)
            private val share: Button = view.findViewById(R.id.dua_row_share)

            fun bind(dua: Dua) {
                title.text = dua.title
                arabic.text = dua.arabic
                transliteration.text = dua.transliteration
                bengali.text = dua.meaningBengali
                english.text = dua.meaningEnglish
                reference.text = getString(R.string.dua_reference_format, dua.reference)
                copy.setOnClickListener { copyToClipboard(dua) }
                share.setOnClickListener { shareDua(dua) }
            }
        }
    }
}
