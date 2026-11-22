package com.rocketdevops.mobile.terminal

import com.rocketdevops.mobile.data.content.LabEnvironment

/** One line of terminal output. Error lines are drawn in red by the UI. */
data class OutputLine(val text: String, val isError: Boolean = false)

/** What one typed line produced. */
data class ExecutionResult(
    val lines: List<OutputLine>,
    val succeeded: Boolean,
    val clearScreen: Boolean = false, // the user typed `clear`
)

/** Result of pressing Tab. `input` is the new text for the input field. */
data class Completion(val input: String, val candidates: List<String>)

/** Thrown by a command to stop with an error message, like a real program printing to stderr. */
class ShellError(message: String) : Exception(message)

/**
 * The input and output of ONE running command.
 * `stdin` is the previous command's output in a pipe (null when nothing is piped in).
 * The command writes normal output to `out` and errors to `err`.
 */
class Io(val stdin: List<String>?) {
    val out = mutableListOf<String>()
    val err = mutableListOf<String>()
    var exitCode = 0
    var clearScreen = false

    /** Write an error and mark the command as failed. */
    fun fail(message: String) {
        err += message
        exitCode = 1
    }
}

/** Every command (ls, git, docker, ...) is one of these. */
fun interface CommandHandler {
    fun run(args: List<String>, io: Io)
}

/**
 * THE SIMULATED TERMINAL.
 * Owns the fake machine (filesystem, git repository, docker daemon) and runs typed lines:
 *   parse -> for each step -> for each command in the pipe -> look up handler -> run.
 *
 * It is fully deterministic (no clock, no randomness), so replaying the same commands
 * always rebuilds the same state. The lab screen uses that to restore a lab after the
 * app was closed in the background.
 *
 * NOTHING is ever executed on the phone: every command is Kotlin code below that only
 * changes these in-memory objects.
 */
class TerminalEngine(environment: LabEnvironment = LabEnvironment()) {

    val fs = VirtualFileSystem()
    val git = GitRepository(fs)
    /** Null when Docker is not part of this lab (typing docker then gives "command not found"). */
    val docker: DockerDaemon? = environment.docker?.let { DockerDaemon(it) }
    /** Pretend running programs for ps / kill (always has the lab's own bash). */
    val processes = ProcessTable(environment.processes)

    private val registry = sortedMapOf<String, CommandHandler>()

    private val _history = mutableListOf<String>()
    /** Everything the user typed, oldest first (for the up-arrow key and `history`). */
    val history: List<String> get() = _history

    /** 0 while running what the user typed, 1+ while running lines inside a script. */
    private var scriptDepth = 0

    private val _successfulCommands = mutableListOf<String>()
    /**
     * Every single command that succeeded, as words joined by one space
     * (`docker ps | grep web` records "docker ps" and "grep web").
     * Lab objectives of type "command" are matched against this list.
     */
    val successfulCommands: List<String> get() = _successfulCommands

    /** Shown before the input, e.g. "lab ~/app $". */
    val prompt: String get() = "lab ${fs.displayPath(fs.cwd)} $"

    val commandNames: Set<String> get() = registry.keys

    init {
        fs.seed(environment.files)
        fs.mkdirs(fs.resolve(environment.cwd))
        fs.cwd = fs.resolve(environment.cwd)
        environment.git?.let { git.seed(it, repositoryRoot = fs.cwd) }

        LinuxCommands(this).registerInto(registry)
        ProcessCommands(processes).registerInto(registry)
        registry["git"] = GitCommands(git, fs)
        docker?.let { registry["docker"] = DockerCommands(it, fs) }
    }

    // ---------------------------------------------------------------- running

    /** Runs one line typed by the user. */
    fun execute(input: String): ExecutionResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ExecutionResult(emptyList(), succeeded = true)
        _history += trimmed
        return runLine(trimmed)
    }

    /** Parses and runs one line. Used for typed input and for each line of a script. */
    private fun runLine(line: String): ExecutionResult {
        val parsed = try {
            CommandParser.parse(line)
        } catch (e: ParseException) {
            return ExecutionResult(listOf(OutputLine("bash: ${e.message}", isError = true)), succeeded = false)
        }

        val output = mutableListOf<OutputLine>()
        var lastSucceeded = true
        var clear = false
        for (step in parsed.steps) {
            if (step.onlyIfPreviousSucceeded && !lastSucceeded) continue // `a && b` skips b when a failed
            val result = runPipeline(step.pipeline)
            if (result.clearScreen) {
                output.clear()
                clear = true
            }
            output += result.lines
            lastSucceeded = result.succeeded
        }
        return ExecutionResult(output, lastSucceeded, clear)
    }

    private fun runPipeline(pipeline: Pipeline): ExecutionResult {
        val shown = mutableListOf<OutputLine>()
        var stdin: List<String>? = null
        var succeeded = true
        var clear = false

        for (command in pipeline.commands) {
            val io = Io(stdin)
            runCommand(command.args, io)
            shown += io.err.map { OutputLine(it, isError = true) }
            // Only what the user typed counts for lab objectives, not lines inside a script.
            if (io.exitCode == 0 && scriptDepth == 0) _successfulCommands += command.args.joinToString(" ")
            succeeded = io.exitCode == 0 // like bash: a pipe's status is its last command's
            clear = clear || io.clearScreen
            stdin = io.out
        }

        val finalOutput = stdin.orEmpty()
        val redirect = pipeline.redirect
        if (redirect == null) {
            shown += finalOutput.map { OutputLine(it) }
        } else {
            val error = writeRedirect(redirect, finalOutput)
            if (error != null) {
                shown += OutputLine(error, isError = true)
                succeeded = false
            }
        }
        return ExecutionResult(shown, succeeded, clear)
    }

    /** `> file` / `>> file`. Returns an error message, or null on success. */
    private fun writeRedirect(redirect: Redirect, lines: List<String>): String? {
        val path = fs.resolve(redirect.path)
        return when {
            fs.dir(path) != null -> "bash: ${redirect.path}: Is a directory"
            fs.dir(fs.parentOf(path)) == null -> "bash: ${redirect.path}: No such file or directory"
            else -> {
                fs.writeFile(path, lines.joinToString("\n"), append = redirect.append)
                null
            }
        }
    }

    private fun runCommand(args: List<String>, io: Io) {
        val name = args.first()
        try {
            when {
                name.contains('/') -> runScriptFile(name, io) // ./deploy.sh
                else -> {
                    val handler = registry[name]
                    if (handler != null) handler.run(args.drop(1), io) else commandNotFound(name, io)
                }
            }
        } catch (e: ShellError) {
            val lines = e.message.orEmpty().lines()
            io.fail(lines.first())
            io.err += lines.drop(1)
        }
    }

    private fun commandNotFound(name: String, io: Io) {
        io.fail("$name: command not found")
        suggestCommand(name)?.let { io.err += "Did you mean \"$it\"?" }
    }

    /** Finds a known command one or two typos away: "dokcer" -> "docker". */
    private fun suggestCommand(typed: String): String? {
        if (typed.length < 2) return null
        return registry.keys
            .map { it to editDistance(typed, it) }
            .filter { (_, distance) -> distance in 1..2 }
            .minByOrNull { (_, distance) -> distance }
            ?.first
    }

    // ---------------------------------------------------------------- scripts

    /** `./deploy.sh`: needs the execute permission, like real Linux. */
    private fun runScriptFile(typedPath: String, io: Io) {
        val path = fs.resolve(typedPath)
        val node = fs.node(path)
        when {
            node == null -> io.fail("bash: $typedPath: No such file or directory")
            node is VirtualFileSystem.DirNode -> io.fail("bash: $typedPath: Is a directory")
            !ownerCanExecute(node.mode) -> io.fail("bash: $typedPath: Permission denied")
            else -> runScript((node as VirtualFileSystem.FileNode).content, io)
        }
    }

    /** Runs each line of a script through this same engine (used by ./file and `sh file`). */
    internal fun runScript(content: String, io: Io) {
        // A script that runs itself would loop forever; stop after a few levels.
        if (scriptDepth >= MAX_SCRIPT_DEPTH) throw ShellError("bash: scripts are nested too deeply")
        scriptDepth++
        try {
            for (raw in content.lines()) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue // skip blank lines, comments and #!/bin/sh
                val result = runLine(line)
                result.lines.forEach { if (it.isError) io.err += it.text else io.out += it.text }
                if (!result.succeeded) io.exitCode = 1
            }
        } finally {
            scriptDepth--
        }
    }

    private fun ownerCanExecute(mode: String) = mode.first().digitToInt() and 1 != 0

    // ---------------------------------------------------------------- tab completion

    /**
     * Tab key. The first word completes to a command name, later words to file names.
     * One match: complete it. Several: complete their common start and return them as candidates.
     */
    fun complete(input: String): Completion {
        val lastSpace = input.lastIndexOf(' ')
        val before = input.substring(0, lastSpace + 1)
        val partial = input.substring(lastSpace + 1)
        val isFirstWord = before.isBlank()

        val candidates: List<String> = if (isFirstWord) {
            registry.keys.filter { it.startsWith(partial) }
        } else {
            val slash = partial.lastIndexOf('/')
            val dirPart = partial.substring(0, slash + 1)
            val namePart = partial.substring(slash + 1)
            val dirPath = fs.resolve(dirPart.ifEmpty { "." })
            fs.list(dirPath)
                .filter { (name, _) -> name.startsWith(namePart) && (namePart.startsWith(".") || !name.startsWith(".")) }
                .map { (name, node) -> dirPart + name + if (node is VirtualFileSystem.DirNode) "/" else "" }
        }

        return when (candidates.size) {
            0 -> Completion(input, emptyList())
            1 -> {
                val only = candidates.single()
                Completion(before + only + if (only.endsWith("/")) "" else " ", emptyList())
            }
            else -> Completion(before + commonPrefix(candidates), candidates)
        }
    }

    // ---------------------------------------------------------------- help

    internal fun helpText(): List<String> = buildList {
        add("Simulated commands (nothing runs on your phone):")
        add("  files:   pwd ls cd cat mkdir touch rm cp mv echo chmod")
        add("  text:    grep head tail wc")
        add("  procs:   ps kill pgrep pkill")
        add("  other:   whoami clear history help sh")
        add("  git:     init status add commit log branch switch checkout merge")
        if (docker != null) add("  docker:  pull images run ps stop start rm rmi logs exec build")
        add("Pipes (|), redirects (> and >>) and && work too.")
    }

    companion object {
        private const val MAX_SCRIPT_DEPTH = 3

        /** Levenshtein distance: how many single-letter edits turn a into b. */
        fun editDistance(a: String, b: String): Int {
            val previous = IntArray(b.length + 1) { it }
            val current = IntArray(b.length + 1)
            for (i in 1..a.length) {
                current[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
                }
                current.copyInto(previous)
            }
            return previous[b.length]
        }

        fun commonPrefix(words: List<String>): String =
            words.reduce { acc, word -> acc.commonPrefixWith(word) }
    }
}
