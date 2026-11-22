package com.rocketdevops.mobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.rocketdevops.mobile.ui.theme.motionSpec

/**
 * 7. StatusChip
 * A small pill with text (and an optional icon). Colours are passed in so the
 * same chip shows "3 correct" (success), a lab objective (primary) or a warning.
 * Colour changes animate, so a lab objective visibly turns green when completed.
 */
@Composable
fun StatusChip(
    text: String,
    container: Color,
    onContainer: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val bg by animateColorAsState(container, motionSpec(), label = "chipContainer")
    val fg by animateColorAsState(onContainer, motionSpec(), label = "chipContent")
    Row(
        modifier = modifier
            .clip(AppShapes.pill)
            .background(bg)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(Dimens.iconSm))
            Spacer(Modifier.width(Spacing.xxs))
        }
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

/**
 * 9. SegmentedControl
 * 2 to 4 pill-shaped options; exactly one is selected (Settings: theme and font size).
 * Each option is a radio button for TalkBack.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(options.size in 2..4) { "SegmentedControl needs 2 to 4 options" }
    val colors = AppTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.pill)
            .background(colors.surface)
            .padding(Spacing.xxs)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val bg by animateColorAsState(
                if (selected) colors.raised else Color.Transparent,
                motionSpec(),
                label = "segmentBackground",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.segmentItemHeight)
                    .clip(AppShapes.pill)
                    .background(bg)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = if (selected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                    color = if (selected) colors.ink else colors.inkSecondary,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ChipsPreview() = ComponentPreview {
    val colors = AppTheme.colors
    var theme by remember { mutableIntStateOf(1) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            StatusChip("Pull image", colors.success.container, colors.success.onContainer, icon = Icons.Outlined.Check)
            StatusChip("Map port", colors.surface, colors.primaryText)
            StatusChip("3 correct", colors.success.container, colors.success.onContainer)
        }
        SegmentedControl(listOf("System", "Light", "Dark"), theme, { theme = it })
    }
}
