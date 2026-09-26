package com.shs.calendar.ui.appearance

import android.app.Activity
import com.shs.calendar.R

/**
 * Maps the selected [Accent] onto a ThemeOverlay style resource.
 *
 * A theme attribute cannot be given a runtime string — setTheme() takes a
 * style *resource id* — so each accent needs its own small overlay style
 * rather than a computed one. The overlays only re-tint the accent roles;
 * surfaces and text are left alone, so switching accent cannot change
 * contrast. The @color/ names they reference are config-qualified, so the same
 * overlay resolves correctly under both the light and the night palette.
 *
 * AMOLED is handled by [ThemeManager], which composes the accent into the
 * AMOLED overlay — setTheme() takes one style id, so the two cannot be
 * applied as separate calls.
 *
 * Call before setContentView(): an overlay applied afterwards reaches already
 * inflated views only if they re-resolve their attributes.
 */
object AccentOverlay {

    /**
     * The overlay style for [accent].
     *
     * CYAN is the palette's own colorPrimary, so the base theme is already
     * correct for it and no overlay is needed — 0 means "do not call
     * setTheme". It must NOT map to android.R.style.ThemeOverlay: that is a
     * base theme intended only as an overlay parent, and setTheme() with it
     * would replace the SHS palette outright.
     */
    fun themeFor(accent: Accent): Int = when (accent) {
        Accent.CYAN -> NO_OVERLAY
        Accent.TEAL -> R.style.ThemeOverlay_SHSCalendar_Accent_Teal
        Accent.INDIGO -> R.style.ThemeOverlay_SHSCalendar_Accent_Indigo
        Accent.PURPLE -> R.style.ThemeOverlay_SHSCalendar_Accent_Purple
        Accent.MAGENTA -> R.style.ThemeOverlay_SHSCalendar_Accent_Magenta
        Accent.AMBER -> R.style.ThemeOverlay_SHSCalendar_Accent_Amber
    }

    /**
     * Applies the stored accent, if any, to [activity].
     *
     * Must be called before setContentView: an overlay applied afterwards only
     * reaches views that have not yet resolved their attributes.
     */
    fun apply(activity: Activity) {
        val theme = themeFor(AppearanceStore.get(activity).load().accent)
        if (theme != NO_OVERLAY) activity.setTheme(theme)
    }

    /** Sentinel for "the base theme is already correct". */
    const val NO_OVERLAY = 0
}
