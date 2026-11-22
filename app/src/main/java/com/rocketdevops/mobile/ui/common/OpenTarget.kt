package com.rocketdevops.mobile.ui.common

import com.rocketdevops.mobile.data.progress.ItemKind

/** Where a tap should go. Screens emit this; AppNavHost turns it into a route. */
sealed interface OpenTarget {
    data class Lesson(val id: String) : OpenTarget
    data class Lab(val id: String) : OpenTarget
    data class Quiz(val id: String) : OpenTarget
    data class Track(val id: String) : OpenTarget
    data object LabsTab : OpenTarget

    companion object {
        fun of(kind: ItemKind, id: String): OpenTarget = when (kind) {
            ItemKind.Lesson -> Lesson(id)
            ItemKind.Lab -> Lab(id)
            ItemKind.Quiz -> Quiz(id)
        }
    }
}

/** "About 1h 20m left", "About 25 min left" or "Track complete". */
fun formatTimeLeft(minutes: Int): String = when {
    minutes <= 0 -> "Track complete"
    minutes < 60 -> "About $minutes min left"
    minutes % 60 == 0 -> "About ${minutes / 60}h left"
    else -> "About ${minutes / 60}h ${minutes % 60}m left"
}

/** "1 lab", "5 labs". */
fun plural(count: Int, one: String, many: String = "${one}s"): String = "$count ${if (count == 1) one else many}"

fun percentText(fraction: Float): String = "${(fraction.coerceIn(0f, 1f) * 100).toInt()}%"
