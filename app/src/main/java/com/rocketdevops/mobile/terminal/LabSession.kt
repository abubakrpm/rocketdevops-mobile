package com.rocketdevops.mobile.terminal

import com.rocketdevops.mobile.data.content.Lab
import com.rocketdevops.mobile.data.content.Objective
import com.rocketdevops.mobile.data.content.ObjectiveCheck
import com.rocketdevops.mobile.terminal.DockerDaemon.ImageRef

/**
 * Decides whether one lab objective is done, by looking at the simulated machine.
 * Called after every command.
 */
object ObjectiveChecker {

    private val regexCache = mutableMapOf<String, Regex>()

    private const val CONFLICT_MARKER = "<<<<<<<"

    fun isMet(check: ObjectiveCheck, engine: TerminalEngine): Boolean {
        val fs = engine.fs
        val git = engine.git
        val docker = engine.docker
        return when (check) {
            is ObjectiveCheck.CommandMatches -> {
                val regex = regexCache.getOrPut(check.pattern) { Regex(check.pattern) }
                engine.successfulCommands.any { regex.containsMatchIn(it) }
            }
            is ObjectiveCheck.CwdIs -> fs.cwd == fs.resolve(check.path)
            is ObjectiveCheck.PathExists ->
                if (check.directory) fs.dir(check.path) != null else fs.file(check.path) != null
            is ObjectiveCheck.FileContains -> fs.file(check.path)?.content?.contains(check.text) == true
            is ObjectiveCheck.FileNotContains -> fs.file(check.path)?.content?.contains(check.text) == false
            is ObjectiveCheck.FileMode -> fs.file(check.path)?.mode == check.mode
            is ObjectiveCheck.GitInitialized -> git.isInitialized
            is ObjectiveCheck.GitCommitCount -> git.commitCountOnCurrentBranch() >= check.atLeast
            is ObjectiveCheck.GitBranchExists -> check.name in git.branches
            is ObjectiveCheck.GitCurrentBranch -> git.isInitialized && git.currentBranch == check.name
            is ObjectiveCheck.GitMerged -> {
                val branchHead = git.branches[check.branch]
                val head = git.headId
                // Merged, finished, and no conflict markers were committed by mistake.
                branchHead != null && head != null && git.merge == null &&
                    git.currentBranch != check.branch && git.isAncestor(branchHead, head) &&
                    git.headSnapshot.values.none { it.contains(CONFLICT_MARKER) }
            }
            is ObjectiveCheck.ProcessStopped -> engine.processes.all.none { it.command.contains(check.command) }
            is ObjectiveCheck.DockerImagePresent ->
                docker?.hasRepository(ImageRef.parse(check.image).repository) == true
            is ObjectiveCheck.DockerContainerRunning -> {
                val repository = ImageRef.parse(check.image).repository
                val port = check.port?.let(DockerDaemon::parsePort)
                docker?.containers?.any { c ->
                    c.running && c.image.repository == repository && (port == null || port in c.ports)
                } == true
            }
        }
    }
}

/**
 * One attempt at a lab: the simulated terminal plus which objectives are done.
 *
 * Objectives are "sticky": once completed they stay green, even if a later command
 * undoes the change (for example cd back out of a folder). That matches how learners
 * think about a checklist.
 *
 * The lab screen (Stage 4) keeps one LabSession in its ViewModel.
 */
class LabSession(val lab: Lab) {

    var engine: TerminalEngine = TerminalEngine(lab.startingState)
        private set

    private val completed = linkedSetOf<String>()

    /** Ids of the objectives done so far, in the order they were completed. */
    val completedObjectiveIds: Set<String> get() = completed

    val isComplete: Boolean get() = completed.size == lab.objectives.size

    data class StepResult(
        val output: ExecutionResult,
        val newlyCompleted: List<Objective>, // turn these chips green (with animation)
        val labJustCompleted: Boolean,       // show the success screen
    )

    /** Runs one typed line and checks the objectives. */
    fun submit(input: String): StepResult {
        val wasComplete = isComplete
        val output = engine.execute(input)
        val newly = lab.objectives.filter { it.id !in completed && ObjectiveChecker.isMet(it.check, engine) }
        newly.forEach { completed += it.id }
        return StepResult(output, newly, labJustCompleted = !wasComplete && isComplete)
    }

    /** "Reset lab": a brand-new machine in the starting state, all objectives cleared. */
    fun reset() {
        engine = TerminalEngine(lab.startingState)
        completed.clear()
    }

    /**
     * Rebuilds the session from the commands typed before (the engine is deterministic,
     * so the result is identical). Used to restore a lab after Android killed the app.
     */
    fun replay(inputs: List<String>): List<StepResult> = inputs.map(::submit)
}
