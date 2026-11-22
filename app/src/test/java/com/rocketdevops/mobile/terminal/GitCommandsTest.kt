package com.rocketdevops.mobile.terminal

import com.rocketdevops.mobile.data.content.FsEntry
import com.rocketdevops.mobile.data.content.GitSeed
import com.rocketdevops.mobile.data.content.LabEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitCommandsTest {

    private val app = "/home/student/app"

    private fun freshProject() = TerminalEngine(
        LabEnvironment(cwd = app, files = listOf(FsEntry("$app/README.md", content = "# My app"))),
    )

    private fun seededProject() = TerminalEngine(
        LabEnvironment(
            cwd = app,
            files = listOf(FsEntry("$app/README.md", content = "# My app")),
            git = GitSeed(commits = listOf("Add README")),
        ),
    )

    @Test
    fun commandsOutsideARepositoryFail() {
        freshProject().fails("git status", "fatal: not a git repository")
    }

    @Test
    fun initAddCommitFlow() {
        val t = freshProject()
        assertEquals("Initialized empty Git repository in /home/student/app/.git/", t.ok("git init"))
        assertTrue(t.ok("ls -a").contains(".git"))

        val untracked = t.ok("git status")
        assertTrue(untracked.contains("No commits yet"))
        assertTrue(untracked.contains("Untracked files:"))
        assertTrue(untracked.contains("README.md"))

        t.ok("git add README.md")
        assertTrue(t.ok("git status").contains("new file:   README.md"))

        val committed = t.ok("git commit -m \"Add README\"")
        assertTrue(committed.startsWith("[main (root-commit) "))
        assertTrue(committed.contains("] Add README"))
        assertTrue(t.ok("git status").contains("nothing to commit, working tree clean"))
        assertEquals(1, t.git.commitCountOnCurrentBranch())
    }

    @Test
    fun commitNeedsStagedChangesAndAMessage() {
        val t = freshProject()
        t.ok("git init")
        val nothing = t.execute("git commit -m \"empty\"")
        assertFalse(nothing.succeeded)
        t.ok("git add .")
        t.fails("git commit", "no text editor")
        t.fails("git commit -m", "requires a value")
    }

    @Test
    fun modifiedAndDeletedFilesShowInStatus() {
        val t = seededProject()
        t.ok("echo changed > README.md")
        assertTrue(t.ok("git status").contains("Changes not staged for commit:"))
        t.ok("git commit -am \"Update README\"") // -a stages tracked changes
        assertEquals(2, t.git.commitCountOnCurrentBranch())
        t.ok("rm README.md")
        assertTrue(t.ok("git status").contains("deleted:    README.md"))
    }

    @Test
    fun logShowsNewestFirst() {
        val t = seededProject()
        t.ok("touch a.txt")
        t.ok("git add a.txt")
        t.ok("git commit -m \"Add a\"")
        val lines = t.ok("git log --oneline").lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("(HEAD -> main) Add a"))
        assertTrue(lines[1].endsWith("Add README"))
        assertTrue(t.ok("git log").contains("Author: Student"))
    }

    @Test
    fun branchesAndSwitching() {
        val t = seededProject()
        t.ok("git switch -c login")
        assertEquals("login", t.git.currentBranch)
        assertLines(listOf("* login", "  main"), t.ok("git branch"))

        t.ok("echo form > login.txt")
        t.ok("git add login.txt")
        t.ok("git commit -m \"Add login\"")

        t.ok("git switch main")
        assertTrue(t.fs.file("$app/login.txt") == null) // the file belongs to the login branch
        t.ok("git checkout login")
        assertEquals("form", t.ok("cat login.txt"))

        t.fails("git switch nope", "invalid reference")
        t.fails("git checkout nope", "did not match any file(s)")
        t.fails("git branch login", "already exists")
        t.ok("git checkout -b feature")
        assertEquals("feature", t.git.currentBranch)
    }

    @Test
    fun switchingWithUncommittedChangesIsRefused() {
        val t = seededProject()
        t.ok("git branch other")
        t.ok("git switch other")
        t.ok("echo x > README.md")
        t.ok("git commit -am \"Change on other\"")
        t.ok("echo dirty > README.md")
        t.fails("git switch main", "would be overwritten")
    }

    @Test
    fun fastForwardMerge() {
        val t = seededProject()
        t.ok("git switch -c login")
        t.ok("touch login.txt")
        t.ok("git add login.txt")
        t.ok("git commit -m \"Add login\"")
        t.ok("git switch main")
        val out = t.ok("git merge login")
        assertTrue(out.contains("Fast-forward"))
        assertTrue(t.fs.file("$app/login.txt") != null)
        assertEquals("Already up to date.", t.ok("git merge login"))
    }

    @Test
    fun mergeCommitWhenBothBranchesMoved() {
        val t = seededProject()
        t.ok("git switch -c login")
        t.ok("touch login.txt")
        t.ok("git add . && git commit -m \"Add login\"")
        t.ok("git switch main")
        t.ok("touch about.txt")
        t.ok("git add . && git commit -m \"Add about\"")
        assertTrue(t.ok("git merge login").contains("Merge made by"))
        assertTrue(t.fs.file("$app/login.txt") != null && t.fs.file("$app/about.txt") != null)
        assertEquals(4, t.git.commitCountOnCurrentBranch()) // README, about, login, merge
    }

    @Test
    fun simpleConflictCanBeResolved() {
        val t = seededProject()
        t.ok("git switch -c dev")
        t.ok("echo dev title > README.md")
        t.ok("git commit -am \"Dev title\"")
        t.ok("git switch main")
        t.ok("echo main title > README.md")
        t.ok("git commit -am \"Main title\"")

        t.fails("git merge dev", "Automatic merge failed")
        val conflicted = t.ok("cat README.md")
        assertTrue(conflicted.contains("<<<<<<< HEAD") && conflicted.contains(">>>>>>> dev"))
        assertTrue(t.ok("git status").contains("both modified:   README.md"))
        t.fails("git commit -m \"Merge\"", "unmerged files")

        t.ok("echo final title > README.md")
        t.ok("git add README.md")
        t.ok("git commit -m \"Merge dev\"")
        assertTrue(t.git.merge == null)
        assertTrue(t.ok("git log --oneline").lines().first().contains("Merge dev"))
    }

    @Test
    fun mergeCanBeAborted() {
        val t = seededProject()
        t.ok("git switch -c dev")
        t.ok("echo a > README.md")
        t.ok("git commit -am \"A\"")
        t.ok("git switch main")
        t.ok("echo b > README.md")
        t.ok("git commit -am \"B\"")
        t.run("git merge dev")
        t.ok("git merge --abort")
        assertEquals("b", t.ok("cat README.md"))
        assertTrue(t.ok("git status").contains("working tree clean"))
    }

    @Test
    fun remoteCommandsExplainTheyAreOffline() {
        seededProject().fails("git push", "offline simulator")
    }
}
