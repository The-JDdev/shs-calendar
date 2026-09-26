package com.shs.calendar.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.shs.calendar.R
import com.shs.calendar.data.CalendarDatabase
import com.shs.calendar.data.repository.SettingsRepository
import java.time.DayOfWeek
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Settings screen: first day of week, time format, default calendar system,
 * Bengali numerals toggle, Hijri moon-sighting adjustment (−3..+3).
 *
 * Every change is persisted through [SettingsRepository] and takes effect
 * immediately on the dashboard via its settings Flow.
 */
class SettingsActivity : SHSBaseActivity() {

    private val repo: SettingsRepository by lazy {
        SettingsRepository(CalendarDatabase.get(this))
    }

    private lateinit var firstDaySpinner: Spinner
    private lateinit var timeFormatGroup: RadioGroup
    private lateinit var defaultSystemSpinner: Spinner
    private lateinit var switchBengaliNumerals: SwitchMaterial
    private lateinit var hijriAdjustText: TextView
    private lateinit var travelModeSwitch: SwitchMaterial
    private lateinit var prayerMethodSpinner: Spinner
    private lateinit var prayerMadhabSpinner: Spinner
    private lateinit var prayerHighLatSpinner: Spinner

    // M10 appearance
    private lateinit var themeGroup: RadioGroup
    private lateinit var accentSpinner: Spinner
    private lateinit var fontScaleGroup: RadioGroup
    private lateinit var languageSpinner: Spinner
    private val appearanceStore by lazy { com.shs.calendar.ui.appearance.AppearanceStore.get(this) }
    private var updatingUi = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // M10: the accent/AMOLED theme is applied by SHSBaseActivity, which
        // runs before super.onCreate() returns. Applying it again HERE would
        // call setTheme() a second time, and setTheme accepts a single style
        // id — so the second call would discard the first, silently dropping
        // AMOLED on this screen. Settings must not apply the theme itself.
        setContentView(R.layout.activity_settings)

        findViewById<MaterialToolbar>(R.id.settings_toolbar)?.apply {
            setNavigationIcon(R.drawable.ic_back)
            setNavigationOnClickListener { finish() }
        }

        firstDaySpinner = findViewById(R.id.settings_first_day_spinner)
        timeFormatGroup = findViewById(R.id.settings_time_format_group)
        defaultSystemSpinner = findViewById(R.id.settings_calendar_spinner)
        switchBengaliNumerals = findViewById(R.id.settings_bengali_numerals_switch)
        hijriAdjustText = findViewById(R.id.settings_hijri_value)
        travelModeSwitch = findViewById(R.id.settings_travel_mode_switch)

        firstDaySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            dayNames()
        )
        defaultSystemSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            resources.getStringArray(R.array.calendar_systems)
        )

        prayerMethodSpinner = findViewById(R.id.settings_prayer_method_spinner)
        prayerMadhabSpinner = findViewById(R.id.settings_prayer_madhab_spinner)
        prayerHighLatSpinner = findViewById(R.id.settings_prayer_high_lat_spinner)

        // M10 appearance. Spinner order is the enum declaration order
        // (Accent.entries, and the language array's system-first ordering),
        // so the index is the value and no lookup table is needed.
        themeGroup = findViewById(R.id.settings_theme_group)
        accentSpinner = findViewById(R.id.settings_accent_spinner)
        fontScaleGroup = findViewById(R.id.settings_font_scale_group)
        languageSpinner = findViewById(R.id.settings_language_spinner)
        accentSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            resources.getStringArray(R.array.appearance_accents)
        )
        languageSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            resources.getStringArray(R.array.appearance_languages)
        )
        renderAppearance()
        prayerMethodSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            resources.getStringArray(R.array.prayer_methods)
        )
        prayerMadhabSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            resources.getStringArray(R.array.prayer_madhabs)
        )
        prayerHighLatSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            resources.getStringArray(R.array.prayer_high_lat_rules)
        )

        bindControls()

        lifecycleScope.launch {
            repo.observe().collectLatest { s ->
                updatingUi = true
                firstDaySpinner.setSelection(
                    (s.firstDayOfWeek.coerceIn(1, 7) - 1 + 7) % 7
                )
                timeFormatGroup.check(
                    if (s.clock24Hour) R.id.settings_time_24 else R.id.settings_time_12
                )
                defaultSystemSpinner.setSelection(
                    when (s.defaultSystem) {
                        "BENGALI" -> 1
                        "HIJRI" -> 2
                        else -> 0
                    }
                )
                switchBengaliNumerals.isChecked = s.bengaliNumerals
                travelModeSwitch.isChecked = s.useDeviceTimezone
                hijriAdjustText.text = (s.hijriAdjustment.coerceIn(-3, 3)).toString()
                prayerMethodSpinner.setSelection(
                    com.shs.calendar.prayer.PrayerSettings.methodOf(s).ordinal
                )
                prayerMadhabSpinner.setSelection(
                    com.shs.calendar.prayer.PrayerSettings.madhabOf(s).ordinal
                )
                prayerHighLatSpinner.setSelection(
                    com.shs.calendar.prayer.PrayerSettings.highLatitudeRuleOf(s).ordinal
                )
                updatingUi = false
            }
        }
    }

    private fun bindControls() {
        firstDaySpinner.onItemSelectedListener = simpleSelection { pos ->
            persist { it.copy(firstDayOfWeek = ((pos + 6) % 7) + 1) }
        }
        timeFormatGroup.setOnCheckedChangeListener { _, checkedId ->
            if (!updatingUi) persist { it.copy(clock24Hour = checkedId == R.id.settings_time_24) }
        }
        defaultSystemSpinner.onItemSelectedListener = simpleSelection { pos ->
            persist {
                it.copy(
                    defaultSystem = when (pos) {
                        1 -> "BENGALI"
                        2 -> "HIJRI"
                        else -> "GREGORIAN"
                    }
                )
            }
        }
        switchBengaliNumerals.setOnCheckedChangeListener { _, checked ->
            if (!updatingUi) persist { it.copy(bengaliNumerals = checked) }
        }
        travelModeSwitch.setOnCheckedChangeListener { _, checked ->
            if (!updatingUi) persist { it.copy(useDeviceTimezone = checked) }
        }
        findViewById<android.view.View>(R.id.settings_hijri_minus)?.setOnClickListener {
            adjustHijri(-1)
        }
        findViewById<android.view.View>(R.id.settings_hijri_plus)?.setOnClickListener {
            adjustHijri(+1)
        }
        prayerMethodSpinner.onItemSelectedListener = simpleSelection { pos ->
            persist {
                it.copy(
                    prayerMethod = com.shs.calendar.prayer.PrayerMethod.values()
                        .getOrElse(pos) { com.shs.calendar.prayer.PrayerMethod.MWL }.name
                )
            }
        }
        prayerMadhabSpinner.onItemSelectedListener = simpleSelection { pos ->
            persist {
                it.copy(
                    prayerMadhab = com.shs.calendar.prayer.Madhab.values()
                        .getOrElse(pos) { com.shs.calendar.prayer.Madhab.HANAFI }.name
                )
            }
        }
        prayerHighLatSpinner.onItemSelectedListener = simpleSelection { pos ->
            persist {
                it.copy(
                    highLatitudeRule = com.shs.calendar.prayer.HighLatitudeRule.values()
                        .getOrElse(pos) { com.shs.calendar.prayer.HighLatitudeRule.NONE }.name
                )
            }
        }
        findViewById<android.view.View>(R.id.settings_prayer_offsets_button)?.setOnClickListener {
            showOffsetsDialog()
        }
        // Entry point for the sync stack. Registered in the manifest, but with
        // no way to reach it from Settings the screen was only launchable by
        // adb — a feature the user cannot find is not a feature.
        findViewById<android.view.View>(R.id.settings_sync_accounts_button)?.setOnClickListener {
            startActivity(android.content.Intent(this, com.shs.calendar.ui.caldav.CalDavAccountsActivity::class.java))
        }

        // M10 appearance. Each change is written to AppearanceStore and then
        // applied to the running process; theme and locale changes need a
        // recreate for the new resources to be inflated.
        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (updatingUi) return@setOnCheckedChangeListener
            val theme = when (checkedId) {
                R.id.settings_theme_dark -> com.shs.calendar.ui.appearance.Theme.DARK
                R.id.settings_theme_amoled -> com.shs.calendar.ui.appearance.Theme.AMOLED
                R.id.settings_theme_light -> com.shs.calendar.ui.appearance.Theme.LIGHT
                else -> com.shs.calendar.ui.appearance.Theme.SYSTEM_DARK
            }
            appearanceStore.saveTheme(theme)
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                appearanceStore.load().nightMode
            )
        }
        accentSpinner.onItemSelectedListener = simpleSelection { pos ->
            appearanceStore.saveAccent(
                com.shs.calendar.ui.appearance.Accent.entries
                    .getOrElse(pos) { com.shs.calendar.ui.appearance.Accent.CYAN }
            )
        }
        fontScaleGroup.setOnCheckedChangeListener { _, checkedId ->
            if (updatingUi) return@setOnCheckedChangeListener
            appearanceStore.saveFontScale(
                when (checkedId) {
                    R.id.settings_font_scale_small -> com.shs.calendar.ui.appearance.FontScale.SMALL
                    R.id.settings_font_scale_large -> com.shs.calendar.ui.appearance.FontScale.LARGE
                    R.id.settings_font_scale_xl -> com.shs.calendar.ui.appearance.FontScale.EXTRA_LARGE
                    else -> com.shs.calendar.ui.appearance.FontScale.NORMAL
                }
            )
        }
        languageSpinner.onItemSelectedListener = simpleSelection { pos ->
            if (updatingUi) return@simpleSelection
            // Indexed into languageOptions() rather than matched on the
            // spinner label: a localised label could collide or change and
            // silently mis-map to the wrong language.
            val tag = languageOptions().getOrNull(pos)
                ?: com.shs.calendar.ui.appearance.AppearanceOptions.TAG_SYSTEM
            appearanceStore.saveLocale(tag)
            // TAG_SYSTEM is our own marker, not a BCP-47 tag. Handing it to
            // forLanguageTags would have the framework try to parse "s" with
            // subtag "ystem" — the empty locale list is what actually means
            // "follow the device language".
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                if (tag == com.shs.calendar.ui.appearance.AppearanceOptions.TAG_SYSTEM) {
                    androidx.core.os.LocaleListCompat.getEmptyLocaleList()
                } else {
                    androidx.core.os.LocaleListCompat.forLanguageTags(tag)
                }
            )
        }
    }

    /**
     * Pushes the stored appearance into the four controls.
     *
     * Spinner positions are guarded with getOrElse because the stored name
     * may belong to an older build whose enum no longer has that entry; the
     * radio ids are checked by name for the same reason. updatingUi is set
     * around this so the listeners bound above do not immediately write the
     * values straight back out again.
     */
    private fun renderAppearance() {
        val options = appearanceStore.load()
        val accentIndex = com.shs.calendar.ui.appearance.Accent.entries
            .indexOf(options.accent)
            .coerceAtLeast(0)
        val scaleId = when (options.fontScale) {
            com.shs.calendar.ui.appearance.FontScale.SMALL -> R.id.settings_font_scale_small
            com.shs.calendar.ui.appearance.FontScale.NORMAL -> R.id.settings_font_scale_system
            com.shs.calendar.ui.appearance.FontScale.LARGE -> R.id.settings_font_scale_large
            com.shs.calendar.ui.appearance.FontScale.EXTRA_LARGE -> R.id.settings_font_scale_xl
        }
        val themeId = when (options.theme) {
            com.shs.calendar.ui.appearance.Theme.DARK -> R.id.settings_theme_dark
            com.shs.calendar.ui.appearance.Theme.AMOLED -> R.id.settings_theme_amoled
            com.shs.calendar.ui.appearance.Theme.LIGHT -> R.id.settings_theme_light
            com.shs.calendar.ui.appearance.Theme.SYSTEM_DARK -> R.id.settings_theme_system
        }
        val languageIndex = languageOptions().indexOf(options.localeTag).coerceAtLeast(0)

        updatingUi = true
        themeGroup.check(themeId)
        accentSpinner.setSelection(accentIndex)
        fontScaleGroup.check(scaleId)
        languageSpinner.setSelection(languageIndex)
        updatingUi = false
    }

    /** Language tags offered by the spinner, in array order. */
    private fun languageOptions(): List<String> = listOf(
        com.shs.calendar.ui.appearance.AppearanceOptions.TAG_SYSTEM,
        "en",
        "bn",
        "ar"
    )

    /** Manual minute offsets per prayer, six signed fields in one dialog. */
    private fun showOffsetsDialog() {
        lifecycleScope.launch {
            val current = com.shs.calendar.prayer.PrayerSettings.offsetsOf(
                repo.get() ?: com.shs.calendar.data.entity.SettingsEntity()
            )
            val labels = arrayOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")
            val start = intArrayOf(
                current.fajr, current.sunrise, current.dhuhr,
                current.asr, current.maghrib, current.isha
            )
            val inputs = arrayOfNulls<android.widget.EditText>(labels.size)

            val grid = android.widget.LinearLayout(this@SettingsActivity).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                val pad = (16 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad / 2, pad, 0)
            }
            labels.forEachIndexed { i, label ->
                val row = android.widget.LinearLayout(this@SettingsActivity).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                row.addView(
                    android.widget.TextView(this@SettingsActivity).apply {
                        text = label
                        textSize = 15f
                    },
                    android.widget.LinearLayout.LayoutParams(0,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )
                val edit = android.widget.EditText(this@SettingsActivity).apply {
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                    setText(start[i].toString())
                    setSingleLine()
                    gravity = android.view.Gravity.END
                }
                inputs[i] = edit
                row.addView(
                    edit,
                    android.widget.LinearLayout.LayoutParams(
                        (72 * resources.displayMetrics.density).toInt(),
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                grid.addView(row)
            }

            androidx.appcompat.app.AlertDialog.Builder(this@SettingsActivity)
                .setTitle(R.string.settings_prayer_offsets)
                .setView(grid)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val vals = IntArray(labels.size) { i ->
                        inputs[i]?.text?.toString()?.trim()?.toIntOrNull() ?: start[i]
                    }
                    persist {
                        it.copy(
                            prayerOffsetsCsv = com.shs.calendar.prayer.PrayerSettings.encodeOffsets(
                                com.shs.calendar.prayer.PrayerOffsets(
                                    vals[0], vals[1], vals[2], vals[3], vals[4], vals[5]
                                )
                            )
                        )
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun adjustHijri(delta: Int) {
        persist { s ->
            s.copy(hijriAdjustment = (s.hijriAdjustment + delta).coerceIn(-3, 3))
        }
    }

    private fun persist(transform: (com.shs.calendar.data.entity.SettingsEntity) -> com.shs.calendar.data.entity.SettingsEntity) {
        lifecycleScope.launch {
            runCatching { repo.update(transform) }
        }
    }

    private fun simpleSelection(onSelected: (Int) -> Unit) =
        object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                if (!updatingUi) onSelected(position)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }

    private fun dayNames(): List<String> {
        val names = DayOfWeek.values().map {
            it.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
        }
        return names.drop(6) + names.take(6) // Sunday-first display order
    }
}
