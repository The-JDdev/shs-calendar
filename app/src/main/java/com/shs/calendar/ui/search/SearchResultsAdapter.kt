package com.shs.calendar.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.shs.calendar.R
import com.shs.calendar.search.SearchEngine.SearchResult

/**
 * Renders ranked [SearchResult] hits. [SearchEngine] has already ordered and
 * capped the list, so this adapter only formats: it maps [SearchResult.Kind] to
 * its badge string and suppresses an empty subtitle rather than leaving a gap.
 */
class SearchResultsAdapter(
    private val onOpen: (SearchResult) -> Unit
) : RecyclerView.Adapter<SearchResultsAdapter.Holder>() {

    private var rows: List<SearchResult> = emptyList()

    fun submit(results: List<SearchResult>) {
        rows = results
        notifyDataSetChanged()
    }

    fun itemAt(position: Int): SearchResult? = rows.getOrNull(position)

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val kind: TextView = view.findViewById(R.id.search_row_kind)
        val title: TextView = view.findViewById(R.id.search_row_title)
        val subtitle: TextView = view.findViewById(R.id.search_row_subtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_search, parent, false))

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val r = rows[position]
        val res = holder.itemView.resources

        holder.kind.setText(kindLabel(r.kind))
        holder.title.text = r.title

        holder.subtitle.text = r.subtitle
        holder.subtitle.visibility = if (r.subtitle.isBlank()) View.GONE else View.VISIBLE

        holder.itemView.setOnClickListener { onOpen(r) }
    }

    private fun kindLabel(kind: SearchResult.Kind): Int = when (kind) {
        SearchResult.Kind.EVENT -> R.string.search_kind_event
        SearchResult.Kind.TASK -> R.string.search_kind_task
        SearchResult.Kind.NOTE -> R.string.search_kind_note
        SearchResult.Kind.HOLIDAY -> R.string.search_kind_holiday
        SearchResult.Kind.DUA -> R.string.search_kind_dua
        SearchResult.Kind.DATE -> R.string.search_kind_date
    }
}
