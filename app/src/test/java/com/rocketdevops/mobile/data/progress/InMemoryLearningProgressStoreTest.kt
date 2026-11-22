package com.rocketdevops.mobile.data.progress

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryLearningProgressStoreTest {

    @Test
    fun lessonsAreMarkedComplete() = runBlocking {
        val store = InMemoryLearningProgressStore()
        assertFalse(store.isLessonComplete("a"))
        store.markLessonComplete("a")
        assertTrue(store.isLessonComplete("a"))
    }

    @Test
    fun bestScoreIgnoresRetriesOfWrongAnswers() = runBlocking {
        val store = InMemoryLearningProgressStore()
        assertNull(store.bestQuizScore("quiz"))
        store.recordQuizAttempt(QuizAttempt("quiz", score = 6, total = 10, wasRetry = false))
        store.recordQuizAttempt(QuizAttempt("quiz", score = 8, total = 10, wasRetry = false))
        store.recordQuizAttempt(QuizAttempt("quiz", score = 4, total = 4, wasRetry = true)) // 4/4 retry is not a best of 10
        assertEquals(8, store.bestQuizScore("quiz"))
    }

    @Test
    fun unfinishedQuizIsSavedLoadedAndCleared() = runBlocking {
        val store = InMemoryLearningProgressStore()
        val progress = QuizProgress(seed = 42, onlyQuestionIds = null, givenAnswers = listOf("docker ps"), index = 1)
        store.saveQuizProgress("quiz", progress)
        assertEquals(progress, store.loadQuizProgress("quiz"))
        store.clearQuizProgress("quiz")
        assertNull(store.loadQuizProgress("quiz"))
    }
}
