package com.rocketdevops.mobile.data.progress

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Checks ProgressRepository's rules against an in-memory fake of the Room DAO
 * (same behaviour as the real tables, no phone or emulator needed).
 */
class ProgressRepositoryTest {

    /** Behaves like the Room tables: each Flow re-emits when its "table" changes. */
    private val today = LocalDate.of(2026, 11, 22)
    private var clock = 1_000L
    private val dao = FakeProgressDao()
    private val repo = ProgressRepository(
        dao = dao,
        catalog = { ProgressTestCatalog.catalog },
        today = { today },
        now = { clock++ },
    )

    @Test
    fun finishingALabUpdatesTheSnapshotAndToday() = runBlocking {
        repo.markLabComplete("l-lab1")
        val snapshot = repo.snapshot.first()
        assertEquals(setOf("l-lab1"), snapshot.completedLabs)
        assertEquals(1, snapshot.activity[today.toEpochDay()])
        assertEquals("l-lab1", snapshot.lastFinishedItemId)
    }

    @Test
    fun markingALessonTwiceCountsOnce() = runBlocking {
        repo.markLessonComplete("l1")
        repo.markLessonComplete("l1")
        assertEquals(1, repo.snapshot.first().activity[today.toEpochDay()])
        assertTrue(repo.isLessonComplete("l1"))
    }

    @Test
    fun achievementsAreAnnouncedExactlyOnce() = runBlocking {
        val announced = mutableListOf<String>()
        val listener = launch(start = CoroutineStart.UNDISPATCHED) {
            repo.achievementUnlocked.collect { announced += it.id }
        }
        repo.markLabComplete("d-lab1")   // first lab + every Docker lab
        repo.markLabComplete("d-lab1")   // again (after Reset lab): nothing new
        listener.cancel()

        assertEquals(listOf("first-command", "container-captain"), announced)
        assertEquals(setOf("first-command", "container-captain"), repo.snapshot.first().achievements.keys)
        assertEquals(2, repo.snapshot.first().activity[today.toEpochDay()]) // both finishes count for the day
    }

    @Test
    fun quizAttemptsKeepTheBestFullScore() = runBlocking {
        repo.recordQuizAttempt(QuizAttempt("l-quiz", 1, 2, wasRetry = false))
        repo.recordQuizAttempt(QuizAttempt("l-quiz", 2, 2, wasRetry = true))
        assertEquals(1, repo.bestQuizScore("l-quiz"))
        assertFalse("quiz-ace" in repo.snapshot.first().achievements) // a perfect retry is not an ace
    }

    @Test
    fun unfinishedQuizRoundTrips() = runBlocking {
        val saved = QuizProgress(seed = 42, onlyQuestionIds = listOf("q2", "q5"), givenAnswers = listOf("docker ps", "True"), index = 2)
        repo.saveQuizProgress("l-quiz", saved)
        assertEquals(saved, repo.loadQuizProgress("l-quiz"))
        repo.clearQuizProgress("l-quiz")
        assertNull(repo.loadQuizProgress("l-quiz"))
    }

    @Test
    fun aWriteFinishesEvenIfTheScreenIsClosedMidway() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        dao.gate = gate
        val screen = launch { repo.markLabComplete("l-lab1") } // like viewModelScope.launch
        yield()               // the write starts and waits inside the database
        screen.cancel()       // the learner pressed Back: the ViewModel's scope is cancelled
        gate.complete(Unit)
        screen.join()
        val snapshot = repo.snapshot.first()
        assertEquals(1, snapshot.activity[today.toEpochDay()])   // the streak day was still saved
        assertTrue("first-command" in snapshot.achievements)      // and the achievement
    }

    @Test
    fun resetWipesEverything() = runBlocking {
        repo.markLabComplete("l-lab1")
        repo.resetAll()
        assertEquals(ProgressSnapshot.EMPTY, repo.snapshot.first())
    }
}
