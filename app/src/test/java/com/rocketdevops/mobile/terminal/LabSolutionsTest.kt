package com.rocketdevops.mobile.terminal

import com.rocketdevops.mobile.data.content.ContentLoadResult
import com.rocketdevops.mobile.data.content.ContentLoader
import com.rocketdevops.mobile.data.content.FileContentSource
import com.rocketdevops.mobile.data.content.realContentRoot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the solution of EVERY lab in the real content through the simulator.
 * Proves each lab can be finished and that nothing is already "done" before the learner starts.
 * If a content author writes an objective the simulator cannot satisfy, this test names the lab.
 */
class LabSolutionsTest {

    private val catalog = (ContentLoader(FileContentSource(realContentRoot())).load() as ContentLoadResult.Success).catalog

    @Test
    fun everyLabSolutionCompletesItsLab() {
        val problems = mutableListOf<String>()
        for (ref in catalog.allLabs) {
            val lab = ref.lab
            val session = LabSession(lab)
            val transcript = StringBuilder()
            for (command in lab.solution) {
                val step = session.submit(command)
                transcript.append("\n  $ ").append(command)
                step.output.lines.forEach { transcript.append("\n    ").append(it.text) }
                // A merge conflict is the one failure a solution may contain on purpose (the conflict lab).
                val expectedConflict = step.output.lines.any { it.text.startsWith("CONFLICT") }
                if (!step.output.succeeded && !expectedConflict) problems += "${lab.id}: solution command failed: $command$transcript"
            }
            if (!session.isComplete) {
                val missing = lab.objectives.filter { it.id !in session.completedObjectiveIds }.map { it.label }
                problems += "${lab.id}: objectives not completed by the solution: $missing$transcript"
            }
        }
        assertTrue(problems.joinToString("\n\n"), problems.isEmpty())
    }

    @Test
    fun noLabStartsAlreadyComplete() {
        for (ref in catalog.allLabs) {
            val session = LabSession(ref.lab)
            val step = session.submit("whoami") // a harmless command that completes nothing
            assertFalse("${ref.lab.id} has objectives that are done before starting", step.newlyCompleted.isNotEmpty())
        }
    }
}
