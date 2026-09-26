package com.shs.calendar.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.shs.calendar.R
import com.shs.calendar.calendar.BengaliEngine
import com.shs.calendar.calendar.ConversionEngine
import com.shs.calendar.calendar.HijriEngine
import com.shs.calendar.data.CalendarDatabase
import com.shs.calendar.data.entity.SettingsEntity
import com.shs.calendar.data.repository.SettingsRepository
import com.shs.calendar.holidays.HolidayDatabase
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.YearMonth

/** Shared helpers used by more than one widget. */
internal object WidgetCore {

    private const val REQUEST_OFFSET = 1000

    fun settings(context: Context): SettingsEntity = runCatching {
        SettingsRepository(CalendarDatabase.get(context)).let { runBlocking { it.get() } }
    }.getOrDefault(SettingsEntity())

    /**
     * Hijri offset from settings, so a widget never contradicts the app's own
     * moon-sighting adjustment.
     */
    fun hijriAdjustment(context: Context): Int =
        com.shs.calendar.calendar.HijriEngine.clampAdjustment(settings(context).hijriAdjustment)

    fun bengali(date: LocalDate): BengaliEngine.BengaliDate =
        ConversionEngine.gregorianToBengali(date)

    fun hijri(context: Context, date: LocalDate): HijriEngine.HijriDate =
        ConversionEngine.gregorianToHijri(date, hijriAdjustment(context))

    /** Holiday dates in the month, for calendar-cell badges. */
    fun holidayDates(context: Context, date: LocalDate): Set<LocalDate> = runCatching {
        val adjustment = hijriAdjustment(context)
        HolidayDatabase(hijriAdjustmentProvider = { adjustment })
            .holidayDatesInMonth(YearMonth.from(date))
    }.getOrDefault(emptySet())

    /** Click-through from a widget into an app section. */
    fun clickThrough(context: Context, views: RemoteViews, target: Class<*>) {
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context, REQUEST_OFFSET + target.name.hashCode(), Intent(context, target),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
    }
}
