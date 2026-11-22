package com.rocketdevops.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rocketdevops.mobile.navigation.RocketDevOpsApp
import com.rocketdevops.mobile.ui.app.AppViewModel
import com.rocketdevops.mobile.ui.theme.AppTheme
import com.rocketdevops.mobile.ui.theme.RocketDevOpsTheme
import com.rocketdevops.mobile.ui.theme.ThemeMode

/**
 * The only Activity. Everything else is Compose screens switched by Navigation.
 */
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels { AppViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // draw behind the status and navigation bars, like modern Android apps

        setContent {
            // Saved settings from DataStore (Stage 6): the theme and name survive closing the app.
            val preferences by appViewModel.preferences.collectAsStateWithLifecycle()
            val darkTheme = when (preferences?.themeMode ?: ThemeMode.System) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            // Font size from Settings: multiplies the phone's own font scale, so every sp text
            // in the app grows or shrinks together (layouts use dp and are not stretched).
            val systemDensity = LocalDensity.current
            val textScale = preferences?.fontSize?.scale ?: 1f
            val density = remember(systemDensity, textScale) {
                Density(systemDensity.density, systemDensity.fontScale * textScale)
            }

            CompositionLocalProvider(LocalDensity provides density) {
                RocketDevOpsTheme(darkTheme = darkTheme) {
                    val prefs = preferences
                    if (prefs == null) {
                        // DataStore is still reading (a few milliseconds): plain background, no flash.
                        Box(Modifier.fillMaxSize().background(AppTheme.colors.background))
                    } else {
                        RocketDevOpsApp(
                            preferences = prefs,
                            achievementUnlocked = appViewModel.achievementUnlocked,
                            onFinishOnboarding = appViewModel::finishOnboarding,
                            onNameChange = appViewModel::setName,
                            onThemeModeChange = appViewModel::setThemeMode,
                            onFontSizeChange = appViewModel::setFontSize,
                            onQuizTimerChange = appViewModel::setQuizTimer,
                            onResetProgress = appViewModel::resetProgress,
                        )
                    }
                }
            }
        }
    }
}
