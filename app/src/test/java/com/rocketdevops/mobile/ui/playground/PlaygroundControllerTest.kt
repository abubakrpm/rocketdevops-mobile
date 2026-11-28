package com.rocketdevops.mobile.ui.playground

import com.rocketdevops.mobile.terminal.Playground
import com.rocketdevops.mobile.ui.components.TerminalLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The free terminal: same simulator as the labs, no objectives. */
class PlaygroundControllerTest {

    private fun PlaygroundController.run(vararg commands: String): List<TerminalLine> {
        val before = snapshot().lines.size
        commands.forEach(::submit)
        return snapshot().lines.drop(before)
    }

    private fun List<TerminalLine>.errors() = filter { it.kind == TerminalLine.Kind.Error }.map { it.text }
    private fun List<TerminalLine>.outputText() = filter { it.kind == TerminalLine.Kind.Output }.joinToString("\n") { it.text }

    @Test
    fun opensWithAWelcomeAndAPlaygroundPrompt() {
        val state = PlaygroundController().snapshot()
        assertEquals(Playground.welcome, state.lines.map { it.text })
        assertEquals("playground ~ $", state.prompt)
        assertEquals(0, state.commandCount)
    }

    @Test
    fun theStartingMachineHasSomethingToExplore() {
        val out = PlaygroundController().run("ls").outputText()
        listOf("README.md", "notes.txt", "projects", "downloads").forEach { assertTrue(it, it in out) }
    }

    @Test
    fun everySuggestionWorksWhenTappedInOrder() {
        val c = PlaygroundController()
        Playground.suggestions.forEach { command ->
            val lines = c.run(command)
            assertTrue("\"$command\" failed: ${lines.errors()}", lines.errors().isEmpty())
        }
        assertEquals("playground ~/projects/webapp $", c.snapshot().prompt)
    }

    @Test
    fun anyCommandsInAnyOrderNoObjectives() {
        val c = PlaygroundController()
        c.run("mkdir demo", "cd demo", "echo hi > a.txt", "git init", "git add a.txt", "git commit -m one")
        val log = c.run("git log --oneline").outputText()
        assertTrue(log, "one" in log)
        assertTrue(c.run("docker pull redis").errors().isEmpty())
        assertTrue("redis" in c.run("docker images").outputText())
    }

    @Test
    fun theStubbornProcessNeedsKillNine() {
        val c = PlaygroundController()
        c.run("kill 977")
        assertTrue("node server.js" in c.run("ps aux").outputText())
        c.run("kill -9 977")
        assertFalse("node server.js" in c.run("ps aux").outputText())
    }

    @Test
    fun replayingTheTypedCommandsRebuildsTheSameMachine() {
        val first = PlaygroundController()
        first.run("cd projects/webapp", "git init", "docker run -d --name web -p 8080:80 nginx")
        val second = PlaygroundController(restoredInputs = first.typed)
        assertEquals(first.snapshot().prompt, second.snapshot().prompt)
        assertEquals(first.run("docker ps").outputText(), second.run("docker ps").outputText())
    }

    @Test
    fun resetGivesAFreshMachine() {
        val c = PlaygroundController()
        c.run("rm notes.txt", "docker run -d nginx", "cd projects")
        c.reset()
        assertEquals(Playground.welcome, c.snapshot().lines.map { it.text })
        assertEquals("playground ~ $", c.snapshot().prompt)
        assertTrue(c.typed.isEmpty())
        assertTrue("notes.txt" in c.run("ls").outputText())
        assertFalse("nginx" in c.run("docker ps").outputText())
    }

    @Test
    fun historyKeysWalkThroughTypedCommands() {
        val c = PlaygroundController()
        c.run("pwd", "whoami")
        assertEquals("whoami", c.previousCommand())
        assertEquals("pwd", c.previousCommand())
        assertEquals("whoami", c.nextCommand())
        assertEquals("", c.nextCommand())
    }
}
