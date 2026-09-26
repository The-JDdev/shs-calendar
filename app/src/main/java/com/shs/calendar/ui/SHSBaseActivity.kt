package com.shs.calendar.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.shs.calendar.ui.appearance.ThemeManager

/**
 * Base Activity for every screen, so the M10 appearance settings apply
 * app-wide rather than only on the settings screen.
 *
 * M10 previously applied the accent in [com.shs.calendar.ui.SettingsActivity]
 * alone, which meant the other five accents were visible on exactly one
 * screen. Centralising it here means a newly added Activity inherits the
 * user's chosen theme/accent/AMOLED automatically, and cannot forget to.
 *
 * The theme must be applied before setContentView(), so subclasses must not
 * inflate before calling super.onCreate().
 */
abstract class SHSBaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
    }
}
