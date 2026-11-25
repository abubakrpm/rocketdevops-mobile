package com.rocketdevops.mobile.data.sync

import com.rocketdevops.mobile.data.progress.QuizAttemptRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Merging two copies of progress and the Firestore document format. */
class ProgressBackupTest {

    private val phone = ProgressBackup(
        completed = listOf(CompletedRecord("l1", "lesson", 100), CompletedRecord("lab1", "lab", 300)),
        quizAttempts = listOf(QuizAttemptRecord("q1", 4, 5, false, 200)),
        activity = mapOf(20_000L to 2, 20_001L to 1),
        achievements = mapOf("first-lesson" to 100L),
    )
    private val online = ProgressBackup(
        completed = listOf(CompletedRecord("l1", "lesson", 50), CompletedRecord("l2", "lesson", 400)),
        quizAttempts = listOf(QuizAttemptRecord("q1", 4, 5, false, 200), QuizAttemptRecord("q2", 3, 5, false, 500)),
        activity = mapOf(20_000L to 3),
        achievements = mapOf("first-lesson" to 60L, "streak-3" to 700L),
    )

    @Test
    fun mergeKeepsEverythingEarnedOnBothSides() {
        val merged = mergeBackups(phone, online)
        assertEquals(setOf("l1", "l2", "lab1"), merged.completed.map { it.itemId }.toSet())
        assertEquals(100L, merged.completed.first { it.itemId == "l1" }.atMillis) // later finish wins
        assertEquals(2, merged.quizAttempts.size)                                // the shared attempt once
        assertEquals(mapOf(20_000L to 3, 20_001L to 1), merged.activity)         // max per day, not doubled
        assertEquals(60L, merged.achievements["first-lesson"])                   // earliest unlock
        assertEquals(700L, merged.achievements["streak-3"])
    }

    @Test
    fun mergeIsSymmetricAndRepeatable() {
        val ab = mergeBackups(phone, online)
        assertEquals(ab, mergeBackups(online, phone))
        assertEquals(ab, mergeBackups(ab, ab))
        assertEquals(phone.copy(completed = phone.completed.sortedBy { it.atMillis }), mergeBackups(phone, ProgressBackup.EMPTY))
    }

    @Test
    fun tooManyAttemptsKeepsTheNewestAndEveryBestScore() {
        val old = QuizAttemptRecord("q-old", 5, 5, false, 1)
        val many = (2..700L).map { QuizAttemptRecord("q1", 1, 5, false, it) }
        val merged = mergeBackups(ProgressBackup(quizAttempts = listOf(old) + many), ProgressBackup.EMPTY)
        assertEquals(ProgressBackup.MAX_ATTEMPTS, merged.quizAttempts.size)
        assertTrue(old in merged.quizAttempts)
        assertEquals(700L, merged.quizAttempts.last().atMillis)
    }

    @Test
    fun documentRoundTripGivesTheSameProgress() {
        val backup = mergeBackups(phone, online)
        val (name, back) = BackupFormat.fromMap(BackupFormat.toMap(backup, "Ali", 999))
        assertEquals("Ali", name)
        assertEquals(backup, back)
    }

    @Test
    fun damagedDocumentIsReadWithoutCrashing() {
        val (name, back) = BackupFormat.fromMap(
            mapOf(
                "completed" to listOf(mapOf("id" to "l1", "kind" to "lesson", "at" to 5L), "junk", mapOf("id" to 3)),
                "attempts" to "not a list",
                "activity" to mapOf("abc" to 2L, "7" to 1L),
            ),
        )
        assertEquals("", name)
        assertEquals(listOf(CompletedRecord("l1", "lesson", 5)), back.completed)
        assertTrue(back.quizAttempts.isEmpty())
        assertEquals(mapOf(7L to 1), back.activity)
    }

    @Test
    fun formChecks() {
        assertNull(AuthValidation.emailError("ali@example.com"))
        assertEquals(AuthError.InvalidEmail.message, AuthValidation.emailError("ali@"))
        assertEquals("Enter your email.", AuthValidation.emailError("  "))
        assertEquals(AuthError.WeakPassword.message, AuthValidation.passwordError("12345"))
        assertNull(AuthValidation.passwordError("123456"))
        assertEquals("Enter your name.", AuthValidation.nameError(" "))
    }
}
