package com.rocketdevops.mobile.ui.cheats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rocketdevops.mobile.data.content.CheatCategory
import com.rocketdevops.mobile.data.content.CheatEntry
import com.rocketdevops.mobile.data.content.CheatFlag
import com.rocketdevops.mobile.ui.theme.RocketDevOpsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI test: the real cheat sheet screen with the real filter function.
 * Run with: .\gradlew.bat :app:connectedDebugAndroidTest   (a device must be connected)
 */
@RunWith(AndroidJUnit4::class)
class CheatSheetScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val entries = listOf(
        CheatEntry("ls", "ls", "List files in a folder", "ls -la", listOf(CheatFlag("-a", "include hidden files")), CheatCategory.LINUX),
        CheatEntry("git-log", "git log", "Show commit history", "git log --oneline", emptyList(), CheatCategory.GIT),
        CheatEntry("docker-ps", "docker ps", "List running containers", "docker ps -a", emptyList(), CheatCategory.DOCKER),
    )

    /** Same wiring as the ViewModel, kept in local state so the test needs no app or database. */
    private fun showScreen() {
        compose.setContent {
            RocketDevOpsTheme {
                var query by remember { mutableStateOf("") }
                var filter by remember { mutableStateOf(CheatFilter.All) }
                CheatSheetScreen(
                    state = CheatSheetUiState.Ready(query, filter, filterCheats(entries, query, filter), countsPerFilter(entries, query)),
                    onQueryChange = { query = it },
                    onFilterChange = { filter = it },
                    onClear = { query = ""; filter = CheatFilter.All },
                    onRetry = {},
                )
            }
        }
    }

    @Test
    fun categoryChipFiltersTheList() {
        showScreen()
        compose.onNodeWithText("Git").performClick()
        compose.onNodeWithText("git log --oneline").assertIsDisplayed()
        compose.onNodeWithText("ls -la").assertDoesNotExist()
    }

    @Test
    fun searchWithNoResultsShowsEmptyStateAndClearWorks() {
        showScreen()
        compose.onNodeWithText("Search commands").performTextInput("kubectl")
        compose.onNodeWithText("No commands found").assertIsDisplayed()
        compose.onNodeWithText("Clear search").performClick()
        compose.onNodeWithText("docker ps -a").assertIsDisplayed()
    }

    @Test
    fun tappingARowShowsItsFlags() {
        showScreen()
        compose.onNodeWithText("ls -la").performClick()
        compose.onNodeWithText("include hidden files").assertIsDisplayed()
    }

    @Test
    fun copyButtonConfirms() {
        showScreen()
        // Stop the test clock so the 1.5 s "Copied" feedback cannot time out before we look.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Copy git log --oneline").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Copied").assertIsDisplayed()
    }
}
