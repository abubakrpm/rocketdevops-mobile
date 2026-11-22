package com.rocketdevops.mobile.ui

import com.rocketdevops.mobile.data.prefs.UserPreferences
import com.rocketdevops.mobile.data.prefs.cleanName
import com.rocketdevops.mobile.data.prefs.initialsOf
import com.rocketdevops.mobile.data.progress.ModuleStatus
import com.rocketdevops.mobile.data.progress.ProgressSnapshot
import com.rocketdevops.mobile.data.progress.ProgressTestCatalog
import com.rocketdevops.mobile.data.progress.QuizAttemptRecord
import com.rocketdevops.mobile.ui.common.OpenTarget
import com.rocketdevops.mobile.ui.common.formatTimeLeft
import com.rocketdevops.mobile.ui.home.buildHomeState
import com.rocketdevops.mobile.ui.profile.buildProfileState
import com.rocketdevops.mobile.ui.track.buildTrackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Home, Track and Profile are built by pure functions, so their text is checked here. */
class ScreenStateTest {

    private val catalog = ProgressTestCatalog.catalog
    private val today = LocalDate.of(2026, 11, 22)

    // ---------------------------------------------------------------- names

    @Test
    fun namesAreCleanedAndShortened() {
        assertEquals("Abu Bakr", cleanName("  Abu   Bakr "))
        assertEquals(24, cleanName("x".repeat(40)).length)
        assertEquals("AB", initialsOf("Abu Bakr"))
        assertEquals("M", initialsOf("muhib"))
        assertEquals("", initialsOf("   "))
    }

    @Test
    fun greetingUsesTheFirstNameOrAFriendlyDefault() {
        assertEquals("Hi, Muhib", UserPreferences(name = "Muhib").greeting)
        assertEquals("Hi, Abu", UserPreferences(name = "Abu Bakr").greeting)
        assertEquals("Hi there", UserPreferences(name = "").greeting)
    }

    // ---------------------------------------------------------------- home

    @Test
    fun homeOnFirstStartSaysStart() {
        val home = buildHomeState(catalog, ProgressSnapshot.EMPTY, UserPreferences(name = "Ali"), today)
        assertEquals("Start, Linux basics", home.continueCard.eyebrow)
        assertEquals("Read lesson", home.continueCard.actionLabel)
        assertEquals(OpenTarget.Lesson("l1"), home.continueCard.target)
        assertNull(home.continueCard.progress)
        assertEquals("2 labs, 0% done", home.tracks.first().subtitle)
        assertEquals(7, home.week.size)
    }

    @Test
    fun homeContinuesTheLastTrackWithALab() {
        val snapshot = ProgressSnapshot(completedLessons = setOf("d1"), lastFinishedItemId = "d1", activity = mapOf(today.toEpochDay() to 1))
        val home = buildHomeState(catalog, snapshot, UserPreferences(), today)
        assertEquals("Continue, Docker", home.continueCard.eyebrow)
        assertEquals("Resume lab", home.continueCard.actionLabel)
        assertEquals(0.5f, home.continueCard.progress)
        assertEquals("1 lab, 50% done", home.tracks.last().subtitle)
        assertEquals(1, home.streakDays)
    }

    // ---------------------------------------------------------------- track

    @Test
    fun trackShowsModulesLockedAndTimeLeft() {
        val track = buildTrackState(catalog, ProgressSnapshot.EMPTY, "linux")!!
        assertEquals("0 of 2 modules", track.modulesLine)
        assertEquals("About 30 min left", track.timeLeft)
        assertEquals("Up next, 3 steps", track.modules[0].subtitle)
        assertEquals(ModuleStatus.Locked, track.modules[1].status)
        assertEquals("Finish \"Moving\" first", track.modules[1].lockedMessage)
        assertEquals("Quiz, 2 questions", track.modules[0].items.last().subtitle)
    }

    @Test
    fun finishedModuleShowsQuizScore() {
        val snapshot = ProgressSnapshot(
            completedLessons = setOf("l1"),
            completedLabs = setOf("l-lab1"),
            quizAttempts = listOf(QuizAttemptRecord("l-quiz", 1, 2, false, 1)),
        )
        val track = buildTrackState(catalog, snapshot, "linux")!!
        assertEquals("Done, quiz 50%", track.modules[0].subtitle)
        assertEquals("Up next, 2 steps", track.modules[1].subtitle)
        assertEquals("Quiz, best 1 of 2", track.modules[0].items.last().subtitle)
        assertNull(buildTrackState(catalog, snapshot, "nope"))
    }

    @Test
    fun timeLeftFormats() {
        assertEquals("About 1h 20m left", formatTimeLeft(80))
        assertEquals("About 2h left", formatTimeLeft(120))
        assertEquals("Track complete", formatTimeLeft(0))
    }

    // ---------------------------------------------------------------- profile

    @Test
    fun profileBeforeAnyQuizShowsADash() {
        val profile = buildProfileState(catalog, ProgressSnapshot.EMPTY, today)
        assertEquals("-", profile.averageQuiz)
        assertEquals(0, profile.unlockedCount)
        assertEquals("0 of 7 days", profile.achievements.first { it.achievement.id == "week-warrior" }.detail)
        assertEquals("0 of 1 labs", profile.achievements.first { it.achievement.id == "container-captain" }.detail)
    }

    @Test
    fun profileShowsUnlockedAchievements() {
        val snapshot = ProgressSnapshot(
            completedLabs = setOf("d-lab1"),
            achievements = mapOf("first-command" to 1L, "container-captain" to 2L),
            quizAttempts = listOf(QuizAttemptRecord("l-quiz", 2, 2, false, 1)),
        )
        val profile = buildProfileState(catalog, snapshot, today)
        assertEquals(1, profile.labsDone)
        assertEquals("100%", profile.averageQuiz)
        assertEquals(2, profile.unlockedCount)
        assertTrue(profile.achievements.first { it.achievement.id == "container-captain" }.unlocked)
        assertFalse(profile.achievements.first { it.achievement.id == "quiz-ace" }.unlocked) // not stored yet
    }
}
