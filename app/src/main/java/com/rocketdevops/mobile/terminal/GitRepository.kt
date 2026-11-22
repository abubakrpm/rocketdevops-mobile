package com.rocketdevops.mobile.terminal

import com.rocketdevops.mobile.data.content.GitSeed
import java.security.MessageDigest

/**
 * The state of a pretend Git repository, kept in memory.
 *
 * Simplified but faithful model of real Git:
 *  - a commit is a full snapshot of the files (relative path -> content) plus its parent commits
 *  - a branch is just a name pointing at a commit
 *  - the index (staging area) is the snapshot the NEXT commit will save
 *  - the working tree is whatever is in the VirtualFileSystem under the repository folder
 */
class GitRepository(private val fs: VirtualFileSystem) {

    data class Commit(
        val id: String,                       // short 7-character id, like git log --oneline shows
        val message: String,
        val snapshot: Map<String, String>,
        val parents: List<String>,
    )

    /** An unfinished merge after a conflict. */
    data class MergeState(val otherBranch: String, val otherHead: String, val conflicted: MutableSet<String>)

    /** Absolute path of the folder where `git init` ran. Null means "not a repository yet". */
    var root: String? = null
        private set

    val commits = mutableMapOf<String, Commit>()
    /** Branch name -> commit id it points at (null for a branch with no commits yet). */
    val branches = linkedMapOf<String, String?>()
    var currentBranch: String = DEFAULT_BRANCH
        private set
    val index = sortedMapOf<String, String>()
    var merge: MergeState? = null
        private set

    private var commitCounter = 0

    val isInitialized: Boolean get() = root != null

    // ---------------------------------------------------------------- basic info

    /** True if the current folder is inside the repository. */
    fun containsCwd(): Boolean = root?.let { fs.isSameOrInside(fs.cwd, it) } ?: false

    val headId: String? get() = branches[currentBranch]
    val head: Commit? get() = headId?.let { commits[it] }
    val headSnapshot: Map<String, String> get() = head?.snapshot.orEmpty()

    /** The files on disk right now (relative to the repository root), ignoring .git. */
    fun workingTree(): Map<String, String> = root?.let { fs.filesUnder(it, skip = setOf(GIT_DIR)) }.orEmpty()

    /** Commits reachable from a commit, newest first (follows every parent, like git log). */
    fun history(fromId: String?): List<Commit> {
        val result = mutableListOf<Commit>()
        val seen = mutableSetOf<String>()
        val queue = ArrayDeque(listOfNotNull(fromId))
        while (queue.isNotEmpty()) {
            val commit = commits[queue.removeFirst()] ?: continue
            if (seen.add(commit.id)) {
                result += commit
                queue.addAll(commit.parents)
            }
        }
        return result
    }

    fun commitCountOnCurrentBranch(): Int = history(headId).size

    fun isAncestor(ancestorId: String, ofId: String): Boolean = history(ofId).any { it.id == ancestorId }

    /** True when there are changes that are not committed yet (staged or not). Untracked files do not count. */
    fun hasUncommittedChanges(): Boolean {
        val working = workingTree()
        val tracked = index.keys + headSnapshot.keys
        return index != headSnapshot || tracked.any { working[it] != index[it] }
    }

    // ---------------------------------------------------------------- changes

    fun init(path: String) {
        root = path
        fs.mkdirs("$path/$GIT_DIR")
        branches.putIfAbsent(DEFAULT_BRANCH, null)
    }

    /** Builds the lab's starting repository: every seeded commit contains the starting files. */
    fun seed(seed: GitSeed, repositoryRoot: String) {
        init(repositoryRoot)
        branches.clear()
        currentBranch = seed.currentBranch
        val snapshot = workingTree()
        var parent: String? = null
        for (message in seed.commits) {
            parent = createCommit(message, snapshot, listOfNotNull(parent)).id
        }
        seed.branches.forEach { branches[it] = parent }
        index.clear()
        if (parent != null) index.putAll(snapshot)

        // Extra per-branch commits, then show the current branch's files on disk.
        if (seed.extraCommits.isNotEmpty()) {
            for (extra in seed.extraCommits) {
                val base = branches[extra.branch] ?: continue // the validator reports unknown branches
                val files = commits[base]?.snapshot.orEmpty() + extra.files
                branches[extra.branch] = createCommit(extra.message, files, listOf(base)).id
            }
            checkoutSnapshot(from = snapshot, to = headSnapshot)
        }
    }

    fun createCommit(message: String, snapshot: Map<String, String>, parents: List<String>): Commit {
        commitCounter++
        val id = shortHash("$message|$parents|$commitCounter")
        return Commit(id, message, snapshot.toSortedMap(), parents).also { commits[id] = it }
    }

    fun moveCurrentBranch(commitId: String) {
        branches[currentBranch] = commitId
    }

    fun setCurrentBranch(name: String) {
        currentBranch = name
    }

    /** Makes the working tree and index match a snapshot (used by switch, merge and --abort). */
    fun checkoutSnapshot(from: Map<String, String>, to: Map<String, String>) {
        val base = requireNotNull(root)
        for (path in from.keys - to.keys) fs.delete("$base/$path")
        for ((path, content) in to) {
            val absolute = "$base/$path"
            fs.mkdirs(fs.parentOf(absolute))
            fs.writeFile(absolute, content)
        }
        index.clear()
        index.putAll(to)
    }

    fun startMerge(state: MergeState) {
        merge = state
    }

    fun finishMerge() {
        merge = null
    }

    companion object {
        const val DEFAULT_BRANCH = "main"
        const val GIT_DIR = ".git"

        /** Deterministic fake commit id (7 hex characters), so tests and replays are repeatable. */
        fun shortHash(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(7)
    }
}
