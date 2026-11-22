package com.rocketdevops.mobile.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rocketdevops.mobile.ui.theme.LocalReduceMotion
import com.rocketdevops.mobile.ui.theme.Motion
import com.rocketdevops.mobile.ui.theme.motionSpec
import kotlinx.coroutines.delay

/** How far a block rises while it fades in. Small on purpose: the eye should barely notice it. */
private val RevealRise = 14.dp

/** How much a pressed button shrinks. */
private const val PRESSED_SCALE = 0.96f

/**
 * Fades a block in and lets it rise a little, the first time a screen shows it.
 * `index` staggers blocks top to bottom (0 first), so a screen builds itself calmly
 * instead of appearing all at once.
 *
 * The "already shown" flag is saved, so pressing Back to this screen or rotating the phone
 * does not replay the animation. Only alpha and translation change (graphicsLayer), so
 * layout never jumps and TalkBack reads the content right away.
 */
@Composable
fun Modifier.revealOnEnter(index: Int = 0): Modifier {
    val reduceMotion = LocalReduceMotion.current
    var shown by rememberSaveable { mutableStateOf(reduceMotion) }
    val progress = remember { Animatable(if (shown) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!shown) {
            delay(Motion.STAGGER.toLong() * index.coerceIn(0, Motion.MAX_STAGGERED))
            progress.animateTo(1f, tween(Motion.REVEAL, easing = Motion.EnterEasing))
            shown = true
        }
    }

    val rise = with(LocalDensity.current) { RevealRise.toPx() }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * rise
    }
}

/**
 * Shrinks a button slightly while a finger is on it and springs back on release,
 * which makes taps feel physical. Pass the same InteractionSource the button uses.
 */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        animationSpec = motionSpec(Motion.SHORT),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Text that rolls up when its value changes ("2 days" -> "3 days", "8" -> "9" labs),
 * so a live progress update is noticed instead of silently swapped. Same look as Text.
 */
@Composable
fun RollingText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    val reduceMotion = LocalReduceMotion.current
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            val change = if (reduceMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (slideInVertically(tween(Motion.MEDIUM, easing = Motion.EnterEasing)) { it / 2 } +
                    fadeIn(tween(Motion.MEDIUM))) togetherWith
                    (slideOutVertically(tween(Motion.SHORT, easing = Motion.ExitEasing)) { -it / 2 } +
                        fadeOut(tween(Motion.SHORT)))
            }
            change.using(SizeTransform(clip = false))
        },
        modifier = modifier,
        label = "rollingText",
    ) { value ->
        Text(text = value, style = style, color = color, textAlign = textAlign, modifier = if (textAlign != null) Modifier.fillMaxWidth() else Modifier)
    }
}
