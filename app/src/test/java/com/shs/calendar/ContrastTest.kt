package com.shs.calendar

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * M10 accessibility guard: asserts the shipped palette's text pairs clear
 * WCAG 2.1 AA (4.5:1) for normal text.
 *
 * This exists because two pairs were silently below AA and nothing in the
 * build noticed: shs_text_muted on shs_night measured 3.88:1, and
 * shs_text_primary on shs_cyan_deep (the theme's onPrimaryContainer pair)
 * measured 3.46:1. Both are now corrected. Asserting the *maths* rather than
 * the hex literals means a later palette edit that breaks contrast fails
 * here instead of shipping.
 */
class ContrastTest {

    @Test
    fun mutedTextClearsAAOnAppBackground() {
        assertAtLeastAa(NIGHT, MUTED, "muted/hint text on app background")
    }

    @Test
    fun bodyTextClearsAAOnAppBackground() {
        assertAtLeastAa(NIGHT, TEXT_PRIMARY, "body text on app background")
    }

    @Test
    fun secondaryTextClearsAAOnAppBackground() {
        assertAtLeastAa(NIGHT, TEXT_SECONDARY, "secondary text on app background")
    }

    @Test
    fun bodyTextClearsAAOnCard() {
        assertAtLeastAa(SURFACE, TEXT_PRIMARY, "body text on card")
    }

    @Test
    fun secondaryTextClearsAAOnCard() {
        assertAtLeastAa(SURFACE, TEXT_SECONDARY, "secondary text on card")
    }

    @Test
    fun bodyTextClearsAAOnRaisedSurface() {
        assertAtLeastAa(SURFACE_HIGH, TEXT_PRIMARY, "text on raised/input surface")
    }

    private fun assertAtLeastAa(background: String, foreground: String, desc: String) {
        val ratio = contrastRatio(palette()[foreground]!!, palette()[background]!!)
        assertTrue(
            "$desc: #$foreground on #$background = %.2f:1, below WCAG AA 4.5:1".format(ratio),
            ratio >= MIN_AA_NORMAL_TEXT
        )
    }

    /**
     * Reads the shipped colours.xml rather than duplicating the literals here.
     * A copy would still pass if someone edited the resource back to a failing
     * value, which is the exact regression this test exists to catch.
     */
    private fun palette(): Map<String, String> {
        val file = File("src/main/res/values/colors.xml")
        assertTrue("colors.xml not found at ${file.absolutePath}", file.exists())
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
        val hi = maxOf(a, b)
        val lo = minOf(a, b)
        return (hi + 0.05) / (lo + 0.05)
    }

    /**
     * Every accent overlay pairs a _deep container with shs_text_primary as
     * its on-container. shs_teal_deep was 3.52:1 and shipped that way, so
     * each pair is now asserted rather than assumed.
     */
    @Test
    fun accentContainersClearAaOnPrimaryText() {
        for (accent in ACCENT_CONTAINERS) {
            assertAtLeastAa(TEXT_PRIMARY, accent, "on-container text on $accent")
        }
    }

    /**
     * Solid accent is used as colorPrimary, whose on-colour is
     * shs_text_on_accent. The overlays re-tint colorPrimary/Secondary, so a
     * light accent must stay legible under that dark ink too.
     */
    @Test
    fun solidAccentsClearAaOnAccentInk() {
        for (accent in ACCENTS) {
            assertAtLeastAa(accent, TEXT_ON_ACCENT, "accent text on $accent")
        }
    }

    /**
     * Accent.hex duplicates the palette as a string. If a palette edit and an
     * enum edit disagree, the overlay would tint with a colour nothing else on
     * screen uses, so the two are pinned to the same value.
     */
    @Test
    fun accentEnumHexMatchesPalette() {
        val src = File("src/main/java/com/shs/calendar/ui/appearance/AppearanceEnums.kt")
        assertTrue("AppearanceEnums.kt not found at ${src.absolutePath}", src.exists())
        val colours = palette()
        val declared = Regex("""([A-Z_]+)\("#([0-9A-Fa-f]{6})"\)""").findAll(src.readText())
        var found = 0
        for (m in declared) {
            val (name, hex) = m.destructured
            val resource = "shs_${name.lowercase()}"
            // colors.xml textContent already carries the leading '#'; the
            // enum's hex does not, so both sides are normalised before
            // comparing or the two can never match.
            val fromPalette = colours[resource]?.removePrefix("#")
            assertTrue(
                "Accent.$name (#$hex) does not match $resource (#${fromPalette ?: "missing"})",
                fromPalette.equals(hex, ignoreCase = true)
            )
            found++
        }
        assertTrue("expected 6 Accent entries, found $found", found == 6)
    }

    private companion object {
        const val MIN_AA_NORMAL_TEXT = 4.5
        const val NIGHT = "shs_night"
        const val SURFACE = "shs_surface"
        const val SURFACE_HIGH = "shs_surface_high"
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
    }
}
