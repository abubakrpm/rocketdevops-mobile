package com.rocketdevops.mobile.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import com.rocketdevops.mobile.ui.theme.Motion

/**
 * How one screen replaces another. Three Material 3 patterns:
 *
 * 1. Shared axis X (opening a screen: Home > Track > Lesson > Quiz). The old screen fades out
 *    in the first 100 ms while sliding a little to the left; the new one slides in from the right
 *    and fades in afterwards. The two never show text on top of each other. Back reverses it.
 * 2. Fade through (switching bottom tabs). Tabs are siblings, not "deeper", so they do not slide:
 *    the old tab fades out, the new one fades in while growing from 97 % to full size.
 * 3. Welcome to Home: a slow fade and grow, like a front door opening.
 *
 * With "Remove animations" turned on in Android settings, every change is instant.
 */
internal class NavTransitions(private val reduceMotion: Boolean) {

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
        initialState.destination.route in BottomNavRoutes && targetState.destination.route in BottomNavRoutes

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.involvesWelcome(): Boolean =
        initialState.destination.route == Routes.WELCOME || targetState.destination.route == Routes.WELCOME

    private fun slideDistance(fullWidth: Int) = (fullWidth * Motion.SLIDE_FRACTION).toInt()

    // ------------------------------------------------------------------ the four NavHost hooks

    /** A new screen opens (forward). */
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        when {
            reduceMotion -> EnterTransition.None
            involvesWelcome() -> fadeIn(tween(Motion.SCREEN * 2, easing = Motion.EnterEasing)) +
                scaleIn(tween(Motion.SCREEN * 2, easing = Motion.EnterEasing), initialScale = 0.94f)
            isTabSwitch() -> fadeThroughIn()
            else -> slideInHorizontally(tween(Motion.SCREEN, easing = Motion.EnterEasing)) { slideDistance(it) } +
                fadeIn(tween(Motion.FADE_IN, delayMillis = Motion.FADE_OUT, easing = Motion.EnterEasing))
        }
    }

    /** The current screen is covered by a new one (forward). */
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        when {
            reduceMotion -> ExitTransition.None
            involvesWelcome() -> fadeOut(tween(Motion.SCREEN, easing = Motion.ExitEasing))
            isTabSwitch() -> fadeThroughOut()
            else -> slideOutHorizontally(tween(Motion.SCREEN, easing = Motion.EnterEasing)) { -slideDistance(it) } +
                fadeOut(tween(Motion.FADE_OUT, easing = LinearEasing))
        }
    }

    /** Back: the screen underneath comes back. */
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        when {
            reduceMotion -> EnterTransition.None
            isTabSwitch() -> fadeThroughIn()
            else -> slideInHorizontally(tween(Motion.SCREEN, easing = Motion.EnterEasing)) { -slideDistance(it) } +
                fadeIn(tween(Motion.FADE_IN, delayMillis = Motion.FADE_OUT, easing = Motion.EnterEasing))
        }
    }

    /** Back: the top screen leaves. */
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        when {
            reduceMotion -> ExitTransition.None
            isTabSwitch() -> fadeThroughOut()
            else -> slideOutHorizontally(tween(Motion.SCREEN, easing = Motion.EnterEasing)) { slideDistance(it) } +
                fadeOut(tween(Motion.FADE_OUT, easing = LinearEasing))
        }
    }

    private fun fadeThroughIn(): EnterTransition =
        fadeIn(tween(Motion.TAB_FADE_IN, delayMillis = Motion.FADE_OUT, easing = Motion.EnterEasing)) +
            scaleIn(tween(Motion.TAB_FADE_IN, delayMillis = Motion.FADE_OUT, easing = Motion.EnterEasing), initialScale = 0.97f)

    private fun fadeThroughOut(): ExitTransition = fadeOut(tween(Motion.FADE_OUT, easing = LinearEasing))
}
