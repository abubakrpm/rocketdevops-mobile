package com.rocketdevops.mobile.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rocketdevops.mobile.data.prefs.FontSize
import com.rocketdevops.mobile.data.prefs.UserPreferences
import com.rocketdevops.mobile.ui.theme.RocketDevOpsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compose UI test (phone or emulator): Settings sends the right changes, Reset asks first. */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var fontSize: FontSize? = null
    private var timer: Boolean? = null
    private var resetForgetName: Boolean? = null

    private fun show() = compose.setContent {
        RocketDevOpsTheme {
            SettingsScreen(
                preferences = UserPreferences(name = "Ali"),
                actions = SettingsActions(
                    onFontSizeChange = { fontSize = it },
                    onQuizTimerChange = { timer = it },
                    onResetProgress = { resetForgetName = it },
                ),
                onBack = {},
                onOpenCatalog = {},
            )
        }
    }

    @Test
    fun fontSizeAndTimerAreSent() {
        show()
        compose.onNodeWithText("Large").performClick()
        assertEquals(FontSize.Large, fontSize)
        compose.onNodeWithText("Quiz timer").performScrollTo().performClick()
        assertEquals(true, timer)
    }

    @Test
    fun resetAsksFirstAndCanBeCancelled() {
        show()
        compose.onNodeWithText("Reset progress").performScrollTo().performClick()
        compose.onNodeWithText("Reset progress?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertNull(resetForgetName)                 // nothing deleted
    }

    @Test
    fun resetCanAlsoForgetTheName() {
        show()
        compose.onNodeWithText("Reset progress").performScrollTo().performClick()
        compose.onNodeWithText("Also forget the name (for the next person)").performClick()
        compose.onNodeWithText("Reset").performClick()
        assertEquals(true, resetForgetName)
    }
}
