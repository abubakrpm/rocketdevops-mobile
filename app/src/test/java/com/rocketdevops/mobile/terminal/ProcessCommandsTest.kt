package com.rocketdevops.mobile.terminal

import com.rocketdevops.mobile.data.content.CommitSeed
import com.rocketdevops.mobile.data.content.FsEntry
import com.rocketdevops.mobile.data.content.GitSeed
import com.rocketdevops.mobile.data.content.LabEnvironment
import com.rocketdevops.mobile.data.content.ObjectiveCheck
import com.rocketdevops.mobile.data.content.ProcessSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ps / kill / pgrep / pkill, the per-branch git seed, and the three Stage 8 objective checks. */
class ProcessCommandsTest {

    private fun machine() = TerminalEngine(
        LabEnvironment(
            processes = listOf(
                ProcessSeed(1, "/sbin/init", user = "root"),
                ProcessSeed(2210, "python3 backup.py", cpu = "98.5", ignoresTerm = true),
                ProcessSeed(2305, "node server.js", cpu = "1.2"),
            ),
        ),
    )

    @Test
    fun plainPsShowsOnlyThisTerminal() {
        val out = machine().ok("ps")
        assertTrue(out.contains("bash") && !out.contains("backup.py"))
    }

    @Test
    fun psAuxShowsEverythingAndPipesIntoGrep() {
        val t = machine()
        assertTrue(t.ok("ps aux").lines().first().startsWith("USER"))
        assertEquals("student   2210   98.5   0.1    python3 backup.py", t.ok("ps aux | grep backup"))
        t.fails("ps -zz", "unsupported option")
    }

    @Test
    fun killStopsAProcess() {
        val t = machine()
        t.ok("kill 2305")
        assertFalse(t.ok("ps aux").contains("server.js"))
        t.fails("kill 2305", "No such process")
    }

    @Test
    fun stubbornProcessNeedsKillNine() {
        val t = machine()
        t.ok("kill 2210")                       // politely asked: ignored, no message
        assertTrue(t.ok("ps aux").contains("backup.py"))
        t.ok("kill -9 2210")
        assertFalse(t.ok("ps aux").contains("backup.py"))
    }

    @Test
    fun rootAndShellAreProtected() {
        val t = machine()
        t.fails("kill 1", "Operation not permitted")
        t.fails("kill 1000", "own shell")
        t.fails("kill abc", "arguments must be process or job IDs")
        t.fails("kill -HUP 2305", "invalid signal")
        t.fails("top", "ps aux")
    }

    @Test
    fun pgrepAndPkill() {
        val t = machine()
        assertEquals("2305", t.ok("pgrep node"))
        assertFalse(t.execute("pgrep nothing").succeeded)
        t.ok("pkill node")
        assertTrue(t.ok("pgrep python").isNotEmpty())
        t.ok("pkill -9 python")
        assertFalse(t.execute("pgrep python").succeeded)
    }

    @Test
    fun processStoppedCheck() {
        val t = machine()
        val check = ObjectiveCheck.ProcessStopped("backup.py")
        assertFalse(ObjectiveChecker.isMet(check, t))
        t.ok("kill -9 2210")
        assertTrue(ObjectiveChecker.isMet(check, t))
    }

    // ---------------------------------------------------------------- git seed with differing branches

    private fun conflictRepo() = TerminalEngine(
        LabEnvironment(
            cwd = "/home/student/site",
            files = listOf(FsEntry("/home/student/site/index.html", content = "<h1>Welcome</h1>")),
            git = GitSeed(
                currentBranch = "main",
                branches = listOf("main", "new-title"),
                commits = listOf("Add home page"),
                extraCommits = listOf(
                    CommitSeed("new-title", "Friendlier title", mapOf("index.html" to "<h1>Hello there</h1>")),
                    CommitSeed("main", "Formal title", mapOf("index.html" to "<h1>Good day</h1>")),
                ),
            ),
        ),
    )

    @Test
    fun extraCommitsMakeBranchesDifferAndShowTheCurrentBranch() {
        val t = conflictRepo()
        assertEquals("<h1>Good day</h1>", t.ok("cat index.html"))
        assertTrue(t.ok("git status").contains("working tree clean"))
        assertEquals(2, t.git.commitCountOnCurrentBranch())
        t.ok("git switch new-title")
        assertEquals("<h1>Hello there</h1>", t.ok("cat index.html"))
    }

    @Test
    fun conflictChecksFollowTheFix() {
        val t = conflictRepo()
        val merged = ObjectiveCheck.GitMerged("new-title")
        val noMarkers = ObjectiveCheck.FileNotContains("/home/student/site/index.html", "<<<<<<<")

        t.fails("git merge new-title", "Automatic merge failed")
        assertFalse(ObjectiveChecker.isMet(noMarkers, t))
        assertFalse(ObjectiveChecker.isMet(merged, t))

        t.ok("echo \"<h1>Hello and good day</h1>\" > index.html")
        assertTrue(ObjectiveChecker.isMet(noMarkers, t))
        assertFalse(ObjectiveChecker.isMet(merged, t))   // merge not finished yet

        t.ok("git add index.html")
        t.ok("git commit -m \"Merge new-title\"")
        assertTrue(ObjectiveChecker.isMet(merged, t))
    }

    @Test
    fun committingConflictMarkersDoesNotCountAsMerged() {
        val t = conflictRepo()
        t.run("git merge new-title")
        t.ok("git add index.html")                // staged WITH the markers still in it
        t.ok("git commit -m \"Oops\"")
        assertFalse(ObjectiveChecker.isMet(ObjectiveCheck.GitMerged("new-title"), t))
    }
}
