package com.rocketdevops.mobile.ui.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rocketdevops.mobile.RocketDevOpsApplication
import com.rocketdevops.mobile.data.sync.AccountRepository
import com.rocketdevops.mobile.data.sync.AuthError
import com.rocketdevops.mobile.data.sync.AuthException
import com.rocketdevops.mobile.data.sync.AuthValidation
import com.rocketdevops.mobile.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { SignIn, SignUp }

/**
 * Everything the Sign in / Create account screen shows.
 * The password lives only here, in memory, while the screen is open. It is never saved:
 * Firebase checks it and keeps only a login token.
 */
data class AuthUiState(
    val mode: AuthMode,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    /** A problem from Firebase, shown above the button. */
    val formError: String? = null,
    /** "Reset link sent to ..." after Forgot password. */
    val info: String? = null,
    val busy: Boolean = false,
    /** Set once signed in; the screen then navigates on. */
    val done: Boolean = false,
)

class AuthViewModel(
    savedState: SavedStateHandle,
    private val accounts: AccountRepository,
) : ViewModel() {

    private val startMode =
        if (savedState.get<String>(Routes.ARG_AUTH_MODE) == Routes.AUTH_SIGN_UP) AuthMode.SignUp else AuthMode.SignIn

    private val _state = MutableStateFlow(
        AuthUiState(
            mode = startMode,
            formError = if (accounts.isAvailable) null else AuthError.NotConfigured.message,
        ),
    )
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = null, formError = null) }
    fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailError = null, formError = null, info = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, passwordError = null, formError = null) }

    fun switchMode() = _state.update {
        it.copy(
            mode = if (it.mode == AuthMode.SignIn) AuthMode.SignUp else AuthMode.SignIn,
            nameError = null, emailError = null, passwordError = null, formError = null, info = null,
        )
    }

    fun submit() {
        val s = _state.value
        if (s.busy) return
        val nameError = if (s.mode == AuthMode.SignUp) AuthValidation.nameError(s.name) else null
        val emailError = AuthValidation.emailError(s.email)
        val passwordError = AuthValidation.passwordError(s.password)
        if (nameError != null || emailError != null || passwordError != null) {
            _state.update { it.copy(nameError = nameError, emailError = emailError, passwordError = passwordError) }
            return
        }
        _state.update { it.copy(busy = true, formError = null, info = null) }
        viewModelScope.launch {
            try {
                when (s.mode) {
                    AuthMode.SignIn -> accounts.signIn(s.email, s.password)
                    AuthMode.SignUp -> accounts.signUp(s.name, s.email, s.password)
                }
                _state.update { it.copy(busy = false, password = "", done = true) }
            } catch (e: AuthException) {
                _state.update { it.copy(busy = false, formError = e.error.message) }
            }
        }
    }

    /** "Forgot password?": Firebase emails a link to choose a new one. */
    fun forgotPassword() {
        val email = _state.value.email
        AuthValidation.emailError(email)?.let { error ->
            _state.update { it.copy(emailError = error) }
            return
        }
        _state.update { it.copy(busy = true, formError = null) }
        viewModelScope.launch {
            try {
                accounts.sendPasswordReset(email)
                _state.update { it.copy(busy = false, info = "We sent a link to ${email.trim()} to choose a new password.") }
            } catch (e: AuthException) {
                _state.update { it.copy(busy = false, formError = e.error.message) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as RocketDevOpsApplication).container
                AuthViewModel(createSavedStateHandle(), c.accounts)
            }
        }
    }
}
