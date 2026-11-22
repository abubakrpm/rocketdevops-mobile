package com.rocketdevops.mobile.data.progress

/** One finished quiz attempt as stored. */
data class QuizAttemptRecord(
    val quizId: String,
    val score: Int,
    val total: Int,
    val wasRetry: Boolean,
    val atMillis: Long,
)

/**
 * Everything the learner has done, at one moment. Built from the Room tables by
 * ProgressRepository and re-emitted whenever any table changes, so every progress bar,
 * ring and chip in the app reads from this one object and updates instantly.
 *
 * Pure data (no Android), so all calculations on it are unit tested.
 */
data class ProgressSnapshot(
    val completedLessons: Set<String> = emptySet(),
    val completedLabs: Set<String> = emptySet(),
    val quizAttempts: List<QuizAttemptRecord> = emptyList(),
    /** epochDay (days since 1970-01-01, in the phone's local time zone) -> things finished that day. */
    val activity: Map<Long, Int> = emptyMap(),
    /** achievement id -> when it was unlocked (millis). */
    val achievements: Map<String, Long> = emptyMap(),
    /** The lesson, lab or quiz finished most recently, for the Home "Continue" card. */
    val lastFinishedItemId: String? = null,
) {
    val activeDays: Set<Long> get() = activity.filterValues { it > 0 }.keys

    /** Best score of full attempts (retries of wrong answers are not comparable). */
    fun bestScore(quizId: String): QuizAttemptRecord? =
        quizAttempts.filter { it.quizId == quizId && !it.wasRetry }.maxByOrNull { it.score }

    fun hasAttempted(quizId: String): Boolean = quizAttempts.any { it.quizId == quizId && !it.wasRetry }

    companion object {
        val EMPTY = ProgressSnapshot()
    }
}
