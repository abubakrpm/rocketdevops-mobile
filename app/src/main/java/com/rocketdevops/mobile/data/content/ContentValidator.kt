package com.rocketdevops.mobile.data.content

import java.util.regex.PatternSyntaxException

/** One problem found in the content, with where it is. Example: "lab docker-lab-run: needs exactly 3 hints". */
data class ContentIssue(val location: String, val message: String) {
    override fun toString() = "$location: $message"
}

/**
 * Checks content rules that JSON parsing alone cannot catch, so a typo in a JSON file
 * is reported clearly (in a unit test, or on the error screen) instead of breaking a lab later.
 *
 * Rules:
 *  - ids are lowercase-with-dashes and unique across ALL content
 *  - titles, prompts and explanations are not blank
 *  - every question has a correct answer, and for choice questions it is one of the options
 *  - every regular expression compiles
 *  - every lab has objectives, exactly 3 hints, a solution, absolute paths, and can actually be solved
 *    (for example, a Docker image it asks for exists in the lab's fake registry)
 *    LabSolutionsTest then runs each solution in the simulator to prove it really finishes the lab.
 */
object ContentValidator {

    /** Finds "myapp" in "docker build -t myapp ." */
    private val BUILD_TAG = Regex("""docker build .*(?:-t|--tag) ([a-z0-9][a-z0-9._/-]*(?::[A-Za-z0-9._-]+)?)""")
    const val HINT_COUNT = 3
    private val ID_FORMAT = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
    private val MODE_FORMAT = Regex("^[0-7]{3}$")
    private val PORT_FORMAT = Regex("^\\d{1,5}:\\d{1,5}$")

    fun validate(bundle: ContentBundle): List<ContentIssue> {
        val c = Checker()
        if (bundle.tracks.isEmpty()) c.issue("content", "no tracks found")

        for (track in bundle.tracks) {
            val where = "track ${track.id}"
            c.id("track", track.id)
            c.notBlank(where, "title", track.title)
            c.notBlank(where, "description", track.description)
            if (track.modules.isEmpty()) c.issue(where, "has no modules")

            for (module in track.modules) {
                val m = "module ${module.id}"
                c.id("module", module.id)
                c.notBlank(m, "title", module.title)
                if (module.lessons.isEmpty() && module.quiz == null && module.labs.isEmpty()) {
                    c.issue(m, "is empty (no lessons, quiz or labs)")
                }
                module.lessons.forEach { validateLesson(it, c) }
                module.quiz?.let { validateQuiz(it, c) }
                module.labs.forEach { validateLab(it, c) }
            }
        }

        for (entry in bundle.cheatSheet) {
            val where = "cheat ${entry.id}"
            c.id("cheat", entry.id)
            c.notBlank(where, "command", entry.command)
            c.notBlank(where, "description", entry.description)
            c.notBlank(where, "example", entry.example)
        }
        return c.issues
    }

    private fun validateLesson(lesson: Lesson, c: Checker) {
        val where = "lesson ${lesson.id}"
        c.id("lesson", lesson.id)
        c.notBlank(where, "title", lesson.title)
        if (lesson.estimatedMinutes <= 0) c.issue(where, "estimatedMinutes must be more than 0")
        if (lesson.body.isEmpty()) c.issue(where, "body is empty")
        lesson.body.forEachIndexed { i, block ->
            val text = when (block) {
                is LessonBlock.Heading -> block.text
                is LessonBlock.Paragraph -> block.text
                is LessonBlock.Code -> block.code
                is LessonBlock.Tip -> block.text
            }
            c.notBlank(where, "block ${i + 1}", text)
        }
    }

    private fun validateQuiz(quiz: Quiz, c: Checker) {
        val where = "quiz ${quiz.id}"
        c.id("quiz", quiz.id)
        c.notBlank(where, "title", quiz.title)
        if (quiz.questions.isEmpty()) c.issue(where, "has no questions")
        quiz.questions.forEach { validateQuestion(it, c) }
    }

    private fun validateQuestion(question: QuizQuestion, c: Checker) {
        val where = "question ${question.id}"
        c.id("question", question.id)
        c.notBlank(where, "prompt", question.prompt)
        c.notBlank(where, "explanation", question.explanation)

        when (question.type) {
            QuestionType.MULTIPLE_CHOICE -> {
                if (question.options.size < 2) c.issue(where, "needs at least 2 options")
                if (question.options.toSet().size != question.options.size) c.issue(where, "has duplicate options")
                if (question.correctAnswers.isEmpty()) c.issue(where, "has no correct answer")
                question.correctAnswers
                    .filter { it !in question.options }
                    .forEach { c.issue(where, "correct answer \"$it\" is not one of the options") }
            }
            QuestionType.TRUE_FALSE -> {
                if (question.options.isNotEmpty()) c.issue(where, "TRUE_FALSE questions must not list options")
                val answer = question.correctAnswers.singleOrNull()
                if (answer !in QuizQuestion.TRUE_FALSE_OPTIONS) {
                    c.issue(where, "TRUE_FALSE needs exactly one correct answer: \"True\" or \"False\"")
                }
            }
            QuestionType.TYPE_THE_COMMAND -> {
                if (question.correctAnswers.isEmpty() && question.acceptedPatterns.isEmpty()) {
                    c.issue(where, "has no correct answer or accepted pattern")
                }
                if (question.correctAnswers.any { it.isBlank() }) c.issue(where, "has a blank correct answer")
            }
        }
        question.acceptedPatterns.forEach { c.regex(where, it) }
    }

    private fun validateLab(lab: Lab, c: Checker) {
        val where = "lab ${lab.id}"
        c.id("lab", lab.id)
        c.notBlank(where, "title", lab.title)
        c.notBlank(where, "scenario", lab.scenario)
        if (lab.hints.size != HINT_COUNT) c.issue(where, "needs exactly $HINT_COUNT hints (has ${lab.hints.size})")
        lab.hints.forEachIndexed { i, hint -> c.notBlank(where, "hint ${i + 1}", hint) }
        if (lab.solution.isEmpty()) c.issue(where, "needs a solution (the commands that finish it)")

        // --- Starting state
        val env = lab.startingState
        c.absolutePath(where, env.cwd)
        env.files.groupBy { it.path }
            .filter { it.value.size > 1 }
            .keys.forEach { c.issue(where, "file \"$it\" is listed twice") }
        env.files.forEach { file ->
            c.absolutePath(where, file.path)
            file.mode?.let { if (!MODE_FORMAT.matches(it)) c.issue(where, "file ${file.path} has invalid mode \"$it\"") }
        }
        env.git?.let { git ->
            if (git.currentBranch !in git.branches) {
                c.issue(where, "git currentBranch \"${git.currentBranch}\" is not in branches")
            }
            if (git.extraCommits.isNotEmpty() && git.commits.isEmpty()) {
                c.issue(where, "git extraCommits need at least one base commit in \"commits\"")
            }
            git.extraCommits.forEach { extra ->
                if (extra.branch !in git.branches) c.issue(where, "git extra commit \"${extra.message}\" is on unknown branch \"${extra.branch}\"")
                if (extra.files.isEmpty()) c.issue(where, "git extra commit \"${extra.message}\" changes no files")
                extra.files.keys.filter { it.startsWith("/") }
                    .forEach { c.issue(where, "git extra commit file \"$it\" must be relative to the repository") }
            }
        }
        env.processes.groupBy { it.pid }.filter { it.value.size > 1 }.keys
            .forEach { c.issue(where, "process id $it is listed twice") }
        env.processes.filter { it.pid < 1 || it.pid == 1000 || it.pid == 1001 }
            .forEach { c.issue(where, "process ${it.command} uses a reserved PID ${it.pid} (1000 and 1001 are taken by the shell and ps)") }
        // Images the lab can use: its fake registry, images already on the machine,
        // and images the solution builds itself (docker build -t myapp .).
        val builtImages = lab.solution.mapNotNull { BUILD_TAG.find(it)?.groupValues?.get(1) }
        val availableImages = env.docker?.let { it.registryImages + it.localImages }.orEmpty() + builtImages
        env.docker?.containers?.forEach { container ->
            container.ports.filterNot { PORT_FORMAT.matches(it) }
                .forEach { c.issue(where, "container ${container.name} has invalid port \"$it\"") }
        }

        // --- Objectives
        if (lab.objectives.isEmpty()) c.issue(where, "has no objectives")
        val objectiveIds = mutableSetOf<String>()
        for (objective in lab.objectives) {
            val o = "$where objective ${objective.id}"
            if (!objectiveIds.add(objective.id)) c.issue(o, "duplicate objective id in this lab")
            c.notBlank(o, "label", objective.label)
            when (val check = objective.check) {
                is ObjectiveCheck.CommandMatches -> c.regex(o, check.pattern)
                is ObjectiveCheck.CwdIs -> c.absolutePath(o, check.path)
                is ObjectiveCheck.PathExists -> c.absolutePath(o, check.path)
                is ObjectiveCheck.FileContains -> {
                    c.absolutePath(o, check.path)
                    c.notBlank(o, "text", check.text)
                }
                is ObjectiveCheck.FileMode -> {
                    c.absolutePath(o, check.path)
                    if (!MODE_FORMAT.matches(check.mode)) c.issue(o, "invalid mode \"${check.mode}\"")
                }
                is ObjectiveCheck.FileNotContains -> {
                    c.absolutePath(o, check.path)
                    c.notBlank(o, "text", check.text)
                }
                is ObjectiveCheck.GitInitialized -> Unit
                is ObjectiveCheck.GitMerged -> c.notBlank(o, "branch", check.branch)
                is ObjectiveCheck.ProcessStopped -> {
                    c.notBlank(o, "command", check.command)
                    if (lab.startingState.processes.none { it.command.contains(check.command) }) {
                        c.issue(o, "no starting process runs \"${check.command}\", so this objective would be done at once")
                    }
                }
                is ObjectiveCheck.GitCommitCount -> if (check.atLeast < 1) c.issue(o, "atLeast must be 1 or more")
                is ObjectiveCheck.GitBranchExists -> c.notBlank(o, "name", check.name)
                is ObjectiveCheck.GitCurrentBranch -> c.notBlank(o, "name", check.name)
                is ObjectiveCheck.DockerImagePresent -> c.imageAvailable(o, check.image, availableImages)
                is ObjectiveCheck.DockerContainerRunning -> {
                    c.imageAvailable(o, check.image, availableImages)
                    check.port?.let {
                        if (!PORT_FORMAT.matches(it)) c.issue(o, "invalid port \"$it\", use host:container like 8080:80")
                    }
                }
            }
        }
    }

    /** Collects issues and remembers every id seen so far (to catch duplicates). */
    private class Checker {
        val issues = mutableListOf<ContentIssue>()
        private val seenIds = mutableMapOf<String, String>() // id -> where it was first used

        fun issue(location: String, message: String) {
            issues += ContentIssue(location, message)
        }

        fun id(kind: String, id: String) {
            val location = "$kind $id"
            if (!ID_FORMAT.matches(id)) issue(location, "id must be lowercase letters, digits and dashes")
            val firstUse = seenIds.putIfAbsent(id, location)
            if (firstUse != null) issue(location, "duplicate id, already used by $firstUse")
        }

        fun notBlank(location: String, field: String, value: String) {
            if (value.isBlank()) issue(location, "$field is empty")
        }

        fun regex(location: String, pattern: String) {
            try {
                Regex(pattern)
            } catch (e: PatternSyntaxException) {
                issue(location, "invalid regular expression \"$pattern\" (${e.description})")
            }
        }

        fun absolutePath(location: String, path: String) {
            if (!path.startsWith("/")) issue(location, "path \"$path\" must start with /")
        }

        fun imageAvailable(location: String, image: String, available: List<String>) {
            if (image !in available) issue(location, "image \"$image\" is not in the lab's registryImages or localImages")
        }
    }
}
