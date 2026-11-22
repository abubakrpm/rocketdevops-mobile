package com.rocketdevops.mobile.terminal

import com.rocketdevops.mobile.terminal.GitRepository.MergeState

/**
 * The `git` command. Each sub-command (init, add, commit, ...) mimics the real output
 * closely enough for learning, while only changing the in-memory GitRepository.
 * Network commands (push, pull, clone) explain that the simulator is offline.
 */
class GitCommands(
    private val repo: GitRepository,
    private val fs: VirtualFileSystem,
) : CommandHandler {

    override fun run(args: List<String>, io: Io) {
        val sub = args.firstOrNull() ?: return usage(io)
        val rest = args.drop(1)
        when (sub) {
            "init" -> init(io)
            "--version", "version" -> io.out += "git version 2.43.0 (simulated)"
            "help", "--help" -> usage(io)
            "push", "pull", "fetch", "clone", "remote" ->
                io.fail("git $sub: remotes are not available in the offline simulator. Everything here stays on this pretend machine.")
            else -> {
                if (!repo.isInitialized || !repo.containsCwd()) {
                    return io.fail("fatal: not a git repository (or any of the parent directories): .git")
                }
                when (sub) {
                    "status" -> status(io)
                    "add" -> add(rest, io)
                    "commit" -> commit(rest, io)
                    "log" -> log(rest, io)
                    "branch" -> branch(rest, io)
                    "switch" -> switch(rest, io, checkoutStyle = false)
                    "checkout" -> switch(rest, io, checkoutStyle = true)
                    "merge" -> merge(rest, io)
                    else -> io.fail("git: '$sub' is not a git command. See 'git --help'.")
                }
            }
        }
    }

    private fun usage(io: Io) {
        io.out += "usage: git <command> [<args>]"
        io.out += ""
        io.out += "Commands in this simulator:"
        io.out += "   init, status, add, commit, log, branch, switch, checkout, merge"
    }

    // ---------------------------------------------------------------- init / status

    private fun init(io: Io) {
        val root = repo.root
        if (root != null && fs.isSameOrInside(fs.cwd, root)) {
            io.out += "Reinitialized existing Git repository in $root/.git/"
            return
        }
        repo.init(fs.cwd)
        io.out += "Initialized empty Git repository in ${fs.cwd}/.git/"
    }

    private fun status(io: Io) {
        val working = repo.workingTree()
        val head = repo.headSnapshot
        val index = repo.index
        val merge = repo.merge

        io.out += "On branch ${repo.currentBranch}"
        if (repo.head == null) {
            io.out += ""
            io.out += "No commits yet"
        }

        val conflicted = merge?.conflicted.orEmpty()
        if (merge != null) {
            io.out += ""
            if (conflicted.isEmpty()) {
                io.out += "All conflicts fixed but you are still merging."
                io.out += "  (use \"git commit\" to conclude merge)"
            } else {
                io.out += "You have unmerged paths."
                io.out += "  (fix conflicts and run \"git commit\")"
                io.out += "  (use \"git merge --abort\" to abort the merge)"
            }
        }

        val staged = (index.keys + head.keys).filter { it !in conflicted && index[it] != head[it] }
            .map { path ->
                when {
                    path !in head -> "new file:   $path"
                    path !in index -> "deleted:    $path"
                    else -> "modified:   $path"
                }
            }
        val unstaged = index.keys.filter { it !in conflicted && working[it] != index[it] }
            .map { path -> if (path in working) "modified:   $path" else "deleted:    $path" }
        val untracked = working.keys.filter { it !in index && it !in conflicted }

        if (staged.isNotEmpty()) {
            io.out += ""
            io.out += "Changes to be committed:"
            staged.forEach { io.out += "\t$it" }
        }
        if (conflicted.isNotEmpty()) {
            io.out += ""
            io.out += "Unmerged paths:"
            io.out += "  (use \"git add <file>...\" to mark resolution)"
            conflicted.sorted().forEach { io.out += "\tboth modified:   $it" }
        }
        if (unstaged.isNotEmpty()) {
            io.out += ""
            io.out += "Changes not staged for commit:"
            io.out += "  (use \"git add <file>...\" to update what will be committed)"
            unstaged.forEach { io.out += "\t$it" }
        }
        if (untracked.isNotEmpty()) {
            io.out += ""
            io.out += "Untracked files:"
            io.out += "  (use \"git add <file>...\" to include in what will be committed)"
            untracked.forEach { io.out += "\t$it" }
        }
        if (staged.isEmpty() && unstaged.isEmpty() && conflicted.isEmpty()) {
            io.out += ""
            io.out += when {
                untracked.isNotEmpty() -> "nothing added to commit but untracked files present (use \"git add\" to track)"
                repo.head == null -> "nothing to commit (create/copy files and use \"git add\" to track)"
                merge != null -> "nothing to commit"
                else -> "nothing to commit, working tree clean"
            }
        }
    }

    // ---------------------------------------------------------------- add / commit

    private fun add(args: List<String>, io: Io) {
        val paths = args.filter { !it.startsWith("-") }
        val addAll = "-A" in args || "--all" in args
        if (paths.isEmpty() && !addAll) {
            io.out += "Nothing specified, nothing added."
            io.out += "hint: Maybe you wanted to say 'git add .'?"
            return
        }
        val root = requireNotNull(repo.root)
        val working = repo.workingTree()
        val targets = if (addAll) listOf(root) else paths.map { fs.resolve(it) }

        for ((i, absolute) in targets.withIndex()) {
            if (!fs.isSameOrInside(absolute, root)) {
                return io.fail("fatal: ${paths.getOrElse(i) { absolute }}: '$absolute' is outside repository at '$root'")
            }
            val relative = absolute.removePrefix(root).removePrefix("/")
            fun matches(path: String) = relative.isEmpty() || path == relative || path.startsWith("$relative/")

            val inWorking = working.filterKeys(::matches)
            val inIndex = repo.index.keys.filter(::matches)
            if (inWorking.isEmpty() && inIndex.isEmpty()) {
                return io.fail("fatal: pathspec '${paths.getOrElse(i) { relative }}' did not match any files")
            }
            repo.index.putAll(inWorking)
            inIndex.filter { it !in working }.forEach { repo.index.remove(it) } // staged deletion
            repo.merge?.conflicted?.removeAll(inWorking.keys)                  // marks conflicts as resolved
        }
    }

    private fun commit(rawArgs: List<String>, io: Io) {
        // "-am" means "-a -m": stage every change to tracked files, then use the message.
        val args = rawArgs.flatMap { if (it == "-am") listOf("-a", "-m") else listOf(it) }
        if ("-a" in args || "--all" in args) {
            val working = repo.workingTree()
            for (path in repo.index.keys.toList()) {
                val content = working[path]
                if (content == null) repo.index.remove(path) else repo.index[path] = content
            }
        }
        val merge = repo.merge
        val messageIndex = args.indexOfFirst { it == "-m" || it == "--message" }
        val inlineMessage = args.firstOrNull { it.startsWith("-m") && it.length > 2 }?.drop(2)
        val message = when {
            messageIndex >= 0 -> args.getOrNull(messageIndex + 1)
                ?: return io.fail("error: switch `m' requires a value")
            inlineMessage != null -> inlineMessage
            merge != null -> "Merge branch '${merge.otherBranch}'"
            else -> return io.fail("Aborting commit: the simulator has no text editor. Use: git commit -m \"your message\"")
        }
        if (message.isBlank()) return io.fail("Aborting commit due to empty commit message.")

        if (merge != null && merge.conflicted.isNotEmpty()) {
            io.fail("error: Committing is not possible because you have unmerged files.")
            io.err += "hint: Fix them up in the work tree, and then use 'git add <file>'"
            return
        }

        val headSnapshot = repo.headSnapshot
        if (repo.index == headSnapshot && merge == null) {
            val hasUnstaged = repo.workingTree().let { working ->
                working.keys.any { it !in repo.index } || repo.index.any { (path, content) -> working[path] != content }
            }
            io.out += "On branch ${repo.currentBranch}"
            io.out += when {
                repo.head == null && hasUnstaged -> "nothing added to commit but untracked files present (use \"git add\" to track)"
                hasUnstaged -> "no changes added to commit (use \"git add\" and/or \"git commit -a\")"
                else -> "nothing to commit, working tree clean"
            }
            io.exitCode = 1
            return
        }

        val parents = listOfNotNull(repo.headId) + listOfNotNull(merge?.otherHead)
        val isRoot = repo.head == null
        val newCommit = repo.createCommit(message, repo.index, parents)
        repo.moveCurrentBranch(newCommit.id)
        repo.finishMerge()

        val changed = (newCommit.snapshot.keys + headSnapshot.keys).count { newCommit.snapshot[it] != headSnapshot[it] }
        io.out += "[${repo.currentBranch}${if (isRoot) " (root-commit)" else ""} ${newCommit.id}] $message"
        io.out += " $changed ${if (changed == 1) "file" else "files"} changed"
    }

    // ---------------------------------------------------------------- log / branch

    private fun log(args: List<String>, io: Io) {
        val commits = repo.history(repo.headId)
        if (commits.isEmpty()) {
            return io.fail("fatal: your current branch '${repo.currentBranch}' does not have any commits yet")
        }
        val oneLine = "--oneline" in args
        commits.forEachIndexed { i, commit ->
            val labels = branchLabels(commit.id, isHead = i == 0)
            if (oneLine) {
                io.out += "${commit.id}$labels ${commit.message}"
            } else {
                if (i > 0) io.out += ""
                io.out += "commit ${commit.id}$labels"
                if (commit.parents.size > 1) io.out += "Merge: ${commit.parents.joinToString(" ")}"
                io.out += "Author: Student <student@rocketdevops.local>"
                io.out += ""
                io.out += "    ${commit.message}"
            }
        }
    }

    /** " (HEAD -> main, login)" */
    private fun branchLabels(commitId: String, isHead: Boolean): String {
        val names = repo.branches.filter { it.value == commitId }.keys
        if (names.isEmpty()) return ""
        val ordered = names.sortedBy { it != repo.currentBranch }
            .map { if (isHead && it == repo.currentBranch) "HEAD -> $it" else it }
        return " (${ordered.joinToString(", ")})"
    }

    private fun branch(args: List<String>, io: Io) {
        val deleteFlag = args.firstOrNull { it == "-d" || it == "-D" || it == "--delete" }
        val names = args.filter { !it.startsWith("-") }

        when {
            deleteFlag != null -> {
                val name = names.firstOrNull() ?: return io.fail("fatal: branch name required")
                when {
                    name !in repo.branches -> io.fail("error: branch '$name' not found")
                    name == repo.currentBranch -> io.fail("error: cannot delete branch '$name' used by worktree at '${repo.root}'")
                    else -> {
                        val id = repo.branches.remove(name)
                        io.out += "Deleted branch $name (was ${id ?: "empty"})."
                    }
                }
            }
            names.isEmpty() -> repo.branches.keys.sorted().forEach { name ->
                io.out += if (name == repo.currentBranch) "* $name" else "  $name"
            }
            else -> createBranch(names.first(), io)
        }
    }

    /** Returns true if the branch was created. */
    private fun createBranch(name: String, io: Io): Boolean {
        val headId = repo.headId
        return when {
            !BRANCH_NAME.matches(name) -> { io.fail("fatal: '$name' is not a valid branch name"); false }
            name in repo.branches -> { io.fail("fatal: a branch named '$name' already exists"); false }
            headId == null -> { io.fail("fatal: not a valid object name: '${repo.currentBranch}'"); false }
            else -> { repo.branches[name] = headId; true }
        }
    }

    // ---------------------------------------------------------------- switch / checkout

    private fun switch(args: List<String>, io: Io, checkoutStyle: Boolean) {
        val createFlag = if (checkoutStyle) "-b" else "-c"
        val create = createFlag in args || (!checkoutStyle && "--create" in args)
        val name = args.firstOrNull { !it.startsWith("-") }
            ?: return io.fail(if (create) "error: switch `${createFlag.drop(1)}' requires a value" else "fatal: missing branch or commit argument")

        if (repo.merge != null) return io.fail("error: you need to resolve your current merge first")

        if (create) {
            if (repo.head == null) {
                // A repository with no commits: just rename the empty branch, like real git.
                if (name in repo.branches) return io.fail("fatal: a branch named '$name' already exists")
                repo.branches.remove(repo.currentBranch)
                repo.branches[name] = null
                repo.setCurrentBranch(name)
                io.out += "Switched to a new branch '$name'"
                return
            }
            if (!createBranch(name, io)) return
            repo.setCurrentBranch(name) // same commit, so files do not change
            io.out += "Switched to a new branch '$name'"
            return
        }

        if (name !in repo.branches) {
            return io.fail(
                if (checkoutStyle) "error: pathspec '$name' did not match any file(s) known to git"
                else "fatal: invalid reference: $name",
            )
        }
        if (name == repo.currentBranch) {
            io.out += "Already on '$name'"
            return
        }
        val target = repo.branches[name]?.let { repo.commits[it] }?.snapshot.orEmpty()
        if (target != repo.headSnapshot && repo.hasUncommittedChanges()) {
            io.fail("error: Your local changes would be overwritten by checkout.")
            io.err += "Please commit your changes before you switch branches."
            return
        }
        repo.checkoutSnapshot(from = repo.headSnapshot, to = target)
        repo.setCurrentBranch(name)
        io.out += "Switched to branch '$name'"
    }

    // ---------------------------------------------------------------- merge

    private fun merge(args: List<String>, io: Io) {
        if ("--abort" in args) {
            val merge = repo.merge ?: return io.fail("fatal: There is no merge to abort (MERGE_HEAD missing).")
            repo.checkoutSnapshot(from = repo.workingTree(), to = repo.headSnapshot)
            repo.finishMerge()
            io.out += "Merge of '${merge.otherBranch}' aborted."
            return
        }
        val name = args.firstOrNull { !it.startsWith("-") } ?: return io.fail("fatal: No remote for the current branch.")
        if (repo.merge != null) return io.fail("error: Merging is not possible because you have unmerged files.")
        val otherId = repo.branches[name]
        if (name !in repo.branches || otherId == null) return io.fail("merge: $name - not something we can merge")
        val ourId = repo.headId ?: return io.fail("fatal: your current branch has no commits to merge into")

        if (repo.hasUncommittedChanges()) {
            io.fail("error: Your local changes would be overwritten by merge.")
            io.err += "Please commit your changes before you merge."
            return
        }
        if (otherId == ourId || repo.isAncestor(otherId, ourId)) {
            io.out += "Already up to date."
            return
        }

        val ours = repo.headSnapshot
        val theirs = repo.commits.getValue(otherId).snapshot

        // Fast-forward: our branch is simply behind theirs.
        if (repo.isAncestor(ourId, otherId)) {
            repo.checkoutSnapshot(from = ours, to = theirs)
            repo.moveCurrentBranch(otherId)
            io.out += "Updating $ourId..$otherId"
            io.out += "Fast-forward"
            return
        }

        // Three-way merge against the newest common ancestor.
        val baseId = repo.history(ourId).map { it.id }.firstOrNull { repo.isAncestor(it, otherId) }
        val base = baseId?.let { repo.commits.getValue(it).snapshot }.orEmpty()
        val result = sortedMapOf<String, String>()
        val conflicts = mutableSetOf<String>()
        for (path in base.keys + ours.keys + theirs.keys) {
            val b = base[path]
            val o = ours[path]
            val t = theirs[path]
            val merged: String? = when {
                o == t -> o
                o == b -> t       // only they changed it
                t == b -> o       // only we changed it
                else -> {         // both changed it differently: conflict
                    conflicts += path
                    "<<<<<<< HEAD\n${o.orEmpty()}\n=======\n${t.orEmpty()}\n>>>>>>> $name"
                }
            }
            if (merged != null) result[path] = merged
        }

        repo.checkoutSnapshot(from = ours, to = result)
        conflicts.forEach { repo.index[it] = ours[it] ?: "" } // conflicted files stay as "ours" in the index
        conflicts.forEach { if (ours[it] == null) repo.index.remove(it) }

        if (conflicts.isEmpty()) {
            val commit = repo.createCommit("Merge branch '$name'", result, listOf(ourId, otherId))
            repo.moveCurrentBranch(commit.id)
            io.out += "Merge made by the 'ort' strategy."
        } else {
            repo.startMerge(MergeState(name, otherId, conflicts.toMutableSet()))
            // Written to the error stream with the failure line, so all three appear in Git's order.
            conflicts.sorted().forEach {
                io.err += "Auto-merging $it"
                io.err += "CONFLICT (content): Merge conflict in $it"
            }
            io.fail("Automatic merge failed; fix conflicts and then commit the result.")
        }
    }

    companion object {
        private val BRANCH_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._/-]*$")
    }
}
