package com.rocketdevops.mobile.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rocketdevops.mobile.data.prefs.FontSize
import com.rocketdevops.mobile.data.prefs.UserPreferences
import com.rocketdevops.mobile.data.progress.Achievement
import com.rocketdevops.mobile.ui.catalog.DesignSystemCatalogScreen
import com.rocketdevops.mobile.ui.cheats.CheatSheetRoute
import com.rocketdevops.mobile.ui.common.OpenTarget
import com.rocketdevops.mobile.ui.components.AppScaffold
import com.rocketdevops.mobile.ui.components.PillBottomNav
import com.rocketdevops.mobile.ui.home.HomeRoute
import com.rocketdevops.mobile.ui.lab.LabRoute
import com.rocketdevops.mobile.ui.labs.LabsRoute
import com.rocketdevops.mobile.ui.lesson.LessonRoute
import com.rocketdevops.mobile.ui.lesson.NextStep
import com.rocketdevops.mobile.ui.profile.ProfileRoute
import com.rocketdevops.mobile.ui.quiz.QuizResultsRoute
import com.rocketdevops.mobile.ui.quiz.QuizRoute
import com.rocketdevops.mobile.ui.settings.SettingsActions
import com.rocketdevops.mobile.ui.settings.SettingsScreen
import com.rocketdevops.mobile.ui.theme.LocalReduceMotion
import com.rocketdevops.mobile.ui.theme.Motion
import com.rocketdevops.mobile.ui.theme.ThemeMode
import com.rocketdevops.mobile.ui.track.TrackRoute
import com.rocketdevops.mobile.ui.welcome.WelcomeScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The app's root composable: one scaffold, the bottom nav, the snackbar and the navigation graph.
 *
 * Stage 6: Home, Track and Profile are real screens fed live from Room; first launch shows
 * the Welcome screen. Stage 7: the cheat sheet. Stage 9: full settings (font size, timer, reset).
 *
 * @param preferences saved settings, already loaded (MainActivity waits for them).
 * @param achievementUnlocked events from ProgressRepository, shown as snackbars.
 */
@Composable
fun RocketDevOpsApp(
    preferences: UserPreferences,
    achievementUnlocked: Flow<Achievement>,
    onFinishOnboarding: (name: String) -> Unit,
    onNameChange: (String) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onFontSizeChange: (FontSize) -> Unit,
    onQuizTimerChange: (Boolean) -> Unit,
    onResetProgress: (forgetName: Boolean, onDone: () -> Unit) -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val reduceMotion = LocalReduceMotion.current

    // Snackbars: "Achievement unlocked" and "Finish X first".
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
    LaunchedEffect(achievementUnlocked) {
        achievementUnlocked.collect { snackbarHostState.showSnackbar("Achievement unlocked: ${it.title}") }
    }

    // Decided once: changing the name later must not restart navigation.
    val startDestination = remember { if (preferences.onboarded) Routes.HOME else Routes.WELCOME }
    val open: (OpenTarget) -> Unit = { navController.open(it) }

    AppScaffold(
        snackbarHostState = snackbarHostState,
        bottomBar = {
            if (currentRoute in BottomNavRoutes) {
                PillBottomNav(
                    items = BottomNavItems,
                    selectedRoute = currentRoute,
                    onSelect = { item -> navController.navigateToTab(item.route) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            // consumeWindowInsets stops screens that have their own scaffold from adding
            // the status-bar padding a second time.
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            // Short fades (spec: 150-250 ms). None at all if the user turned animations off.
            enterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(tween(Motion.MEDIUM)) },
            exitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(tween(Motion.SHORT)) },
        ) {
            // ---------- First launch ----------
            composable(Routes.WELCOME) {
                WelcomeScreen(
                    onFinish = { name ->
                        onFinishOnboarding(name)
                        navController.navigate(Routes.HOME) { popUpTo(Routes.WELCOME) { inclusive = true } }
                    },
                )
            }

            // ---------- Bottom-nav tabs ----------
            composable(Routes.HOME) {
                HomeRoute(onOpen = open, onOpenSettings = { navController.navigate(Routes.SETTINGS) })
            }
            composable(Routes.LABS) {
                // Stage 2: real screen, reading the labs from assets/content.
                LabsRoute(onOpenLab = { labId -> navController.navigate(Routes.lab(labId)) })
            }
            composable(Routes.CHEATS) {
                CheatSheetRoute()
            }
            composable(Routes.PROFILE) {
                ProfileRoute(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
            }

            // ---------- Detail screens ----------
            composable(
                route = Routes.TRACK,
                arguments = listOf(navArgument(Routes.ARG_TRACK_ID) { type = NavType.StringType }),
            ) {
                TrackRoute(onBack = { navController.popBackStack() }, onOpen = open, onShowMessage = showMessage)
            }
            composable(
                route = Routes.LESSON,
                arguments = listOf(navArgument(Routes.ARG_LESSON_ID) { type = NavType.StringType }),
            ) {
                LessonRoute(
                    onClose = { navController.popBackStack() },
                    onOpenNext = { next ->
                        val route = when (next) {
                            is NextStep.Lesson -> Routes.lesson(next.id)
                            is NextStep.Lab -> Routes.lab(next.id)
                            is NextStep.Quiz -> Routes.quiz(next.id)
                        }
                        // Replace the finished lesson, so Back returns to the track, not to old lessons.
                        navController.navigate(route) { popUpTo(Routes.LESSON) { inclusive = true } }
                    },
                )
            }
            composable(
                route = Routes.QUIZ,
                arguments = listOf(
                    navArgument(Routes.ARG_QUIZ_ID) { type = NavType.StringType },
                    navArgument(Routes.ARG_RETRY) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                QuizRoute(
                    onLeave = { navController.popBackStack() },
                    onFinished = { result, isRetry ->
                        val quizId = entry.arguments?.getString(Routes.ARG_QUIZ_ID).orEmpty()
                        // Replace the quiz with its results, so Back from the results returns to the track.
                        navController.navigate(
                            Routes.quizResults(quizId, result.score, result.total, result.wrongQuestionIds, isRetry),
                        ) {
                            popUpTo(Routes.QUIZ) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = Routes.QUIZ_RESULTS,
                arguments = listOf(
                    navArgument(Routes.ARG_QUIZ_ID) { type = NavType.StringType },
                    navArgument(Routes.ARG_SCORE) { type = NavType.IntType; defaultValue = 0 },
                    navArgument(Routes.ARG_TOTAL) { type = NavType.IntType; defaultValue = 0 },
                    navArgument(Routes.ARG_WRONG) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument(Routes.ARG_WAS_RETRY) { type = NavType.BoolType; defaultValue = false },
                ),
            ) {
                QuizResultsRoute(
                    onBackToTrack = { navController.popBackStack() },
                    onRetryWrong = { quizId, wrongIds ->
                        navController.navigate(Routes.quiz(quizId, wrongIds)) {
                            popUpTo(Routes.QUIZ_RESULTS) { inclusive = true }
                        }
                    },
                    onTryAgain = { quizId ->
                        navController.navigate(Routes.quiz(quizId)) {
                            popUpTo(Routes.QUIZ_RESULTS) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = Routes.LAB,
                arguments = listOf(navArgument(Routes.ARG_LAB_ID) { type = NavType.StringType }),
            ) {
                // Stage 4: the real lab screen with the simulated terminal.
                LabRoute(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    preferences = preferences,
                    actions = SettingsActions(
                        onNameChange = onNameChange,
                        onThemeModeChange = onThemeModeChange,
                        onFontSizeChange = onFontSizeChange,
                        onQuizTimerChange = onQuizTimerChange,
                        onResetProgress = { forgetName ->
                            onResetProgress(forgetName) {
                                if (forgetName) {
                                    // Next visitor: clear the whole back stack and start at Welcome.
                                    navController.navigate(Routes.WELCOME) {
                                        popUpTo(navController.graph.id) { inclusive = true }
                                    }
                                } else {
                                    showMessage("Progress reset")
                                }
                            }
                        },
                    ),
                    onBack = { navController.popBackStack() },
                    onOpenCatalog = { navController.navigate(Routes.CATALOG) },
                )
            }
            composable(Routes.CATALOG) {
                DesignSystemCatalogScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/**
 * Standard bottom-nav behaviour: switching tabs keeps each tab's own back stack and
 * scroll position, and tapping the current tab again does not stack a duplicate.
 */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        // Home is the root after onboarding (the Welcome screen is removed from the back stack).
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Turns a tap target from a screen into navigation. */
private fun NavHostController.open(target: OpenTarget) {
    when (target) {
        is OpenTarget.Lesson -> navigate(Routes.lesson(target.id))
        is OpenTarget.Lab -> navigate(Routes.lab(target.id))
        is OpenTarget.Quiz -> navigate(Routes.quiz(target.id))
        is OpenTarget.Track -> navigate(Routes.track(target.id))
        OpenTarget.LabsTab -> navigateToTab(Routes.LABS)
    }
}
