package com.shs.calendar.widget

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.shs.calendar.R
import com.shs.calendar.ui.traditional.TraditionalCalendarActivity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * (1) Monthly calendar widget.
 *
 * RemoteViews cannot host a RecyclerView and cannot set LayoutParams on an
 * added child, so the grid is assembled from six pre-baked week-row layouts.
 * Each row owns a distinct set of cell ids (r0_c0 .. r5_c6) because reusing
 * one id across rows would overwrite every copy at once.
 */
class CalendarWidget : SHSWidgetProvider() {

    override val layoutRes = R.layout.widget_calendar

    /** Weekday header ids, Monday-first, mirroring the shell layout. */
    private val headerIds = intArrayOf(
        R.id.widget_wd0, R.id.widget_wd1, R.id.widget_wd2, R.id.widget_wd3,
        R.id.widget_wd4, R.id.widget_wd5, R.id.widget_wd6
    )

    /** Row layout for each of the six week rows. */
    private val rowLayouts = intArrayOf(
        R.layout.widget_calendar_row0, R.layout.widget_calendar_row1,
        R.layout.widget_calendar_row2, R.layout.widget_calendar_row3,
        R.layout.widget_calendar_row4, R.layout.widget_calendar_row5
    )

    /** Cell view ids, indexed [row][column]; mirrors the row layouts above. */
    private val cellIds = arrayOf(
        intArrayOf(R.id.r0_c0, R.id.r0_c1, R.id.r0_c2, R.id.r0_c3, R.id.r0_c4, R.id.r0_c5, R.id.r0_c6),
        intArrayOf(R.id.r1_c0, R.id.r1_c1, R.id.r1_c2, R.id.r1_c3, R.id.r1_c4, R.id.r1_c5, R.id.r1_c6),
        intArrayOf(R.id.r2_c0, R.id.r2_c1, R.id.r2_c2, R.id.r2_c3, R.id.r2_c4, R.id.r2_c5, R.id.r2_c6),
        intArrayOf(R.id.r3_c0, R.id.r3_c1, R.id.r3_c2, R.id.r3_c3, R.id.r3_c4, R.id.r3_c5, R.id.r3_c6),
        intArrayOf(R.id.r4_c0, R.id.r4_c1, R.id.r4_c2, R.id.r4_c3, R.id.r4_c4, R.id.r4_c5, R.id.r4_c6),
        intArrayOf(R.id.r5_c0, R.id.r5_c1, R.id.r5_c2, R.id.r5_c3, R.id.r5_c4, R.id.r5_c5, R.id.r5_c6)
    )

    override fun render(context: Context, views: RemoteViews, id: Int) {
        val today = SHSWidgetProvider.today()
        val month = YearMonth.from(today)
        val holidays = WidgetCore.holidayDates(context, today)

        views.setTextViewText(
            R.id.widget_title,
            month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.year
        )
        for (d in DayOfWeek.entries) {
            views.setTextViewText(
                headerIds[d.value - 1],
                d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(2)
            )
        }
        views.removeAllViews(R.id.widget_rows)

        // Monday-first, matching the app's default firstDayOfWeek = 1.
        var cursor = month.atDay(1).minusDays((month.atDay(1).dayOfWeek.value - 1).toLong())
        for (row in 0..5) {
            val week = RemoteViews(context.packageName, rowLayouts[row])
            for (column in 0..6) {
                val inMonth = YearMonth.from(cursor) == month
                val cellId = cellIds[row][column]
                week.setTextViewText(cellId, if (inMonth) cursor.dayOfMonth.toString() else "")
                week.setInt(cellId, "setTextColor", cellColor(context, cursor, inMonth, cursor == today, cursor in holidays))
                week.setViewVisibility(cellId, if (inMonth) View.VISIBLE else View.INVISIBLE)
                cursor = cursor.plusDays(1)
            }
            views.addView(R.id.widget_rows, week)
        }

        WidgetCore.clickThrough(context, views, TraditionalCalendarActivity::class.java)
    }

    private fun cellColor(
        context: Context,
        date: LocalDate,
        inMonth: Boolean,
        isToday: Boolean,
        isHoliday: Boolean
    ): Int = ContextCompat.getColor(
        context,
        when {
            !inMonth -> R.color.shs_text_muted
            isToday -> R.color.shs_cyan
            isHoliday -> R.color.shs_orange
            else -> R.color.shs_text_primary
        }
    )
}
