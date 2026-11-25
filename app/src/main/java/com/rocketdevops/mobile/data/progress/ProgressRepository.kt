package com.rocketdevops.mobile.data.progress

import com.rocketdevops.mobile.data.content.ContentCatalog
import com.rocketdevops.mobile.data.progress.db.AchievementEntity
import com.rocketdevops.mobile.data.progress.db.ActivityEntity
import com.rocketdevops.mobile.data.progress.db.CompletedItemEntity
import com.rocketdevops.mobile.data.progress.db.ProgressDao
import com.rocketdevops.mobile.data.progress.db.QuizAttemptEntity
import com.rocketdevops.mobile.data.progress.db.QuizInProgressEntity
import com.rocketdevops.mobile.data.sync.CompletedRecord
import com.rocketdevops.mobile.data.sync.ProgressBackup
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * THE single source of truth for ALL progress (spec screen 8).
 *
 * - Writes: lessons, labs and quiz attempts are saved in Room, today's activity is counted,
 *   and achievement rules are checked after every write.
 * - Reads: `snapshot` is a Flow that re-emits whenever any table changes. Every screen
 *   observes it, so finishing a lab updates Home, the track, the Labs tab and Profile at once.
 *
 * @param catalog gives the loaded content (needed by "finish every Docker lab" achievements),
 *        or null if it could not be loaded.
 * @param today local date provider (replaceable in tests).
 */
class ProgressRepository(
    private val dao: ProgressDao,
    private val catalog: suspend () -> ContentCatalog?,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val now: () -> Long = { System.currentTimeMillis() },
) : LearningProgressStore {

    val snapshot: Flow<ProgressSnapshot> = combine(
        dao.observeCompleted(),
        dao.observeAttempts(),
        dao.observeActivity(),
        dao.observeAchievements(),
    ) { completed, attempts, activity, achievements ->
        val newest = (completed.map { it.itemId to it.completedAtMillis } + attempts.map { it.quizId to it.atMillis })
            .maxByOrNull { it.second }
        ProgressSnapshot(
            completedLessons = completed.filter { it.kind == KIND_LESSON }.map { it.itemId }.toSet(),
            completedLabs = completed.filter { it.kind == KIND_LAB }.map { it.itemId }.toSet(),
            quizAttempts = attempts.map { QuizAttemptRecord(it.quizId, it.score, it.total, it.wasRetry, it.atMillis) },
            activity = activity.associate { it.epochDay to it.count },
            achievements = achievements.associate { it.id to it.unlockedAtMillis },
            lastFinishedItemId = newest?.first,
        )
    }

    private val _achievementUnlocked = MutableSharedFlow<Achievement>(extraBufferCapacity = 8)
    /** One event per newly unlocked achievement; the app shows "Achievement unlocked" for each. */
    val achievementUnlocked: SharedFlow<Achievement> = _achievementUnlocked.asSharedFlow()

    // ---------------------------------------------------------------- lessons and labs

    override suspend fun markLessonComplete(lessonId: String): Unit = durable {
        val inserted = dao.insertCompleted(CompletedItemEntity(lessonId, KIND_LESSON, now())) != -1L
        if (inserted) afterProgress() // pressing "Mark complete" twice does not count twice
    }

    override suspend fun isLessonComplete(lessonId: String): Boolean = dao.isCompleted(lessonId)

    /** Finishing a lab again (after Reset lab) still counts for today's streak. */
    override suspend fun markLabComplete(labId: String): Unit = durable {
        dao.upsertCompleted(CompletedItemEntity(labId, KIND_LAB, now()))
        afterProgress()
    }

    // ---------------------------------------------------------------- quizzes

    override suspend fun recordQuizAttempt(attempt: QuizAttempt): Unit = durable {
        dao.insertAttempt(QuizAttemptEntity(0, attempt.quizId, attempt.score, attempt.total, attempt.wasRetry, now()))
        afterProgress()
    }

    override suspend fun bestQuizScore(quizId: String): Int? = dao.bestScore(quizId)

    override suspend fun saveQuizProgress(quizId: String, progress: QuizProgress): Unit = durable {
        dao.saveQuizInProgress(
            QuizInProgressEntity(
                quizId = quizId,
                seed = progress.seed,
                onlyQuestionIdsJson = progress.onlyQuestionIds?.let { json.encodeToString(answersSerializer, it) },
                givenAnswersJson = json.encodeToString(answersSerializer, progress.givenAnswers),
                questionIndex = progress.index,
            ),
        )
    }

    override suspend fun loadQuizProgress(quizId: String): QuizProgress? =
        dao.quizInProgress(quizId)?.let { saved ->
            // Unreadable data: start the quiz fresh rather than crash.
            val answers = runCatching { json.decodeFromString(answersSerializer, saved.givenAnswersJson) }.getOrNull()
                ?: return null
            val only = saved.onlyQuestionIdsJson?.let {
                runCatching { json.decodeFromString(answersSerializer, it) }.getOrNull() ?: return null
            }
            QuizProgress(saved.seed, onlyQuestionIds = only, givenAnswers = answers, index = saved.questionIndex)
        }

    override suspend fun clearQuizProgress(quizId: String): Unit = durable { dao.clearQuizInProgress(quizId) }

    // ---------------------------------------------------------------- reset

    /** Settings > Reset progress: wipes every table (used for exhibition visitors). */
    suspend fun resetAll(): Unit = durable { dao.deleteEverything() }

    // ---------------------------------------------------------------- backup (accounts)

    /** Everything worth keeping, for the online copy of a signed-in learner. */
    suspend fun exportBackup(): ProgressBackup = ProgressBackup(
        completed = dao.allCompleted().map { CompletedRecord(it.itemId, it.kind, it.completedAtMillis) },
        quizAttempts = dao.allAttempts().map { QuizAttemptRecord(it.quizId, it.score, it.total, it.wasRetry, it.atMillis) },
        activity = dao.allActivity().associate { it.epochDay to it.count },
        achievements = dao.allAchievements().associate { it.id to it.unlockedAtMillis },
    )

    /**
     * Makes this phone show exactly `backup` (after signing in). Unfinished quizzes are cleared,
     * because they belonged to whoever used the phone before. No "Achievement unlocked" popups:
     * these were earned earlier.
     */
    suspend fun importBackup(backup: ProgressBackup): Unit = durable {
        dao.replaceAll(
            completed = backup.completed.map { CompletedItemEntity(it.itemId, it.kind, it.atMillis) },
            attempts = backup.quizAttempts.map { QuizAttemptEntity(0, it.quizId, it.score, it.total, it.wasRetry, it.atMillis) },
            activity = backup.activity.map { (day, count) -> ActivityEntity(day, count) },
            achievements = backup.achievements.map { (id, at) -> AchievementEntity(id, at) },
        )
    }

    // ---------------------------------------------------------------- internals

    /**
     * Writes are started from screens (viewModelScope). If the learner leaves the screen right
     * away ("Mark complete", then "Next"), that scope is cancelled. NonCancellable lets a write
     * that already started finish, so a lesson is never saved without its streak day or achievement.
     */
    private suspend fun <T> durable(block: suspend () -> T): T = withContext(NonCancellable) { block() }

    /** After anything is finished: count today's activity, then check achievements. */
    private suspend fun afterProgress() {
        dao.addActivity(today().toEpochDay())
        val content = catalog() ?: return
        val current = snapshot.first() // Room re-reads, so this already includes the write above
        for (achievement in Achievements.newlyEarned(current, content, today())) {
            if (dao.insertAchievement(AchievementEntity(achievement.id, now())) != -1L) {
                _achievementUnlocked.emit(achievement)
            }
        }
    }

    companion object {
        const val KIND_LESSON = "lesson"
        const val KIND_LAB = "lab"
        private val json = Json
        private val answersSerializer = ListSerializer(String.serializer())
    }
}
