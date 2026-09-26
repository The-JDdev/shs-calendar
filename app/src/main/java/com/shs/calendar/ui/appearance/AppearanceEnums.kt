package com.shs.calendar.ui.appearance

/**
 * M10 theming vocabulary. Names are the persisted form, so values must not
 * be renamed without a migration path.
 */
enum class Theme {
    /**
     * Follow the system setting.
     *
     * M10: the app is no longer dark-only. The light palette is the default
     * values/ slot and the deep-navy identity is qualified into values-night/,
     * so this follows the device's day/night setting across both palettes.
     */
    SYSTEM_DARK,
    DARK,
    /** True-black surfaces for OLED panels. */
    AMOLED,
    LIGHT;

    companion object {
        fun from(raw: String?): Theme =
            entries.firstOrNull { it.name == raw } ?: SYSTEM_DARK
    }
}

enum class Accent(val hex: String) {
    CYAN("#22D3EE"),
    TEAL("#2DD4BF"),
    INDIGO("#818CF8"),
    PURPLE("#A78BFA"),
    MAGENTA("#F472B6"),
    AMBER("#FBBF24");

    companion object {
        fun from(raw: String?): Accent =
            entries.firstOrNull { it.name == raw } ?: CYAN
    }
}

/**
 * In-app text size. NORMAL deliberately means "inherit the system scale"
 * rather than "100% of base", so the app never overrides a user's
 * accessibility font preference.
 */
enum class FontScale {
    SMALL,
    NORMAL,
    LARGE,
    EXTRA_LARGE;

    companion object {
        fun from(raw: String?): FontScale =
            entries.firstOrNull { it.name == raw } ?: NORMAL
    }
}

/**
 * The AppCompat night-mode constants, mirrored here so [AppearanceOptions]
 * stays pure and unit-testable. Values match
 * androidx.appcompat.app.AppCompatDelegate; referenced by name only to
 * avoid an import in a file that must run under plain JUnit.
 */
object AppCompatDelegateCompat {
    const val MODE_NIGHT_FOLLOW_SYSTEM = -1
    const val MODE_NIGHT_NO = 1
    const val MODE_NIGHT_YES = 2
}
