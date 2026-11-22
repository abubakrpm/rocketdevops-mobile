package com.rocketdevops.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rocketdevops.mobile.ui.theme.AppShapes
import com.rocketdevops.mobile.ui.theme.AppTheme
import com.rocketdevops.mobile.ui.theme.Dimens
import com.rocketdevops.mobile.ui.theme.Spacing

/**
 * The rounded square behind a row icon. Default: violet icon on lavender.
 * Module rows recolour it: green (done), primary (in progress), muted (locked).
 */
@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = AppTheme.colors.surfaceStrong,
    contentColor: Color = AppTheme.colors.primary,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(Dimens.iconTile)
            .clip(AppShapes.iconTile)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = contentColor, modifier = Modifier.size(Dimens.iconMd))
    }
}

/** The grey chevron at the end of a tappable row. Decorative, so no content description. */
@Composable
fun Chevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = AppTheme.colors.inkSecondary,
        modifier = Modifier.size(Dimens.iconLg),
    )
}

/**
 * 11. ListRow
 * Leading slot (icon tile or dot), a title and subtitle, a trailing slot (chip or chevron)
 * and a hairline divider underneath. `muted` greys out the text (locked modules).
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    muted: Boolean = false,
    showDivider: Boolean = true,
) {
    val colors = AppTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .heightIn(min = Dimens.minTouchTarget)
                .padding(vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs / 2)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (muted) colors.inkSecondary else colors.ink,
                )
                if (subtitle != null) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.inkSecondary)
                }
            }
            trailing?.invoke()
        }
        if (showDivider) {
            HorizontalDivider(thickness = Dimens.hairline, color = colors.line)
        }
    }
}

@PreviewLightDark
@Composable
private fun ListRowPreview() = ComponentPreview {
    val c = AppTheme.colors
    Column {
        ListRow("Docker", subtitle = "5 labs, 40% done", leading = { IconTile(Icons.Outlined.Inventory2) }, trailing = { Chevron() }, onClick = {})
        ListRow(
            "Images and containers", subtitle = "Done, quiz 90%",
            leading = { IconTile(Icons.Outlined.Check, containerColor = c.success.container, contentColor = c.success.onContainer) },
        )
        ListRow(
            "Ports and volumes", subtitle = "In progress, 2 of 5",
            leading = { IconTile(Icons.Outlined.PlayArrow, containerColor = c.primary, contentColor = c.onPrimary) },
        )
        ListRow(
            "Writing a Dockerfile", subtitle = "Locked", muted = true, showDivider = false,
            leading = { IconTile(Icons.Outlined.Lock, containerColor = c.surface, contentColor = c.inkSecondary) },
        )
    }
}
