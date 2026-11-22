package com.rocketdevops.mobile.ui.lesson

import com.rocketdevops.mobile.data.content.ContentBundle
import com.rocketdevops.mobile.data.content.ContentCatalog
import com.rocketdevops.mobile.data.content.Lab
import com.rocketdevops.mobile.data.content.Lesson
import com.rocketdevops.mobile.data.content.LessonBlock
import com.rocketdevops.mobile.data.content.Module
import com.rocketdevops.mobile.data.content.Objective
import com.rocketdevops.mobile.data.content.ObjectiveCheck
import com.rocketdevops.mobile.data.content.Quiz
import com.rocketdevops.mobile.data.content.Track
import com.rocketdevops.mobile.data.content.TrackIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextStepTest {

    private fun lesson(id: String) = Lesson(id, "Lesson $id", 3, listOf(LessonBlock.Paragraph("text")))
    private val lab = Lab("lab-1", "Lab one", "s", objectives = listOf(Objective("o", "O", ObjectiveCheck.GitInitialized)), hints = listOf("a", "b", "c"))

    private val catalog = ContentCatalog(
        ContentBundle(
            tracks = listOf(
                Track(
                    "t", "Track", "d", TrackIcon.TERMINAL,
                    listOf(
                        Module("m1", "One", lessons = listOf(lesson("a"), lesson("b")), quiz = Quiz("quiz-1", "Quiz one", emptyList()), labs = listOf(lab)),
                        Module("m2", "Two", lessons = listOf(lesson("c"))),
                    ),
                ),
            ),
            cheatSheet = emptyList(),
        ),
    )

    private fun next(lessonId: String) = nextStepAfterLesson(catalog.lesson(lessonId)!!)

    @Test
    fun nextLessonInTheSameModule() = assertEquals(NextStep.Lesson("b", "Lesson b"), next("a"))

    @Test
    fun afterTheLastLessonComesTheLab() = assertEquals(NextStep.Lab("lab-1", "Lab one"), next("b"))

    @Test
    fun theLastLessonOfTheTrackHasNoNextStep() = assertNull(next("c"))

    @Test
    fun withoutLabsTheQuizComesNext() {
        val noLabs = ContentCatalog(
            ContentBundle(
                listOf(Track("t", "T", "d", TrackIcon.TERMINAL, listOf(Module("m", "M", lessons = listOf(lesson("x")), quiz = Quiz("qz", "Quiz", emptyList()))))),
                emptyList(),
            ),
        )
        assertEquals(NextStep.Quiz("qz", "Quiz"), nextStepAfterLesson(noLabs.lesson("x")!!))
    }
}
