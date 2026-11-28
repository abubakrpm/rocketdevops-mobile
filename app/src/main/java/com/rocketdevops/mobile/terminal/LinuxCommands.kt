package com.rocketdevops.mobile.terminal

import com.rocketdevops.mobile.terminal.VirtualFileSystem.DirNode
import com.rocketdevops.mobile.terminal.VirtualFileSystem.FileNode

/**
 * Basic Linux commands, written to behave (and fail) like the real GNU tools,
 * but only on the in-memory VirtualFileSystem.
 */
class LinuxCommands(private val engine: TerminalEngine) {

    private val fs = engine.fs
    private var previousDir: String = fs.cwd // for `cd -`

    fun registerInto(registry: MutableMap<String, CommandHandler>) {
        registry["pwd"] = CommandHandler { _, io -> io.out += fs.cwd }
        registry["whoami"] = CommandHandler { _, io -> io.out += USER }
        registry["echo"] = CommandHandler(::echo)
        registry["cd"] = CommandHandler(::cd)
        registry["ls"] = CommandHandler(::ls)
        registry["cat"] = CommandHandler(::cat)
        registry["mkdir"] = CommandHandler(::mkdir)
        registry["touch"] = CommandHandler(::touch)
        registry["rm"] = CommandHandler(::rm)
        registry["cp"] = CommandHandler { args, io -> copyOrMove(args, io, move = false) }
        registry["mv"] = CommandHandler { args, io -> copyOrMove(args, io, move = true) }
        registry["chmod"] = CommandHandler(::chmod)
        registry["grep"] = CommandHandler(::grep)
        registry["head"] = CommandHandler { args, io -> headOrTail(args, io, head = true) }
        registry["tail"] = CommandHandler { args, io -> headOrTail(args, io, head = false) }
        registry["wc"] = CommandHandler(::wc)
        registry["clear"] = CommandHandler { _, io -> io.clearScreen = true }
        registry["history"] = CommandHandler { _, io ->
            engine.history.forEachIndexed { i, line -> io.out += "%5d  %s".format(i + 1, line) }
        }
        registry["help"] = CommandHandler { _, io -> io.out += engine.helpText() }
        registry["sh"] = CommandHandler(::sh)
        registry["bash"] = CommandHandler(::sh)

        // Friendly answers for things a real server has but this simulator does not.
        registry["sudo"] = unsupported("sudo: not needed here. This simulated machine already gives you the permissions you need.")
        registry["exit"] = unsupported("exit: the simulated terminal stays open. Use the back button to leave.")
        registry["man"] = unsupported("man: manual pages are not included. Open the Cheats tab for quick help.")
        listOf("nano", "vim", "vi").forEach {
            registry[it] = unsupported("$it: editors are not available here. Write files with: echo \"text\" > file.txt")
        }
    }

    private fun unsupported(message: String) = CommandHandler { _, io -> io.fail(message) }

    // ---------------------------------------------------------------- helpers

    /** Splits ["-la", "notes"] into flags {l, a} and operands ["notes"]. "--" ends the flags. */
    private data class Parsed(val flags: Set<Char>, val operands: List<String>)

    private fun parseFlags(command: String, args: List<String>, allowed: String): Parsed {
        val flags = mutableSetOf<Char>()
        val operands = mutableListOf<String>()
        var flagsEnded = false
        for (arg in args) {
            if (!flagsEnded && arg == "--") {
                flagsEnded = true
            } else if (!flagsEnded && arg.startsWith("-") && arg.length > 1) {
                for (c in arg.drop(1)) {
                    if (c !in allowed) {
                        throw ShellError("$command: invalid option -- '$c'\nTry '$command --help' for more information.")
                    }
                    flags += c
                }
            } else {
                operands += arg
            }
        }
        return Parsed(flags, operands)
    }

    /** Text lines of a file (an empty file has no lines). */
    private fun linesOf(content: String): List<String> = if (content.isEmpty()) emptyList() else content.split("\n")

    // ---------------------------------------------------------------- commands

    private fun echo(args: List<String>, io: Io) {
        val words = if (args.firstOrNull() == "-n") args.drop(1) else args
        io.out += words.joinToString(" ")
    }

    private fun cd(args: List<String>, io: Io) {
        if (args.size > 1) return io.fail("bash: cd: too many arguments")
        val typed = args.firstOrNull() ?: "~"
        val target = if (typed == "-") previousDir else fs.resolve(typed)
        when (fs.node(target)) {
            null -> io.fail("bash: cd: $typed: No such file or directory")
            is FileNode -> io.fail("bash: cd: $typed: Not a directory")
            is DirNode -> {
                previousDir = fs.cwd
                fs.cwd = target
                if (typed == "-") io.out += target
            }
        }
    }

    private fun ls(args: List<String>, io: Io) {
        val (flags, operands) = parseFlags("ls", args, allowed = "laAh1")
        val long = 'l' in flags
        val showAll = 'a' in flags
        val showHidden = showAll || 'A' in flags
        val targets = operands.ifEmpty { listOf(".") }

        targets.forEachIndexed { index, typed ->
            val path = fs.resolve(typed)
            when (val node = fs.node(path)) {
                null -> io.fail("ls: cannot access '$typed': No such file or directory")
                is FileNode -> io.out += if (long) longLine(typed, node) else typed
                is DirNode -> {
                    if (targets.size > 1) {
                        if (index > 0) io.out += ""
                        io.out += "$typed:"
                    }
                    val entries = buildList {
                        if (showAll) {
                            add("." to node)
                            add(".." to (fs.dir(fs.parentOf(path)) ?: node))
                        }
                        node.children.forEach { (name, child) ->
                            if (showHidden || !name.startsWith(".")) add(name to child)
                        }
                    }
                    if (long) {
                        val width = entries.maxOfOrNull { sizeOf(it.second).toString().length } ?: 1
                        entries.forEach { (name, child) -> io.out += longLine(name, child, width) }
                    } else {
                        // One name per line: easy to read on a phone and to pipe into grep.
                        entries.forEach { (name, _) -> io.out += name }
                    }
                }
            }
        }
    }

    private fun sizeOf(node: VirtualFileSystem.Node): Int = when (node) {
        is DirNode -> 4096
        // Real files end with a newline, so count one extra byte for non-empty files.
        is FileNode -> if (node.content.isEmpty()) 0 else node.content.toByteArray().size + 1
    }

    /** "-rw-r--r-- 1 student student   42 todo.txt" */
    private fun longLine(name: String, node: VirtualFileSystem.Node, sizeWidth: Int = 1): String {
        val type = if (node is DirNode) 'd' else '-'
        val links = if (node is DirNode) 2 else 1
        val size = sizeOf(node).toString().padStart(sizeWidth)
        return "$type${VirtualFileSystem.permissionString(node.mode)} $links $USER $USER $size $name"
    }

    private fun cat(args: List<String>, io: Io) {
        val (_, files) = parseFlags("cat", args, allowed = "")
        if (files.isEmpty()) {
            io.out += io.stdin.orEmpty() // `echo hi | cat`
            return
        }
        for (typed in files) {
            when (val node = fs.node(fs.resolve(typed))) {
                null -> io.fail("cat: $typed: No such file or directory")
                is DirNode -> io.fail("cat: $typed: Is a directory")
                is FileNode -> io.out += linesOf(node.content)
            }
        }
    }

    private fun mkdir(args: List<String>, io: Io) {
        val (flags, dirs) = parseFlags("mkdir", args, allowed = "p")
        if (dirs.isEmpty()) return io.fail("mkdir: missing operand")
        val parents = 'p' in flags
        for (typed in dirs) {
            val path = fs.resolve(typed)
            val parent = fs.node(fs.parentOf(path))
            when {
                fs.exists(path) -> if (!(parents && fs.dir(path) != null)) {
                    io.fail("mkdir: cannot create directory '$typed': File exists")
                }
                parents -> if (pathBlockedByFile(path)) {
                    io.fail("mkdir: cannot create directory '$typed': Not a directory")
                } else {
                    fs.mkdirs(path)
                }
                parent == null -> io.fail("mkdir: cannot create directory '$typed': No such file or directory")
                parent is FileNode -> io.fail("mkdir: cannot create directory '$typed': Not a directory")
                else -> fs.mkdirs(path)
            }
        }
    }

    /** True if some folder on the way to `path` is actually a file. */
    private fun pathBlockedByFile(path: String): Boolean {
        var current = fs.parentOf(path)
        while (current != "/") {
            if (fs.file(current) != null) return true
            current = fs.parentOf(current)
        }
        return false
    }

    private fun touch(args: List<String>, io: Io) {
        val (_, files) = parseFlags("touch", args, allowed = "")
        if (files.isEmpty()) return io.fail("touch: missing file operand")
        for (typed in files) {
            val path = fs.resolve(typed)
            when {
                fs.exists(path) -> Unit // real touch only updates the time stamp
                fs.dir(fs.parentOf(path)) == null -> io.fail("touch: cannot touch '$typed': No such file or directory")
                else -> fs.writeFile(path, "")
            }
        }
    }

    private fun rm(args: List<String>, io: Io) {
        val (flags, targets) = parseFlags("rm", args, allowed = "rRfi")
        val recursive = 'r' in flags || 'R' in flags
        val force = 'f' in flags
        if (targets.isEmpty()) {
            if (!force) io.fail("rm: missing operand")
            return
        }
        for (typed in targets) {
            val path = fs.resolve(typed)
            val node = fs.node(path)
            when {
                path == "/" && recursive -> {
                    io.fail("rm: it is dangerous to operate recursively on '/'")
                    io.err += "rm: use --no-preserve-root to override this failsafe"
                }
                node == null -> if (!force) io.fail("rm: cannot remove '$typed': No such file or directory")
                node is DirNode && !recursive -> io.fail("rm: cannot remove '$typed': Is a directory")
                else -> {
                    fs.delete(path)
                    // If we deleted the folder we are standing in, step back to the nearest one that exists.
                    var cwd = fs.cwd
                    while (fs.dir(cwd) == null) cwd = fs.parentOf(cwd)
                    fs.cwd = cwd
                }
            }
        }
    }

    private fun copyOrMove(args: List<String>, io: Io, move: Boolean) {
        val name = if (move) "mv" else "cp"
        val (flags, operands) = parseFlags(name, args, allowed = if (move) "fi" else "rRfi")
        val recursive = move || 'r' in flags || 'R' in flags
        if (operands.isEmpty()) return io.fail("$name: missing file operand")
        if (operands.size == 1) return io.fail("$name: missing destination file operand after '${operands[0]}'")

        val sources = operands.dropLast(1)
        val destTyped = operands.last()
        val destPath = fs.resolve(destTyped)
        val destIsDir = fs.dir(destPath) != null
        if (sources.size > 1 && !destIsDir) return io.fail("$name: target '$destTyped' is not a directory")

        for (typed in sources) {
            val srcPath = fs.resolve(typed)
            val node = fs.node(srcPath)
            val target = if (destIsDir) "$destPath/${fs.nameOf(srcPath)}".replace("//", "/") else destPath
            when {
                node == null -> io.fail("$name: cannot stat '$typed': No such file or directory")
                node is DirNode && !recursive -> io.fail("cp: -r not specified; omitting directory '$typed'")
                node is DirNode && fs.isSameOrInside(target, srcPath) ->
                    io.fail("$name: cannot ${if (move) "move" else "copy"} a directory, '$typed', into itself, '$destTyped'")
                srcPath == target -> io.fail("$name: '$typed' and '$destTyped' are the same file")
                fs.dir(fs.parentOf(target)) == null ->
                    io.fail("$name: cannot create regular file '$destTyped': No such file or directory")
                fs.dir(target) != null && node is FileNode ->
                    io.fail("$name: cannot overwrite directory '$destTyped' with non-directory")
                else -> {
                    fs.put(target, if (move) node else fs.deepCopy(node))
                    if (move) fs.delete(srcPath)
                }
            }
        }
    }

    /** chmod 755 file, chmod +x file, chmod u+x,g-w file */
    private fun chmod(args: List<String>, io: Io) {
        if (args.isEmpty()) return io.fail("chmod: missing operand")
        if (args.size == 1) return io.fail("chmod: missing operand after '${args[0]}'")
        val mode = args[0]
        for (typed in args.drop(1)) {
            val node = fs.node(fs.resolve(typed))
            if (node == null) {
                io.fail("chmod: cannot access '$typed': No such file or directory")
                continue
            }
            val newMode = applyMode(node.mode, mode) ?: return io.fail("chmod: invalid mode: '$mode'")
            node.mode = newMode
        }
    }

    /** Applies a numeric ("755") or symbolic ("u+x", "+x", "go-w") mode. Null if the mode is invalid. */
    private fun applyMode(current: String, mode: String): String? {
        if (NUMERIC_MODE.matches(mode)) return mode.takeLast(3).padStart(3, '0')
        val bits = current.map { it.digitToInt() }.toIntArray() // [user, group, other]
        for (clause in mode.split(',')) {
            val match = SYMBOLIC_MODE.matchEntire(clause) ?: return null
            val (who, op, perms) = match.destructured
            val targets = when {
                who.isEmpty() || 'a' in who -> listOf(0, 1, 2)
                else -> who.map { "ugo".indexOf(it) }
            }
            val value = perms.sumOf { "xwr".indexOf(it).let { pos -> 1 shl pos } }
            for (t in targets) {
                bits[t] = when (op) {
                    "+" -> bits[t] or value
                    "-" -> bits[t] and value.inv()
                    else -> value // "="
                } and 7
            }
        }
        return bits.joinToString("")
    }

    private fun grep(args: List<String>, io: Io) {
        val (flags, operands) = parseFlags("grep", args, allowed = "invc")
        val patternText = operands.firstOrNull()
            ?: return io.fail("Usage: grep [OPTION]... PATTERNS [FILE]...")
        val regex = try {
            if ('i' in flags) Regex(patternText, RegexOption.IGNORE_CASE) else Regex(patternText)
        } catch (e: IllegalArgumentException) {
            return io.fail("grep: invalid pattern '$patternText'")
        }
        val files = operands.drop(1)

        // (label, lines) for each input. Label is null for stdin or a single file.
        val inputs: List<Pair<String?, List<String>>> = if (files.isEmpty()) {
            listOf(null to io.stdin.orEmpty())
        } else {
            files.mapNotNull { typed ->
                when (val node = fs.node(fs.resolve(typed))) {
                    null -> { io.err += "grep: $typed: No such file or directory"; null }
                    is DirNode -> { io.err += "grep: $typed: Is a directory"; null }
                    is FileNode -> (if (files.size > 1) typed else null) to linesOf(node.content)
                }
            }
        }

        var matched = 0
        for ((label, lines) in inputs) {
            var countHere = 0
            lines.forEachIndexed { i, line ->
                if (regex.containsMatchIn(line) != ('v' in flags)) {
                    countHere++
                    if ('c' !in flags) {
                        val number = if ('n' in flags) "${i + 1}:" else ""
                        io.out += (label?.let { "$it:" } ?: "") + number + line
                    }
                }
            }
            if ('c' in flags) io.out += (label?.let { "$it:" } ?: "") + countHere
            matched += countHere
        }
        // Like real grep: exit status 1 when nothing matched (no message), 2 on errors.
        if (io.err.isNotEmpty()) io.exitCode = 2 else if (matched == 0) io.exitCode = 1
    }

    private fun headOrTail(args: List<String>, io: Io, head: Boolean) {
        val name = if (head) "head" else "tail"
        var count = 10
        val files = mutableListOf<String>()
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            when {
                arg == "-n" -> {
                    count = args.getOrNull(i + 1)?.toIntOrNull()
                        ?: return io.fail("$name: invalid number of lines: '${args.getOrNull(i + 1) ?: ""}'")
                    i++
                }
                arg.startsWith("-n") -> count = arg.drop(2).toIntOrNull() ?: return io.fail("$name: invalid number of lines: '${arg.drop(2)}'")
                arg.startsWith("-") && arg.drop(1).toIntOrNull() != null -> count = arg.drop(1).toInt()
                arg.startsWith("-") && arg.length > 1 -> return io.fail("$name: invalid option -- '${arg[1]}'")
                else -> files += arg
            }
            i++
        }
        val take: (List<String>) -> List<String> = { lines -> if (head) lines.take(count) else lines.takeLast(count) }
        if (files.isEmpty()) {
            io.out += take(io.stdin.orEmpty())
            return
        }
        files.forEachIndexed { index, typed ->
            when (val node = fs.node(fs.resolve(typed))) {
                null -> io.fail("$name: cannot open '$typed' for reading: No such file or directory")
                is DirNode -> io.fail("$name: error reading '$typed': Is a directory")
                is FileNode -> {
                    if (files.size > 1) {
                        if (index > 0) io.out += ""
                        io.out += "==> $typed <=="
                    }
                    io.out += take(linesOf(node.content))
                }
            }
        }
    }

    private fun wc(args: List<String>, io: Io) {
        val (flags, files) = parseFlags("wc", args, allowed = "lwc")
        val showAll = flags.isEmpty()

        fun format(lines: Int, words: Int, bytes: Int, label: String?): String {
            val parts = buildList {
                if (showAll || 'l' in flags) add(lines)
                if (showAll || 'w' in flags) add(words)
                if (showAll || 'c' in flags) add(bytes)
            }
            return parts.joinToString(" ") + (label?.let { " $it" } ?: "")
        }

        fun countOf(lines: List<String>): Triple<Int, Int, Int> {
            val text = lines.joinToString("\n")
            val words = text.split(Regex("\\s+")).count { it.isNotEmpty() }
            val bytes = if (lines.isEmpty()) 0 else text.toByteArray().size + 1
            return Triple(lines.size, words, bytes)
        }

        if (files.isEmpty()) {
            val (l, w, c) = countOf(io.stdin.orEmpty())
            io.out += format(l, w, c, null)
            return
        }
        for (typed in files) {
            when (val node = fs.node(fs.resolve(typed))) {
                null -> io.fail("wc: $typed: No such file or directory")
                is DirNode -> io.fail("wc: $typed: Is a directory")
                is FileNode -> {
                    val (l, w, c) = countOf(linesOf(node.content))
                    io.out += format(l, w, c, typed)
                }
            }
        }
    }

    /** `sh deploy.sh` runs a script without needing the execute permission. */
    private fun sh(args: List<String>, io: Io) {
        val typed = args.firstOrNull()
            ?: return io.fail("sh: interactive shells are not available here. Run one command at a time.")
        when (val node = fs.node(fs.resolve(typed))) {
            null -> io.fail("sh: 0: cannot open $typed: No such file")
            is DirNode -> io.fail("sh: 0: cannot open $typed: Is a directory")
            is FileNode -> engine.runScript(node.content, io)
        }
    }

    companion object {
        const val USER = "student"
        private val NUMERIC_MODE = Regex("^0?[0-7]{3}$")
        private val SYMBOLIC_MODE = Regex("^([ugoa]*)([+\\-=])([rwx]+)$")
    }
}
