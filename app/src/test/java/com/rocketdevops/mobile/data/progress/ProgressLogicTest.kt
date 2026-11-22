package com.rocketdevops.mobile.data.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressLogicTest {

    private val catalog = ProgressTestCatalog.catalog

    private val today = LocalDate.of(2026, 11, 22) // a Sunday
    private fun day(daysAgo: Long) = today.minusDays(daysAgo).toEpochDay()

    // ---------------------------------------------------------------- streaks

    @Test
    fun streakCountsConsecutiveDaysEndingToday() {
        assertEquals(3, Streaks.current(setOf(day(0), day(1), day(2), day(4)), today))
    }

    @Test
    fun streakSurvivesUntilTheDayIsOver() {
        // Nothing yet today, but yesterday and the day before were active.
        assertEquals(2, Streaks.current(setOf(day(1), day(2)), today))
    }

    @Test
    fun streakResetsAfterAMissedDay() {
        assertEquals(0, Streaks.current(setOf(day(2), day(3)), today))
        assertEquals(0, Streaks.current(emptySet(), today))
    }

    @Test
    fun weekRunsMondayToSundayAndMarksToday() {
        val week = Streaks.week(today, mapOf(day(0) to 2))
        assertEquals(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), week.map { it.label })
        assertEquals(16, week.first().dayOfMonth)
        assertTrue(week.last().isToday && week.last().isActive)
    }

    @Test
    fun lastSevenDaysEndWithToday() {
        val days = Streaks.lastDays(today, mapOf(day(6) to 1, day(0) to 3))
        assertEquals(7, days.size)
        assertEquals(1, days.first().count)
        assertEquals(3, days.last().count)
        assertTrue(days.last().isToday)
    }

    // ---------------------------------------------------------------- track progress

    @Test
    fun emptyProgressLocksLaterModules() {
        val linux = ProgressCalculator.track(catalog.track("linux")!!, ProgressSnapshot.EMPTY)
        assertEquals(listOf(ModuleStatus.InProgress, ModuleStatus.Locked), linux.modules.map { it.status })
        assertEquals(0, linux.percent)
        assertEquals(5, linux.total) // 2 lessons + 2 labs + 1 quiz
        assertEquals(4 + 10 + 2 + 4 + 10, linux.minutesLeft)
    }

    @Test
    fun finishingAModuleUnlocksTheNextOne() {
        val snapshot = ProgressSnapshot(
            completedLessons = setOf("l1"),
            completedLabs = setOf("l-lab1"),
            quizAttempts = listOf(QuizAttemptRecord("l-quiz", 2, 2, wasRetry = false, atMillis = 1)),
        )
        val linux = ProgressCalculator.track(catalog.track("linux")!!, snapshot)
        assertEquals(listOf(ModuleStatus.Done, ModuleStatus.InProgress), linux.modules.map { it.status })
        assertEquals(60, linux.percent)
        assertEquals(1, linux.modulesDone)
        assertEquals(2, linux.modules[0].bestQuiz?.score)
    }

    @Test
    fun aRetryAloneDoesNotCompleteTheQuiz() {
        val snapshot = ProgressSnapshot(quizAttempts = listOf(QuizAttemptRecord("l-quiz", 1, 1, wasRetry = true, atMillis = 1)))
        val quizItem = ProgressCalculator.track(catalog.track("linux")!!, snapshot).items.first { it.kind == ItemKind.Quiz }
        assertFalse(quizItem.done)
    }

    @Test
    fun workDoneInALockedModuleShowsAsInProgress() {
        val snapshot = ProgressSnapshot(completedLabs = setOf("l-lab2")) // practised from the Labs tab
        val linux = ProgressCalculator.track(catalog.track("linux")!!, snapshot)
        assertEquals(ModuleStatus.InProgress, linux.modules[1].status)
    }

    // ---------------------------------------------------------------- continue card

    @Test
    fun firstStartPointsAtTheFirstLesson() {
        val target = ProgressCalculator.continueTarget(catalog, ProgressSnapshot.EMPTY)!!
        assertEquals("l1", target.item.id)
        assertTrue(target.isFirstStart)
    }

    @Test
    fun continuePrefersTheMostRecentTrack() {
        val snapshot = ProgressSnapshot(completedLessons = setOf("d1"), lastFinishedItemId = "d1")
        val target = ProgressCalculator.continueTarget(catalog, snapshot)!!
        assertEquals("docker", target.track.track.id)
        assertEquals("d-lab1", target.item.id)
        assertFalse(target.isFirstStart)
    }

    @Test
    fun nothingToContinueWhenEverythingIsDone() {
        val snapshot = ProgressSnapshot(
            completedLessons = setOf("l1", "l2", "d1"),
            completedLabs = setOf("l-lab1", "l-lab2", "d-lab1"),
            quizAttempts = listOf(QuizAttemptRecord("l-quiz", 2, 2, false, 1)),
        )
        assertNull(ProgressCalculator.continueTarget(catalog, snapshot))
    }

    @Test
    fun averageQuizUsesTheBestFullAttemptOfEachQuiz() {
        val snapshot = ProgressSnapshot(quizAttempts = listOf(
            QuizAttemptRecord("a", 1, 2, false, 1),
            QuizAttemptRecord("a", 2, 2, false, 2),  // best of quiz a: 100%
            QuizAttemptRecord("b", 1, 2, false, 3),  // quiz b: 50%
            QuizAttemptRecord("b", 2, 2, true, 4),   // retry ignored
        ))
        assertEquals(75, ProgressCalculator.averageQuizPercent(snapshot))
        assertNull(ProgressCalculator.averageQuizPercent(ProgressSnapshot.EMPTY))
    }

    // ---------------------------------------------------------------- achievements

    @Test
    fun firstCommandAfterTheFirstLab() {
        val earned = Achievements.earned(ProgressSnapshot(completedLabs = setOf("l-lab1")), catalog, today)
        assertTrue("first-command" in earned)
        assertFalse("container-captain" in earned)
    }

    @Test
    fun containerCaptainNeedsEveryDockerLab() {
        val earned = Achievements.earned(ProgressSnapshot(completedLabs = setOf("d-lab1")), catalog, today)
        assertTrue("container-captain" in earned)
    }

    @Test
    fun weekWarriorNeedsSixOfTheLastSevenDays() {
        val five = (0L..4L).associate { day(it) to 1 }
        assertFalse("week-warrior" in Achievements.earned(ProgressSnapshot(activity = five), catalog, today))
        val six = (0L..5L).associate { day(it) to 1 }
        assertTrue("week-warrior" in Achievements.earned(ProgressSnapshot(activity = six), catalog, today))
    }

    @Test
    fun quizAceNeedsAPerfectFullAttempt() {
        val retryOnly = ProgressSnapshot(quizAttempts = listOf(QuizAttemptRecord("l-quiz", 1, 1, true, 1)))
        assertFalse("quiz-ace" in Achievements.earned(retryOnly, catalog, today))
        val perfect = ProgressSnapshot(quizAttempts = listOf(QuizAttemptRecord("l-quiz", 2, 2, false, 1)))
        assertTrue("quiz-ace" in Achievements.earned(perfect, catalog, today))
    }

    @Test
    fun alreadyStoredAchievementsAreNotAnnouncedAgain() {
        val snapshot = ProgressSnapshot(completedLabs = setOf("l-lab1"), achievements = mapOf("first-command" to 1L))
        assertTrue(Achievements.newlyEarned(snapshot, catalog, today).none { it.id == "first-command" })
    }
}
