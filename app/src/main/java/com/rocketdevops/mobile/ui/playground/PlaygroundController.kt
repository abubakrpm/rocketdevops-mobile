package com.rocketdevops.mobile.ui.playground

import com.rocketdevops.mobile.terminal.Completion
import com.rocketdevops.mobile.terminal.Playground
import com.rocketdevops.mobile.terminal.TerminalEngine
import com.rocketdevops.mobile.ui.components.TerminalLine

/** Everything the playground screen draws, as one immutable snapshot. */
data class PlaygroundScreenState(
    val lines: List<TerminalLine>,
    val prompt: String,
    val suggestions: List<String>,
    val commandCount: Int,
)

/**
 * The free terminal's logic in plain Kotlin (no Android, no Compose), so it is unit tested.
 * Same simulator as the labs, but no objectives, hints or success screen.
 *
 * @param restoredInputs commands typed earlier; replayed silently. The engine is deterministic,
 *        so replaying rebuilds exactly the same files, commits and containers.
 */
class PlaygroundController(restoredInputs: List<String> = emptyList()) {

    private var engine: TerminalEngine = Playground.newEngine()

    private val _lines = mutableListOf<TerminalLine>()
    private val _typed = mutableListOf<String>()
    /** Every non-empty command typed since the start (or the last reset). */
    val typed: List<String> get() = _typed

    private var historyCursor: Int? = null

    init {
        showWelcome()
        restoredInputs.forEach(::run)
    }

    // ---------------------------------------------------------------- commands

    fun submit(input: String) {
        historyCursor = null
        run(input)
    }

    private fun run(input: String) {
        val promptBefore = engine.prompt
        val result = engine.execute(input)
        if (result.clearScreen) {
            _lines.clear()
        } else {
            _lines += TerminalLine(input, TerminalLine.Kind.Command, promptBefore)
        }
        result.lines.forEach { out ->
            _lines += TerminalLine(out.text, if (out.isError) TerminalLine.Kind.Error else TerminalLine.Kind.Output)
        }
        if (_lines.size > MAX_LINES) repeat(_lines.size - MAX_LINES) { _lines.removeAt(0) }
        if (input.isNotBlank()) _typed += input.trim()
    }

    /** Tab key. With several matches, lists them under the line like bash does. */
    fun complete(input: String): Completion {
        val completion = engine.complete(input)
        if (completion.candidates.size > 1) {
            _lines += TerminalLine(input, TerminalLine.Kind.Command, engine.prompt)
            _lines += TerminalLine(completion.candidates.joinToString("  "), TerminalLine.Kind.Output)
        }
        return completion
    }

    /** Ctrl+C: abandon the current line. */
    fun cancel(input: String) {
        historyCursor = null
        _lines += TerminalLine("$input^C", TerminalLine.Kind.Command, engine.prompt)
    }

    /** Up key: the previous command, or null if there is none. */
    fun previousCommand(): String? {
        val history = engine.history
        if (history.isEmpty()) return null
        val next = ((historyCursor ?: history.size) - 1).coerceAtLeast(0)
        historyCursor = next
        return history[next]
    }

    /** Down key: the next newer command, or an empty line after the newest. */
    fun nextCommand(): String {
        val history = engine.history
        val cursor = historyCursor ?: return ""
        return if (cursor + 1 < history.size) {
            historyCursor = cursor + 1
            history[cursor + 1]
        } else {
            historyCursor = null
            ""
        }
    }

    /** A brand-new machine: the starting files again, no repository, no containers. */
    fun reset() {
        engine = Playground.newEngine()
        _lines.clear()
        _typed.clear()
        historyCursor = null
        showWelcome()
    }

    private fun showWelcome() {
        Playground.welcome.forEach { _lines += TerminalLine(it, TerminalLine.Kind.Output) }
    }

    fun snapshot() = PlaygroundScreenState(
        lines = _lines.toList(),
        prompt = engine.prompt,
        suggestions = Playground.suggestions,
        commandCount = _typed.size,
    )

    companion object {
        /** Older lines are dropped so a very long session cannot use too much memory. */
        const val MAX_LINES = 500
    }
}

/**
 * Keeps the playground's commands while the app is open, so leaving the screen and coming
 * back (for example to check the cheat sheet) does not wipe the visitor's work.
 * Held by AppContainer. Only the typed commands are kept; the machine is rebuilt by replaying them.
 */
class PlaygroundMemory {
    @Volatile var typed: List<String> = emptyList()
}
