package com.rocketdevops.mobile.ui.lab

import com.rocketdevops.mobile.data.content.ContentCatalog
import com.rocketdevops.mobile.terminal.Completion
import com.rocketdevops.mobile.terminal.LabSession
import com.rocketdevops.mobile.ui.components.TerminalLine

/** One objective chip on the lab screen. */
data class ObjectiveChipState(val id: String, val label: String, val done: Boolean)

/** Everything the lab screen draws, as one immutable snapshot. */
data class LabScreenState(
    val labId: String,
    val caption: String,              // "Docker, Lab 2"
    val title: String,
    val scenario: String,
    val objectives: List<ObjectiveChipState>,
    val lines: List<TerminalLine>,
    val prompt: String,
    val hints: List<String>,          // only the hints revealed so far
    val totalHints: Int,
    val hintsVisible: Boolean,
    val isComplete: Boolean,
    val showSuccess: Boolean,
)

/**
 * The lab screen's logic in plain Kotlin (no Android, no Compose), so it is easy to unit test.
 * LabViewModel wraps it and turns every change into a new LabScreenState.
 *
 * It keeps the terminal transcript, the commands typed (to restore the lab after Android
 * kills the app), the revealed hints, and whether the success screen is showing.
 *
 * @param restoredInputs commands typed before the app was killed; replayed silently.
 */
class LabController(
    private val ref: ContentCatalog.LabRef,
    restoredInputs: List<String> = emptyList(),
    restoredHintsShown: Int = 0,
) {
    private val lab = ref.lab
    private val session = LabSession(lab)

    private val _lines = mutableListOf<TerminalLine>()
    private val _typed = mutableListOf<String>()
    /** Every non-empty command typed since the start (or last reset), for saving. */
    val typed: List<String> get() = _typed

    var hintsShown: Int = restoredHintsShown.coerceIn(0, lab.hints.size)
        private set
    private var hintsVisible = false
    private var showSuccess = false
    private var historyCursor: Int? = null // position while pressing the up/down keys

    init {
        restoredInputs.forEach { run(it, celebrate = false) }
    }

    // ---------------------------------------------------------------- commands

    /** Enter key. */
    fun submit(input: String) {
        historyCursor = null
        run(input, celebrate = true)
    }

    private fun run(input: String, celebrate: Boolean) {
        val promptBefore = session.engine.prompt
        val step = session.submit(input)
        if (step.output.clearScreen) {
            _lines.clear()
        } else {
            _lines += TerminalLine(input, TerminalLine.Kind.Command, promptBefore)
        }
        step.output.lines.forEach { out ->
            _lines += TerminalLine(out.text, if (out.isError) TerminalLine.Kind.Error else TerminalLine.Kind.Output)
        }
        if (_lines.size > MAX_LINES) repeat(_lines.size - MAX_LINES) { _lines.removeAt(0) }
        if (input.isNotBlank()) _typed += input.trim()
        if (celebrate && step.labJustCompleted) showSuccess = true
    }

    /** Tab key. With several matches, lists them under the line like bash does. */
    fun complete(input: String): Completion {
        val completion = session.engine.complete(input)
        if (completion.candidates.size > 1) {
            _lines += TerminalLine(input, TerminalLine.Kind.Command, session.engine.prompt)
            _lines += TerminalLine(completion.candidates.joinToString("  "), TerminalLine.Kind.Output)
        }
        return completion
    }

    /** Ctrl+C: abandon the current line. */
    fun cancel(input: String) {
        historyCursor = null
        _lines += TerminalLine("$input^C", TerminalLine.Kind.Command, session.engine.prompt)
    }

    /** Up key: the previous command, or null if there is none. */
    fun previousCommand(): String? {
        val history = session.engine.history
        if (history.isEmpty()) return null
        val next = ((historyCursor ?: history.size) - 1).coerceAtLeast(0)
        historyCursor = next
        return history[next]
    }

    /** Down key: the next newer command, or an empty line after the newest. */
    fun nextCommand(): String {
        val history = session.engine.history
        val cursor = historyCursor ?: return ""
        return if (cursor + 1 < history.size) {
            historyCursor = cursor + 1
            history[cursor + 1]
        } else {
            historyCursor = null
            ""
        }
    }

    // ---------------------------------------------------------------- hints, reset, success

    /** The bulb button: reveal the next hint (up to 3) and show the hint card. */
    fun showNextHint() {
        if (hintsVisible || hintsShown == 0) {
            hintsShown = (hintsShown + 1).coerceAtMost(lab.hints.size)
        }
        hintsVisible = true
    }

    fun hideHints() {
        hintsVisible = false
    }

    /** Reset lab: a fresh machine, cleared transcript and objectives. Revealed hints stay revealed. */
    fun reset() {
        session.reset()
        _lines.clear()
        _typed.clear()
        historyCursor = null
        showSuccess = false
    }

    fun dismissSuccess() {
        showSuccess = false
    }

    // ---------------------------------------------------------------- state

    fun snapshot(): LabScreenState = LabScreenState(
        labId = lab.id,
        caption = "${ref.track.title}, Lab ${ref.numberInTrack}",
        title = lab.title,
        scenario = lab.scenario,
        objectives = lab.objectives.map { ObjectiveChipState(it.id, it.label, it.id in session.completedObjectiveIds) },
        lines = _lines.toList(),
        prompt = session.engine.prompt,
        hints = lab.hints.take(hintsShown),
        totalHints = lab.hints.size,
        hintsVisible = hintsVisible,
        isComplete = session.isComplete,
        showSuccess = showSuccess,
    )

    companion object {
        /** Older lines are dropped so a very long session cannot use too much memory. */
        const val MAX_LINES = 500
    }
}
