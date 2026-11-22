package com.rocketdevops.mobile.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Terminal
import com.rocketdevops.mobile.ui.components.NavItem

/**
 * Every screen's address. A route with {braces} takes an argument, for example
 * "track/docker" opens the Track screen for the Docker track.
 * Use the helper functions (Routes.track("docker")) instead of building strings by hand.
 */
object Routes {
    // Bottom-nav tabs
    const val HOME = "home"
    const val LABS = "labs"
    const val CHEATS = "cheats"
    const val PROFILE = "profile"

    // First launch only: asks for the learner's name
    const val WELCOME = "welcome"

    // Detail screens (no bottom nav)
    const val SETTINGS = "settings"
    const val CATALOG = "catalog"

    const val ARG_TRACK_ID = "trackId"
    const val ARG_LESSON_ID = "lessonId"
    const val ARG_QUIZ_ID = "quizId"
    const val ARG_LAB_ID = "labId"
    const val ARG_RETRY = "retry"          // quiz: comma-separated question ids to retry
    const val ARG_SCORE = "score"
    const val ARG_TOTAL = "total"
    const val ARG_WRONG = "wrong"          // results: comma-separated ids answered wrongly
    const val ARG_WAS_RETRY = "wasRetry"

    const val TRACK = "track/{$ARG_TRACK_ID}"
    const val LESSON = "lesson/{$ARG_LESSON_ID}"
    const val QUIZ = "quiz/{$ARG_QUIZ_ID}?$ARG_RETRY={$ARG_RETRY}"
    const val QUIZ_RESULTS =
        "quiz-results/{$ARG_QUIZ_ID}?$ARG_SCORE={$ARG_SCORE}&$ARG_TOTAL={$ARG_TOTAL}&$ARG_WRONG={$ARG_WRONG}&$ARG_WAS_RETRY={$ARG_WAS_RETRY}"
    const val LAB = "lab/{$ARG_LAB_ID}"

    fun track(trackId: String) = "track/$trackId"
    fun lesson(lessonId: String) = "lesson/$lessonId"

    /** Ids are lowercase-with-dashes (checked by ContentValidator), so they are safe in a URL. */
    fun quiz(quizId: String, retryQuestionIds: List<String> = emptyList()) =
        if (retryQuestionIds.isEmpty()) "quiz/$quizId" else "quiz/$quizId?$ARG_RETRY=${retryQuestionIds.joinToString(",")}"

    fun quizResults(quizId: String, score: Int, total: Int, wrongIds: List<String>, wasRetry: Boolean) =
        "quiz-results/$quizId?$ARG_SCORE=$score&$ARG_TOTAL=$total&$ARG_WRONG=${wrongIds.joinToString(",")}&$ARG_WAS_RETRY=$wasRetry"

    fun lab(labId: String) = "lab/$labId"
}

/** The four bottom-nav tabs, in order: Home, Labs, Cheats, Profile. */
val BottomNavItems = listOf(
    NavItem(Routes.HOME, "Home", Icons.Outlined.Home),
    NavItem(Routes.LABS, "Labs", Icons.Outlined.Terminal),
    NavItem(Routes.CHEATS, "Cheat sheet", Icons.AutoMirrored.Outlined.ListAlt),
    NavItem(Routes.PROFILE, "Profile", Icons.Outlined.Person),
)

/** The bottom nav is only shown on these routes. */
val BottomNavRoutes = BottomNavItems.map { it.route }.toSet()
