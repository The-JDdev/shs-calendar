package com.shs.calendar.ui.appearance

import android.app.Activity
import com.shs.calendar.R

/**
 * M10 theme selection: resolves the stored [AppearanceOptions] into the single
 * ThemeOverlay style to hand to [Activity.setTheme].
 *
 * Pure logic — every method is a total function of its arguments, so the whole
 * selection matrix is unit-testable without a device. The project has no
 * Robolectric, which is exactly why the decision lives here and the Activity
 * only performs it.
 *
 * Why one style and not two overlays: setTheme() accepts a single style
 * resource id, so applying an AMOLED overlay and then an accent overlay would
 * leave only the second in effect. AMOLED variants therefore *inherit* the
 * AMOLED overlay and restate the accent inside themselves, and this object
 * picks exactly one style for the (theme, accent) pair.
 */
object ThemeManager {

    /**
     * The style resource for [isAmoled] + [accent], or [NO_OVERLAY] when the
     * base theme is already correct.
     *
     * NO_OVERLAY covers the default case — a non-AMOLED CYAN theme is exactly
     * what Theme.SHSCalendar already renders, so applying nothing is correct.
     * It must never map to android.R.style.ThemeOverlay: that is a base theme
     * meant only as an overlay parent, and setTheme() with it would discard
     * the SHS palette entirely.
     */
    fun themeFor(isAmoled: Boolean, accent: Accent): Int = when {
        !isAmoled -> AccentOverlay.themeFor(accent)
        accent == Accent.CYAN -> R.style.ThemeOverlay_SHSCalendar_Amoled
        else -> when (accent) {
            Accent.TEAL -> R.style.ThemeOverlay_SHSCalendar_Amoled_Accent_Teal
            Accent.INDIGO -> R.style.ThemeOverlay_SHSCalendar_Amoled_Accent_Indigo
            Accent.PURPLE -> R.style.ThemeOverlay_SHSCalendar_Amoled_Accent_Purple
            Accent.MAGENTA -> R.style.ThemeOverlay_SHSCalendar_Amoled_Accent_Magenta
            Accent.AMBER -> R.style.ThemeOverlay_SHSCalendar_Amoled_Accent_Amber
            Accent.CYAN -> error("unreachable: CYAN is handled above")
        }
    }

    /** The style resource for a stored set of [options]. */
    fun themeFor(options: AppearanceOptions): Int = themeFor(options.isAmoled, options.accent)

    /**
     * Applies the stored appearance to [activity].
     *
     * Must be called before setContentView: a theme applied afterwards reaches
     * only views that have not yet resolved their attributes, so already
     * inflated views would keep the base palette.
     */
    fun apply(activity: Activity) {
        val theme = themeFor(AppearanceStore.get(activity).load())
        if (theme != NO_OVERLAY) activity.setTheme(theme)
    }

    /** Sentinel for "the base theme is already correct". */
    const val NO_OVERLAY = 0
}
