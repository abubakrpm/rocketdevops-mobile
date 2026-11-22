package com.rocketdevops.mobile.terminal

import com.rocketdevops.mobile.data.content.ProcessSeed

/**
 * A pretend list of running programs for the Linux "processes" lab.
 * Nothing runs: these are rows in a list. `kill` removes a row.
 *
 * Always contains the learner's own shell (bash, PID 1000), which cannot be stopped.
 */
class ProcessTable(seeds: List<ProcessSeed>) {

    private val running = linkedMapOf<Int, ProcessSeed>()

    init {
        running[SHELL_PID] = ProcessSeed(SHELL_PID, "bash", user = "student")
        seeds.sortedBy { it.pid }.forEach { running[it.pid] = it }
    }

    /** Every running process, lowest PID first. */
    val all: List<ProcessSeed> get() = running.values.sortedBy { it.pid }

    fun find(pid: Int): ProcessSeed? = running[pid]

    fun matching(name: String): List<ProcessSeed> = all.filter { it.pid != SHELL_PID && it.command.contains(name) }

    fun remove(pid: Int) {
        running.remove(pid)
    }

    companion object {
        const val SHELL_PID = 1000
        /** The PID `ps` itself shows, fixed so output is the same every time. */
        const val PS_PID = 1001
    }
}

/**
 * ps, kill, pgrep and pkill over a ProcessTable.
 * Real behaviour that the lab teaches:
 *  - `ps` alone shows only this terminal; `ps aux` shows everything
 *  - `kill PID` sends TERM (polite); some programs ignore it, then `kill -9 PID` (KILL) is needed
 *  - root's processes cannot be stopped by a normal user
 */
class ProcessCommands(private val table: ProcessTable) {

    fun registerInto(registry: MutableMap<String, CommandHandler>) {
        registry["ps"] = CommandHandler(::ps)
        registry["kill"] = CommandHandler(::kill)
        registry["pgrep"] = CommandHandler(::pgrep)
        registry["pkill"] = CommandHandler(::pkill)
        listOf("top", "htop").forEach {
            registry[it] = CommandHandler { _, io ->
                io.fail("$it: full-screen tools are not available here. Use: ps aux")
            }
        }
    }

    // ---------------------------------------------------------------- ps

    private fun ps(args: List<String>, io: Io) {
        val everything = when (args.joinToString(" ")) {
            "" -> false
            "aux", "-aux", "ax", "-e", "-A", "-ef", "-eF", "-ax" -> true
            else -> return io.fail("ps: unsupported option \"${args.joinToString(" ")}\". Try: ps or ps aux")
        }
        if (!everything) {
            io.out += "    PID TTY          TIME CMD"
            io.out += "%7d pts/0    00:00:00 bash".format(ProcessTable.SHELL_PID)
            io.out += "%7d pts/0    00:00:00 ps".format(ProcessTable.PS_PID)
            return
        }
        val self = ProcessSeed(ProcessTable.PS_PID, "ps ${args.joinToString(" ")}", mem = "0.0")
        val processes = (table.all + self).sortedBy { it.pid }
        val rows = listOf(listOf("USER", "PID", "%CPU", "%MEM", "COMMAND")) +
            processes.map { listOf(it.user, it.pid.toString(), it.cpu, it.mem, it.command) }
        io.out += DockerCommands.table(rows)
    }

    // ---------------------------------------------------------------- kill

    private enum class Signal { TERM, KILL }

    /** Reads "-9", "-KILL", "-SIGKILL", "-15", "-TERM" or "-SIGTERM". Null = not a signal flag. */
    private fun parseSignal(arg: String): Signal? = when (arg.removePrefix("-").uppercase().removePrefix("SIG")) {
        "9", "KILL" -> Signal.KILL
        "15", "TERM" -> Signal.TERM
        else -> null
    }

    private fun kill(args: List<String>, io: Io) {
        var signal = Signal.TERM
        val targets = mutableListOf<String>()
        for (arg in args) {
            if (arg.startsWith("-") && targets.isEmpty()) {
                signal = parseSignal(arg) ?: return io.fail("bash: kill: ${arg.removePrefix("-")}: invalid signal specification")
            } else {
                targets += arg
            }
        }
        if (targets.isEmpty()) return io.fail("kill: usage: kill [-9] PID")

        for (target in targets) {
            val pid = target.toIntOrNull()
            if (pid == null) {
                io.fail("bash: kill: $target: arguments must be process or job IDs")
                continue
            }
            stop(pid, signal, io, prefix = "bash: kill: ($pid)")
        }
    }

    /** Tries to stop one process; writes an error if it cannot. */
    private fun stop(pid: Int, signal: Signal, io: Io, prefix: String) {
        val process = table.find(pid)
        when {
            process == null -> io.fail("$prefix - No such process")
            pid == ProcessTable.SHELL_PID -> io.fail("$prefix - this is the lab's own shell, it keeps running")
            process.user == "root" -> io.fail("$prefix - Operation not permitted")
            process.ignoresTerm && signal == Signal.TERM -> Unit // like real life: no message, it just keeps running
            else -> table.remove(pid)
        }
    }

    // ---------------------------------------------------------------- pgrep / pkill

    private fun pgrep(args: List<String>, io: Io) {
        val name = args.firstOrNull { !it.startsWith("-") } ?: return io.fail("pgrep: no matching criteria specified")
        val found = table.matching(name)
        if (found.isEmpty()) io.exitCode = 1 // nothing printed, like the real pgrep
        io.out += found.map { it.pid.toString() }
    }

    private fun pkill(args: List<String>, io: Io) {
        var signal = Signal.TERM
        var name: String? = null
        for (arg in args) {
            if (arg.startsWith("-")) {
                signal = parseSignal(arg) ?: return io.fail("pkill: invalid signal \"${arg.removePrefix("-")}\"")
            } else {
                name = arg
            }
        }
        val pattern = name ?: return io.fail("pkill: no matching criteria specified")
        val found = table.matching(pattern)
        if (found.isEmpty()) {
            io.exitCode = 1
            return
        }
        found.forEach { stop(it.pid, signal, io, prefix = "pkill: killing pid ${it.pid} failed") }
    }
}
