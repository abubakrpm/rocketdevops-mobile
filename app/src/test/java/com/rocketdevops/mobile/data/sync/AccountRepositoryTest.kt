package com.rocketdevops.mobile.data.sync

import com.rocketdevops.mobile.data.progress.FakeProgressDao
import com.rocketdevops.mobile.data.progress.ProgressRepository
import com.rocketdevops.mobile.data.progress.ProgressTestCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

/**
 * Optional accounts: the promise to the learner is "sign in again and everything is back".
 * Firebase is replaced by in-memory fakes, so these tests run offline on the plain JVM.
 */
class AccountRepositoryTest {

    /** Accounts live in a map: email -> (password, uid). */
    private class FakeAuth : AuthGateway {
        val accounts = mutableMapOf<String, Pair<String, String>>()
        private val user = MutableStateFlow<CloudUser?>(null)
        override val isAvailable = true
        override val currentUser: StateFlow<CloudUser?> = user
        override suspend fun signIn(email: String, password: String): CloudUser {
            val (pw, uid) = accounts[email] ?: throw AuthException(AuthError.NoSuchAccount)
            if (pw != password) throw AuthException(AuthError.WrongCredentials)
            return CloudUser(uid, email).also { user.value = it }
        }
        override suspend fun signUp(email: String, password: String): CloudUser {
            if (email in accounts) throw AuthException(AuthError.EmailInUse)
            accounts[email] = password to "uid-${accounts.size + 1}"
            return signIn(email, password)
        }
        override suspend fun sendPasswordReset(email: String) = Unit
        override fun signOut() { user.value = null }
    }

    /** The "server". `offline = true` makes every call hang, like Firestore without internet. */
    private class FakeCloud : CloudProgressStore {
        val docs = mutableMapOf<String, Pair<String, ProgressBackup>>()
        var offline = false
        override suspend fun load(uid: String): Pair<String, ProgressBackup>? {
            if (offline) awaitCancellation()
            return docs[uid]
        }
        override suspend fun save(uid: String, name: String, backup: ProgressBackup) {
            if (offline) awaitCancellation()
            docs[uid] = name to backup
        }
    }

    private class FakeIdentity : LearnerIdentity {
        var name = ""
        var onboarded = false
        override suspend fun currentName() = name
        override suspend fun signedIn(name: String) { this.name = name; onboarded = true }
        override suspend fun signedOut() { name = ""; onboarded = false }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val auth = FakeAuth()
    private val cloud = FakeCloud()
    private val identity = FakeIdentity()
    private var clock = 1_000L
    private val dao = FakeProgressDao()
    private val progress = ProgressRepository(dao, { ProgressTestCatalog.catalog }, { LocalDate.of(2026, 11, 25) }, { clock++ })
    private val accounts = AccountRepository(
        auth, cloud, progress, identity, scope,
        saveDelayMillis = 50, uploadTimeoutMillis = 200,
    )

    @After fun tearDown() = scope.cancel()

    private suspend fun completedIds() = progress.exportBackup().completed.map { it.itemId }.toSet()

    @Test
    fun signUpSavesGuestProgressOnline() = runBlocking {
        progress.markLessonComplete("l1")
        val user = accounts.signUp("  Ali  Raza ", "ali@example.com", "secret1")
        assertEquals("Ali Raza", cloud.docs.getValue(user.uid).first)
        assertEquals(setOf("l1"), cloud.docs.getValue(user.uid).second.completed.map { it.itemId }.toSet())
        assertEquals(SyncStatus.Saved, accounts.syncStatus.value)
    }

    @Test
    fun signOutThenSignInBringsEverythingBack() = runBlocking {
        accounts.signUp("Ali", "ali@example.com", "secret1")
        progress.markLessonComplete("l1")
        progress.markLabComplete("l-lab1")

        assertEquals(SignOutResult.Done, accounts.signOut())
        assertTrue(completedIds().isEmpty())       // phone is clear for the next person
        assertFalse(identity.onboarded)            // Welcome screen is shown

        accounts.signIn("ali@example.com", "secret1")
        assertEquals(setOf("l1", "l-lab1"), completedIds())
        assertEquals("Ali", identity.name)
    }

    @Test
    fun signingInMergesGuestProgressWithTheAccount() = runBlocking {
        accounts.signUp("Ali", "ali@example.com", "secret1")
        progress.markLessonComplete("l1")
        accounts.signOut()

        progress.markLabComplete("l-lab1")         // done as a guest before signing in
        val user = accounts.signIn("ali@example.com", "secret1")

        assertEquals(setOf("l1", "l-lab1"), completedIds())
        assertEquals(setOf("l1", "l-lab1"), cloud.docs.getValue(user.uid).second.completed.map { it.itemId }.toSet())
    }

    @Test
    fun progressIsSavedAutomaticallyWhileSignedIn() = runBlocking {
        val user = accounts.signUp("Ali", "ali@example.com", "secret1")
        progress.markLessonComplete("l1")
        repeat(40) {
            if (cloud.docs[user.uid]?.second?.completed?.isNotEmpty() == true) return@runBlocking
            delay(25)
        }
        fail("progress was not saved online")
    }

    @Test
    fun signOutWithoutInternetKeepsEverything() = runBlocking {
        accounts.signUp("Ali", "ali@example.com", "secret1")
        progress.markLessonComplete("l1")
        cloud.offline = true

        assertEquals(SignOutResult.NotSaved, accounts.signOut())
        assertEquals(setOf("l1"), completedIds())
        assertTrue(auth.currentUser.value != null)
        assertTrue(accounts.syncStatus.value != SyncStatus.Saved) // Offline, or Saving a retry

        assertEquals(SignOutResult.Done, accounts.signOut(force = true)) // learner chose to anyway
        assertTrue(completedIds().isEmpty())
        assertNull(auth.currentUser.value)
    }

    @Test
    fun noInternetWhileSigningInLeavesThePhoneAlone() = runBlocking {
        accounts.signUp("Ali", "ali@example.com", "secret1")
        accounts.signOut()
        progress.markLessonComplete("l1")
        cloud.offline = true
        try {
            accounts.signIn("ali@example.com", "secret1")
            fail("expected a network error")
        } catch (e: AuthException) {
            assertEquals(AuthError.Network, e.error)
        }
        assertNull(auth.currentUser.value)
        assertEquals(setOf("l1"), completedIds())
    }

    @Test
    fun wrongPasswordIsReported() = runBlocking {
        accounts.signUp("Ali", "ali@example.com", "secret1")
        accounts.signOut()
        try {
            accounts.signIn("ali@example.com", "nope123")
            fail("expected wrong credentials")
        } catch (e: AuthException) {
            assertEquals(AuthError.WrongCredentials, e.error)
        }
    }

    @Test
    fun resetWhileSignedInAlsoEmptiesTheOnlineCopy() = runBlocking {
        val user = accounts.signUp("Ali", "ali@example.com", "secret1")
        progress.markLessonComplete("l1")
        assertTrue(accounts.resetProgress())
        assertTrue(cloud.docs.getValue(user.uid).second.isEmpty)

        accounts.signOut()
        accounts.signIn("ali@example.com", "secret1")
        assertTrue(completedIds().isEmpty())       // the old progress does not come back
    }
}
