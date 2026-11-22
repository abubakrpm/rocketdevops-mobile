package com.rocketdevops.mobile.terminal

import com.rocketdevops.mobile.data.content.FsEntry
import com.rocketdevops.mobile.data.content.LabEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pipe and redirect cases the Stage 8 labs depend on (spec: "pipes and redirection" tests). */
class PipesAndRedirectsTest {

    private val log = listOf("INFO start", "ERROR disk full", "INFO ok", "ERROR timeout", "DEBUG x").joinToString("\n")
    private fun machine() = TerminalEngine(LabEnvironment(files = listOf(FsEntry("/home/student/app.log", content = log))))

    @Test
    fun threeStagePipeCounts() {
        assertEquals("2", machine().ok("cat app.log | grep ERROR | wc -l").trim())
    }

    @Test
    fun pipeOutputCanBeRedirected() {
        val t = machine()
        assertEquals("", t.ok("cat app.log | grep ERROR > errors.txt"))   // nothing printed: it went to the file
        assertEquals("ERROR disk full\nERROR timeout", t.ok("cat errors.txt"))
    }

    @Test
    fun aQuotedArrowIsTextNotARedirect() {
        val t = machine()
        t.ok("echo \"echo ready > status.txt\" > deploy.sh")
        assertEquals("echo ready > status.txt", t.ok("cat deploy.sh"))
        assertTrue(t.fs.file("/home/student/status.txt") == null)          // not created yet
        t.ok("sh deploy.sh")
        assertEquals("ready", t.ok("cat status.txt"))                       // the script line did it
    }

    @Test
    fun appendBuildsAFileLineByLine() {
        val t = machine()
        t.ok("echo FROM nginx > Dockerfile")
        t.ok("echo \"COPY index.html /usr/share/nginx/html/\" >> Dockerfile")
        assertEquals("FROM nginx\nCOPY index.html /usr/share/nginx/html/", t.ok("cat Dockerfile"))
    }

    @Test
    fun redirectErrors() {
        val t = machine()
        t.ok("mkdir out")
        t.fails("echo hi > out", "Is a directory")
        t.fails("echo hi > missing/file.txt", "No such file or directory")
    }

    /** Like bash: the pipe's status is the LAST command's (wc works), but cat's error is still shown. */
    @Test
    fun anErrorInsideAPipeIsShownButTheLastCommandDecides() {
        val result = machine().execute("cat nope.txt | wc -l")
        assertTrue(result.succeeded)
        assertTrue(result.lines.any { it.isError && it.text.contains("No such file") })
        assertEquals("0", result.lines.last().text.trim())
    }
}
