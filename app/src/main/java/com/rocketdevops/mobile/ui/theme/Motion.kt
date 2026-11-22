package com.rocketdevops.mobile.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Motion tokens. Every animation in the app takes its duration and curve from here.
 *
 * Stage 12 (after the user asked for smoother, slower motion): durations follow Material 3
 * (medium 300 ms, long 400-450 ms) instead of the spec's original 150-250 ms, and the curves are
 * Material 3's "standard" and "emphasized" easings, which start quickly and settle gently.
 * All of it is switched off when the phone's "Remove animations" setting is on.
 */
object Motion {
    const val SHORT = 200        // press feedback, small colour changes
    const val MEDIUM = 300       // chips, quiz options, bottom-nav pill, Why card
    const val LONG = 450         // progress bars, rings, chart bars
    /** Cursor blink and skeleton pulse period. */
    const val BLINK = 530

    // Screen-to-screen
    const val SCREEN = 400       // shared-axis slide of a new screen
    const val FADE_OUT = 100     // the old screen fades out first, so text never overlaps
    const val FADE_IN = 300      // the new screen fades in after FADE_OUT
    const val TAB_FADE_IN = 260  // bottom tabs: "fade through"

    // Content entering a screen
    const val REVEAL = 420       // fade + small rise of one block
    const val STAGGER = 55       // delay between blocks
    const val MAX_STAGGERED = 8  // later blocks do not wait any longer

    /** Material 3 standard easing: for things that stay on screen (colours, sizes). */
    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** Material 3 emphasized decelerate: for things arriving on screen. */
    val EnterEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** Material 3 emphasized accelerate: for things leaving the screen. */
    val ExitEasing: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** How far (as a fraction of the screen width) screens slide: about 30 dp on a phone. */
    const val SLIDE_FRACTION = 0.08f
}

/** True when the user turned on "Remove animations" in Android accessibility settings. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * Reads the system animator scale. "Remove animations" sets it to 0.
 * Wrapped in runCatching because Android Studio's preview renderer has no real settings.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}

/**
 * The single animation spec used by every component.
 * Returns an instant "snap" when the user has turned animations off.
 */
@Composable
fun <T> motionSpec(durationMillis: Int = Motion.MEDIUM): FiniteAnimationSpec<T> =
    if (LocalReduceMotion.current) snap() else tween(durationMillis, easing = Motion.StandardEasing)
