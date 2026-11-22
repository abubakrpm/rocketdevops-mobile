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

/** The optional 30-second quiz timer and what happens when time runs out. */
class QuizTimerTest {

    @Test
    fun countsDownAndExpiresExactlyOnce() {
        val timer = QuizTimer(totalSeconds = 3)
        assertFalse(timer.tick())
        assertFalse(timer.tick())
        assertTrue(timer.tick())   // reaches 0
        assertFalse(timer.tick())  // stays at 0, does not fire again
        assertEquals(0, timer.secondsLeft)
    }

    @Test
    fun warnsInTheLastFiveSecondsAndResets() {
        val timer = QuizTimer(startAt = 6)
        assertFalse(timer.isRunningOut)
        timer.tick()
        assertTrue(timer.isRunningOut)
        timer.reset()
        assertEquals(QuizTimer.DEFAULT_SECONDS, timer.secondsLeft)
        assertEquals(1f, timer.fraction)
    }

    @Test
    fun restoredTimeIsKeptInRange() {
        assertEquals(30, QuizTimer(startAt = 99).secondsLeft)
        assertEquals(0, QuizTimer(startAt = -4).secondsLeft)
    }

    private val quiz = Quiz(
        "q", "Quiz",
        listOf(
            QuizQuestion("a", QuestionType.MULTIPLE_CHOICE, "Pick", options = listOf("x", "y"), correctAnswers = listOf("x"), explanation = "x"),
            QuizQuestion("b", QuestionType.TRUE_FALSE, "True?", correctAnswers = listOf("True"), explanation = "yes"),
        ),
    )

    @Test
    fun timingOutLocksTheQuestionAsWrong() {
        val session = QuizSession(quiz, seed = 1)
        val answer = session.timeOut()!!
        assertFalse(answer.correct)
        assertTrue(session.isLocked)
        assertNull(session.timeOut()) // already locked
        val screen = session.toScreenState(showInputError = false, isRetry = false)
        assertTrue(screen.timedOut)
        assertEquals(false, screen.lastAnswerCorrect)
        // The right option is still highlighted, nothing is marked as the learner's wrong pick.
        assertTrue(screen.options.none { it.state == QuizOptionState.Wrong })
    }

    @Test
    fun aTimedOutAnswerSurvivesRestore() {
        val first = QuizSession(quiz, seed = 1).apply { timeOut() }
        val restored = QuizSession(quiz, seed = 1).apply { restore(first.answers.map { it.given }, 0) }
        assertTrue(restored.toScreenState(false, false).timedOut)
        assertEquals(0, restored.score)
    }
}
