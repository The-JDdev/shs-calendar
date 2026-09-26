package com.shs.calendar.widget

import android.content.Context
import android.widget.RemoteViews
import com.shs.calendar.R
import com.shs.calendar.ui.MainActivity
import com.shs.calendar.ui.prayer.PrayerActivity
import com.shs.calendar.ui.traditional.TraditionalCalendarActivity

/** (2) Today card — Gregorian headline with the other two calendars beneath. */
class TodayWidget : SHSWidgetProvider() {
    override val layoutRes = R.layout.widget_today

    override fun render(context: Context, views: RemoteViews, id: Int) {
        val today = SHSWidgetProvider.today()
        views.setTextViewText(
            R.id.widget_date,
            WidgetPresenter.formatTodayLong(today)
        )
        views.setTextViewText(
            R.id.widget_subtitle,
            "${WidgetPresenter.formatBengali(WidgetCore.bengali(today))} · " +
                WidgetPresenter.formatHijri(WidgetCore.hijri(context, today))
        )
        WidgetCore.clickThrough(context, views, MainActivity::class.java)
    }
}

/** (3) Bengali date card. */
class BengaliDateWidget : SHSWidgetProvider() {
    override val layoutRes = R.layout.widget_date_single

    override fun render(context: Context, views: RemoteViews, id: Int) {
        val b = WidgetCore.bengali(SHSWidgetProvider.today())
        views.setTextViewText(R.id.widget_title, b.monthNameEn)
        views.setTextViewText(
            R.id.widget_value,
            "${b.day} ${b.monthNameEn} ${b.year}"
        )
        views.setTextViewText(
            R.id.widget_subtitle,
            "${b.season} · ${WidgetPresenter.formatTodayLong(SHSWidgetProvider.today())}"
        )
        WidgetCore.clickThrough(context, views, TraditionalCalendarActivity::class.java)
    }
}

/** (4) Hijri date card, honouring the user's moon-sighting adjustment. */
class HijriDateWidget : SHSWidgetProvider() {
    override val layoutRes = R.layout.widget_date_single

    override fun render(context: Context, views: RemoteViews, id: Int) {
        val h = WidgetCore.hijri(context, SHSWidgetProvider.today())
        views.setTextViewText(R.id.widget_title, h.monthName)
        views.setTextViewText(
            R.id.widget_value,
            "${h.day} ${h.monthName} ${h.year}"
        )
        views.setTextViewText(
            R.id.widget_subtitle,
            WidgetPresenter.formatTodayLong(SHSWidgetProvider.today())
        )
        WidgetCore.clickThrough(context, views, MainActivity::class.java)
    }
}

/** (10) Clock — digital time plus both alternate calendars. */
class ClockWidget : SHSWidgetProvider() {
    override val layoutRes = R.layout.widget_date_single

    override fun render(context: Context, views: RemoteViews, id: Int) {
        val loc = SHSWidgetProvider.locationOrNull(context)
        val zone = SHSWidgetProvider.zoneOrDefault(loc)
        views.setTextViewText(
            R.id.widget_value,
            WidgetPresenter.formatTime(SHSWidgetProvider.now(zone))
        )
        val today = SHSWidgetProvider.today()
        views.setTextViewText(
            R.id.widget_subtitle,
            "${WidgetPresenter.formatWeekdayShort(today)} · " +
                WidgetPresenter.formatBengali(WidgetCore.bengali(today))
        )
        views.setTextViewText(R.id.widget_title, zone.id)
        WidgetCore.clickThrough(context, views, MainActivity::class.java)
    }
}

/** (11) Moon phase — computed, never a hardcoded table. */
class MoonPhaseWidget : SHSWidgetProvider() {
    override val layoutRes = R.layout.widget_date_single

    override fun render(context: Context, views: RemoteViews, id: Int) {
        val today = SHSWidgetProvider.today()
        views.setTextViewText(R.id.widget_title, "Moon")
        views.setTextViewText(
            R.id.widget_value,
            WidgetPresenter.formatMoonPhase(today)
        )
        views.setTextViewText(
            R.id.widget_subtitle,
            "${Math.round(com.shs.calendar.astronomy.MoonPhase.phase(today).illumination * 100)}% lit"
        )
        WidgetCore.clickThrough(context, views, MainActivity::class.java)
    }
}

/** (7) Prayer times — next prayer plus today's row list. */
class PrayerWidget : SHSWidgetProvider() {
    override val layoutRes = R.layout.widget_prayer

    override fun render(context: Context, views: RemoteViews, id: Int) {
        val loc = SHSWidgetProvider.locationOrNull(context)
        val times = SHSWidgetProvider.prayerTimesOrNull(context, loc)
        if (times == null) {
            applyFallback(views)
            return
        }
        val zone = SHSWidgetProvider.zoneOrDefault(loc)
        WidgetPresenter.nextPrayer(times, SHSWidgetProvider.now(zone))?.let { (name, time) ->
            views.setTextViewText(R.id.widget_value, "$name $time")
        }
        views.setTextViewText(
            R.id.widget_subtitle,
            WidgetPresenter.prayerRows(times).joinToString("  ") { it.second }
        )
        views.setTextViewText(R.id.widget_title, loc?.label ?: "Prayer times")
        WidgetCore.clickThrough(context, views, PrayerActivity::class.java)
    }

    override fun applyFallback(views: RemoteViews) {
        views.setTextViewText(R.id.widget_value, WidgetPresenter.DASH_TIME)
        views.setTextViewText(R.id.widget_subtitle, "Set a location to see prayer times")
        views.setTextViewText(R.id.widget_title, "Prayer times")
    }
}
