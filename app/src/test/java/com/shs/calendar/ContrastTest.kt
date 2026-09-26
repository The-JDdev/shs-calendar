package com.shs.calendar

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * M10 accessibility guard: asserts the SHIPPED palette's text pairs clear
 * WCAG 2.1 AA (4.5:1) for normal text.
 *
 * This exists because two pairs were silently below AA and nothing in the
 * build noticed: shs_text_muted on shs_night measured 3.88:1, and
 * shs_text_primary on shs_cyan_deep (the theme's onPrimaryContainer pair)
 * measured 3.46:1. Both are corrected.
 *
 * M10 LIGHT/AMOLED: every assertion now runs against BOTH palettes - the
 * light one in values/ and the dark identity in values-night/. The helper
 * used to read a single hardcoded path, so when the identity moved to
 * values-night/ the dark palette would have gone entirely unasserted while
 * the test still reported green: a check that passes while being blind. The
 * path is a parameter of every assertion for that reason.
 *
 * Asserting the *maths* against the parsed resource, rather than hex literals
 * copied here, means a later palette edit that breaks contrast fails here
 * instead of shipping.
 */
class ContrastTest {

    @Test fun mutedTextClearsAaOnAppBackground() = forEachPalette { p, d ->
        assertAtLeastAa(p, d, NIGHT, MUTED, "muted/hint text on app background")
    }

    @Test fun bodyTextClearsAaOnAppBackground() = forEachPalette { p, d ->
        assertAtLeastAa(p, d, NIGHT, TEXT_PRIMARY, "body text on app background")
    }

    @Test fun secondaryTextClearsAaOnAppBackground() = forEachPalette { p, d ->
        assertAtLeastAa(p, d, NIGHT, TEXT_SECONDARY, "secondary text on app background")
    }

    @Test fun bodyTextClearsAaOnCard() = forEachPalette { p, d ->
        assertAtLeastAa(p, d, SURFACE, TEXT_PRIMARY, "body text on card")
    }

    @Test fun secondaryTextClearsAaOnCard() = forEachPalette { p, d ->
        assertAtLeastAa(p, d, SURFACE, TEXT_SECONDARY, "secondary text on card")
    }

    @Test fun bodyTextClearsAaOnRaisedSurface() = forEachPalette { p, d ->
        assertAtLeastAa(p, d, SURFACE_HIGH, TEXT_PRIMARY, "text on raised/input surface")
    }

    /**
     * The AMOLED overlay forces true black, so text must clear AA there too.
     * Only the dark palette offers AMOLED, so this reads that one alone.
     */
    @Test fun bodyTextClearsAaOnAmoledSurfaces() {
        val p = paletteAt(NIGHT_VALUES)
        assertAtLeastAa(p, "AMOLED", NIGHT_AMOLED, TEXT_PRIMARY, "text on AMOLED background")
        assertAtLeastAa(p, "AMOLED", SURFACE_AMOLED, TEXT_PRIMARY, "text on AMOLED surface")
    }

    /**
     * Every accent overlay pairs a _deep container with shs_text_primary as
     * its on-container. shs_teal_deep was 3.52:1 and shipped that way.
     *
     * The polarity inverts between palettes - deep under light text at night,
     * a pale tint under dark text in light - but the *pair* is the same, so one
     * assertion covers both.
     */
    @Test fun accentContainersClearAaOnPrimaryText() = forEachPalette { p, d ->
        for (container in ACCENT_CONTAINERS) {
            assertAtLeastAa(p, d, TEXT_PRIMARY, container, "on-container text on $container")
        }
    }

    /**
     * Solid accent is used as colorPrimary, whose on-colour is
     * shs_text_on_accent: near-white in light, near-black at night. Both
     * polarities are asserted, so an accent legible on a dark surface cannot
     * ship illegible on a light one.
     */
    @Test fun solidAccentsClearAaOnAccentInk() = forEachPalette { p, d ->
        for (accent in ACCENTS) {
            assertAtLeastAa(p, d, accent, TEXT_ON_ACCENT, "accent text on $accent")
        }
    }

    /**
     * The semantic and calendar accents are used as *text* (holiday names,
     * Bengali day numbers, Hijri dates), so each must clear AA on the app
     * background in both palettes.
     */
    @Test fun semanticAndCalendarAccentsClearAaOnBackground() = forEachPalette { p, d ->
        for (name in SEMANTIC_TEXT) {
            assertAtLeastAa(p, d, NIGHT, name, "$name text on app background")
        }
    }

    /**
     * Accent.hex duplicates the palette as a string; if the enum and the
     * palette disagree, something tints with a colour nothing else uses.
     *
     * Pinned to the NIGHT palette deliberately. Accent.hex records the
     * deep-navy identity, and values/ is now the light palette where the same
     * role name holds a darker, AA-safe value.
     */
    @Test fun accentEnumHexMatchesNightPalette() {
        val src = File("src/main/java/com/shs/calendar/ui/appearance/AppearanceEnums.kt")
        assertTrue("AppearanceEnums.kt not found at ${src.absolutePath}", src.exists())
        val colours = paletteAt(NIGHT_VALUES)
        val declared = Regex("""([A-Z_]+)\("#([0-9A-Fa-f]{6})"\)""").findAll(src.readText())
        var found = 0
        for (m in declared) {
            val (name, hex) = m.destructured
            val fromPalette = colours["shs_${name.lowercase()}"]?.removePrefix("#")
            assertTrue(
                "Accent.$name (#$hex) does not match the night shs_${name.lowercase()} " +
                    "(#${fromPalette ?: "missing"})",
                fromPalette.equals(hex, ignoreCase = true)
            )
            found++
        }
        assertTrue("expected 6 Accent entries, found $found", found == 6)
    }

    /**
     * Every colour the app names in Java/Kotlin must exist in BOTH palettes.
     *
     * A name defined in only one configuration compiles, and then resolves
     * for one user and dangles for the other — a defect no compiler in this
     * project would report, and one that surfaces as a crash or an unstyled
     * screen on exactly the users the theme options exist for.
     */
    @Test fun everyColourNamedInCodeExistsInBothPalettes() {
        val light = paletteAt(LIGHT_VALUES)
        val night = paletteAt(NIGHT_VALUES)
        val srcDir = File("src/main/java")
        assertTrue("src/main/java not found at ${srcDir.absolutePath}", srcDir.exists())
        val pattern = Regex("""R\.color\.(shs_[A-Za-z0-9_]+)""")
        var checked = 0
        srcDir.walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            pattern.findAll(f.readText()).forEach { m ->
                val name = m.groupValues[1]
                assertTrue("$name (${f.name}) missing from values/colors.xml", light.containsKey(name))
                assertTrue("$name (${f.name}) missing from values-night/colors.xml", night.containsKey(name))
                checked++
            }
        }
        assertTrue("expected to find R.color references, found $checked", checked > 0)
    }

    /**
     * Light and night must define exactly the same colour names, or a
     * reference resolves in one configuration and dangles in the other. This
     * is the shape check: a name present only in light would be a defect for
     * exactly the user in the other mode.
     */
    @Test fun bothPalettesDefineTheSameNames() {
        val light = paletteAt(LIGHT_VALUES).keys
        val night = paletteAt(NIGHT_VALUES).keys
        assertTrue("only in values/: ${light - night}", (light - night).isEmpty())
        assertTrue("only in values-night/: ${night - light}", (night - light).isEmpty())
    }

    // --- helpers ---

    private inline fun forEachPalette(body: (Map<String, String>, String) -> Unit) {
        body(paletteAt(LIGHT_VALUES), "values/")
        body(paletteAt(NIGHT_VALUES), "values-night/")
    }

    private fun assertAtLeastAa(
        p: Map<String, String>, which: String,
        background: String, foreground: String, desc: String
    ) {
        val bg = p[background] ?: error("[$which] $background missing from palette")
        val fg = p[foreground] ?: error("[$which] $foreground missing from palette")
        val ratio = contrastRatio(fg, bg)
        assertTrue(
            "[$which] $desc: #$foreground on #$background = %.2f:1, below WCAG AA 4.5:1".format(ratio),
            ratio >= MIN_AA_NORMAL_TEXT
        )
    }

    /**
     * Parses a colors.xml rather than duplicating the literals. A copy here
     * would still pass if someone edited the resource back to a failing value,
     * which is the exact regression this test exists to catch.
     */
    private fun paletteAt(path: String): Map<String, String> {
        val file = File(path)
        assertTrue("$path not found at ${file.absolutePath}", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("color")
        val out = HashMap<String, String>(nodes.length)
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            out[el.getAttribute("name")] = el.textContent.trim()
        }
        return out
    }

    /** WCAG 2.1 relative luminance. */
    private fun luminance(hex: String): Double {
        val v = hex.removePrefix("#")
        val channel = { s: String ->
            val c = s.toInt(16) / 255.0
            if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(v.substring(0, 2)) +
            0.7152 * channel(v.substring(2, 4)) +
            0.0722 * channel(v.substring(4, 6))
    }

    private fun contrastRatio(fg: String, bg: String): Double {
        val a = luminance(fg)
        val b = luminance(bg)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private companion object {
        const val MIN_AA_NORMAL_TEXT = 4.5
        const val LIGHT_VALUES = "src/main/res/values/colors.xml"
        const val NIGHT_VALUES = "src/main/res/values-night/colors.xml"
        const val NIGHT = "shs_night"
        const val SURFACE = "shs_surface"
        const val SURFACE_HIGH = "shs_surface_high"
        const val NIGHT_AMOLED = "shs_night_amoled"
        const val SURFACE_AMOLED = "shs_surface_amoled"
        const val TEXT_PRIMARY = "shs_text_primary"
        const val TEXT_SECONDARY = "shs_text_secondary"
        const val MUTED = "shs_text_muted"
        const val TEXT_ON_ACCENT = "shs_text_on_accent"
        val ACCENTS = listOf(
            "shs_cyan", "shs_teal", "shs_indigo",
            "shs_purple", "shs_magenta", "shs_amber"
        )
        val ACCENT_CONTAINERS = listOf(
            "shs_cyan_deep", "shs_teal_deep", "shs_indigo_deep",
            "shs_purple_deep", "shs_magenta_deep", "shs_amber_deep"
        )
        val SEMANTIC_TEXT = listOf(
            "shs_success", "shs_danger", "shs_warning", "shs_orange",
            "shs_bengali_green", "shs_hijri_blue"
        )
    }
}
