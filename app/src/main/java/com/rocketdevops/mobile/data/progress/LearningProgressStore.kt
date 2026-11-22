package com.rocketdevops.mobile.data.progress

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A quiz the learner left in the middle. Enough to rebuild it exactly (see QuizSession). */
data class QuizProgress(
    val seed: Long,                       // decides the shuffled order
    val onlyQuestionIds: List<String>?,   // set when retrying only the wrong answers
    val givenAnswers: List<String>,       // in the order they were answered
    val index: Int,                       // the question on screen
)

/** One finished quiz attempt. */
data class QuizAttempt(val quizId: String, val score: Int, val total: Int, val wasRetry: Boolean)

/**
 * Where learning progress is kept: finished lessons and labs, quiz attempts and unfinished quizzes.
 *
 * Screens depend on this interface, not on a database, so the storage can change without
 * touching them. The app uses ProgressRepository (Room, survives restarts and drives the live
 * progress bars). InMemoryLearningProgressStore is kept as a simple fake for tests.
 */
interface LearningProgressStore {
    suspend fun markLessonComplete(lessonId: String)
    suspend fun isLessonComplete(lessonId: String): Boolean
    suspend fun markLabComplete(labId: String)

    suspend fun recordQuizAttempt(attempt: QuizAttempt)
    /** Best score of full attempts (retries of wrong answers do not count), or null if none. */
    suspend fun bestQuizScore(quizId: String): Int?

    suspend fun saveQuizProgress(quizId: String, progress: QuizProgress)
    suspend fun loadQuizProgress(quizId: String): QuizProgress?
    suspend fun clearQuizProgress(quizId: String)
}

/** Keeps progress in memory only. Used in tests; the app uses ProgressRepository (Room). */
class InMemoryLearningProgressStore : LearningProgressStore {
    private val mutex = Mutex()
    private val lessons = mutableSetOf<String>()
    private val labs = mutableSetOf<String>()
    private val attempts = mutableListOf<QuizAttempt>()
    private val inProgress = mutableMapOf<String, QuizProgress>()

    override suspend fun markLessonComplete(lessonId: String) = mutex.withLock { lessons += lessonId }
    override suspend fun isLessonComplete(lessonId: String) = mutex.withLock { lessonId in lessons }
    override suspend fun markLabComplete(labId: String) = mutex.withLock { labs += labId }

    override suspend fun recordQuizAttempt(attempt: QuizAttempt) = mutex.withLock { attempts += attempt }
    override suspend fun bestQuizScore(quizId: String) = mutex.withLock {
        attempts.filter { it.quizId == quizId && !it.wasRetry }.maxOfOrNull { it.score }
    }

    override suspend fun saveQuizProgress(quizId: String, progress: QuizProgress) = mutex.withLock { inProgress[quizId] = progress }
    override suspend fun loadQuizProgress(quizId: String) = mutex.withLock { inProgress[quizId] }
    override suspend fun clearQuizProgress(quizId: String) = mutex.withLock { inProgress.remove(quizId); Unit }
}
