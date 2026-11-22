package com.rocketdevops.mobile.terminal

import com.rocketdevops.mobile.data.content.FsEntry
import java.util.TreeMap

/**
 * A pretend Linux filesystem that lives only in memory (a tree of folders and files).
 * Nothing here touches the phone's real storage. Resetting a lab simply builds a new one.
 *
 * This class only stores and finds things. The bash-style error messages
 * ("No such file or directory") are written by the commands in LinuxCommands.
 */
class VirtualFileSystem(val home: String = HOME) {

    sealed class Node {
        abstract var mode: String // permissions as 3 octal digits, e.g. "644"
    }

    class FileNode(var content: String = "", override var mode: String = DEFAULT_FILE_MODE) : Node()

    /** Children are kept sorted by name, like `ls` shows them. */
    class DirNode(
        val children: TreeMap<String, Node> = TreeMap(),
        override var mode: String = DEFAULT_DIR_MODE,
    ) : Node()

    private val root = DirNode()

    /** The current working directory (always an absolute, normalised path). */
    var cwd: String = home
        set(value) {
            require(dir(value) != null) { "cwd must be an existing directory: $value" }
            field = value
        }

    init {
        mkdirs(home)
    }

    // ---------------------------------------------------------------- paths

    /**
     * Turns any path the user typed into a clean absolute path.
     * Handles ~, ., .. and relative paths: with cwd = /home/student, "../tmp" becomes "/home/tmp".
     */
    fun resolve(path: String): String {
        val expanded = when {
            path == "~" -> home
            path.startsWith("~/") -> home + path.substring(1)
            else -> path
        }
        val parts = if (expanded.startsWith("/")) mutableListOf() else segments(cwd).toMutableList()
        for (segment in expanded.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += segment
            }
        }
        return "/" + parts.joinToString("/")
    }

    fun parentOf(absolute: String): String = absolute.substringBeforeLast('/').ifEmpty { "/" }

    fun nameOf(absolute: String): String = absolute.substringAfterLast('/')

    /** Shows a path the way a prompt does: /home/student/app becomes ~/app. */
    fun displayPath(absolute: String): String = when {
        absolute == home -> "~"
        absolute.startsWith("$home/") -> "~" + absolute.removePrefix(home)
        else -> absolute
    }

    /** True if `inner` is `outer` itself or somewhere inside it. */
    fun isSameOrInside(inner: String, outer: String): Boolean =
        inner == outer || outer == "/" || inner.startsWith("$outer/")

    private fun segments(absolute: String) = absolute.split('/').filter { it.isNotEmpty() }

    // ---------------------------------------------------------------- lookups

    fun node(absolute: String): Node? {
        var current: Node = root
        for (segment in segments(absolute)) {
            current = (current as? DirNode)?.children?.get(segment) ?: return null
        }
        return current
    }

    fun dir(absolute: String): DirNode? = node(absolute) as? DirNode
    fun file(absolute: String): FileNode? = node(absolute) as? FileNode
    fun exists(absolute: String): Boolean = node(absolute) != null

    /** Entries of a folder as (name, node), sorted by name. */
    fun list(absolute: String): List<Pair<String, Node>> =
        dir(absolute)?.children?.map { it.key to it.value }.orEmpty()

    /**
     * Every file under a folder as relative path -> content, skipping `skip` folders.
     * Git uses this to take snapshots of the working tree.
     */
    fun filesUnder(absolute: String, skip: Set<String> = emptySet()): Map<String, String> {
        val result = sortedMapOf<String, String>()
        fun walk(dir: DirNode, prefix: String) {
            for ((name, child) in dir.children) {
                val relative = if (prefix.isEmpty()) name else "$prefix/$name"
                when (child) {
                    is FileNode -> result[relative] = child.content
                    is DirNode -> if (relative !in skip) walk(child, relative)
                }
            }
        }
        dir(absolute)?.let { walk(it, "") }
        return result
    }

    // ---------------------------------------------------------------- changes
    // Callers check the rules first (parent exists, not a folder, ...) and show the error.

    /** Creates a folder and any missing parents. Does nothing if it already exists. */
    fun mkdirs(absolute: String) {
        var current = root
        for (segment in segments(absolute)) {
            current = when (val child = current.children[segment]) {
                is DirNode -> child
                null -> DirNode().also { current.children[segment] = it }
                is FileNode -> error("$segment is a file")
            }
        }
    }

    /** Creates or overwrites (or appends to) a file. The parent folder must exist. */
    fun writeFile(absolute: String, content: String, append: Boolean = false) {
        val parent = requireNotNull(dir(parentOf(absolute))) { "parent folder missing for $absolute" }
        val name = nameOf(absolute)
        val existing = parent.children[name] as? FileNode
        if (existing == null) {
            parent.children[name] = FileNode(content)
        } else if (append && existing.content.isNotEmpty()) {
            existing.content = existing.content + "\n" + content
        } else {
            existing.content = content
        }
    }

    fun delete(absolute: String) {
        dir(parentOf(absolute))?.children?.remove(nameOf(absolute))
    }

    /** Places a node at a path (used by cp and mv). The parent folder must exist. */
    fun put(absolute: String, node: Node) {
        requireNotNull(dir(parentOf(absolute))).children[nameOf(absolute)] = node
    }

    /** A full independent copy of a file or a folder tree. */
    fun deepCopy(node: Node): Node = when (node) {
        is FileNode -> FileNode(node.content, node.mode)
        is DirNode -> DirNode(TreeMap(node.children.mapValues { deepCopy(it.value) }), node.mode)
    }

    /** Builds the lab's starting files. Parent folders are created automatically. */
    fun seed(entries: List<FsEntry>) {
        for (entry in entries) {
            val path = resolve(entry.path)
            if (entry.directory) {
                mkdirs(path)
                entry.mode?.let { dir(path)?.mode = it }
            } else {
                mkdirs(parentOf(path))
                writeFile(path, entry.content)
                entry.mode?.let { file(path)?.mode = it }
            }
        }
    }

    companion object {
        const val HOME = "/home/student"
        const val DEFAULT_FILE_MODE = "644"
        const val DEFAULT_DIR_MODE = "755"

        /** "755" -> "rwxr-xr-x" */
        fun permissionString(mode: String): String = mode.map { digit ->
            val bits = digit.digitToInt()
            buildString {
                append(if (bits and 4 != 0) 'r' else '-')
                append(if (bits and 2 != 0) 'w' else '-')
                append(if (bits and 1 != 0) 'x' else '-')
            }
        }.joinToString("")
    }
}
