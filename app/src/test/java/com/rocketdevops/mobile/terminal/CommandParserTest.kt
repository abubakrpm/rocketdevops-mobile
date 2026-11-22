package com.rocketdevops.mobile.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CommandParserTest {

    private fun words(input: String) = Tokenizer.tokenize(input).map { (it as Token.Word).text }

    @Test
    fun splitsOnSpacesAndKeepsFlags() {
        assertEquals(listOf("ls", "-la", "/home"), words("ls   -la  /home"))
    }

    @Test
    fun quotesKeepSpacesTogether() {
        assertEquals(listOf("git", "commit", "-m", "Add login page"), words("git commit -m \"Add login page\""))
        assertEquals(listOf("echo", "it's here"), words("echo \"it's here\""))
        assertEquals(listOf("echo", "a \"quoted\" word"), words("echo 'a \"quoted\" word'"))
    }

    @Test
    fun escapedCharacters() {
        assertEquals(listOf("cat", "my file.txt"), words("cat my\\ file.txt"))
        assertEquals(listOf("echo", "say \"hi\""), words("echo \"say \\\"hi\\\"\""))
    }

    @Test
    fun emptyQuotesMakeAnEmptyWord() {
        assertEquals(listOf("echo", ""), words("echo \"\""))
    }

    @Test
    fun unclosedQuoteIsAnError() {
        try {
            Tokenizer.tokenize("echo \"oops")
            fail("expected ParseException")
        } catch (e: ParseException) {
            assertTrue(e.message!!.contains("matching"))
        }
    }

    @Test
    fun pipesRedirectsAndAnd() {
        val line = CommandParser.parse("cat log.txt | grep error > errors.txt && echo done")
        assertEquals(2, line.steps.size)
        val first = line.steps[0].pipeline
        assertEquals(listOf(listOf("cat", "log.txt"), listOf("grep", "error")), first.commands.map { it.args })
        assertEquals(Redirect("errors.txt", append = false), first.redirect)
        assertTrue(line.steps[1].onlyIfPreviousSucceeded)
    }

    @Test
    fun appendRedirectWithoutSpaces() {
        val pipeline = CommandParser.parse("echo hi>>notes.txt").steps.single().pipeline
        assertEquals(listOf("echo", "hi"), pipeline.commands.single().args)
        assertEquals(Redirect("notes.txt", append = true), pipeline.redirect)
    }

    @Test
    fun syntaxErrorsLikeBash() {
        listOf("| ls", "ls |", "echo hi >", "&& ls", "ls ; ;").forEach { input ->
            try {
                CommandParser.parse(input)
                fail("expected a syntax error for: $input")
            } catch (e: ParseException) {
                assertTrue(e.message!!.contains("syntax error"))
            }
        }
    }
}
