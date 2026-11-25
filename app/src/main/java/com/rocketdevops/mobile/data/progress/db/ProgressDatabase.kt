package com.rocketdevops.mobile.data.progress.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/*
 * ROOM DATABASE (stored only on the phone, in the app's private folder).
 * Five small tables. Room writes the SQL code for us at build time (via KSP).
 */

/** A finished lesson or lab. `kind` is "lesson" or "lab". */
@Entity(tableName = "completed_items")
data class CompletedItemEntity(
    @PrimaryKey val itemId: String,
    val kind: String,
    val completedAtMillis: Long,
)

/** Every quiz attempt, including retries of wrong answers. */
@Entity(tableName = "quiz_attempts")
data class QuizAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quizId: String,
    val score: Int,
    val total: Int,
    val wasRetry: Boolean,
    val atMillis: Long,
)

/** A quiz left in the middle ("Leave quiz? Your progress is saved"). */
@Entity(tableName = "quiz_in_progress")
data class QuizInProgressEntity(
    @PrimaryKey val quizId: String,
    val seed: Long,
    val onlyQuestionIdsJson: String?, // JSON array when retrying only the wrong answers, else null
    val givenAnswersJson: String,     // JSON array of the answers, in order
    val questionIndex: Int,
)

/** How many things were finished on each local day: feeds streaks and the "Last 7 days" chart. */
@Entity(tableName = "activity")
data class ActivityEntity(
    @PrimaryKey val epochDay: Long,
    val count: Int,
)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val unlockedAtMillis: Long,
)

/**
 * All database reads and writes. Functions returning Flow re-emit automatically whenever
 * their table changes; that is what makes progress update live everywhere.
 */
@Dao
abstract class ProgressDao {

    // ---- lessons and labs
    /** Returns -1 if it was already there. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertCompleted(item: CompletedItemEntity): Long

    /** Insert or update (a lab finished again gets a new time, so "Continue" knows it was recent). */
    @Upsert
    abstract suspend fun upsertCompleted(item: CompletedItemEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM completed_items WHERE itemId = :itemId)")
    abstract suspend fun isCompleted(itemId: String): Boolean

    @Query("SELECT * FROM completed_items")
    abstract fun observeCompleted(): Flow<List<CompletedItemEntity>>

    // ---- quizzes
    @Insert
    abstract suspend fun insertAttempt(attempt: QuizAttemptEntity)

    @Query("SELECT * FROM quiz_attempts")
    abstract fun observeAttempts(): Flow<List<QuizAttemptEntity>>

    @Query("SELECT MAX(score) FROM quiz_attempts WHERE quizId = :quizId AND wasRetry = 0")
    abstract suspend fun bestScore(quizId: String): Int?

    @Upsert
    abstract suspend fun saveQuizInProgress(progress: QuizInProgressEntity)

    @Query("SELECT * FROM quiz_in_progress WHERE quizId = :quizId")
    abstract suspend fun quizInProgress(quizId: String): QuizInProgressEntity?

    @Query("DELETE FROM quiz_in_progress WHERE quizId = :quizId")
    abstract suspend fun clearQuizInProgress(quizId: String)

    // ---- daily activity
    @Query("SELECT * FROM activity WHERE epochDay = :epochDay")
    abstract suspend fun activityOn(epochDay: Long): ActivityEntity?

    @Upsert
    abstract suspend fun upsertActivity(activity: ActivityEntity)

    /** Adds one to today's count. A transaction, so two quick finishes cannot lose a count. */
    @Transaction
    open suspend fun addActivity(epochDay: Long) {
        val current = activityOn(epochDay)?.count ?: 0
        upsertActivity(ActivityEntity(epochDay, current + 1))
    }

    @Query("SELECT * FROM activity")
    abstract fun observeActivity(): Flow<List<ActivityEntity>>

    // ---- achievements
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAchievement(achievement: AchievementEntity): Long

    @Query("SELECT * FROM achievements")
    abstract fun observeAchievements(): Flow<List<AchievementEntity>>

    // ---- whole-table reads and writes (account sign-in: copy progress to and from the cloud)
    @Query("SELECT * FROM completed_items") abstract suspend fun allCompleted(): List<CompletedItemEntity>
    @Query("SELECT * FROM quiz_attempts") abstract suspend fun allAttempts(): List<QuizAttemptEntity>
    @Query("SELECT * FROM activity") abstract suspend fun allActivity(): List<ActivityEntity>
    @Query("SELECT * FROM achievements") abstract suspend fun allAchievements(): List<AchievementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertAllCompleted(items: List<CompletedItemEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertAllAttempts(items: List<QuizAttemptEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertAllActivity(items: List<ActivityEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertAllAchievements(items: List<AchievementEntity>)

    /**
     * Replaces all progress in one transaction, so screens never see a half-copied state
     * (for example lessons from the new account but the streak of the old one).
     */
    @Transaction
    open suspend fun replaceAll(
        completed: List<CompletedItemEntity>,
        attempts: List<QuizAttemptEntity>,
        activity: List<ActivityEntity>,
        achievements: List<AchievementEntity>,
    ) {
        deleteEverything()
        insertAllCompleted(completed)
        insertAllAttempts(attempts)
        insertAllActivity(activity)
        insertAllAchievements(achievements)
    }

    // ---- reset (Settings > Reset progress, Stage 9)
    @Query("DELETE FROM completed_items") abstract suspend fun deleteCompleted()
    @Query("DELETE FROM quiz_attempts") abstract suspend fun deleteAttempts()
    @Query("DELETE FROM quiz_in_progress") abstract suspend fun deleteQuizzesInProgress()
    @Query("DELETE FROM activity") abstract suspend fun deleteActivity()
    @Query("DELETE FROM achievements") abstract suspend fun deleteAchievements()

    @Transaction
    open suspend fun deleteEverything() {
        deleteCompleted()
        deleteAttempts()
        deleteQuizzesInProgress()
        deleteActivity()
        deleteAchievements()
    }
}

@Database(
    entities = [
        CompletedItemEntity::class,
        QuizAttemptEntity::class,
        QuizInProgressEntity::class,
        ActivityEntity::class,
        AchievementEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class ProgressDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao

    companion object {
        fun create(context: Context): ProgressDatabase =
            Room.databaseBuilder(context, ProgressDatabase::class.java, "rocketdevops-progress.db").build()
    }
}
