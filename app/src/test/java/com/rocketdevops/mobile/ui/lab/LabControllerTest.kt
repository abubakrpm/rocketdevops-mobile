package com.rocketdevops.mobile.ui.lab

import com.rocketdevops.mobile.data.content.ContentCatalog
import com.rocketdevops.mobile.data.content.FsEntry
import com.rocketdevops.mobile.data.content.Lab
import com.rocketdevops.mobile.data.content.LabEnvironment
import com.rocketdevops.mobile.data.content.Module
import com.rocketdevops.mobile.data.content.Objective
import com.rocketdevops.mobile.data.content.ObjectiveCheck
import com.rocketdevops.mobile.data.content.Track
import com.rocketdevops.mobile.data.content.TrackIcon
import com.rocketdevops.mobile.ui.components.TerminalLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabControllerTest {

    private val lab = Lab(
        id = "linux-lab-navigate",
        title = "Find your way around",
        scenario = "Move around.",
        startingState = LabEnvironment(files = listOf(FsEntry("/home/student/projects", directory = true))),
        objectives = listOf(
            Objective("where", "Print location", ObjectiveCheck.CommandMatches("^pwd$")),
            Objective("move", "Enter projects", ObjectiveCheck.CwdIs("/home/student/projects")),
        ),
        hints = listOf("First hint", "Second hint", "Third hint"),
        solution = listOf("pwd", "cd projects"),
    )
    private val track = Track("linux", "Linux basics", "About.", TrackIcon.TERMINAL, listOf(Module("m", "Module", labs = listOf(lab))))
    private val ref = ContentCatalog.LabRef(track, track.modules.first(), lab, numberInTrack = 1)

    @Test
    fun captionAndStartingState() {
        val state = LabController(ref).snapshot()
        assertEquals("Linux basics, Lab 1", state.caption)
        assertEquals("lab ~ $", state.prompt)
        assertTrue(state.lines.isEmpty())
        assertTrue(state.objectives.none { it.done })
    }

    @Test
    fun commandLinesKeepThePromptTheyWereTypedWith() {
        val c = LabController(ref)
        c.submit("cd projects")
        c.submit("pwd")
        val commands = c.snapshot().lines.filter { it.kind == TerminalLine.Kind.Command }
        assertEquals("lab ~ $", commands[0].prompt)
        assertEquals("lab ~/projects $", commands[1].prompt)
        assertEquals("/home/student/projects", c.snapshot().lines.last().text)
    }

    @Test
    fun errorsAreMarkedAsErrors() {
        val c = LabController(ref)
        c.submit("dokcer ps")
        assertEquals(TerminalLine.Kind.Error, c.snapshot().lines[1].kind)
    }

    @Test
    fun finishingTheLabShowsSuccessOnce() {
        val c = LabController(ref)
        c.submit("pwd")
        assertFalse(c.snapshot().showSuccess)
        c.submit("cd projects")
        assertTrue(c.snapshot().showSuccess)
        assertTrue(c.snapshot().isComplete)
        c.dismissSuccess()
        c.submit("pwd")
        assertFalse(c.snapshot().showSuccess)
    }

    @Test
    fun restoringReplaysWithoutTheSuccessScreen() {
        val restored = LabController(ref, restoredInputs = listOf("pwd", "cd projects"), restoredHintsShown = 2)
        val state = restored.snapshot()
        assertTrue(state.isComplete)
        assertFalse(state.showSuccess) // do not celebrate again after the app was restored
        assertEquals(listOf("First hint", "Second hint"), state.hints)
        assertEquals(3, state.lines.size) // the transcript is rebuilt too: pwd, its output, cd projects
    }

    @Test
    fun clearEmptiesTheTranscriptButIsStillSaved() {
        val c = LabController(ref)
        c.submit("pwd")
        c.submit("clear")
        assertTrue(c.snapshot().lines.isEmpty())
        assertEquals(listOf("pwd", "clear"), c.typed)
    }

    @Test
    fun hintsRevealOneAtATimeUpToThree() {
        val c = LabController(ref)
        c.showNextHint()
        assertEquals(listOf("First hint"), c.snapshot().hints)
        assertTrue(c.snapshot().hintsVisible)
        c.hideHints()
        c.showNextHint() // re-opening the card shows the same hints, it does not skip one
        assertEquals(1, c.snapshot().hints.size)
        repeat(5) { c.showNextHint() }
        assertEquals(3, c.snapshot().hints.size)
    }

    @Test
    fun historyKeysWalkThroughCommands() {
        val c = LabController(ref)
        c.submit("pwd")
        c.submit("ls")
        assertEquals("ls", c.previousCommand())
        assertEquals("pwd", c.previousCommand())
        assertEquals("pwd", c.previousCommand()) // stays at the oldest
        assertEquals("ls", c.nextCommand())
        assertEquals("", c.nextCommand())        // past the newest: empty line
    }

    @Test
    fun tabWithSeveralMatchesListsThem() {
        val c = LabController(ref)
        val completion = c.complete("c")
        assertTrue(completion.candidates.size > 1)
        assertTrue(c.snapshot().lines.last().text.contains("cd"))
        assertEquals("cd projects/", c.complete("cd pro").input)
    }

    @Test
    fun ctrlCShowsTheAbandonedLine() {
        val c = LabController(ref)
        c.cancel("rm -rf something")
        assertEquals("rm -rf something^C", c.snapshot().lines.single().text)
        assertTrue(c.typed.isEmpty())
    }

    @Test
    fun resetClearsEverythingExceptHints() {
        val c = LabController(ref)
        c.showNextHint()
        c.submit("cd projects")
        c.reset()
        val state = c.snapshot()
        assertTrue(state.lines.isEmpty())
        assertTrue(state.objectives.none { it.done })
        assertEquals("lab ~ $", state.prompt)
        assertTrue(c.typed.isEmpty())
        assertEquals(1, state.hints.size)
    }
}
