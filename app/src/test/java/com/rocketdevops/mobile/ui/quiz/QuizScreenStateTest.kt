package com.rocketdevops.mobile.ui.quiz

import com.rocketdevops.mobile.data.content.QuestionType
import com.rocketdevops.mobile.data.content.Quiz
import com.rocketdevops.mobile.data.content.QuizQuestion
import com.rocketdevops.mobile.ui.components.QuizOptionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizScreenStateTest {

    private val quiz = Quiz(
        "q", "Docker basics",
        listOf(
            QuizQuestion("ps", QuestionType.MULTIPLE_CHOICE, "Lists running containers?",
                options = listOf("docker images", "docker ps", "docker ps -a"), correctAnswers = listOf("docker ps"),
                explanation = "ps lists running ones.", isCode = true),
        ),
    )

    @Test
    fun beforeAnsweringEveryOptionIsIdle() {
        val state = QuizSession(quiz, 1).toScreenState(showInputError = false, isRetry = false)
        assertTrue(state.options.all { it.state == QuizOptionState.Idle })
        assertFalse(state.isLocked)
        assertNull(state.lastAnswerCorrect)
        assertEquals(1, state.questionNumber)
        assertEquals(1, state.total)
    }

    @Test
    fun wrongPickIsRedAndTheRightOneIsStillShown() {
        val session = QuizSession(quiz, 1)
        session.answer("docker ps -a")
        val byText = session.toScreenState(false, false).options.associate { it.text to it.state }
        assertEquals(QuizOptionState.Wrong, byText["docker ps -a"])
        assertEquals(QuizOptionState.Correct, byText["docker ps"])
        assertEquals(QuizOptionState.Disabled, byText["docker images"])
    }

    @Test
    fun rightPickIsGreenAndScored() {
        val session = QuizSession(quiz, 1)
        session.answer("docker ps")
        val state = session.toScreenState(false, false)
        assertEquals(true, state.lastAnswerCorrect)
        assertEquals(1, state.score)
        assertEquals(1, state.answeredCount)
        assertTrue(state.isLastQuestion)
    }

    @Test
    fun typedQuestionsHaveNoOptions() {
        val typed = Quiz("t", "T", listOf(QuizQuestion("x", QuestionType.TYPE_THE_COMMAND, "Type pwd",
            correctAnswers = listOf("pwd"), explanation = "pwd prints the folder.")))
        val state = QuizSession(typed, 1).toScreenState(showInputError = true, isRetry = true)
        assertTrue(state.options.isEmpty())
        assertTrue(state.showInputError)
        assertEquals("pwd", state.correctAnswer)
    }
}
