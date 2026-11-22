package com.rocketdevops.mobile.ui.quiz

import com.rocketdevops.mobile.data.content.QuestionType
import com.rocketdevops.mobile.ui.components.QuizOptionState

data class QuizOptionUi(val text: String, val state: QuizOptionState)

/** Everything the quiz screen draws for the current question. */
data class QuizScreenState(
    val quizTitle: String,
    val questionNumber: Int,          // 1-based, for "Question 4 of 10"
    val total: Int,
    val answeredCount: Int,           // drives the progress bar
    val prompt: String,
    val type: QuestionType,
    val isCode: Boolean,
    val options: List<QuizOptionUi>,  // empty for TYPE_THE_COMMAND
    val isLocked: Boolean,
    val lastAnswerCorrect: Boolean?,  // null until the question is answered
    val givenAnswer: String?,
    val timedOut: Boolean,            // the quiz timer ran out on this question
    val correctAnswer: String,
    val explanation: String,
    val score: Int,
    val streak: Int,
    val isLastQuestion: Boolean,
    val showInputError: Boolean,      // "Enter an answer first"
    val isRetry: Boolean,
)

/**
 * Turns the attempt into what the screen shows. Pure Kotlin, so it is unit tested,
 * and the Compose UI test uses it to drive a real QuizSession.
 *
 * Once a question is locked, the right option is ALWAYS shown as correct (even when the
 * learner picked another one), the wrong pick is red, and the rest are greyed out.
 */
fun QuizSession.toScreenState(showInputError: Boolean, isRetry: Boolean): QuizScreenState {
    val prepared = current
    val question = prepared.question
    val answer = currentAnswer
    val options = prepared.options.map { option ->
        val state = when {
            answer == null -> QuizOptionState.Idle
            option in question.correctAnswers -> QuizOptionState.Correct
            option == answer.given -> QuizOptionState.Wrong
            else -> QuizOptionState.Disabled
        }
        QuizOptionUi(option, state)
    }
    return QuizScreenState(
        quizTitle = quiz.title,
        questionNumber = index + 1,
        total = total,
        answeredCount = answers.size,
        prompt = question.prompt,
        type = question.type,
        isCode = question.isCode,
        options = if (question.type == QuestionType.TYPE_THE_COMMAND) emptyList() else options,
        isLocked = answer != null,
        lastAnswerCorrect = answer?.correct,
        givenAnswer = answer?.given,
        timedOut = answer?.given == QuizSession.TIMED_OUT,
        correctAnswer = question.correctAnswers.firstOrNull().orEmpty(),
        explanation = question.explanation,
        score = score,
        streak = streak,
        isLastQuestion = isLastQuestion,
        showInputError = showInputError,
        isRetry = isRetry,
    )
}
