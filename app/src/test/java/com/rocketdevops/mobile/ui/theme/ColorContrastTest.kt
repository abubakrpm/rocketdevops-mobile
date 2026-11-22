package com.rocketdevops.mobile.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quality gate from spec 1.6: every text colour must reach WCAG AA (4.5:1) on the
 * background it is used on, in BOTH themes. If someone edits Color.kt and breaks
 * readability, this test fails before the change ships.
 */
class ColorContrastTest {

    /** WCAG 2 contrast ratio: (lighter + 0.05) / (darker + 0.05). Ranges from 1 to 21. */
    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun assertReadable(name: String, text: Color, background: Color, minimum: Double = 4.5) {
        val ratio = contrast(text, background)
        assertTrue("$name is %.2f:1, needs %.1f:1".format(ratio, minimum), ratio >= minimum)
    }

    /** The text-on-background pairs the app actually uses. */
    private fun checkTheme(label: String, c: AppColors) {
        assertReadable("$label ink on background", c.ink, c.background)
        assertReadable("$label ink on surface", c.ink, c.surface)
        assertReadable("$label inkSecondary on background", c.inkSecondary, c.background)
        assertReadable("$label inkSecondary on surface", c.inkSecondary, c.surface)
        assertReadable("$label ink on raised", c.ink, c.raised)
        assertReadable("$label onPrimary on primary", c.onPrimary, c.primary)
        assertReadable("$label primaryText on background", c.primaryText, c.background)
        assertReadable("$label primaryText on surface", c.primaryText, c.surface)
        assertReadable("$label onHero on heroDark", c.onHero, c.heroDark)
        assertReadable("$label accent on heroDark", c.accent, c.heroDark)
        assertReadable("$label button text (background on ink)", c.background, c.ink)
        assertReadable("$label terminal text", c.terminalText, c.terminalBg)
        assertReadable("$label terminal muted", c.terminalMuted, c.terminalBg)
        assertReadable("$label terminal prompt", c.terminalPrompt, c.terminalBg)
        assertReadable("$label terminal error", c.error.base, c.terminalBg)
        listOf("success" to c.success, "error" to c.error, "warning" to c.warning, "info" to c.info)
            .forEach { (name, s) -> assertReadable("$label $name text on container", s.onContainer, s.container) }
        // Icons only need 3:1 (WCAG non-text contrast).
        assertReadable("$label primary icon on surfaceStrong", c.primary, c.surfaceStrong, minimum = 3.0)
    }

    @Test
    fun lightThemeMeetsWcagAA() = checkTheme("light", LightAppColors)

    @Test
    fun darkThemeMeetsWcagAA() = checkTheme("dark", DarkAppColors)
}
