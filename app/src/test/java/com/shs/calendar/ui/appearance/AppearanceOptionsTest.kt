package com.shs.calendar.ui.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M10: appearance rules. Pure logic, no Android framework, so these run in
 * the sandbox's plain JUnit setup.
 */
class AppearanceOptionsTest {

    @Test
    fun amoledForcesTrueBlackSurfaces() {
        assertTrue(AppearanceOptions(theme = Theme.AMOLED).isAmoled)
    }

    @Test
    fun normalThemeIsNotAmoled() {
        assertFalse(AppearanceOptions().isAmoled)
    }

    @Test
    fun systemDarkFollowsTheSystemSetting() {
        assertEquals(
            AppCompatDelegateCompat.MODE_NIGHT_FOLLOW_SYSTEM,
            AppearanceOptions().nightMode
        )
    }

    @Test
    fun lightThemeRequestsNightModeNo() {
        assertEquals(
            AppCompatDelegateCompat.MODE_NIGHT_NO,
            AppearanceOptions(theme = Theme.LIGHT).nightMode
        )
    }

    @Test
    fun defaultRespectsSystemFontScale() {
        // A user who has already set a large system font must not be
        // overridden by the in-app default.
        assertTrue(AppearanceOptions().respectsSystemFontScale)
    }

    @Test
    fun explicitScalesDoNotClaimToRespectTheSystem() {
        assertFalse(AppearanceOptions(fontScale = FontScale.LARGE).respectsSystemFontScale)
        assertFalse(AppearanceOptions(fontScale = FontScale.EXTRA_LARGE).respectsSystemFontScale)
    }

    @Test
    fun fontScaleFactorsAreOrdered() {
        val small = AppearanceOptions(fontScale = FontScale.SMALL).fontScaleFactor
        val normal = AppearanceOptions(fontScale = FontScale.NORMAL).fontScaleFactor
        val large = AppearanceOptions(fontScale = FontScale.LARGE).fontScaleFactor
        val xl = AppearanceOptions(fontScale = FontScale.EXTRA_LARGE).fontScaleFactor
        assertTrue(small < normal)
        assertTrue(normal < large)
        assertTrue(large < xl)
    }

    @Test
    fun unknownPersistedNamesFallBackInsteadOfThrowing() {
        // A downgrade or a hand-edited pref must not crash the launch path.
        assertEquals(Theme.SYSTEM_DARK, Theme.from("NOT_A_THEME"))
        assertEquals(Theme.SYSTEM_DARK, Theme.from(null))
        assertEquals(Accent.CYAN, Accent.from("NOT_AN_ACCENT"))
        assertEquals(FontScale.NORMAL, FontScale.from("NOT_A_SCALE"))
    }

    @Test
    fun storedNamesRoundTrip() {
        for (t in Theme.entries) assertEquals(t, Theme.from(t.name))
        for (a in Accent.entries) assertEquals(a, Accent.from(a.name))
        for (f in FontScale.entries) assertEquals(f, FontScale.from(f.name))
    }

    @Test
    fun everyAccentIsAValidSixDigitHex() {
        // The picker renders these directly; a malformed value would throw
        // inside Color.parseColor at runtime.
        for (a in Accent.entries) {
            assertTrue("${a.name} = ${a.hex}", a.hex.matches(Regex("^#[0-9A-Fa-f]{6}$")))
        }
    }
}
