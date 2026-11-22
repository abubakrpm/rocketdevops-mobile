package com.rocketdevops.mobile.ui.welcome

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rocketdevops.mobile.ui.theme.RocketDevOpsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compose UI test (phone or emulator): the first-launch name screen. */
@RunWith(AndroidJUnit4::class)
class WelcomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var finishedWith: String? = null

    private fun show() = compose.setContent {
        RocketDevOpsTheme { WelcomeScreen(onFinish = { finishedWith = it }) }
    }

    @Test
    fun continueNeedsANameThenPassesIt() {
        show()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithText("Your name").performTextInput("Abu Bakr")
        // Typing opens the keyboard, which can cover the button on a real phone.
        // Close it and scroll to the button first, like a person would.
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Continue").performScrollTo().assertIsEnabled().performClick()
        assertEquals("Abu Bakr", finishedWith)
    }

    @Test
    fun keyboardDoneKeyAlsoFinishes() {
        show()
        compose.onNodeWithText("Your name").performTextInput("Muhib")
        compose.onNodeWithText("Muhib").performImeAction()
        assertEquals("Muhib", finishedWith)
    }

    @Test
    fun skipFinishesWithoutAName() {
        show()
        compose.onNodeWithText("Skip for now").performScrollTo().performClick()
        assertEquals("", finishedWith)
    }
}
