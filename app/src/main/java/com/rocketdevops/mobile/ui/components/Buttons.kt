package com.rocketdevops.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rocketdevops.mobile.ui.theme.AppShapes
import com.rocketdevops.mobile.ui.theme.AppTheme
import com.rocketdevops.mobile.ui.theme.Dimens
import com.rocketdevops.mobile.ui.theme.Spacing

/**
 * 3. IconCircleButton
 * A 40dp circle in the surface colour (back, close, settings). The touch area is a full 48dp.
 * `contentDescription` is required because the button has no visible text.
 */
@Composable
fun IconCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = AppTheme.colors.surface,
    contentColor: Color = AppTheme.colors.ink,
) {
    Box(
        modifier = modifier
            .size(Dimens.minTouchTarget)
            .clip(AppShapes.circle)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.iconCircle)
                .clip(AppShapes.circle)
                .background(containerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(Dimens.iconMd),
            )
        }
    }
}

private val ButtonPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.sm)

/** 6a. PrimaryButton: ink fill (black in light mode, near-white in dark mode), pill shape. One per screen. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val colors = AppTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = Dimens.buttonMinHeight),
        enabled = enabled,
        shape = AppShapes.pill,
        contentPadding = ButtonPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.ink,
            contentColor = colors.background,
            disabledContainerColor = colors.line,
            disabledContentColor = colors.inkSecondary,
        ),
    ) {
        ButtonContent(text, leadingIcon)
    }
}

/** 6b. SecondaryButton: outline in the line colour, no fill. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val colors = AppTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Dimens.buttonMinHeight),
        enabled = enabled,
        shape = AppShapes.pill,
        contentPadding = ButtonPadding,
        border = BorderStroke(Dimens.hairline, colors.line),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = colors.ink,
            disabledContentColor = colors.inkSecondary,
        ),
    ) {
        ButtonContent(text, leadingIcon)
    }
}

/** 6c. TextAction: a low-emphasis text-only action in violet ("Show hint", "Skip"). */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Dimens.minTouchTarget),
        enabled = enabled,
        shape = AppShapes.pill,
        colors = ButtonDefaults.textButtonColors(contentColor = AppTheme.colors.primaryText),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ButtonContent(text: String, leadingIcon: ImageVector?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (leadingIcon != null) {
            // Decorative: the button text already says what it does.
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(Dimens.iconSm))
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@PreviewLightDark
@Composable
private fun ButtonsPreview() = ComponentPreview {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            IconCircleButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", {})
            IconCircleButton(Icons.Outlined.Notifications, "Notifications", {})
        }
        PrimaryButton("Mark complete", {}, Modifier.fillMaxWidth())
        PrimaryButton("Resume lab", {}, leadingIcon = Icons.Outlined.PlayArrow)
        PrimaryButton("Next question", {}, Modifier.fillMaxWidth(), enabled = false)
        SecondaryButton("Back to track", {}, Modifier.fillMaxWidth())
        TextAction("Show a hint", {})
    }
}
