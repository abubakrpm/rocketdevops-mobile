package com.rocketdevops.mobile.terminal

import com.rocketdevops.mobile.data.content.FsEntry
import com.rocketdevops.mobile.data.content.LabEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxCommandsTest {

    private fun engine(vararg files: FsEntry) = TerminalEngine(LabEnvironment(files = files.toList()))

    @Test
    fun startsInHomeFolder() {
        val t = engine()
        assertEquals("/home/student", t.ok("pwd"))
        assertEquals("student", t.ok("whoami"))
        assertEquals("lab ~ $", t.prompt)
    }

    @Test
    fun cdHandlesRelativeAbsoluteParentAndHome() {
        val t = engine(FsEntry("/home/student/projects/app", directory = true))
        t.ok("cd projects/app")
        assertEquals("/home/student/projects/app", t.fs.cwd)
        assertEquals("lab ~/projects/app $", t.prompt)
        t.ok("cd ..")
        assertEquals("/home/student/projects", t.fs.cwd)
        t.ok("cd /tmp/../home")
        assertEquals("/home", t.fs.cwd)
        t.ok("cd")
        assertEquals("/home/student", t.fs.cwd)
        t.ok("cd -")
        assertEquals("/home", t.fs.cwd)
    }

    @Test
    fun cdErrors() {
        val t = engine(FsEntry("/home/student/notes.txt", content = "hi"))
        t.fails("cd nowhere", "bash: cd: nowhere: No such file or directory")
        t.fails("cd notes.txt", "Not a directory")
        t.fails("cd a b", "too many arguments")
    }

    @Test
    fun lsListsSortedAndHidesDotFiles() {
        val t = engine(
            FsEntry("/home/student/b.txt"),
            FsEntry("/home/student/a.txt"),
            FsEntry("/home/student/.secret"),
            FsEntry("/home/student/docs", directory = true),
        )
        assertLines(listOf("a.txt", "b.txt", "docs"), t.ok("ls"))
        assertTrue(t.ok("ls -a").lines().containsAll(listOf(".", "..", ".secret")))
        t.fails("ls missing", "ls: cannot access 'missing': No such file or directory")
        t.fails("ls -z", "ls: invalid option -- 'z'")
    }

    @Test
    fun lsLongShowsPermissionsAndSize() {
        val t = engine(FsEntry("/home/student/run.sh", content = "echo hi", mode = "755"))
        assertEquals("-rwxr-xr-x 1 student student 8 run.sh", t.ok("ls -l"))
    }

    @Test
    fun echoRedirectAndAppend() {
        val t = engine()
        t.ok("echo \"buy milk\" > todo.txt")
        t.ok("echo fix bug >> todo.txt")
        assertLines(listOf("buy milk", "fix bug"), t.ok("cat todo.txt"))
        t.ok("echo fresh > todo.txt")
        assertEquals("fresh", t.ok("cat todo.txt"))
        t.fails("echo x > nope/file.txt", "No such file or directory")
    }

    @Test
    fun mkdirTouchRm() {
        val t = engine()
        t.ok("mkdir notes")
        t.fails("mkdir notes", "File exists")
        t.fails("mkdir a/b", "No such file or directory")
        t.ok("mkdir -p a/b/c")
        assertTrue(t.fs.dir("/home/student/a/b/c") != null)
        t.ok("touch notes/today.md")
        t.fails("rm notes", "Is a directory")
        t.ok("rm -r notes")
        assertNull(t.fs.node("/home/student/notes"))
        t.fails("rm ghost.txt", "cannot remove 'ghost.txt': No such file or directory")
        t.ok("rm -f ghost.txt") // -f ignores missing files
        t.fails("rm -rf /", "dangerous")
    }

    @Test
    fun removingTheCurrentFolderMovesUp() {
        val t = engine(FsEntry("/home/student/tmp", directory = true))
        t.ok("cd tmp")
        t.ok("rm -r /home/student/tmp")
        assertEquals("/home/student", t.fs.cwd)
    }

    @Test
    fun cpAndMv() {
        val t = engine(FsEntry("/home/student/a.txt", content = "A"), FsEntry("/home/student/dir", directory = true))
        t.ok("cp a.txt b.txt")
        assertEquals("A", t.ok("cat b.txt"))
        t.ok("cp a.txt dir")
        assertEquals("A", t.ok("cat dir/a.txt"))
        t.fails("cp dir dir2", "-r not specified")
        t.ok("cp -r dir dir2")
        assertEquals("A", t.ok("cat dir2/a.txt"))
        t.ok("mv b.txt c.txt")
        assertNull(t.fs.node("/home/student/b.txt"))
        t.fails("cp missing.txt x", "cannot stat 'missing.txt'")
        t.fails("mv dir dir/inside", "into itself")
        t.fails("cp a.txt", "missing destination file operand")
    }

    @Test
    fun copiesAreIndependent() {
        val t = engine(FsEntry("/home/student/a.txt", content = "A"))
        t.ok("cp a.txt b.txt")
        t.ok("echo changed > a.txt")
        assertEquals("A", t.ok("cat b.txt"))
    }

    @Test
    fun chmodNumericAndSymbolic() {
        val t = engine(FsEntry("/home/student/s.sh", content = "echo hi"))
        assertEquals("644", t.fs.file("/home/student/s.sh")!!.mode)
        t.ok("chmod +x s.sh")
        assertEquals("755", t.fs.file("/home/student/s.sh")!!.mode)
        t.ok("chmod go-rx s.sh")
        assertEquals("700", t.fs.file("/home/student/s.sh")!!.mode)
        t.ok("chmod 640 s.sh")
        assertEquals("640", t.fs.file("/home/student/s.sh")!!.mode)
        t.fails("chmod 999 s.sh", "invalid mode")
        t.fails("chmod 755 ghost", "cannot access 'ghost'")
    }

    @Test
    fun scriptsNeedExecutePermission() {
        val t = engine(FsEntry("/home/student/deploy.sh", content = "#!/bin/sh\n# comment\necho \"Deploying...\"\npwd", mode = "644"))
        t.fails("./deploy.sh", "bash: ./deploy.sh: Permission denied")
        assertLines(listOf("Deploying...", "/home/student"), t.ok("sh deploy.sh")) // sh does not need +x
        t.ok("chmod 755 deploy.sh")
        assertLines(listOf("Deploying...", "/home/student"), t.ok("./deploy.sh"))
        t.fails("./nothing.sh", "No such file or directory")
    }

    @Test
    fun scriptThatRunsItselfStops() {
        val t = engine(FsEntry("/home/student/loop.sh", content = "sh loop.sh"))
        t.fails("sh loop.sh", "nested too deeply") // must not hang or crash
    }

    @Test
    fun grepWithFilesFlagsAndPipes() {
        val t = engine(FsEntry("/home/student/app.log", content = "INFO start\nERROR disk full\ninfo ok\nERROR timeout"))
        assertLines(listOf("ERROR disk full", "ERROR timeout"), t.ok("grep ERROR app.log"))
        assertLines(listOf("INFO start", "info ok"), t.ok("grep -i info app.log"))
        assertLines(listOf("2:ERROR disk full", "4:ERROR timeout"), t.ok("grep -n ERROR app.log"))
        assertEquals("2", t.ok("grep -c ERROR app.log"))
        assertEquals("ERROR timeout", t.ok("cat app.log | grep timeout"))
        val noMatch = t.execute("grep nothing app.log")
        assertFalse(noMatch.succeeded)          // exit status 1, like real grep
        assertTrue(noMatch.lines.isEmpty())     // but no error message
    }

    @Test
    fun headTailWc() {
        val t = engine(FsEntry("/home/student/n.txt", content = (1..15).joinToString("\n")))
        assertEquals((1..10).joinToString("\n"), t.ok("head n.txt"))
        assertLines(listOf("1", "2"), t.ok("head -n 2 n.txt"))
        assertLines(listOf("14", "15"), t.ok("tail -2 n.txt"))
        assertEquals("15 n.txt", t.ok("wc -l n.txt"))
        assertEquals("3", t.ok("cat n.txt | head -3 | wc -l"))
    }

    @Test
    fun andStopsAfterAFailureButSemicolonContinues() {
        val t = engine()
        val result = t.execute("cd nowhere && mkdir skipped")
        assertFalse(result.succeeded)
        assertNull(t.fs.node("/home/student/skipped"))
        t.run("cd nowhere ; mkdir made")
        assertTrue(t.fs.dir("/home/student/made") != null)
    }

    @Test
    fun unknownCommandSuggestsTheClosestOne() {
        val t = engine()
        t.fails("dokcer ps", "dokcer: command not found")
        // docker is not part of this lab, so the closest command is git... unless docker exists:
        val withDocker = TerminalEngine(LabEnvironment(docker = com.rocketdevops.mobile.data.content.DockerSeed()))
        withDocker.fails("dokcer ps", "Did you mean \"docker\"?")
        t.fails("gti status", "Did you mean \"git\"?")
    }

    @Test
    fun clearHistoryAndHelp() {
        val t = engine()
        t.ok("pwd")
        assertTrue(t.execute("clear").clearScreen)
        assertTrue(t.ok("history").contains("1  pwd"))
        assertTrue(t.ok("help").contains("Simulated commands"))
        t.fails("sudo ls", "sudo")
        t.fails("vim notes.txt", "editors are not available")
    }

    @Test
    fun tabCompletesCommandsAndFiles() {
        val t = engine(FsEntry("/home/student/projects", directory = true), FsEntry("/home/student/readme.txt"))
        assertEquals("pwd ", t.complete("pw").input)
        assertEquals("cd projects/", t.complete("cd pro").input)
        assertEquals("cat readme.txt ", t.complete("cat r").input)
        val many = t.complete("c")             // cat, cd, chmod, clear, cp
        assertTrue(many.candidates.containsAll(listOf("cat", "cd", "cp")))
    }

    @Test
    fun recordsOnlySuccessfulCommandsForObjectives() {
        val t = engine()
        t.run("cd nowhere")
        t.run("pwd | grep student")
        assertEquals(listOf("pwd", "grep student"), t.successfulCommands)
    }
}
