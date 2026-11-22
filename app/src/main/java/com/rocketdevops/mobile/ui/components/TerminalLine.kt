package com.rocketdevops.mobile.ui.components

/**
 * One line in the terminal's scrollback.
 * Kept in its own file with no Compose imports, so plain-Kotlin classes (and tests) can use it.
 *
 * @param prompt for Command lines: the prompt shown when it was typed ("lab ~/app $").
 *        Stored per line because `cd` changes the prompt for later lines only.
 */
data class TerminalLine(
    val text: String,
    val kind: Kind,
    val prompt: String? = null,
) {
    enum class Kind {
        Command, // something the user typed, drawn after the violet prompt
        Output,  // normal output from the simulated command
        Error,   // "command not found", wrong flags, and so on
    }
}
