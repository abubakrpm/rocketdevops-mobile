package com.rocketdevops.mobile.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rocketdevops.mobile.RocketDevOpsApplication
import com.rocketdevops.mobile.data.prefs.FontSize
import com.rocketdevops.mobile.data.prefs.UserPreferences
import com.rocketdevops.mobile.data.prefs.UserPreferencesRepository
import com.rocketdevops.mobile.data.progress.Achievement
import com.rocketdevops.mobile.data.progress.ProgressRepository
import com.rocketdevops.mobile.data.sync.AccountRepository
import com.rocketdevops.mobile.data.sync.CloudUser
import com.rocketdevops.mobile.data.sync.SignOutResult
import com.rocketdevops.mobile.data.sync.SyncStatus
import com.rocketdevops.mobile.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * App-wide state for MainActivity: the saved settings (name, onboarding, theme) and
 * "Achievement unlocked" events. `preferences` is null for the first few milliseconds
 * while DataStore reads the file, so the app does not flash the wrong screen or theme.
 */
class AppViewModel(
    private val prefs: UserPreferencesRepository,
    private val progress: ProgressRepository,
    private val accounts: AccountRepository,
) : ViewModel() {

    /** False when the app was built without Firebase settings: account buttons are hidden. */
    val accountsAvailable: Boolean get() = accounts.isAvailable
    val currentUser: StateFlow<CloudUser?> = accounts.currentUser
    val syncStatus: StateFlow<SyncStatus> = accounts.syncStatus

    val preferences: StateFlow<UserPreferences?> =
        prefs.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val achievementUnlocked: Flow<Achievement> = progress.achievementUnlocked

    fun finishOnboarding(name: String) {
        viewModelScope.launch { prefs.finishOnboarding(name) }
    }

    fun setName(name: String) {
        viewModelScope.launch {
            prefs.setName(name)
            accounts.nameChanged() // keep the online copy's name in step
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setFontSize(size: FontSize) {
        viewModelScope.launch { prefs.setFontSize(size) }
    }

    fun setQuizTimer(enabled: Boolean) {
        viewModelScope.launch { prefs.setQuizTimer(enabled) }
    }

    /**
     * Settings > Reset progress. Deletes lessons, labs, quiz scores, streak and achievements.
     * With `forgetName` (exhibition mode) the name is cleared too and Welcome shows again.
     * `onDone` runs after the database is empty, so the app can navigate or show a message.
     */
    fun resetProgress(forgetName: Boolean, onDone: (savedOnline: Boolean) -> Unit) {
        viewModelScope.launch {
            // Signed in: the online copy is emptied too, or signing in again would bring it back.
            val savedOnline = accounts.resetProgress()
            if (forgetName && currentUser.value == null) prefs.forgetUser()
            onDone(savedOnline)
        }
    }

    /**
     * Settings > Sign out. Saves the latest progress online first; if that fails (no internet)
     * the result is NotSaved and nothing is removed, so the screen can ask what to do.
     */
    fun signOut(force: Boolean, onResult: (SignOutResult) -> Unit) {
        viewModelScope.launch { onResult(accounts.signOut(force)) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as RocketDevOpsApplication).container
                AppViewModel(c.userPreferences, c.progressRepository, c.accounts)
            }
        }
    }
}
