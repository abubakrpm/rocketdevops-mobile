package com.rocketdevops.mobile.ui.cheats

import com.rocketdevops.mobile.data.content.CheatCategory
import com.rocketdevops.mobile.data.content.CheatEntry

/** The category chips, in order. `category` is null for "All". */
enum class CheatFilter(val label: String, val category: CheatCategory?) {
    All("All", null),
    Linux("Linux", CheatCategory.LINUX),
    Git("Git", CheatCategory.GIT),
    Docker("Docker", CheatCategory.DOCKER),
}

/**
 * Search and category filter for the cheat sheet. Pure (no Android), so it is unit tested.
 *
 * - Case-insensitive, extra spaces ignored.
 * - Every word must match somewhere: command, description, example or a flag
 *   ("list hidden" finds ls because of the -a flag's meaning).
 * - Entries whose command or example matches come first, in content order; then the rest.
 */
fun filterCheats(entries: List<CheatEntry>, query: String, filter: CheatFilter): List<CheatEntry> {
    val inCategory = entries.filter { filter.category == null || it.category == filter.category }
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return inCategory

    val matches = inCategory.filter { entry ->
        val text = searchText(entry)
        words.all { it in text }
    }
    // sortedBy is stable, so content order is kept inside each group.
    return matches.sortedBy { entry ->
        val commandText = "${entry.command} ${entry.example}".lowercase()
        if (words.all { it in commandText }) 0 else 1
    }
}

/** How many entries each chip would show for the current search, e.g. "Git 2". */
fun countsPerFilter(entries: List<CheatEntry>, query: String): Map<CheatFilter, Int> =
    CheatFilter.entries.associateWith { filterCheats(entries, query, it).size }

private fun searchText(entry: CheatEntry): String = buildString {
    append(entry.command).append(' ')
    append(entry.description).append(' ')
    append(entry.example).append(' ')
    entry.commonFlags.forEach { append(it.flag).append(' ').append(it.meaning).append(' ') }
}.lowercase()
