package com.rocketdevops.mobile.data.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * CONTENT MODEL
 * -------------
 * These classes mirror the JSON files in app/src/main/assets/content.
 * @Serializable lets kotlinx.serialization turn JSON into these objects with no hand-written parsing.
 *
 * Shape:  Track -> Module -> (Lessons, Quiz, Labs)      plus a flat cheat sheet.
 * Every item has an `id` (lowercase-with-dashes) that is unique across ALL content,
 * so progress saved in Room (Stage 6) can point at any item by id alone.
 *
 * Fields with a default value (= ...) are optional in the JSON.
 */

/** Points the loader at the other content files. Lives at content/index.json. */
@Serializable
data class ContentIndex(
    val tracks: List<String>,   // paths relative to content/, in display order
    val cheatSheet: String,
)

@Serializable
enum class TrackIcon {
    @SerialName("terminal") TERMINAL,   // Linux
    @SerialName("branch") BRANCH,       // Git
    @SerialName("container") CONTAINER, // Docker
}

@Serializable
data class Track(
    val id: String,
    val title: String,
    val description: String,
    val icon: TrackIcon,
    val modules: List<Module>,
)

@Serializable
data class Module(
    val id: String,
    val title: String,
    val lessons: List<Lesson> = emptyList(),
    val quiz: Quiz? = null,
    val labs: List<Lab> = emptyList(),
)

// ---------------------------------------------------------------- Lessons

@Serializable
data class Lesson(
    val id: String,
    val title: String,
    val estimatedMinutes: Int,
    val body: List<LessonBlock>,
)

/**
 * One block of lesson content. The JSON "type" field picks the subclass:
 *   {"type": "heading", "text": "..."}
 *   {"type": "paragraph", "text": "..."}
 *   {"type": "code", "code": "ls -la", "caption": "optional"}
 *   {"type": "tip", "text": "..."}
 */
@Serializable
sealed interface LessonBlock {
    @Serializable @SerialName("heading")
    data class Heading(val text: String) : LessonBlock

    @Serializable @SerialName("paragraph")
    data class Paragraph(val text: String) : LessonBlock

    @Serializable @SerialName("code")
    data class Code(val code: String, val caption: String? = null) : LessonBlock

    @Serializable @SerialName("tip")
    data class Tip(val text: String) : LessonBlock
}

// ---------------------------------------------------------------- Quiz

@Serializable
data class Quiz(
    val id: String,
    val title: String,
    val questions: List<QuizQuestion>,
)

@Serializable
enum class QuestionType { MULTIPLE_CHOICE, TRUE_FALSE, TYPE_THE_COMMAND }

/**
 * @param options shown to the user (MULTIPLE_CHOICE). For TRUE_FALSE leave it out: the app shows True / False.
 * @param correctAnswers the exact option text(s) that are right, or for TYPE_THE_COMMAND the accepted commands.
 *        Answers are stored as text (not positions) so options can be shuffled safely.
 * @param acceptedPatterns optional regular expressions for TYPE_THE_COMMAND, for answers with many valid forms.
 * @param isCode render the options (or the typed answer) in the mono font.
 */
@Serializable
data class QuizQuestion(
    val id: String,
    val type: QuestionType,
    val prompt: String,
    val options: List<String> = emptyList(),
    val correctAnswers: List<String> = emptyList(),
    val acceptedPatterns: List<String> = emptyList(),
    val explanation: String,
    val isCode: Boolean = false,
) {
    /** The options the UI should show. TRUE_FALSE questions always get these two. */
    val displayOptions: List<String>
        get() = if (type == QuestionType.TRUE_FALSE) TRUE_FALSE_OPTIONS else options

    companion object {
        val TRUE_FALSE_OPTIONS = listOf("True", "False")
    }
}

// ---------------------------------------------------------------- Labs

/**
 * A hands-on lab in the simulated terminal.
 * @param startingState what the fake machine looks like when the lab opens (and after "Reset lab").
 * @param hints exactly three, from a gentle nudge to almost the answer.
 * @param solution commands that finish the lab. Not shown to learners: a unit test runs them
 *        in the simulator to prove every lab can actually be completed.
 */
@Serializable
data class Lab(
    val id: String,
    val title: String,
    val scenario: String,
    val startingState: LabEnvironment = LabEnvironment(),
    val objectives: List<Objective>,
    val hints: List<String>,
    val solution: List<String> = emptyList(),
)

/** The simulated machine. Nothing here ever touches the real phone. */
@Serializable
data class LabEnvironment(
    val cwd: String = "/home/student",
    val files: List<FsEntry> = emptyList(),
    val git: GitSeed? = null,       // null = no repository yet (the user may run "git init")
    val docker: DockerSeed? = null, // null = Docker is not part of this lab
    val processes: List<ProcessSeed> = emptyList(), // extra programs shown by `ps aux` (Linux processes lab)
)

/**
 * A pretend running program for `ps`, `kill`, `pgrep` and `pkill`. Nothing really runs.
 * @param user "root" processes cannot be stopped by the learner ("Operation not permitted").
 * @param ignoresTerm true = ignores a normal `kill` (SIGTERM) and needs `kill -9`.
 */
@Serializable
data class ProcessSeed(
    val pid: Int,
    val command: String,
    val user: String = "student",
    val cpu: String = "0.0",
    val mem: String = "0.1",
    val ignoresTerm: Boolean = false,
)

/** A file or folder in the fake filesystem. Parent folders are created automatically. */
@Serializable
data class FsEntry(
    val path: String,              // absolute, e.g. "/home/student/notes.txt"
    val directory: Boolean = false,
    val content: String = "",
    val mode: String? = null,      // permissions like "644"; default chosen by the engine
)

@Serializable
data class GitSeed(
    val currentBranch: String = "main",
    val branches: List<String> = listOf("main"),
    val commits: List<String> = emptyList(), // commit messages, oldest first
    /** Extra commits on top, per branch, so branches can differ (e.g. a ready-made merge conflict). */
    val extraCommits: List<CommitSeed> = emptyList(),
)

/**
 * One extra commit on `branch`. `files` maps paths RELATIVE to the repository
 * (e.g. "README.md") to their new content. The learner starts on GitSeed.currentBranch.
 */
@Serializable
data class CommitSeed(
    val branch: String,
    val message: String,
    val files: Map<String, String>,
)

/**
 * @param registryImages images a "docker pull" can download (the fake Docker Hub).
 * @param localImages images already on the fake machine.
 */
@Serializable
data class DockerSeed(
    val registryImages: List<String> = emptyList(),
    val localImages: List<String> = emptyList(),
    val containers: List<ContainerSeed> = emptyList(),
)

@Serializable
data class ContainerSeed(
    val name: String,
    val image: String,
    val running: Boolean = true,
    val ports: List<String> = emptyList(), // "host:container", e.g. "8080:80"
)

/** One goal of a lab. Shown as a chip that turns green when `check` passes. */
@Serializable
data class Objective(
    val id: String,
    val label: String,   // short chip text, e.g. "Map port"
    val check: ObjectiveCheck,
)

/**
 * How the lab knows an objective is done. Checked after every command (Stage 4).
 * Most checks look at the final STATE (better: any correct command counts);
 * "command" checks look at WHAT was typed (for read-only commands like pwd or docker ps).
 */
@Serializable
sealed interface ObjectiveCheck {
    /** Any command typed so far matches this regular expression. */
    @Serializable @SerialName("command")
    data class CommandMatches(val pattern: String) : ObjectiveCheck

    /** The shell is currently in this folder. */
    @Serializable @SerialName("cwd")
    data class CwdIs(val path: String) : ObjectiveCheck

    /** A file (or folder, if directory = true) exists. */
    @Serializable @SerialName("path-exists")
    data class PathExists(val path: String, val directory: Boolean = false) : ObjectiveCheck

    /** A file exists and contains this text. */
    @Serializable @SerialName("file-contains")
    data class FileContains(val path: String, val text: String) : ObjectiveCheck

    /** A file exists and does NOT contain this text (e.g. conflict markers are gone). */
    @Serializable @SerialName("file-not-contains")
    data class FileNotContains(val path: String, val text: String) : ObjectiveCheck

    /** A file has exactly these permissions, e.g. "755". */
    @Serializable @SerialName("file-mode")
    data class FileMode(val path: String, val mode: String) : ObjectiveCheck

    @Serializable @SerialName("git-initialized")
    data object GitInitialized : ObjectiveCheck

    @Serializable @SerialName("git-commit-count")
    data class GitCommitCount(val atLeast: Int) : ObjectiveCheck

    @Serializable @SerialName("git-branch-exists")
    data class GitBranchExists(val name: String) : ObjectiveCheck

    /** This branch's work is part of the current branch, no merge is half-done, and no conflict markers were committed. */
    @Serializable @SerialName("git-merged")
    data class GitMerged(val branch: String) : ObjectiveCheck

    @Serializable @SerialName("git-current-branch")
    data class GitCurrentBranch(val name: String) : ObjectiveCheck

    @Serializable @SerialName("docker-image-present")
    data class DockerImagePresent(val image: String) : ObjectiveCheck

    /** A running container from this image; if `port` is set it must be published, e.g. "8080:80". */
    @Serializable @SerialName("docker-container-running")
    data class DockerContainerRunning(val image: String, val port: String? = null) : ObjectiveCheck

    /** No running process has this text in its command (the learner stopped it). */
    @Serializable @SerialName("process-stopped")
    data class ProcessStopped(val command: String) : ObjectiveCheck
}

// ---------------------------------------------------------------- Cheat sheet

@Serializable
enum class CheatCategory {
    @SerialName("linux") LINUX,
    @SerialName("git") GIT,
    @SerialName("docker") DOCKER,
}

@Serializable
data class CheatFlag(val flag: String, val meaning: String)

@Serializable
data class CheatEntry(
    val id: String,
    val command: String,
    val description: String,
    val example: String,
    val commonFlags: List<CheatFlag> = emptyList(),
    val category: CheatCategory,
)

/** Everything parsed from assets/content, before indexing. */
data class ContentBundle(
    val tracks: List<Track>,
    val cheatSheet: List<CheatEntry>,
)
