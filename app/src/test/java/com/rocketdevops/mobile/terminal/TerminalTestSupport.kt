package com.rocketdevops.mobile.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** Runs a line and returns all output (normal and error) as one string, for easy assertions. */
fun TerminalEngine.run(line: String): String =
    execute(line).lines.joinToString("\n") { it.text }

/** Runs a line and asserts it succeeded. Returns its output. */
fun TerminalEngine.ok(line: String): String {
    val result = execute(line)
    val text = result.lines.joinToString("\n") { it.text }
    assertTrue("Expected \"$line\" to succeed but got:\n$text", result.succeeded)
    return text
}

/** Runs a line and asserts it failed with an error containing `expected`. */
fun TerminalEngine.fails(line: String, expected: String) {
    val result = execute(line)
    val errors = result.lines.filter { it.isError }.joinToString("\n") { it.text }
    assertFalse("Expected \"$line\" to fail", result.succeeded)
    assertTrue("Expected an error containing \"$expected\" but got:\n$errors", errors.contains(expected))
}

fun assertLines(expected: List<String>, actual: String) = assertEquals(expected.joinToString("\n"), actual)
