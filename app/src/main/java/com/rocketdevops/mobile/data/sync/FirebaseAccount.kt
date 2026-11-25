package com.rocketdevops.mobile.data.sync

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/*
 * The only file that talks to Firebase. Everything else uses the interfaces in
 * AccountContracts.kt, so the account logic is unit tested without Firebase or a network.
 */

/** True when the app was built with app/google-services.json (see docs/firebase-setup.md). */
fun isFirebaseConfigured(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

/**
 * Waits for a Firebase Task inside a coroutine. Written here (10 lines) instead of adding
 * the kotlinx-coroutines-play-services library.
 */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        val error = task.exception
        when {
            error != null -> cont.resumeWithException(error)
            task.isCanceled -> cont.cancel()
            else -> cont.resume(task.result)
        }
    }
}

/** Firebase Authentication, email and password only (no SMS: that costs money). */
class FirebaseAuthGateway(context: Context) : AuthGateway {

    override val isAvailable: Boolean = isFirebaseConfigured(context)
    private val auth: FirebaseAuth? = if (isAvailable) FirebaseAuth.getInstance() else null

    private val _currentUser = MutableStateFlow(auth?.currentUser?.toCloudUser())
    override val currentUser: StateFlow<CloudUser?> = _currentUser.asStateFlow()

    init {
        // Firebase remembers the login between app starts; this keeps currentUser in step.
        auth?.addAuthStateListener { _currentUser.value = it.currentUser?.toCloudUser() }
    }

    override suspend fun signIn(email: String, password: String): CloudUser =
        signedInUser { requireAuth().signInWithEmailAndPassword(email, password).await().user }

    override suspend fun signUp(email: String, password: String): CloudUser =
        signedInUser { requireAuth().createUserWithEmailAndPassword(email, password).await().user }

    override suspend fun sendPasswordReset(email: String) {
        try {
            requireAuth().sendPasswordResetEmail(email).await()
        } catch (e: Exception) {
            throw e.toAuthException()
        }
    }

    override fun signOut() {
        auth?.signOut()
        _currentUser.value = null
    }

    private fun requireAuth(): FirebaseAuth = auth ?: throw AuthException(AuthError.NotConfigured)

    private inline fun signedInUser(block: () -> FirebaseUser?): CloudUser {
        val user = try {
            block()
        } catch (e: Exception) {
            throw e.toAuthException()
        } ?: throw AuthException(AuthError.Unknown)
        return user.toCloudUser().also { _currentUser.value = it }
    }

    private fun FirebaseUser.toCloudUser() = CloudUser(uid, email.orEmpty())

    /** Firebase errors in plain words. The order matters: some classes extend others. */
    private fun Exception.toAuthException(): AuthException = when (this) {
        is AuthException -> this
        is FirebaseAuthWeakPasswordException -> AuthException(AuthError.WeakPassword, this)
        is FirebaseAuthUserCollisionException -> AuthException(AuthError.EmailInUse, this)
        is FirebaseAuthInvalidUserException -> AuthException(AuthError.NoSuchAccount, this)
        is FirebaseAuthInvalidCredentialsException ->
            if (errorCode == "ERROR_INVALID_EMAIL") AuthException(AuthError.InvalidEmail, this)
            else AuthException(AuthError.WrongCredentials, this)
        is FirebaseNetworkException -> AuthException(AuthError.Network, this)
        is FirebaseTooManyRequestsException -> AuthException(AuthError.TooManyAttempts, this)
        else -> AuthException(AuthError.Unknown, this)
    }
}

/**
 * One Firestore document per learner: users/{uid}. Security rules (firestore.rules) allow
 * each signed-in learner to read and write only their own document.
 */
class FirestoreProgressStore(private val available: Boolean) : CloudProgressStore {

    private val db: FirebaseFirestore? = if (available) FirebaseFirestore.getInstance() else null

    private fun doc(uid: String) =
        (db ?: throw AuthException(AuthError.NotConfigured)).collection(COLLECTION).document(uid)

    override suspend fun load(uid: String): Pair<String, ProgressBackup>? {
        // From the server, never the offline cache: a stale copy merged and saved back could
        // overwrite progress made on another phone. Offline this fails and sign-in says so.
        val snapshot = doc(uid).get(Source.SERVER).await()
        val data = snapshot.data ?: return null
        return BackupFormat.fromMap(data)
    }

    override suspend fun save(uid: String, name: String, backup: ProgressBackup) {
        doc(uid).set(BackupFormat.toMap(backup, name, System.currentTimeMillis())).await()
    }

    private companion object {
        const val COLLECTION = "users"
    }
}
