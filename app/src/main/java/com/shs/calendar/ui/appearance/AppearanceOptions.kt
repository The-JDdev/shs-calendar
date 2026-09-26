package com.shs.calendar.ui.appearance

/**
 * M10 appearance options, kept as pure logic so the rules are testable
 * without a device. The project has no Robolectric, so anything that touches
 * resources or Views cannot be exercised in the sandbox — the decision logic
 * therefore lives here and the Activity only applies the result.
 *
 * Every field is a stable stored string rather than an enum ordinal, so
 * changing this file later cannot silently reinterpret existing preferences.
 */
data class AppearanceOptions(
    val theme: Theme = Theme.SYSTEM_DARK,
    val accent: Accent = Accent.CYAN,
    val fontScale: FontScale = FontScale.NORMAL,
    val localeTag: String = TAG_SYSTEM
) {

    /** The night-mode constant to hand to AppCompatDelegate. */
    val nightMode: Int
        get() = when (theme) {
            Theme.SYSTEM_DARK -> AppCompatDelegateCompat.MODE_NIGHT_FOLLOW_SYSTEM
            Theme.AMOLED -> AppCompatDelegateCompat.MODE_NIGHT_YES
            Theme.DARK -> AppCompatDelegateCompat.MODE_NIGHT_YES
            Theme.LIGHT -> AppCompatDelegateCompat.MODE_NIGHT_NO
        }

    /** True when surfaces should be true black rather than deep navy. */
    val isAmoled: Boolean get() = theme == Theme.AMOLED

    /**
     * True when the system font-scale setting should be respected untouched.
     * A user who has already chosen a large system font must not be
     * overridden by an in-app default, so NORMAL means "defer to the system".
     */
    val respectsSystemFontScale: Boolean get() = fontScale == FontScale.NORMAL

    /** The multiplier to apply over the system scale. */
    val fontScaleFactor: Float
        get() = when (fontScale) {
            FontScale.SMALL -> 0.9f
            FontScale.NORMAL -> 1.0f
            FontScale.LARGE -> 1.15f
            FontScale.EXTRA_LARGE -> 1.3f
        }

    companion object {
        /** Sentinel meaning "follow the device language", not a locale. */
        const val TAG_SYSTEM = "system"
    }
}
