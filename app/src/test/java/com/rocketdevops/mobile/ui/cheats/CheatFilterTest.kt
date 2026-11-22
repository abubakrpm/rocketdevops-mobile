package com.rocketdevops.mobile.ui.cheats

import com.rocketdevops.mobile.data.content.CheatCategory
import com.rocketdevops.mobile.data.content.CheatEntry
import com.rocketdevops.mobile.data.content.CheatFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheatFilterTest {

    private val entries = listOf(
        CheatEntry("ls", "ls", "List files in a folder", "ls -la", listOf(CheatFlag("-a", "include hidden files")), CheatCategory.LINUX),
        CheatEntry("grep", "grep", "Search inside files", "grep -r \"text\" .", emptyList(), CheatCategory.LINUX),
        CheatEntry("git-log", "git log", "Show commit history", "git log --oneline", emptyList(), CheatCategory.GIT),
        CheatEntry("docker-ps", "docker ps", "List running containers", "docker ps -a", emptyList(), CheatCategory.DOCKER),
    )

    private fun ids(query: String, filter: CheatFilter = CheatFilter.All) = filterCheats(entries, query, filter).map { it.id }

    @Test
    fun emptySearchShowsTheWholeCategory() {
        assertEquals(4, ids("").size)
        assertEquals(listOf("ls", "grep"), ids("   ", CheatFilter.Linux))
        assertEquals(listOf("docker-ps"), ids("", CheatFilter.Docker))
    }

    @Test
    fun searchIgnoresCaseAndSpaces() {
        assertEquals(listOf("git-log"), ids("  GIT   Log "))
    }

    @Test
    fun everyWordMustMatchSomewhereIncludingFlags() {
        assertEquals(listOf("ls"), ids("list hidden"))   // "hidden" is only in the -a flag's meaning
        assertTrue(ids("list nothing-like-this").isEmpty())
    }

    @Test
    fun commandMatchesComeBeforeDescriptionMatches() {
        // "ps" is in docker ps (command) and in nothing else's command
        assertEquals(listOf("docker-ps"), ids("ps"))
        // "list": ls (description) and docker ps (description); both description-only, content order kept
        assertEquals(listOf("ls", "docker-ps"), ids("list"))
        // "files": grep's description and ls's description, but "files" appears in no command
        assertEquals(listOf("ls", "grep"), ids("files"))
    }

    @Test
    fun categoryAndSearchCombine() {
        assertEquals(listOf("ls"), ids("list", CheatFilter.Linux))
        assertTrue(ids("docker", CheatFilter.Git).isEmpty())
    }

    @Test
    fun chipCountsFollowTheSearch() {
        val counts = countsPerFilter(entries, "list")
        assertEquals(2, counts[CheatFilter.All])
        assertEquals(1, counts[CheatFilter.Linux])
        assertEquals(0, counts[CheatFilter.Git])
        assertEquals(1, counts[CheatFilter.Docker])
    }
}
