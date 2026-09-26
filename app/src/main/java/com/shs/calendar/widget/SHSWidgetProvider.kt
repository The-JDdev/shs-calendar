package com.shs.calendar.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.shs.calendar.R
import com.shs.calendar.data.CalendarDatabase
import com.shs.calendar.data.repository.SettingsRepository
import com.shs.calendar.location.LocationRepository
import com.shs.calendar.location.ResolvedLocation
import com.shs.calendar.prayer.PrayerConfig
import com.shs.calendar.prayer.PrayerEngine
import com.shs.calendar.prayer.PrayerSettings
import com.shs.calendar.prayer.PrayerTimes
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Shared plumbing for every M7 home-screen widget.
 *
 * Subclasses only implement [render]; this base class owns the periodic
 * AlarmManager refresh, the repaint-everything broadcast, and tolerant data
 * gathering. A denied location permission must never crash a widget — every
 * data path degrades to honest placeholder text instead.
 */
abstract class SHSWidgetProvider : AppWidgetProvider() {

    /** Layout this widget inflates. */
    abstract val layoutRes: Int

    /**
     * Renders the widget body. Called with a live [RemoteViews]; any throw is
     * caught by [onUpdate] and turned into a fallback rather than a crash.
     */
    protected abstract fun render(context: Context, views: RemoteViews, id: Int)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            updateOne(context, appWidgetManager, id)
        }
        scheduleRefresh(context)
    }

    private fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
        val views = RemoteViews(context.packageName, layoutRes)
        runCatching { render(context, views, id) }
            .onFailure { applyFallback(views) }
        manager.updateAppWidget(id, views)
    }

    /** Truthful "no data" state, used when a render path fails. */
    protected open fun applyFallback(views: RemoteViews) {
        views.setTextViewText(R.id.widget_subtitle, WidgetPresenter.DASH_TEXT)
    }

    companion object {
        const val ACTION_REFRESH_ALL = "com.shs.calendar.action.WIDGET_REFRESH"

        /** Half-hourly repaint; clock/countdown widgets are minute-accurate
         *  through their own ticking text, so this need not be faster. */
        const val REFRESH_INTERVAL_MILLIS = 30L * 60L * 1000L
        private const val REFRESH_REQUEST_CODE = 4711

        /** Every concrete widget class, for the repaint-all sweep. */
        private val ALL_WIDGETS: List<Class<out AppWidgetProvider>> = listOf(
            CalendarWidget::class.java, TodayWidget::class.java, BengaliDateWidget::class.java,
            HijriDateWidget::class.java, UpcomingEventsWidget::class.java,
            AgendaWidget::class.java, PrayerWidget::class.java, CountdownWidget::class.java,
            WeatherWidget::class.java, ClockWidget::class.java, MoonPhaseWidget::class.java
        )

        fun requestRefresh(context: Context) {
            context.sendBroadcast(
                Intent(context, SHSWidgetRefreshReceiver::class.java)
                    .setAction(ACTION_REFRESH_ALL)
                    .setPackage(context.packageName)
            )
        }

        fun scheduleRefresh(context: Context) {
            val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            manager.setInexactRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis() + REFRESH_INTERVAL_MILLIS,
                REFRESH_INTERVAL_MILLIS,
                refreshIntent(context)
            )
        }

        fun cancelRefresh(context: Context) {
            val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            manager.cancel(refreshIntent(context))
        }

        private fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, REFRESH_REQUEST_CODE,
            Intent(context, SHSWidgetRefreshReceiver::class.java)
                .setAction(ACTION_REFRESH_ALL)
                .setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        /** Repaints every instance of every SHS widget. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            for (kind in ALL_WIDGETS) {
                val provider = providerFor(kind) ?: continue
                for (id in manager.getAppWidgetIds(ComponentName(context, kind))) {
                    val views = RemoteViews(context.packageName, provider.layoutRes)
                    runCatching { provider.render(context, views, id) }
                        .onFailure { provider.applyFallback(views) }
                    manager.updateAppWidget(id, views)
                }
            }
        }

        private fun providerFor(kind: Class<out AppWidgetProvider>): SHSWidgetProvider? =
            runCatching {
                kind.getDeclaredConstructor().newInstance() as SHSWidgetProvider
            }.getOrNull()

        fun locationOrNull(context: Context): ResolvedLocation? =
            runCatching { LocationRepository(context).lastKnown() }.getOrNull()

        fun zoneOrDefault(loc: ResolvedLocation?): ZoneId =
            runCatching { ZoneId.of(loc?.timeZone ?: DEFAULT_ZONE) }.getOrDefault(ZoneId.of(DEFAULT_ZONE))

        fun today(): LocalDate = LocalDate.now()

        fun now(zone: ZoneId): LocalTime = LocalTime.now(zone)

        /** Reads the persisted prayer settings; defaults when unreadable. */
        fun prayerConfig(context: Context): PrayerConfig = runCatching {
            val repo = SettingsRepository(CalendarDatabase.get(context))
            PrayerSettings.configOf(runBlocking { repo.get() })
        }.getOrDefault(PrayerConfig())

        fun prayerTimesOrNull(context: Context, loc: ResolvedLocation?): PrayerTimes? {
            if (loc == null) return null
            return runCatching {
                PrayerEngine.compute(
                    today(), loc.latitude, loc.longitude, zoneOrDefault(loc), prayerConfig(context)
                )
            }.getOrNull()
        }

        const val DEFAULT_ZONE = "Asia/Dhaka"
    }
}

/** Receives the AlarmManager tick and repaints every widget. */
class SHSWidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == SHSWidgetProvider.ACTION_REFRESH_ALL) {
            SHSWidgetProvider.updateAll(context)
        }
    }
}
