package com.rocketdevops.mobile.ui.quiz

import com.rocketdevops.mobile.data.content.QuestionType
import com.rocketdevops.mobile.data.content.Quiz
import com.rocketdevops.mobile.data.content.QuizQuestion
import kotlin.random.Random

/** A question as shown in this attempt: options already shuffled. */
data class PreparedQuestion(val question: QuizQuestion, val options: List<String>)

/** What the learner answered, and whether it was right. */
data class GivenAnswer(val questionId: String, val given: String, val correct: Boolean)

/** The outcome of an attempt, passed to the results screen. */
data class QuizResult(val score: Int, val total: Int, val wrongQuestionIds: List<String>)

/**
 * One attempt at a quiz, in plain Kotlin (no Android), so every rule is unit tested.
 *
 * - Question order and option order are shuffled with `seed`. The same seed always gives the
 *   same order, which is how an attempt is restored after rotation or process death.
 * - Answering locks the question: it cannot be changed.
 * - `onlyQuestionIds` limits the attempt to some questions ("Retry wrong ones").
 */
class QuizSession(
    val quiz: Quiz,
    val seed: Long,
    val onlyQuestionIds: List<String>? = null,
) {
    val questions: List<PreparedQuestion> = run {
        val random = Random(seed)
        quiz.questions
            .filter { onlyQuestionIds == null || it.id in onlyQuestionIds }
            .shuffled(random)
            .map { q ->
                // True / False keep their natural order; everything else is shuffled.
                val options = if (q.type == QuestionType.TRUE_FALSE) q.displayOptions else q.displayOptions.shuffled(random)
                PreparedQuestion(q, options)
            }
    }

    private val _answers = mutableListOf<GivenAnswer>()
    val answers: List<GivenAnswer> get() = _answers

    /** Index of the question on screen (0-based). */
    var index: Int = 0
        private set

    val total: Int get() = questions.size
    val current: PreparedQuestion get() = questions[index]

    /** The answer to the question on screen, or null if it is not answered (locked) yet. */
    val currentAnswer: GivenAnswer? get() = _answers.firstOrNull { it.questionId == current.question.id }
    val isLocked: Boolean get() = currentAnswer != null
    val isLastQuestion: Boolean get() = index == questions.lastIndex
    val isFinished: Boolean get() = isLastQuestion && isLocked

    val score: Int get() = _answers.count { it.correct }

    /** Correct answers in a row, counting back from the latest one. */
    val streak: Int get() = _answers.takeLastWhile { it.correct }.size

    /** Locks in an answer for the question on screen. Returns null if it was already answered. */
    fun answer(given: String): GivenAnswer? {
        if (isLocked) return null
        val result = GivenAnswer(current.question.id, given, isCorrect(current.question, given))
        _answers += result
        return result
    }

    /**
     * The quiz timer ran out: the question is locked as wrong with an empty answer.
     * Stored as "" so restoring the attempt (which replays answers) gives the same result.
     */
    fun timeOut(): GivenAnswer? = answer(TIMED_OUT)

    /** "Next question". Only allowed once the current question is answered. Returns false at the end. */
    fun next(): Boolean {
        if (!isLocked || isLastQuestion) return false
        index++
        return true
    }

    fun result(): QuizResult = QuizResult(score, total, _answers.filter { !it.correct }.map { it.questionId })

    /**
     * Rebuilds an attempt: answers the questions again in the same order and moves to `index`.
     * Works because the shuffle depends only on the seed.
     */
    fun restore(givenAnswers: List<String>, restoredIndex: Int) {
        givenAnswers.forEachIndexed { i, given ->
            answer(given)
            if (i < restoredIndex) next()
        }
        while (index < restoredIndex && next()) Unit
    }

    companion object {
        /** The answer recorded when time runs out. No real answer is ever empty. */
        const val TIMED_OUT = ""

        private val WHITESPACE = Regex("\\s+")

        /** Trims and squeezes spaces, so "git  status " matches "git status". */
        fun normalize(text: String): String = text.trim().replace(WHITESPACE, " ")

        fun isCorrect(question: QuizQuestion, given: String): Boolean = when (question.type) {
            QuestionType.MULTIPLE_CHOICE, QuestionType.TRUE_FALSE -> given in question.correctAnswers
            QuestionType.TYPE_THE_COMMAND -> {
                val typed = normalize(given)
                question.correctAnswers.any { normalize(it) == typed } ||
                    question.acceptedPatterns.any { Regex(it).containsMatchIn(typed) }
            }
        }

        /** A new random seed for a fresh attempt. */
        fun newSeed(): Long = Random.nextLong()
    }
}
