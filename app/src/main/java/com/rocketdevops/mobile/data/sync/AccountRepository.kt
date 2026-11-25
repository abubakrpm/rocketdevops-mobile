package com.rocketdevops.mobile.data.sync

import com.rocketdevops.mobile.data.prefs.cleanName
import com.rocketdevops.mobile.data.progress.ProgressRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** Shown in Settings under the account row. */
enum class SyncStatus { Off, Saving, Saved, Offline }

/** What happened when the learner asked to sign out. */
enum class SignOutResult {
    Done,
    /** The latest progress could not be saved online (no internet). Nothing was removed. */
    NotSaved,
}

/**
 * Optional accounts (Firebase, Spark plan). The app still works fully as a guest.
 *
 *  - Sign up: the progress already on this phone becomes the new account's progress.
 *  - Sign in: guest progress on this phone and the account's online copy are MERGED
 *    (nothing earned is lost), then the phone shows the merged progress.
 *  - While signed in: every change is saved online about 2 seconds later.
 *  - Sign out: the latest progress is saved online first, then this phone is cleared and shows
 *    the Welcome screen. Signing in again (on this or any phone) brings everything back.
 *
 * Room stays the single source of truth for the screens; the cloud is a copy kept in step with it.
 */
@OptIn(FlowPreview::class)
class AccountRepository(
    private val auth: AuthGateway,
    private val cloud: CloudProgressStore,
    private val progress: ProgressRepository,
    private val identity: LearnerIdentity,
    private val scope: CoroutineScope,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val saveDelayMillis: Long = 2_000,
    /**
     * How long to wait for the server to confirm a save. Offline, Firestore keeps the write in
     * its own queue and sends it later, but never confirms; without a limit the app would hang.
     */
    private val uploadTimeoutMillis: Long = 8_000,
) {
    val isAvailable: Boolean get() = auth.isAvailable
    val currentUser: StateFlow<CloudUser?> get() = auth.currentUser

    private val _syncStatus = MutableStateFlow(SyncStatus.Off)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    /** Sign-in, sign-out and automatic saves never run at the same time. */
    private val lock = Mutex()

    /** While true, local changes are ours (copying an account in), not the learner's: do not upload. */
    @Volatile private var copying = false

    init {
        if (auth.isAvailable) startAutoSave()
    }

    // ---------------------------------------------------------------- sign in / up / out

    /** @throws AuthException */
    suspend fun signUp(name: String, email: String, password: String): CloudUser = lock.withLock {
        val user = auth.signUp(email.trim(), password)
        val cleanName = cleanName(name)
        identity.signedIn(cleanName)
        // The progress made so far as a guest becomes this account's first online copy.
        uploadNow(user, cleanName)
        user
    }

    /** @throws AuthException */
    suspend fun signIn(email: String, password: String): CloudUser = lock.withLock {
        val user = auth.signIn(email.trim(), password)
        val remote = try {
            withTimeout(uploadTimeoutMillis * 2) { cloud.load(user.uid) }
        } catch (e: Exception) { // includes the timeout
            // Signed in, but the online copy cannot be read: do not risk overwriting it.
            auth.signOut()
            throw AuthException(AuthError.Network, e)
        }
        val local = progress.exportBackup()
        val merged = mergeBackups(local, remote?.second ?: ProgressBackup.EMPTY)
        val name = remote?.first?.takeIf { it.isNotBlank() } ?: identity.currentName()

        copying = true
        try {
            progress.importBackup(merged)
        } finally {
            copying = false
        }
        identity.signedIn(name)
        if (merged != remote?.second) uploadNow(user, name) // guest progress was added
        _syncStatus.value = SyncStatus.Saved
        user
    }

    /**
     * @param force sign out even if the latest progress could not be saved online
     *        (the learner confirmed; unsaved progress on this phone is lost).
     */
    suspend fun signOut(force: Boolean = false): SignOutResult = lock.withLock {
        val user = auth.currentUser.value
        if (user != null && !force && !uploadNow(user, identity.currentName())) {
            return@withLock SignOutResult.NotSaved
        }
        auth.signOut()
        copying = true
        try {
            progress.resetAll()
        } finally {
            copying = false
        }
        identity.signedOut()
        _syncStatus.value = SyncStatus.Off
        SignOutResult.Done
    }

    /** @throws AuthException */
    suspend fun sendPasswordReset(email: String) = auth.sendPasswordReset(email.trim())

    /**
     * Settings > Reset progress while signed in: the online copy is emptied too, otherwise
     * signing in again would bring the old progress back. Returns false if that failed.
     */
    suspend fun resetProgress(): Boolean = lock.withLock {
        progress.resetAll()
        val user = auth.currentUser.value ?: return@withLock true
        uploadNow(user, identity.currentName())
    }

    /** The name was changed in Settings: keep the online copy in step. */
    fun nameChanged() {
        val user = auth.currentUser.value ?: return
        scope.launch { lock.withLock { uploadNow(user, identity.currentName()) } }
    }

    // ---------------------------------------------------------------- saving

    /**
     * Saves this phone's progress online. Returns true once the server confirmed it, false
     * (never throws) if it failed or was not confirmed in time. While signed in, Firestore still
     * sends an unconfirmed write by itself when the internet comes back.
     */
    private suspend fun uploadNow(user: CloudUser, name: String): Boolean {
        _syncStatus.value = SyncStatus.Saving
        val confirmed = try {
            withTimeoutOrNull(uploadTimeoutMillis) {
                cloud.save(user.uid, name, progress.exportBackup())
                true
            } ?: false
        } catch (e: CancellationException) {
            throw e // the coroutine was cancelled (a newer save started): not a sync failure
        } catch (e: Exception) {
            false
        }
        _syncStatus.value = if (confirmed) SyncStatus.Saved else SyncStatus.Offline
        return confirmed
    }

    /**
     * Every progress change while signed in is saved online after a short pause, so a burst
     * of changes (finishing a lab, then an achievement) becomes one write. Spark-plan friendly:
     * a busy learner makes a few dozen writes a day; the free limit is 20,000.
     */
    private fun startAutoSave() {
        scope.launch {
            // No drop(1): the first value is also saved, so changes made offline last time are
            // sent when the app opens again (one extra write per app start while signed in).
            combine(auth.currentUser, progress.snapshot) { user, _ -> user }
                .filter { it != null && !copying }
                .debounce(saveDelayMillis)
                .collectLatest { user ->
                    // Waits for sign-in / sign-out / reset to finish rather than skipping the save.
                    lock.withLock {
                        // Re-check under the lock: the learner may have signed out during the
                        // pause, and an old save must never overwrite the account with an empty phone.
                        if (user != null && auth.currentUser.value?.uid == user.uid && !copying) {
                            uploadNow(user, identity.currentName())
                        }
                    }
                }
        }
    }
}
