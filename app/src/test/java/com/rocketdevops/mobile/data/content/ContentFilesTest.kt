package com.rocketdevops.mobile.data.content

import com.rocketdevops.mobile.ui.labs.buildLabSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the REAL files in app/src/main/assets/content.
 * If anyone adds a lesson, quiz or lab with a mistake, this test fails and prints every problem.
 */
class ContentFilesTest {

    private fun loadCatalog(): ContentCatalog =
        when (val result = ContentLoader(FileContentSource(realContentRoot())).load()) {
            is ContentLoadResult.Success -> result.catalog
            is ContentLoadResult.Failure ->
                throw AssertionError("Content has problems:\n" + result.problems.joinToString("\n"))
        }

    @Test
    fun allContentFilesParseAndPassValidation() {
        loadCatalog() // fails with the full problem list if anything is wrong
    }

    @Test
    fun hasTheThreeTracksInOrder() {
        assertEquals(listOf("linux", "git", "docker"), loadCatalog().tracks.map { it.id })
    }

    @Test
    fun everyTrackHasLessonsAQuizAndLabs() {
        for (track in loadCatalog().tracks) {
            val modules = track.modules
            assertTrue("${track.id} has no lessons", modules.any { it.lessons.isNotEmpty() })
            assertTrue("${track.id} has no quiz", modules.any { it.quiz != null })
            assertTrue("${track.id} has no labs", modules.any { it.labs.isNotEmpty() })
        }
    }

    @Test
    fun lookupsFindItemsAndTheirParents() {
        val catalog = loadCatalog()
        val lab = requireNotNull(catalog.lab("docker-lab-first-container"))
        assertEquals("docker", lab.track.id)
        assertEquals("docker-run", lab.module.id)

        assertEquals("linux", catalog.lesson("linux-filesystem")?.track?.id)
        assertEquals("git", catalog.quiz("git-basics-quiz")?.track?.id)
        assertNull(catalog.lab("does-not-exist"))
    }

    @Test
    fun labsAreNumberedWithinEachTrack() {
        val catalog = loadCatalog()
        for (track in catalog.tracks) {
            val numbers = catalog.labsOfTrack(track.id).map { it.numberInTrack }
            assertEquals("lab numbers in ${track.id}", (1..numbers.size).toList(), numbers)
        }
    }

    @Test
    fun labsScreenGroupsEveryLabUnderItsTrack() {
        val catalog = loadCatalog()
        val sections = buildLabSections(catalog)
        assertEquals(catalog.tracks.map { it.id }, sections.map { it.trackId })
        assertEquals(catalog.allLabs.size, sections.sumOf { it.labs.size })
        assertTrue(sections.first().labs.first().subtitle.startsWith("Lab 1,"))
    }

    @Test
    fun cheatSheetCoversEveryCategory() {
        val categories = loadCatalog().cheatSheet.map { it.category }.toSet()
        assertEquals(CheatCategory.entries.toSet(), categories)
    }

    /**
     * The amounts the project spec asks for (Part 2, "Content writing"), so content can
     * only grow. Linux: about 6 lessons, 20 questions, 6 labs. Git and Docker: about 5, 15, 5.
     */
    @Test
    fun meetsTheSpecContentTargets() {
        val catalog = loadCatalog()
        val targets = mapOf("linux" to Triple(6, 20, 6), "git" to Triple(5, 15, 5), "docker" to Triple(5, 15, 5))
        for (track in catalog.tracks) {
            val (lessons, questions, labs) = targets.getValue(track.id)
            val modules = track.modules
            assertTrue("${track.id} lessons", modules.sumOf { it.lessons.size } >= lessons)
            assertTrue("${track.id} questions", modules.sumOf { it.quiz?.questions?.size ?: 0 } >= questions)
            assertTrue("${track.id} labs", modules.sumOf { it.labs.size } >= labs)
        }
        assertTrue("cheat sheet should have 60 to 80 entries", catalog.cheatSheet.size in 60..80)
    }
}
