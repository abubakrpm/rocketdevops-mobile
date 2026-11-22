package com.rocketdevops.mobile.ui.quiz

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rocketdevops.mobile.data.content.QuestionType
import com.rocketdevops.mobile.data.content.Quiz
import com.rocketdevops.mobile.data.content.QuizQuestion
import com.rocketdevops.mobile.ui.theme.RocketDevOpsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI test (runs on a phone or emulator): drives the REAL quiz screen with a real QuizSession.
 * Run with: .\gradlew.bat :app:connectedDebugAndroidTest   (a device must be connected)
 */
@RunWith(AndroidJUnit4::class)
class QuizScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val quiz = Quiz(
        "docker-quiz", "Docker basics",
        listOf(
            QuizQuestion("ps", QuestionType.MULTIPLE_CHOICE, "Which command lists running containers?",
                options = listOf("docker images", "docker ps"), correctAnswers = listOf("docker ps"),
                explanation = "docker ps shows running containers.", isCode = true),
            QuizQuestion("tf", QuestionType.TRUE_FALSE, "The -d flag deletes the container.",
                correctAnswers = listOf("False"), explanation = "-d means detached."),
        ),
    )

    /** Shows the quiz screen backed by a real session; every tap updates it like the ViewModel would. */
    private fun showQuiz() {
        val session = QuizSession(quiz, seed = 1)
        compose.setContent {
            var version by remember { mutableIntStateOf(0) }
            val screen = remember(version) { session.toScreenState(showInputError = false, isRetry = false) }
            RocketDevOpsTheme {
                QuizScreen(
                    state = QuizUiState.Ready(screen),
                    typedAnswer = "",
                    actions = QuizActions(
                        onSelectOption = { session.answer(it); version++ },
                        onNext = { session.next(); version++ },
                    ),
                    onLeave = {},
                )
            }
        }
    }

    @Test
    fun nextIsDisabledUntilAnAnswerIsLockedThenItMovesOn() {
        showQuiz()
        compose.onNodeWithText("Question 1 of 2").assertIsDisplayed()
        compose.onNodeWithText("Next question").assertIsNotEnabled()

        // Answer whichever question came first after shuffling, correctly.
        val firstIsPs = compose.onAllNodesWithTextCount("docker ps") > 0
        compose.onNodeWithText(if (firstIsPs) "docker ps" else "False").performClick()

        compose.onNodeWithText("Correct").assertIsDisplayed()   // the Why card appeared
        compose.onNodeWithText("1 correct").assertIsDisplayed() // live score updated
        compose.onNodeWithText("Next question").assertIsEnabled().performClick()
        compose.onNodeWithText("Question 2 of 2").assertIsDisplayed()
        compose.onNodeWithText("See results").assertIsNotEnabled()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}
