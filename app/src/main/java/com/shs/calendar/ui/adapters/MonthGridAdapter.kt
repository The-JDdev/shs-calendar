package com.shs.calendar.ui.adapters

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.shs.calendar.R
import com.shs.calendar.calendar.BengaliEngine
import com.shs.calendar.calendar.BengaliNumerals
import com.shs.calendar.calendar.HijriEngine
import java.time.LocalDate

/**
 * Dashboard month grid adapter.
 *
 * Each cell renders the **Gregorian day dominant**, with a small Bengali day
 * and a small Hijri day beneath it (SPEC dashboard calendar card). Today is
 * highlighted with [R.drawable.bg_cell_selected]; trailing/leading days from
 * adjacent months are dimmed.
 *
 * @param cells result of [com.shs.calendar.calendar.GregorianEngine.monthGrid]
 * @param hijriAdjustment settings-driven moon-sighting offset (−3..+3)
 * @param bengaliNumerals render digits in Bengali numerals when true
 */
class MonthGridAdapter(
    context: Context,
    private var cells: List<GridCell>,
    private var hijriAdjustment: Int = 0,
    private var bengaliNumerals: Boolean = false
) : BaseAdapter() {

    /** View-model decoupled from engine types so the adapter stays testable. */
    data class GridCell(
        val date: LocalDate,
        val inMonth: Boolean,
        val isToday: Boolean
    )

    private val inflater = LayoutInflater.from(context)

    fun submit(
        newCells: List<GridCell>,
        adjustment: Int = hijriAdjustment,
        numerals: Boolean = bengaliNumerals
    ) {
        cells = newCells
        hijriAdjustment = adjustment
        bengaliNumerals = numerals
        notifyDataSetChanged()
    }

    override fun getCount(): Int = cells.size
    override fun getItem(position: Int): GridCell = cells[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_cal_cell, parent, false)
        val cell = cells[position]

        val dayView = view.findViewById<TextView>(R.id.cell_day)
        val bengaliView = view.findViewById<TextView>(R.id.cell_bengali)
        val hijriView = view.findViewById<TextView>(R.id.cell_hijri)

        // --- Gregorian day (dominant) ---
        val westernDay = cell.date.dayOfMonth.toString()
        dayView.text = if (bengaliNumerals) BengaliNumerals.toBengali(westernDay) else westernDay

        // --- Bengali day (sub-line) ---
        val bengali = BengaliEngine.fromGregorian(cell.date)
        val bengaliDay = bengali.day.toString()
        bengaliView.text = if (bengaliNumerals) bengaliDay else BengaliNumerals.toBengali(bengaliDay)

        // --- Hijri day (sub-line, western digits for legibility next to BN) ---
        val hijri = HijriEngine.fromGregorian(cell.date, hijriAdjustment)
        hijriView.text = hijri.day.toString()

        // --- Highlight / dimming ---
        if (cell.isToday) {
            view.setBackgroundResource(R.drawable.bg_cell_selected)
            dayView.setTextColor(dayView.context.getColor(R.color.shs_text_on_accent))
            dayView.typeface = Typeface.DEFAULT_BOLD
            bengaliView.setTextColor(dayView.context.getColor(R.color.shs_text_on_accent))
            hijriView.setTextColor(dayView.context.getColor(R.color.shs_text_on_accent))
        } else {
            view.setBackgroundResource(android.R.color.transparent)
            if (cell.inMonth) {
                dayView.setTextColor(dayView.context.getColor(R.color.shs_text_primary))
                dayView.typeface = Typeface.DEFAULT
                bengaliView.setTextColor(dayView.context.getColor(R.color.shs_bengali_green))
                hijriView.setTextColor(dayView.context.getColor(R.color.shs_hijri_blue))
            } else {
                dayView.setTextColor(dayView.context.getColor(R.color.shs_text_muted))
                dayView.typeface = Typeface.DEFAULT
                bengaliView.setTextColor(dayView.context.getColor(R.color.shs_text_muted))
                hijriView.setTextColor(dayView.context.getColor(R.color.shs_text_muted))
            }
        }
        return view
    }
}
