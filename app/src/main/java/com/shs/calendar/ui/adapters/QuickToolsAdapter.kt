package com.shs.calendar.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.shs.calendar.R

/**
 * Horizontal QUICK TOOLS row on the dashboard.
 *
 * Phase 1: Age Calculator and Date Converter are fully functional; Tasbih,
 * Dua and Widgets open designed placeholder screens marked TODO-Phase2.
 */
class QuickToolsAdapter(
    private val tools: List<Tool>,
    private val onClick: (Tool) -> Unit
) : RecyclerView.Adapter<QuickToolsAdapter.Holder>() {

    data class Tool(
        val id: String,
        val glyph: String,
        val labelRes: Int,
        val isPlaceholder: Boolean
    )

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val glyph: TextView = view.findViewById(R.id.quick_tool_glyph)
        val label: TextView = view.findViewById(R.id.quick_tool_label)
        val root: View = view.findViewById(R.id.quick_tool_root)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_quick_tool, parent, false))

    override fun getItemCount(): Int = tools.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val tool = tools[position]
        holder.glyph.text = tool.glyph
        holder.label.setText(tool.labelRes)
        holder.root.setOnClickListener { onClick(tool) }
        holder.root.contentDescription =
            holder.root.context.getString(tool.labelRes)
    }
}
