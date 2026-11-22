package com.rocketdevops.mobile.terminal

import com.rocketdevops.mobile.data.content.FsEntry
import com.rocketdevops.mobile.data.content.Lab
import com.rocketdevops.mobile.data.content.LabEnvironment
import com.rocketdevops.mobile.data.content.Objective
import com.rocketdevops.mobile.data.content.ObjectiveCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabSessionTest {

    private val lab = Lab(
        id = "test-lab",
        title = "Find your way",
        scenario = "Move around.",
        startingState = LabEnvironment(files = listOf(FsEntry("/home/student/projects", directory = true))),
        objectives = listOf(
            Objective("where", "Print location", ObjectiveCheck.CommandMatches("^pwd$")),
            Objective("move", "Enter projects", ObjectiveCheck.CwdIs("/home/student/projects")),
            Objective("notes", "Create notes", ObjectiveCheck.PathExists("/home/student/projects/notes", directory = true)),
        ),
        hints = listOf("1", "2", "3"),
        solution = listOf("pwd", "cd projects", "mkdir notes"),
    )

    @Test
    fun objectivesTurnGreenOneByOne() {
        val session = LabSession(lab)
        assertEquals(listOf("where"), session.submit("pwd").newlyCompleted.map { it.id })
        assertEquals(listOf("move"), session.submit("cd projects").newlyCompleted.map { it.id })
        val last = session.submit("mkdir notes")
        assertEquals(listOf("notes"), last.newlyCompleted.map { it.id })
        assertTrue(last.labJustCompleted)
        assertTrue(session.isComplete)
    }

    @Test
    fun completedObjectivesStayCompleted() {
        val session = LabSession(lab)
        session.submit("cd projects")
        session.submit("cd ..") // moving away again does not un-tick "Enter projects"
        assertTrue("move" in session.completedObjectiveIds)
    }

    @Test
    fun failedCommandsDoNotCount() {
        val session = LabSession(lab)
        session.submit("pwdd") // typo: command not found
        assertFalse("where" in session.completedObjectiveIds)
    }

    @Test
    fun anyCorrectWayCountsForStateChecks() {
        val session = LabSession(lab)
        session.submit("mkdir -p /home/student/projects/notes") // not the hinted command, still correct
        assertTrue("notes" in session.completedObjectiveIds)
    }

    @Test
    fun labJustCompletedFiresOnlyOnce() {
        val session = LabSession(lab)
        lab.solution.forEach { session.submit(it) }
        assertFalse(session.submit("pwd").labJustCompleted)
    }

    @Test
    fun resetRestoresTheStartingState() {
        val session = LabSession(lab)
        session.replay(lab.solution)
        session.reset()
        assertTrue(session.completedObjectiveIds.isEmpty())
        assertEquals("/home/student", session.engine.fs.cwd)
        assertTrue(session.engine.fs.node("/home/student/projects/notes") == null)
    }

    @Test
    fun replayRebuildsTheSameState() {
        val first = LabSession(lab)
        val typed = listOf("pwd", "cd projects", "echo hi > a.txt")
        typed.forEach { first.submit(it) }

        val restored = LabSession(lab)
        restored.replay(typed)
        assertEquals(first.completedObjectiveIds, restored.completedObjectiveIds)
        assertEquals(first.engine.fs.cwd, restored.engine.fs.cwd)
        assertEquals("hi", restored.engine.fs.file("/home/student/projects/a.txt")?.content)
    }
}
