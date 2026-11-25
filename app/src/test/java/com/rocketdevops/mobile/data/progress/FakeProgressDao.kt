package com.rocketdevops.mobile.data.progress

import com.rocketdevops.mobile.data.progress.db.AchievementEntity
import com.rocketdevops.mobile.data.progress.db.ActivityEntity
import com.rocketdevops.mobile.data.progress.db.CompletedItemEntity
import com.rocketdevops.mobile.data.progress.db.ProgressDao
import com.rocketdevops.mobile.data.progress.db.QuizAttemptEntity
import com.rocketdevops.mobile.data.progress.db.QuizInProgressEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory ProgressDao for unit tests (Room cannot run on the plain JVM).
 * Shared by ProgressRepositoryTest and AccountRepositoryTest.
 */
class FakeProgressDao : ProgressDao() {
    val completed = MutableStateFlow<List<CompletedItemEntity>>(emptyList())
    val attempts = MutableStateFlow<List<QuizAttemptEntity>>(emptyList())
    val activity = MutableStateFlow<List<ActivityEntity>>(emptyList())
    val achievements = MutableStateFlow<List<AchievementEntity>>(emptyList())
    val inProgress = mutableMapOf<String, QuizInProgressEntity>()
    /** When set, saving today's activity waits here (to test leaving a screen mid-write). */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun insertCompleted(item: CompletedItemEntity): Long {
        if (completed.value.any { it.itemId == item.itemId }) return -1
        completed.value = completed.value + item
        return 1
    }
    override suspend fun upsertCompleted(item: CompletedItemEntity) {
        completed.value = completed.value.filterNot { it.itemId == item.itemId } + item
    }
    override suspend fun isCompleted(itemId: String) = completed.value.any { it.itemId == itemId }
    override fun observeCompleted(): Flow<List<CompletedItemEntity>> = completed

    override suspend fun insertAttempt(attempt: QuizAttemptEntity) {
        attempts.value = attempts.value + attempt.copy(id = attempts.value.size + 1L)
    }
    override fun observeAttempts(): Flow<List<QuizAttemptEntity>> = attempts
    override suspend fun bestScore(quizId: String) =
        attempts.value.filter { it.quizId == quizId && !it.wasRetry }.maxOfOrNull { it.score }

    override suspend fun saveQuizInProgress(progress: QuizInProgressEntity) { inProgress[progress.quizId] = progress }
    override suspend fun quizInProgress(quizId: String) = inProgress[quizId]
    override suspend fun clearQuizInProgress(quizId: String) { inProgress.remove(quizId) }

    override suspend fun activityOn(epochDay: Long) = activity.value.firstOrNull { it.epochDay == epochDay }
    override suspend fun upsertActivity(activity: ActivityEntity) {
        gate?.await()
        this.activity.value = this.activity.value.filterNot { it.epochDay == activity.epochDay } + activity
    }
    override fun observeActivity(): Flow<List<ActivityEntity>> = activity

    override suspend fun insertAchievement(achievement: AchievementEntity): Long {
        if (achievements.value.any { it.id == achievement.id }) return -1
        achievements.value = achievements.value + achievement
        return 1
    }
    override fun observeAchievements(): Flow<List<AchievementEntity>> = achievements

    override suspend fun deleteCompleted() { completed.value = emptyList() }
    override suspend fun deleteAttempts() { attempts.value = emptyList() }
    override suspend fun deleteQuizzesInProgress() { inProgress.clear() }
    override suspend fun deleteActivity() { activity.value = emptyList() }
    override suspend fun deleteAchievements() { achievements.value = emptyList() }

    // Backup export / import (accounts).
    override suspend fun allCompleted() = completed.value
    override suspend fun allAttempts() = attempts.value
    override suspend fun allActivity() = activity.value
    override suspend fun allAchievements() = achievements.value
    override suspend fun insertAllCompleted(items: List<CompletedItemEntity>) {
        completed.value = completed.value.filterNot { old -> items.any { it.itemId == old.itemId } } + items
    }
    override suspend fun insertAllAttempts(items: List<QuizAttemptEntity>) {
        items.forEach { insertAttempt(it) }
    }
    override suspend fun insertAllActivity(items: List<ActivityEntity>) {
        activity.value = activity.value.filterNot { old -> items.any { it.epochDay == old.epochDay } } + items
    }
    override suspend fun insertAllAchievements(items: List<AchievementEntity>) {
        achievements.value = achievements.value.filterNot { old -> items.any { it.id == old.id } } + items
    }
}
