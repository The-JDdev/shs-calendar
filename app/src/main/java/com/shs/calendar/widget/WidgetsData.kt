package com.shs.calendar.widget

import android.content.Context
import android.view.Gravity
import android.widget.RemoteViews
import com.shs.calendar.R
import com.shs.calendar.data.CalendarDatabase
import com.shs.calendar.data.entity.EventEntity
import com.shs.calendar.data.repository.EventRepository
import com.shs.calendar.ui.MainActivity
import com.shs.calendar.ui.clock.WorldClockActivity
import com.shs.calendar.ui.event.EventsAgendaActivity
import com.shs.calendar.ui.tools.DateConverterActivity
import com.shs.calendar.weather.WeatherRepository
import kotlinx.coroutines.runBlocking
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val EVENT_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

private fun events(context: Context): List<EventEntity> = runCatching {
    runBlocking {
        EventRepository(CalendarDatabase.get(context)).upcoming(System.currentTimeMillis(), 20)
    }
}.getOrDefault(emptyList())

/** (5) Upcoming events — the next few across all calendars. */
class UpcomingEventsWidget : SHSWidgetProvider() {
    override val layoutRes = R.layout.widget_list

    override fun render(context: Context, views: RemoteViews, id: Int) {
        val upcoming = events(context).take(3)
        views.setTextViewText(R.id.widget_title, "Upcoming")
        if (upcoming.isEmpty()) {
            views.setTextViewText(R.id.widget_subtitle, "No upcoming events")
            views.setViewVisibility(R.id.widget_list_container, android.view.View.GONE)
            return
        }
        views.setViewVisibility(R.id.widget_list_container, android.view.View.VISIBLE)
        views.setTextViewText(R.id.widget_subtitle, "")
        views.removeAllViews(R.id.widget_list_container)
        val zone = SHSWidgetProvider.zoneOrDefault(SHSWidgetProvider.locationOrNull(context))
        for ((index, e) in upcoming.withIndex()) {
            val row = RemoteViews(context.packageName, R.layout.widget_row)
            row.setTextViewText(
                R.id.row_title,
                if (e.allDay) e.title else "${e.title} · ${timeOf(e, zone)}"
            )
            views.addView(R.id.widget_list_container, row)
            if (index == upcoming.lastIndex) break
        }
        WidgetCore.clickThrough(context, views, EventsAgendaActivity::class.java)
    }

    private fun timeOf(e: EventEntity, zone: ZoneId): String =
        Instant.ofEpochMilli(e.startUtcMillis).atZone(zone).toLocalTime().format(EVENT_TIME)
}

/** (6) Agenda list — today's events, or an honest empty state. */
class AgendaWidget : SHSWidgetProvider() {
    override val layoutRes = R.layout.widget_list

    override fun render(context: Context, views: RemoteViews, id: Int) {
        val zone = SHSWidgetProvider.zoneOrDefault(SHSWidgetProvider.locationOrNull(context))
        val today = SHSWidgetProvider.today()
        val startOfDay = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfDay = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val todays = runCatching {
            runBlocking {
                EventRepository(CalendarDatabase.get(context))
                    .eventsInWindow(startOfDay, endOfDay)
            }
        }.getOrDefault(emptyList())

        views.setTextViewText(R.id.widget_title, WidgetPresenter.formatTodayLong(today))
        if (todays.isEmpty()) {
            views.setViewVisibility(R.id.widget_list_container, android.view.View.GONE)
            views.setTextViewText(R.id.widget_subtitle, "Nothing scheduled today")
            return
        }
        views.setViewVisibility(R.id.widget_list_container, android.view.View.VISIBLE)
        views.setTextViewText(R.id.widget_subtitle, "${todays.size} event(s)")
        views.removeAllViews(R.id.widget_list_container)
        for (e in todays) {
            val row = RemoteViews(context.packageName, R.layout.widget_row)
            val time = if (e.allDay) "All day"
            else Instant.ofEpochMilli(e.startUtcMillis).atZone(zone).toLocalTime().format(EVENT_TIME)
            row.setTextViewText(R.id.row_title, "$time · ${e.title}")
            views.addView(R.id.widget_list_container, row)
        }
        WidgetCore.clickThrough(context, views, EventsAgendaActivity::class.java)
    }
}

/** (8) Countdown — days remaining until the next event (or next holiday). */
class CountdownWidget : SHSWidgetProvider() {
    override val layoutRes = R.layout.widget_date_single

    override fun render(context: Context, views: RemoteViews, id: Int) {
        val today = SHSWidgetProvider.today()
        val next = events(context).firstOrNull()
        if (next == null) {
            applyFallback(views)
            return
        }
        val zone = SHSWidgetProvider.zoneOrDefault(SHSWidgetProvider.locationOrNull(context))
        val target = Instant.ofEpochMilli(next.startUtcMillis).atZone(zone).toLocalDate()
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, target).coerceAtLeast(0L)
        views.setTextViewText(R.id.widget_title, next.title)
        views.setTextViewText(
            R.id.widget_value,
            if (days == 0L) "Today" else "$days day(s)"
        )
        views.setTextViewText(R.id.widget_subtitle, target.toString())
        WidgetCore.clickThrough(context, views, EventsAgendaActivity::class.java)
    }

    override fun applyFallback(views: RemoteViews) {
        views.setTextViewText(R.id.widget_title, "Countdown")
        views.setTextViewText(R.id.widget_value, WidgetPresenter.DASH_TEXT)
        views.setTextViewText(R.id.widget_subtitle, "No events to count down to")
    }
}

/** (9) Weather — cached snapshot only, so the widget never blocks on network. */
class WeatherWidget : SHSWidgetProvider() {
    override val layoutRes = R.layout.widget_date_single

    override fun render(context: Context, views: RemoteViews, id: Int) {
        val snapshot = runCatching {
            val settings = WidgetCore.settings(context)
            WeatherRepository(settings = { settings }).cachedSnapshot()
        }.getOrNull()
        if (snapshot == null) {
            applyFallback(views)
            return
        }
        val current = snapshot.current
        views.setTextViewText(
            R.id.widget_value,
            "${Math.round(current.temperatureC)}° ${current.conditionLabel}"
        )
        val daily = snapshot.daily.firstOrNull()
        views.setTextViewText(
            R.id.widget_subtitle,
            if (daily != null) {
                "H:${Math.round(daily.maxTempC)}°  L:${Math.round(daily.minTempC)}°"
            } else {
                "Humidity ${current.humidityPercent}%"
            }
        )
        views.setTextViewText(R.id.widget_title, "Weather")
        WidgetCore.clickThrough(context, views, MainActivity::class.java)
    }

    override fun applyFallback(views: RemoteViews) {
        views.setTextViewText(R.id.widget_title, "Weather")
        views.setTextViewText(R.id.widget_value, WidgetPresenter.DASH_TEXT)
        views.setTextViewText(R.id.widget_subtitle, "Open the app to load weather")
    }
}
