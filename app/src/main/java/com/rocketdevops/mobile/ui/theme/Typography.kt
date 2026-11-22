package com.rocketdevops.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rocketdevops.mobile.R

/*
 * TYPOGRAPHY
 * ----------
 * Fonts are bundled in res/font (both SIL Open Font Licence, free to ship),
 * so text renders the same with no internet connection.
 * Only two weights exist in the whole app: Regular (400) and Medium (500).
 *
 * The spec's scale mapped onto Material 3 slots:
 *   display  26 Medium  -> headlineMedium
 *   title    20 Medium  -> titleLarge
 *   section  16 Medium  -> titleMedium
 *   (row title 14 Medium -> titleSmall)  list-row titles, the mockups use 14 Medium here
 *   body     14 Regular -> bodyMedium (and bodyLarge, which text fields use)
 *   label    13 Medium  -> labelLarge (buttons) and labelMedium (chips)
 *   caption  12 Regular -> bodySmall and labelSmall
 *   mono     12 Regular -> AppTextStyles.mono
 */

val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
)

// Line heights: about 1.25x for titles, 1.4x for body text (from the spec).
private val Display = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 32.sp)
private val Title = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 25.sp)
private val Section = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp)
private val RowTitle = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp)
private val Body = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
private val Label = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp)
private val Caption = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp)

/** Passed to MaterialTheme. Every slot is filled so no default Roboto style can leak into a screen. */
val AppTypography = Typography(
    displayLarge = Display,
    displayMedium = Display,
    displaySmall = Display,
    headlineLarge = Display,
    headlineMedium = Display,
    headlineSmall = Title,
    titleLarge = Title,
    titleMedium = Section,
    titleSmall = RowTitle,
    bodyLarge = Body,
    bodyMedium = Body,
    bodySmall = Caption,
    labelLarge = Label,
    labelMedium = Label,
    labelSmall = Caption,
)

/** Styles Material 3 has no slot for. */
object AppTextStyles {
    /** Terminal output, commands and code blocks. Taller line height so terminal output is easy to scan. */
    val mono = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 19.sp)

    /** Command names in the cheat sheet and quiz options. */
    val monoMedium = mono.copy(fontWeight = FontWeight.Medium)
}
