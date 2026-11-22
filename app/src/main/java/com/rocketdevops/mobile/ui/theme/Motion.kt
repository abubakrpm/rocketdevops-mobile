package com.rocketdevops.mobile.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** Motion tokens (spec 1.4): short animations with standard easing. */
object Motion {
    const val SHORT = 150
    const val MEDIUM = 200
    const val LONG = 250
    /** Cursor blink and skeleton pulse period. */
    const val BLINK = 530
    val StandardEasing = FastOutSlowInEasing
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
fun <T> motionSpec(durationMillis: Int = Motion.MEDIUM): AnimationSpec<T> =
    if (LocalReduceMotion.current) snap() else tween(durationMillis, easing = Motion.StandardEasing)
