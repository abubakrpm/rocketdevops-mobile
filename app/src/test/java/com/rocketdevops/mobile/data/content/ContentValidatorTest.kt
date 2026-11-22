package com.rocketdevops.mobile.data.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Each test breaks ONE rule on an otherwise valid bundle and checks the validator reports it.
 * This proves the validator really catches mistakes (a validator that never complains would
 * pass ContentFilesTest too).
 */
class ContentValidatorTest {

    // ---------- A small, valid bundle to start from

    private fun question(
        id: String = "q-1",
        type: QuestionType = QuestionType.MULTIPLE_CHOICE,
        options: List<String> = listOf("docker ps", "docker images"),
        correct: List<String> = listOf("docker ps"),
        patterns: List<String> = emptyList(),
    ) = QuizQuestion(id, type, "Which lists containers?", options, correct, patterns, "Because.")

    private fun lab(
        id: String = "lab-1",
        hints: List<String> = listOf("one", "two", "three"),
        objectives: List<Objective> = listOf(Objective("o1", "Print location", ObjectiveCheck.CommandMatches("^pwd$"))),
        env: LabEnvironment = LabEnvironment(),
    ) = Lab(id, "A lab", "Do things.", env, objectives, hints, solution = listOf("pwd"))

    private fun bundle(
        questions: List<QuizQuestion> = listOf(question()),
        labs: List<Lab> = listOf(lab()),
        lessonId: String = "lesson-1",
        cheat: List<CheatEntry> = emptyList(),
    ) = ContentBundle(
        tracks = listOf(
            Track(
                id = "track-1", title = "Track", description = "About.", icon = TrackIcon.TERMINAL,
                modules = listOf(
                    Module(
                        id = "module-1", title = "Module",
                        lessons = listOf(Lesson(lessonId, "Lesson", 3, listOf(LessonBlock.Paragraph("Hello")))),
                        quiz = Quiz("quiz-1", "Quiz", questions),
                        labs = labs,
                    ),
                ),
            ),
        ),
        cheatSheet = cheat,
    )

    private fun issuesOf(bundle: ContentBundle) = ContentValidator.validate(bundle).map { it.toString() }

    private fun assertReported(bundle: ContentBundle, expectedFragment: String) {
        val issues = issuesOf(bundle)
        assertTrue(
            "Expected an issue containing \"$expectedFragment\" but got: $issues",
            issues.any { it.contains(expectedFragment) },
        )
    }

    // ---------- Tests

    @Test
    fun validBundleHasNoIssues() {
        assertEquals(emptyList<String>(), issuesOf(bundle()))
    }

    @Test
    fun duplicateIdsAcrossDifferentKindsAreReported() {
        // A lesson and a quiz question sharing one id would confuse saved progress.
        assertReported(bundle(lessonId = "q-1"), "duplicate id")
    }

    @Test
    fun badIdFormatIsReported() {
        assertReported(bundle(labs = listOf(lab(id = "Lab One"))), "lowercase letters")
    }

    @Test
    fun correctAnswerMustBeAnOption() {
        assertReported(bundle(questions = listOf(question(correct = listOf("docker run")))), "is not one of the options")
    }

    @Test
    fun multipleChoiceNeedsACorrectAnswer() {
        assertReported(bundle(questions = listOf(question(correct = emptyList()))), "has no correct answer")
    }

    @Test
    fun trueFalseNeedsTrueOrFalse() {
        val q = question(type = QuestionType.TRUE_FALSE, options = emptyList(), correct = listOf("Yes"))
        assertReported(bundle(questions = listOf(q)), "\"True\" or \"False\"")
    }

    @Test
    fun typeTheCommandNeedsAnAnswerOrPattern() {
        val q = question(type = QuestionType.TYPE_THE_COMMAND, options = emptyList(), correct = emptyList())
        assertReported(bundle(questions = listOf(q)), "no correct answer or accepted pattern")
    }

    @Test
    fun brokenRegexIsReported() {
        val q = question(type = QuestionType.TYPE_THE_COMMAND, options = emptyList(), correct = emptyList(), patterns = listOf("^docker ("))
        assertReported(bundle(questions = listOf(q)), "invalid regular expression")
    }

    @Test
    fun labNeedsExactlyThreeHints() {
        assertReported(bundle(labs = listOf(lab(hints = listOf("only one")))), "exactly 3 hints")
    }

    @Test
    fun labNeedsASolution() {
        assertReported(bundle(labs = listOf(lab().copy(solution = emptyList()))), "needs a solution")
    }

    @Test
    fun labNeedsObjectives() {
        assertReported(bundle(labs = listOf(lab(objectives = emptyList()))), "has no objectives")
    }

    @Test
    fun relativePathsAreReported() {
        val check = Objective("o1", "Notes", ObjectiveCheck.PathExists("notes", directory = true))
        assertReported(bundle(labs = listOf(lab(objectives = listOf(check)))), "must start with /")
    }

    @Test
    fun dockerImageMustBeAvailableInTheLab() {
        // The lab asks for nginx but its fake registry only has redis: impossible to finish.
        val env = LabEnvironment(docker = DockerSeed(registryImages = listOf("redis")))
        val check = Objective("o1", "Pull image", ObjectiveCheck.DockerImagePresent("nginx"))
        assertReported(bundle(labs = listOf(lab(env = env, objectives = listOf(check)))), "is not in the lab's registryImages")
    }

    @Test
    fun badPortFormatIsReported() {
        val env = LabEnvironment(docker = DockerSeed(registryImages = listOf("nginx")))
        val check = Objective("o1", "Map port", ObjectiveCheck.DockerContainerRunning("nginx", port = "8080"))
        assertReported(bundle(labs = listOf(lab(env = env, objectives = listOf(check)))), "invalid port")
    }

    @Test
    fun gitCurrentBranchMustExist() {
        val env = LabEnvironment(git = GitSeed(currentBranch = "dev", branches = listOf("main")))
        assertReported(bundle(labs = listOf(lab(env = env))), "is not in branches")
    }

    @Test
    fun duplicateObjectiveIdsInOneLabAreReported() {
        val o = Objective("same", "Print location", ObjectiveCheck.CommandMatches("^pwd$"))
        assertReported(bundle(labs = listOf(lab(objectives = listOf(o, o)))), "duplicate objective id")
    }

    @Test
    fun blankCheatSheetFieldsAreReported() {
        val entry = CheatEntry("cheat-1", "ls", "", "ls -la", category = CheatCategory.LINUX)
        assertReported(bundle(cheat = listOf(entry)), "description is empty")
    }

    // ---------- Stage 8 lab features

    private fun labWith(env: LabEnvironment, check: ObjectiveCheck, solution: List<String> = listOf("pwd")) =
        Lab("lab-x", "A lab", "Do things.", env, listOf(Objective("o1", "Goal", check)), listOf("a", "b", "c"), solution)

    @Test
    fun processStoppedNeedsAStartingProcess() {
        val lab = labWith(LabEnvironment(), ObjectiveCheck.ProcessStopped("backup.py"))
        assertReported(bundle(labs = listOf(lab)), "no starting process runs")
    }

    @Test
    fun reservedPidsAndDuplicatePidsAreReported() {
        val env = LabEnvironment(processes = listOf(ProcessSeed(1000, "app"), ProcessSeed(42, "a"), ProcessSeed(42, "b")))
        val issues = issuesOf(bundle(labs = listOf(labWith(env, ObjectiveCheck.ProcessStopped("app")))))
        assertTrue(issues.toString(), issues.any { it.contains("reserved PID") })
        assertTrue(issues.toString(), issues.any { it.contains("listed twice") })
    }

    @Test
    fun extraCommitsMustUseKnownBranchesAndRelativePaths() {
        val git = GitSeed(
            branches = listOf("main"), commits = listOf("Start"),
            extraCommits = listOf(CommitSeed("feature", "Work", mapOf("/abs/file.txt" to "x"))),
        )
        val issues = issuesOf(bundle(labs = listOf(labWith(LabEnvironment(git = git), ObjectiveCheck.GitMerged("feature")))))
        assertTrue(issues.toString(), issues.any { it.contains("unknown branch") })
        assertTrue(issues.toString(), issues.any { it.contains("must be relative") })
    }

    @Test
    fun anImageTheSolutionBuildsCountsAsAvailable() {
        val env = LabEnvironment(docker = DockerSeed(registryImages = listOf("nginx")))
        val check = ObjectiveCheck.DockerImagePresent("mysite")
        val built = labWith(env, check, solution = listOf("echo \"FROM nginx\" > Dockerfile", "docker build -t mysite ."))
        assertTrue(issuesOf(bundle(labs = listOf(built))).none { it.contains("mysite") })
        val notBuilt = labWith(env, check)
        assertReported(bundle(labs = listOf(notBuilt)), "image \"mysite\"")
    }
}
