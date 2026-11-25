package com.rocketdevops.mobile.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rocketdevops.mobile.data.sync.LearnerIdentity
import com.rocketdevops.mobile.ui.theme.ThemeMode
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** One file on the phone ("user_prefs.preferences_pb"), created on first write. */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/**
 * Reads and writes UserPreferences. `preferences` is a Flow, so changing the name in Settings
 * updates the Home greeting immediately, and the theme choice now survives closing the app.
 */
class UserPreferencesRepository(context: Context) : LearnerIdentity {
    private val store = context.applicationContext.dataStore

    val preferences: Flow<UserPreferences> = store.data
        // A damaged file must not crash the app: fall back to defaults.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            UserPreferences(
                name = p[NAME].orEmpty(),
                onboarded = p[ONBOARDED] ?: false,
                themeMode = p[THEME]?.let { saved -> ThemeMode.entries.firstOrNull { it.name == saved } } ?: ThemeMode.System,
                fontSize = p[FONT_SIZE]?.let { saved -> FontSize.entries.firstOrNull { it.name == saved } } ?: FontSize.Medium,
                quizTimer = p[QUIZ_TIMER] ?: false,
            )
        }

    /** Welcome screen "Continue" and "Skip" both end onboarding; Skip passes an empty name. */
    suspend fun finishOnboarding(name: String) = store.edit {
        it[NAME] = cleanName(name)
        it[ONBOARDED] = true
    }

    suspend fun setName(name: String) = store.edit { it[NAME] = cleanName(name) }

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[THEME] = mode.name }

    suspend fun setFontSize(size: FontSize) = store.edit { it[FONT_SIZE] = size.name }

    suspend fun setQuizTimer(enabled: Boolean) = store.edit { it[QUIZ_TIMER] = enabled }

    /** Reset for the next visitor: forgets the name and shows the Welcome screen again. Settings stay. */
    suspend fun forgetUser() = store.edit {
        it.remove(NAME)
        it[ONBOARDED] = false
    }

    // ---- LearnerIdentity: used by AccountRepository when someone signs in or out

    override suspend fun currentName(): String = preferences.first().name

    override suspend fun signedIn(name: String) {
        finishOnboarding(name)
    }

    override suspend fun signedOut() {
        forgetUser()
    }

    private companion object {
        val NAME = stringPreferencesKey("name")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val THEME = stringPreferencesKey("theme_mode")
        val FONT_SIZE = stringPreferencesKey("font_size")
        val QUIZ_TIMER = booleanPreferencesKey("quiz_timer")
    }
}
