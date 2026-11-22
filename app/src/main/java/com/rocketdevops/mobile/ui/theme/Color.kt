package com.rocketdevops.mobile.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * COLOUR TOKENS
 * -------------
 * This is the ONLY file in the app that contains hex colour values.
 * Screens and components read colours from MaterialTheme.colorScheme or from
 * AppTheme.colors (the extra tokens Material 3 has no slot for).
 *
 * Contrast was checked against WCAG AA (4.5:1 for text). Three pairs from the
 * original palette failed, so three small tokens were added (marked "AA FIX").
 */

/** A semantic colour set: a strong base, a soft container, and readable text on that container. */
@Immutable
data class SemanticColor(
    val base: Color,
    val container: Color,
    val onContainer: Color,
)

/** Every brand token. One instance for light, one for dark. */
@Immutable
data class AppColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,          // cards
    val surfaceStrong: Color,    // chips, icon tiles
    val raised: Color,           // an element sitting ON a surface card (week-strip days, selected segment)
    val primary: Color,
    val onPrimary: Color,        // text and icons on a primary fill
    val primaryText: Color,      // violet text on background, surface or surfaceStrong
    val accent: Color,
    val ink: Color,              // main text
    val inkSecondary: Color,     // secondary text (do not place on surfaceStrong, see note below)
    val line: Color,             // hairlines, dividers, outlines, progress tracks
    val heroDark: Color,         // the one dark card per screen
    val onHero: Color,
    val heroTrack: Color,        // progress-bar track inside the hero card
    val chartBarMuted: Color,    // non-today bars in the "Last 7 days" chart
    val terminalBg: Color,
    val terminalText: Color,
    val terminalMuted: Color,
    val terminalPrompt: Color,
    val success: SemanticColor,
    val error: SemanticColor,
    val warning: SemanticColor,
    val info: SemanticColor,
)

// Terminal colours are identical in both themes (the terminal is always dark).
private val TerminalBg = Color(0xFF111111)
private val TerminalText = Color(0xFFE7E4F2)
private val TerminalMuted = Color(0xFF8D8AA0)
private val TerminalPrompt = Color(0xFFA872FB)

// Semantic base colours are shared; containers change per theme.
private val SuccessBase = Color(0xFF1F9D6B)
private val ErrorBase = Color(0xFFE5484D)
private val WarningBase = Color(0xFFF5A524)
private val InfoBase = Color(0xFF2F80ED)

val LightAppColors = AppColors(
    isDark = false,
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF5F0FF),
    surfaceStrong = Color(0xFFEAE0FF),
    raised = Color(0xFFFFFFFF),             // the white day chips in the mockup
    primary = Color(0xFF6954FF),
    onPrimary = Color(0xFFFFFFFF),          // 4.87:1 on primary, passes
    primaryText = Color(0xFF5B45F0),        // AA FIX: #6954FF on #F5F0FF was 4.36:1, this is 5.29:1
    accent = Color(0xFFA872FB),
    ink = Color(0xFF111111),
    inkSecondary = Color(0xFF6B6B76),       // 4.71:1 on surface. 4.16:1 on surfaceStrong, so never use it there.
    line = Color(0xFFECE8F5),
    heroDark = Color(0xFF111111),
    onHero = Color(0xFFFFFFFF),
    heroTrack = Color(0xFF2A2A33),          // taken from the mockup, not in the written spec
    chartBarMuted = Color(0xFFCFC5FF),      // taken from the mockup, not in the written spec
    terminalBg = TerminalBg,
    terminalText = TerminalText,
    terminalMuted = TerminalMuted,
    terminalPrompt = TerminalPrompt,
    success = SemanticColor(SuccessBase, Color(0xFFE6F6EF), Color(0xFF157A52)),
    error = SemanticColor(ErrorBase, Color(0xFFFDECEC), Color(0xFFB42318)),
    warning = SemanticColor(WarningBase, Color(0xFFFFF3E0), Color(0xFF9A5B00)),
    info = SemanticColor(InfoBase, Color(0xFFE8F1FD), Color(0xFF1B5BB5)),
)

val DarkAppColors = AppColors(
    isDark = true,
    background = Color(0xFF0E0E13),
    surface = Color(0xFF1A1826),
    surfaceStrong = Color(0xFF26233A),
    raised = Color(0xFF26233A),             // white chips would glare in dark mode
    primary = Color(0xFF8B7CFF),
    onPrimary = Color(0xFF0E0E13),          // AA FIX: white on #8B7CFF was 3.27:1, dark ink is 5.89:1
    primaryText = Color(0xFF8B7CFF),        // 5.34:1 on dark surface, passes
    accent = Color(0xFFA872FB),
    ink = Color(0xFFF2F1F7),
    inkSecondary = Color(0xFF9A98AB),
    line = Color(0xFF2A2838),
    heroDark = Color(0xFF07070B),
    onHero = Color(0xFFFFFFFF),
    heroTrack = Color(0xFF2A2A33),
    chartBarMuted = Color(0xFF3A3360),
    terminalBg = TerminalBg,
    terminalText = TerminalText,
    terminalMuted = TerminalMuted,
    terminalPrompt = TerminalPrompt,
    success = SemanticColor(SuccessBase, Color(0xFF11302A), Color(0xFF8FDDBE)),
    error = SemanticColor(ErrorBase, Color(0xFF3A1618), Color(0xFFFFB4B0)),
    warning = SemanticColor(WarningBase, Color(0xFF3A2A0E), Color(0xFFFFD28A)),
    info = SemanticColor(InfoBase, Color(0xFF14263F), Color(0xFF9CC4F7)),
)

/** Gives any composable access to the current AppColors via AppTheme.colors. */
val LocalAppColors = staticCompositionLocalOf { LightAppColors }
