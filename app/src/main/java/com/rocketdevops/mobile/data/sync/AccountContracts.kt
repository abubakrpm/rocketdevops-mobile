package com.rocketdevops.mobile.data.sync

import kotlinx.coroutines.flow.StateFlow

/** The signed-in learner. Never holds the password: Firebase keeps only a login token. */
data class CloudUser(val uid: String, val email: String)

/** Why signing in or up failed, in words the screen can show. */
enum class AuthError(val message: String) {
    WrongCredentials("Email or password is not correct."),
    NoSuchAccount("No account uses this email. Create one instead."),
    EmailInUse("An account with this email already exists. Sign in instead."),
    WeakPassword("Choose a longer password (at least 6 characters)."),
    InvalidEmail("That email address does not look right."),
    TooManyAttempts("Too many tries. Wait a few minutes and try again."),
    Network("No internet connection. Connect and try again."),
    NotConfigured("Accounts are not set up in this build of the app."),
    Unknown("Something went wrong. Please try again."),
}

/** A failure the account screens can show. */
class AuthException(val error: AuthError, cause: Throwable? = null) : Exception(error.message, cause)

/** Signing in and out. Implemented with Firebase Authentication (FirebaseAuthGateway). */
interface AuthGateway {
    /** False when the app was built without google-services.json: accounts are hidden. */
    val isAvailable: Boolean
    val currentUser: StateFlow<CloudUser?>

    /** @throws AuthException */
    suspend fun signIn(email: String, password: String): CloudUser

    /** @throws AuthException */
    suspend fun signUp(email: String, password: String): CloudUser

    /** Emails a reset link. @throws AuthException */
    suspend fun sendPasswordReset(email: String)

    fun signOut()
}

/** The online copy of each learner's progress. Implemented with Cloud Firestore. */
interface CloudProgressStore {
    /** Null if this account has no online copy yet. @throws Exception on network or permission errors */
    suspend fun load(uid: String): Pair<String, ProgressBackup>?

    /** Replaces the online copy. @throws Exception on network or permission errors */
    suspend fun save(uid: String, name: String, backup: ProgressBackup)
}

/** The parts of the settings that change when someone signs in or out. */
interface LearnerIdentity {
    suspend fun currentName(): String
    /** Signed in: show this name on Home and skip the Welcome screen. */
    suspend fun signedIn(name: String)
    /** Signed out: forget the name, show Welcome next. */
    suspend fun signedOut()
}

/** Pure checks before anything is sent to Firebase, so mistakes are explained at once. */
object AuthValidation {
    const val MIN_PASSWORD = 6
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun emailError(email: String): String? = when {
        email.isBlank() -> "Enter your email."
        !EMAIL.matches(email.trim()) -> AuthError.InvalidEmail.message
        else -> null
    }

    fun passwordError(password: String): String? = when {
        password.isEmpty() -> "Enter your password."
        password.length < MIN_PASSWORD -> AuthError.WeakPassword.message
        else -> null
    }

    fun nameError(name: String): String? = if (name.isBlank()) "Enter your name." else null
}
