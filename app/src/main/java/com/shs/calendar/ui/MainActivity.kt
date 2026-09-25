package com.shs.calendar.ui

import android.content.Intent
import android.os.Bundle
import android.widget.GridView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip
import com.shs.calendar.R
import com.shs.calendar.astronomy.SolarEngine
import com.shs.calendar.calendar.BengaliEngine
import com.shs.calendar.calendar.BengaliNumerals
import com.shs.calendar.calendar.GregorianEngine
import com.shs.calendar.calendar.HijriEngine
import com.shs.calendar.data.CalendarDatabase
import com.shs.calendar.data.repository.SettingsRepository
import com.shs.calendar.reminders.ReminderScheduler
import com.shs.calendar.ui.adapters.MonthGridAdapter
import com.shs.calendar.ui.adapters.QuickToolsAdapter
import com.shs.calendar.ui.event.EventEditorActivity
import com.shs.calendar.ui.event.EventsAgendaActivity
import com.shs.calendar.ui.placeholder.PlaceholderActivity
import com.shs.calendar.ui.tools.AgeCalculatorActivity
import com.shs.calendar.ui.tools.DateConverterActivity
import com.shs.calendar.ui.SettingsActivity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Dashboard: hero triple-date, live clock, sun times, calendar card, quick tools,
 * inspiration strip, bottom navigation (5 tabs).
 *
 * Calendar tab is the primary surface; tools/prayer/accounts/settings tabs either
 * open dedicated screens or fall back to [PlaceholderActivity].
 */
class MainActivity : AppCompatActivity() {

    private lateinit var grid: GridView
    private lateinit var adapter: MonthGridAdapter
    private val settingsRepo: SettingsRepository by lazy {
        SettingsRepository(CalendarDatabase.get(this))
    }

    private var month: YearMonth = YearMonth.now()
    private var firstDay: DayOfWeek = DayOfWeek.SUNDAY
    private var hijriAdjustment: Int = 0
    private var bengaliNumerals: Boolean = false
    private var latitude: Double = DEFAULT_LAT
    private var longitude: Double = DEFAULT_LON
    private var locationName: String = ""
    private var zone: ZoneId = ZoneId.of(DEFAULT_ZONE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        grid = findViewById(R.id.cal_grid)
        adapter = MonthGridAdapter(
            this,
            GregorianEngine.monthGrid(month),
            hijriAdjustment,
            bengaliNumerals
        )
        grid.adapter = adapter

        grid.setOnItemClickListener { _, _, position, _ ->
            val cell = adapter.getItem(position)
            startActivity(
                Intent(this, EventEditorActivity::class.java)
                    .putExtra(EventEditorActivity.EXTRA_DATE, cell.date.toString())
            )
        }

        bindNavigation()
        bindCalendarControls()
        bindLocationPill()
        bindQuickTools()
        bindInspiration()

        lifecycleScope.launch {
            settingsRepo.observe().collectLatest { s ->
                firstDay = DayOfWeek.of(s.firstDayOfWeek.coerceIn(1, 7))
                hijriAdjustment = HijriEngine.clampAdjustment(s.hijriAdjustment)
                bengaliNumerals = s.bengaliNumerals
                s.latitude?.let { latitude = it }
                s.longitude?.let { longitude = it }
                locationName = s.locationName
                if (!s.useDeviceTimezone && s.timezone.isNotBlank()) {
                    runCatching { zone = ZoneId.of(s.timezone) }
                }
                refreshDashboard()
                renderMonth()
            }
        }

        lifecycleScope.launch {
            runCatching { ReminderScheduler.rescheduleAll(this@MainActivity) }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
    }

    // ---- dashboard surfaces -------------------------------------------------

    private fun refreshDashboard() {
        val today = LocalDate.now()
        val bengali = BengaliEngine.fromGregorian(today)
        val hijri = HijriEngine.fromGregorian(today, hijriAdjustment)

        findViewById<TextView>(R.id.hero_weekday).text =
            GregorianEngine.weekdayName(today)
        findViewById<TextView>(R.id.hero_day).text =
            numeralAware(today.dayOfMonth.toString())
        findViewById<TextView>(R.id.hero_month_year).text =
            GregorianEngine.monthName(today) + " " + today.year
        findViewById<TextView>(R.id.hero_bengali_line).text =
            "${bengali.day} ${bengaliMonthLabel(bengali.month)} ${bengali.year}"
        findViewById<TextView>(R.id.hero_hijri_line).text =
            "${hijri.day} ${hijri.monthName} ${hijri.year}"

        val solar = SolarEngine.compute(today, latitude, longitude, zone)
        findViewById<TextView>(R.id.sunrise_time).text =
            solar.sunrise?.toString() ?: "—"
        findViewById<TextView>(R.id.sunset_time).text =
            solar.sunset?.toString() ?: "—"

        findViewById<TextView>(R.id.location_text).text =
            locationName.ifBlank {
                getString(R.string.location_none_hint)
            }

        val names = GregorianEngine.weekdayHeaders(firstDay)
        val labels = findViewById<LinearLayout>(R.id.cal_weekdays)
        if (labels.childCount != names.size) {
            labels.removeAllViews()
            names.forEach { name ->
                labels.addView(
                    TextView(this).apply {
                        text = name
                        gravity = android.view.Gravity.CENTER
                        setTextAppearance(R.style.SHS_Text_Caption)
                    },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                )
            }
        }
    }

    private fun renderMonth() {
        val today = LocalDate.now()
        adapter.submit(GregorianEngine.monthGrid(month, firstDay, today), hijriAdjustment, bengaliNumerals)
        findViewById<TextView>(R.id.cal_month_title).text =
            GregorianEngine.monthName(month) + " " + month.year
    }

    private fun numeralAware(value: String): String =
        if (bengaliNumerals) BengaliNumerals.toBengali(value) else value

    private fun bengaliMonthLabel(month: Int): String = when (month) {
        1 -> "বৈশাখ"; 2 -> "জ্যৈষ্ঠ"; 3 -> "আষাঢ়"; 4 -> "শ্রাবণ"
        5 -> "ভাদ্র"; 6 -> "আশ্বিন"; 7 -> "কার্তিক"; 8 -> "অগ্রহায়ণ"
        9 -> "পৌষ"; 10 -> "মাঘ"; 11 -> "ফাল্গুন"; 12 -> "চৈত্র"
        else -> ""
    }

    // ---- interactions -------------------------------------------------------

    private fun bindCalendarControls() {
        findViewById<ImageButton>(R.id.btn_prev_month).setOnClickListener {
            month = GregorianEngine.previousMonth(month); renderMonth()
        }
        findViewById<ImageButton>(R.id.btn_next_month).setOnClickListener {
            month = GregorianEngine.nextMonth(month); renderMonth()
        }
        findViewById<TextView>(R.id.btn_today).setOnClickListener {
            month = GregorianEngine.yearMonthOf(LocalDate.now()); renderMonth()
        }
        findViewById<ImageButton>(R.id.btn_widget).setOnClickListener {
            startActivity(Intent(this, EventsAgendaActivity::class.java))
        }
        findViewById<TextView>(R.id.header_title).setOnClickListener {
            startActivity(Intent(this, EventsAgendaActivity::class.java))
        }
    }

    private fun bindQuickTools() {
        val tools = listOf(
            QuickToolsAdapter.Tool("age", "⧗", R.string.tool_age_calculator, false),
            QuickToolsAdapter.Tool("convert", "⇄", R.string.tool_date_converter, false),
            QuickToolsAdapter.Tool("events", "☰", R.string.event_agenda_title, false),
            QuickToolsAdapter.Tool("prayer", "☾", R.string.nav_prayer, true),
            QuickToolsAdapter.Tool("notes", "✎", R.string.nav_accounts, true)
        )
        val recycler = findViewById<RecyclerView>(R.id.quick_tools_row)
        recycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recycler.adapter = QuickToolsAdapter(tools) { tool ->
            when (tool.id) {
                "age" -> startActivity(Intent(this, AgeCalculatorActivity::class.java))
                "convert" -> startActivity(Intent(this, DateConverterActivity::class.java))
                "events" -> startActivity(Intent(this, EventsAgendaActivity::class.java))
                "prayer" -> startActivity(Intent(this, com.shs.calendar.ui.prayer.PrayerActivity::class.java))
                else -> openPlaceholder(tool.id)
            }
        }
    }

    private fun bindInspiration() {
        val quotes = resources.getStringArray(R.array.inspiration_quotes)
        val authors = resources.getStringArray(R.array.inspiration_authors)
        if (quotes.isEmpty()) return
        val text = findViewById<TextView>(R.id.inspiration_text)
        val author = findViewById<TextView>(R.id.inspiration_author)
        var index = (System.nanoTime() % quotes.size).toInt()
        fun render() {
            text.text = quotes[index]
            author.text = authors.getOrElse(index) { "" }
        }
        render()
        findViewById<TextView>(R.id.inspiration_refresh).setOnClickListener {
            index = (index + 1) % quotes.size; render()
        }
        findViewById<TextView>(R.id.inspiration_share).setOnClickListener {
            startActivity(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "${quotes[index]} — ${authors.getOrElse(index) { "" }}")
                }
            )
        }
        listOf<Chip>(findViewById(R.id.chip_lang_en), findViewById(R.id.chip_lang_bn), findViewById(R.id.chip_lang_ar))
            .forEach { chip ->
                chip.setOnClickListener { render() }
            }
    }

    private fun bindLocationPill() {
        findViewById<android.view.View>(R.id.location_pill).setOnClickListener {
            startActivity(Intent(this, com.shs.calendar.location.LocationPickerActivity::class.java))
        }
    }

    private fun bindNavigation() {
        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        nav.selectedItemId = R.id.nav_calendar
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_calendar -> {
                    findViewById<android.view.View>(R.id.dashboard_scroll).scrollTo(0, 0)
                    true
                }
                R.id.nav_tools -> {
                    startActivity(Intent(this, AgeCalculatorActivity::class.java)); true
                }
                R.id.nav_prayer -> {
                    startActivity(Intent(this, com.shs.calendar.ui.prayer.PrayerActivity::class.java)); true
                }
                R.id.nav_accounts -> {
                    openPlaceholder("accounts"); true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java)); true
                }
                else -> false
            }
        }
    }

    private fun openPlaceholder(key: String) {
        startActivity(
            Intent(this, PlaceholderActivity::class.java)
                .putExtra(PlaceholderActivity.EXTRA_KEY, key)
        )
    }

    companion object {
        private const val DEFAULT_LAT = 23.8103
        private const val DEFAULT_LON = 90.4125
        private const val DEFAULT_ZONE = "Asia/Dhaka"
        private val DATE_TIME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)
    }
}
