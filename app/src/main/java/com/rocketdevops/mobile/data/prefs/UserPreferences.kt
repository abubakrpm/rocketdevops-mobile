package com.rocketdevops.mobile.data.prefs

import com.rocketdevops.mobile.ui.theme.ThemeMode

/**
 * Text size chosen in Settings. Multiplies the phone's own font size, so someone who already
 * uses large system text still gets a bigger step (spec: works at font scale 1.3).
 */
enum class FontSize(val label: String, val scale: Float) {
    Small("Small", 0.9f),
    Medium("Medium", 1.0f),
    Large("Large", 1.15f),
}

/** The learner's settings. Small key-value data, so DataStore fits better than a Room table. */
data class UserPreferences(
    val name: String = "",
    val onboarded: Boolean = false,       // the Welcome screen was answered (name given or skipped)
    val themeMode: ThemeMode = ThemeMode.System,
    val fontSize: FontSize = FontSize.Medium,
    val quizTimer: Boolean = false,       // 30 s per question; OFF by default (spec)
) {
    /** "Hi, Muhib" or "Hi there" when no name was given. */
    val greeting: String get() = if (name.isBlank()) "Hi there" else "Hi, ${firstName(name)}"
    val initials: String get() = initialsOf(name)
}

const val MAX_NAME_LENGTH = 24

/** Trims spaces, collapses double spaces and limits the length so the header never overflows. */
fun cleanName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH).trim()

fun firstName(name: String): String = cleanName(name).substringBefore(' ')

/** "Abu Bakr" -> "AB", "Muhib" -> "M", "" -> "" (the avatar then shows a person icon). */
fun initialsOf(name: String): String =
    cleanName(name).split(' ').filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
