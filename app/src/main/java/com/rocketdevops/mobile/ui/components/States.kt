package com.rocketdevops.mobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rocketdevops.mobile.ui.theme.AppShapes
import com.rocketdevops.mobile.ui.theme.AppTheme
import com.rocketdevops.mobile.ui.theme.Dimens
import com.rocketdevops.mobile.ui.theme.LocalReduceMotion
import com.rocketdevops.mobile.ui.theme.Motion
import com.rocketdevops.mobile.ui.theme.Spacing

/** 15a. EmptyState: icon, title, message and an optional action (e.g. "No results for 'xyz'"). */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inbox,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    CenteredMessage(icon, title, message, modifier) {
        if (actionLabel != null) SecondaryButton(actionLabel, onAction)
    }
}

/** 15b. ErrorState: something failed to load (for example a broken content JSON file), with Retry. */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Something went wrong",
) {
    CenteredMessage(Icons.Outlined.ErrorOutline, title, message, modifier, iconTint = true) {
        PrimaryButton("Retry", onRetry)
    }
}

@Composable
private fun CenteredMessage(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier,
    iconTint: Boolean = false,
    action: @Composable () -> Unit,
) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        IconTile(
            icon = icon,
            containerColor = if (iconTint) colors.error.container else colors.surfaceStrong,
            contentColor = if (iconTint) colors.error.onContainer else colors.primary,
        )
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.ink, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.inkSecondary, textAlign = TextAlign.Center)
        action()
    }
}

/**
 * 15c. LoadingSkeleton: grey placeholder bars that gently pulse while content loads.
 * The last line is shorter so it looks like a paragraph. The pulse stops if animations are off.
 */
@Composable
fun LoadingSkeleton(
    modifier: Modifier = Modifier,
    lines: Int = 3,
) {
    val reduceMotion = LocalReduceMotion.current
    val alpha = if (reduceMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val pulse by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(tween(Motion.BLINK * 2, easing = LinearEasing), RepeatMode.Reverse),
            label = "skeletonAlpha",
        )
        pulse
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .semantics { contentDescription = "Loading" },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        repeat(lines) { index ->
            val isLast = index == lines - 1
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (isLast) 0.6f else 1f)
                    .height(Dimens.skeletonLine)
                    .clip(AppShapes.pill)
                    .background(AppTheme.colors.surfaceStrong),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun StatesPreview() = ComponentPreview {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        LoadingSkeleton()
        EmptyState("No commands found", "Try another search or pick a different category.", actionLabel = "Clear search")
        ErrorState("The Docker lessons could not be read. Check assets/content/docker.json.", onRetry = {})
    }
}
