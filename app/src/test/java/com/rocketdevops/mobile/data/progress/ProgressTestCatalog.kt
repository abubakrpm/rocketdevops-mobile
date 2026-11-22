package com.rocketdevops.mobile.data.progress

import com.rocketdevops.mobile.data.content.ContentBundle
import com.rocketdevops.mobile.data.content.ContentCatalog
import com.rocketdevops.mobile.data.content.Lab
import com.rocketdevops.mobile.data.content.Lesson
import com.rocketdevops.mobile.data.content.LessonBlock
import com.rocketdevops.mobile.data.content.Module
import com.rocketdevops.mobile.data.content.Objective
import com.rocketdevops.mobile.data.content.ObjectiveCheck
import com.rocketdevops.mobile.data.content.QuestionType
import com.rocketdevops.mobile.data.content.Quiz
import com.rocketdevops.mobile.data.content.QuizQuestion
import com.rocketdevops.mobile.data.content.Track
import com.rocketdevops.mobile.data.content.TrackIcon

/** A small catalog for progress tests: Linux (2 modules) and Docker (1 module). */
object ProgressTestCatalog {
    private fun lesson(id: String, minutes: Int = 4) = Lesson(id, "Lesson $id", minutes, listOf(LessonBlock.Paragraph("p")))
    private fun lab(id: String) = Lab(id, "Lab $id", "s", objectives = listOf(Objective("o", "O", ObjectiveCheck.GitInitialized)), hints = listOf("a", "b", "c"))
    private fun quiz(id: String) = Quiz(id, "Quiz $id", listOf(
        QuizQuestion("$id-q1", QuestionType.TRUE_FALSE, "p", correctAnswers = listOf("True"), explanation = "e"),
        QuizQuestion("$id-q2", QuestionType.TRUE_FALSE, "p", correctAnswers = listOf("True"), explanation = "e"),
    ))

    val catalog = ContentCatalog(
        ContentBundle(
            listOf(
                Track("linux", "Linux basics", "d", TrackIcon.TERMINAL, listOf(
                    Module("l-m1", "Moving", lessons = listOf(lesson("l1")), quiz = quiz("l-quiz"), labs = listOf(lab("l-lab1"))),
                    Module("l-m2", "Permissions", lessons = listOf(lesson("l2")), labs = listOf(lab("l-lab2"))),
                )),
                Track("docker", "Docker", "d", TrackIcon.CONTAINER, listOf(
                    Module("d-m1", "Run", lessons = listOf(lesson("d1")), labs = listOf(lab("d-lab1"))),
                )),
            ),
            emptyList(),
        ),
    )

}
