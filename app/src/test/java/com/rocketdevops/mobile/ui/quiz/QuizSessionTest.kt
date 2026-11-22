package com.rocketdevops.mobile.ui.quiz

import com.rocketdevops.mobile.data.content.QuestionType
import com.rocketdevops.mobile.data.content.Quiz
import com.rocketdevops.mobile.data.content.QuizQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizSessionTest {

    private val quiz = Quiz(
        id = "docker-quiz",
        title = "Docker basics",
        questions = listOf(
            QuizQuestion("q-ps", QuestionType.MULTIPLE_CHOICE, "Lists running containers?",
                options = listOf("docker images", "docker ps", "docker ps -a", "docker run"),
                correctAnswers = listOf("docker ps"), explanation = "ps shows running ones."),
            QuizQuestion("q-tf", QuestionType.TRUE_FALSE, "-d deletes the container.",
                correctAnswers = listOf("False"), explanation = "-d means detached."),
            QuizQuestion("q-type", QuestionType.TYPE_THE_COMMAND, "Logs of api?",
                correctAnswers = listOf("docker logs api"),
                acceptedPatterns = listOf("^docker\\s+(container\\s+)?logs\\s+(-f\\s+)?api$"),
                explanation = "docker logs prints output."),
            QuizQuestion("q-port", QuestionType.MULTIPLE_CHOICE, "Host port in 8080:80?",
                options = listOf("8080", "80"), correctAnswers = listOf("8080"), explanation = "host:container."),
        ),
    )

    private fun questionIds(s: QuizSession) = s.questions.map { it.question.id }

    /** Answers every question correctly or wrongly, by id. */
    private fun answerAll(s: QuizSession, correct: Set<String>) {
        while (true) {
            val q = s.current.question
            s.answer(if (q.id in correct) q.correctAnswers.first() else "wrong")
            if (!s.next()) break
        }
    }

    @Test
    fun sameSeedGivesSameOrderAndDifferentSeedsShuffle() {
        assertEquals(questionIds(QuizSession(quiz, seed = 7)), questionIds(QuizSession(quiz, seed = 7)))
        val orders = (1L..20L).map { questionIds(QuizSession(quiz, it)) }.toSet()
        assertTrue("expected several different orders", orders.size > 1)
        orders.forEach { assertEquals(quiz.questions.map { it.id }.toSet(), it.toSet()) } // nothing lost
    }

    @Test
    fun optionsAreShuffledButTrueFalseKeepsItsOrder() {
        for (seed in 1L..10L) {
            val session = QuizSession(quiz, seed)
            val tf = session.questions.first { it.question.id == "q-tf" }
            assertEquals(listOf("True", "False"), tf.options)
            val mc = session.questions.first { it.question.id == "q-ps" }
            assertEquals(quiz.questions[0].options.toSet(), mc.options.toSet())
        }
    }

    @Test
    fun answeringLocksTheQuestion() {
        val s = QuizSession(quiz, seed = 1)
        assertFalse(s.isLocked)
        assertFalse("cannot skip an unanswered question", s.next())
        s.answer(s.current.question.correctAnswers.first())
        assertTrue(s.isLocked)
        assertNull("a locked answer cannot change", s.answer("something else"))
        assertEquals(1, s.answers.size)
    }

    @Test
    fun scoreStreakAndResult() {
        val s = QuizSession(quiz, seed = 3)
        answerAll(s, correct = setOf("q-ps", "q-tf", "q-type"))
        assertTrue(s.isFinished)
        assertEquals(3, s.score)
        assertEquals(QuizResult(3, 4, listOf("q-port")), s.result())
    }

    @Test
    fun streakCountsCorrectAnswersInARow() {
        val s = QuizSession(quiz, seed = 3)
        val first = s.current.question
        s.answer("wrong")
        assertEquals(0, s.streak)
        s.next()
        s.answer(s.current.question.correctAnswers.first())
        s.next()
        s.answer(s.current.question.correctAnswers.first())
        assertEquals(2, s.streak)
        assertTrue(first.id in s.result().wrongQuestionIds)
    }

    @Test
    fun typedAnswersIgnoreExtraSpacesAndAcceptPatterns() {
        val q = quiz.questions.first { it.type == QuestionType.TYPE_THE_COMMAND }
        assertTrue(QuizSession.isCorrect(q, "docker logs api"))
        assertTrue(QuizSession.isCorrect(q, "  docker   logs  api "))
        assertTrue(QuizSession.isCorrect(q, "docker logs -f api"))         // via acceptedPatterns
        assertTrue(QuizSession.isCorrect(q, "docker container logs api"))
        assertFalse(QuizSession.isCorrect(q, "docker log api"))
        assertFalse(QuizSession.isCorrect(q, "Docker logs api"))           // commands are case-sensitive
    }

    @Test
    fun retryLimitsTheQuestions() {
        val s = QuizSession(quiz, seed = 5, onlyQuestionIds = listOf("q-port", "q-tf"))
        assertEquals(setOf("q-port", "q-tf"), questionIds(s).toSet())
        assertEquals(2, s.total)
    }

    @Test
    fun restoreRebuildsTheSameAttempt() {
        val original = QuizSession(quiz, seed = 11)
        original.answer(original.current.question.correctAnswers.first())
        original.next()
        original.answer("wrong")          // second question answered, still on screen

        val restored = QuizSession(quiz, seed = 11)
        restored.restore(original.answers.map { it.given }, original.index)
        assertEquals(original.index, restored.index)
        assertEquals(original.answers, restored.answers)
        assertTrue(restored.isLocked)
    }

    @Test
    fun restoreToANextQuestionThatIsNotAnsweredYet() {
        val original = QuizSession(quiz, seed = 11)
        original.answer("wrong")
        original.next()                   // moved on, nothing answered on screen
        val restored = QuizSession(quiz, seed = 11)
        restored.restore(original.answers.map { it.given }, original.index)
        assertEquals(1, restored.index)
        assertFalse(restored.isLocked)
    }
}
