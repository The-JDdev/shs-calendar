package com.shs.calendar.ui.traditional

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.shs.calendar.ui.SHSBaseActivity
import com.shs.calendar.R
import com.shs.calendar.calendar.BengaliEngine
import com.shs.calendar.calendar.BengaliNumerals
import com.shs.calendar.calendar.GregorianEngine
import com.shs.calendar.calendar.HijriEngine
import com.shs.calendar.calendar.HolidayProvider
import com.shs.calendar.calendar.TraditionalHeader
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * M4: the Traditional Bangla printed-calendar view.
 *
 * Renders a 7x6 grid in the classic printed style: deep-green frame, white
 * cells, large green Bengali numerals, blue Gregorian/Hijri numbers, yellow
 * (Bengali) and cyan (Hijri) sub-strips, and red holiday marks.
 *
 * All date values are computed live from the shared Gregorian/Bengali/Hijri
 * engines, so this screen agrees with the modern month view by construction.
 *
 * Holiday cells are driven entirely by [HolidayProvider]; the M6 database
 * will supply the data without any change here.
 */
class TraditionalCalendarActivity : SHSBaseActivity() {

    private lateinit var grid: LinearLayout
    private lateinit var headerView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var weekdayRow: LinearLayout

    private var visibleMonth: YearMonth = YearMonth.now()
    private val holidays: HolidayProvider = HolidayProvider.Empty

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_traditional)

        findViewById<View>(R.id.trad_toolbar).setOnClickListener { finish() }
        headerView = findViewById(R.id.trad_header)
        subtitleView = findViewById(R.id.trad_subtitle)
        weekdayRow = findViewById(R.id.trad_weekday_row)
        grid = findViewById(R.id.trad_grid)

        buildWeekdayStrip()
        render()

        buildWeekdayStrip()
        render()
    }

    /** Builds the Sunday-first Bengali weekday header strip. */
    private fun buildWeekdayStrip() {
        weekdayRow.removeAllViews()
        for (label in TraditionalHeader.weekdayHeaders()) {
            val tv = TextView(this)
            tv.text = label
            tv.setTextColor(getColor(R.color.shs_bengali_green))
            tv.textSize = 10f
            tv.gravity = android.view.Gravity.CENTER
            tv.setBackgroundColor(getColor(R.color.shs_surface_high))
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            weekdayRow.addView(tv, lp)
        }
    }

    /** Renders the live header plus the 7x6 grid for the visible month. */
    private fun render() {
        val today = LocalDate.now()
        val lines = TraditionalHeader.lines(today)
        headerView.text = lines.joinToString("\n")
        val bn = lines.getOrNull(3).orEmpty()
        val hj = lines.getOrNull(4).orEmpty()
        subtitleView.text = if (bn.isEmpty() || hj.isEmpty()) "" else "$bn  |  $hj"

        val cells = buildCells()
        val holidayDates = holidays.holidaysInMonth(visibleMonth)
        grid.removeAllViews()
        val inflater = layoutInflater
        for (row in 0 until 6) {
            val rowView = LinearLayout(this)
            rowView.orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            for (col in 0 until 7) {
                val date = cells.getOrNull(row * 7 + col)
                val cell = inflater.inflate(R.layout.traditional_cell, rowView, false)
                bindCell(cell, date, date in holidayDates)
                rowView.addView(cell)
            }
            grid.addView(rowView, lp)
        }
    }

    /**
     * 42 day cells for the visible month, Sunday-first with leading blanks,
     * matching the Bengali weekday strip built by [buildWeekdayStrip].
     */
    private fun buildCells(): List<LocalDate?> {
        val first = visibleMonth.atDay(1)
        val lead = (first.dayOfWeek.value - DayOfWeek.SUNDAY.value + 7) % 7
        val length = visibleMonth.lengthOfMonth()
        return (0 until 42).map { i ->
            val day = i - lead + 1
            if (day in 1..length) visibleMonth.atDay(day) else null
        }
    }

    /**
     * Fills one printed cell. A null date renders an empty white cell, which
     * is what the leading/trailing blank positions of the grid need.
     */
    private fun bindCell(cell: View, date: LocalDate?, isHoliday: Boolean) {
        val bengaliDay = cell.findViewById<TextView>(R.id.trad_cell_bengali_day)
        val gregorian = cell.findViewById<TextView>(R.id.trad_cell_gregorian)
        val bengaliStrip = cell.findViewById<TextView>(R.id.trad_cell_bengali_strip)
        val hijriStrip = cell.findViewById<TextView>(R.id.trad_cell_hijri_strip)
        val holiday = cell.findViewById<TextView>(R.id.trad_cell_holiday)

        if (date == null) {
            bengaliDay.text = ""
            gregorian.text = ""
            bengaliStrip.visibility = View.GONE
            hijriStrip.visibility = View.GONE
            holiday.visibility = View.GONE
            return
        }

        val b = BengaliEngine.fromGregorian(date)
        val h = HijriEngine.fromGregorian(date)

        // Large green Bengali day numeral.
        bengaliDay.text = BengaliNumerals.number(b.day)

        // Blue Gregorian day with the Bangla Bengali date beneath it.
        gregorian.text = "${date.dayOfMonth} ${b.monthName} ${BengaliNumerals.number(b.year)}"

        // Yellow sub-strip: short Bengali month name.
        bengaliStrip.text = b.monthName
        bengaliStrip.visibility = View.VISIBLE

        // Cyan sub-strip: Hijri day and month.
        hijriStrip.text = "${BengaliNumerals.number(h.day)} ${h.monthName}"
        hijriStrip.visibility = View.VISIBLE

        // Red holiday mark; labelFor may legitimately be null, so hide it.
        val mark = if (isHoliday) holidays.labelFor(date) else null
        holiday.text = mark.orEmpty()
        holiday.visibility = if (mark.isNullOrEmpty()) View.GONE else View.VISIBLE
    }
}
