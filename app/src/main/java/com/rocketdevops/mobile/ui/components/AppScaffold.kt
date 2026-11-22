package com.rocketdevops.mobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.rocketdevops.mobile.ui.theme.RocketDevOpsTheme
import com.rocketdevops.mobile.ui.theme.Spacing
import com.rocketdevops.mobile.ui.theme.motionSpec

/**
 * 1. AppScaffold
 * The frame every screen sits in: app background, an optional bottom bar and an optional snackbar host.
 * `content` receives padding that keeps it clear of the status bar and the bottom nav.
 */
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        bottomBar = bottomBar,
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        containerColor = AppTheme.colors.background,
        contentColor = AppTheme.colors.ink,
        content = content,
    )
}

/** One tab of the bottom nav. `label` is read aloud by TalkBack (the nav shows icons only). */
data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * 2. PillBottomNav
 * Lavender rounded container with icon-only items. The selected item becomes a filled primary circle.
 */
@Composable
fun PillBottomNav(
    items: List<NavItem>,
    selectedRoute: String?,
    onSelect: (NavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier
            .navigationBarsPadding()                          // stay above the gesture bar
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs)
            .fillMaxWidth()
            .clip(AppShapes.pill)
            .background(colors.surface)
            .padding(Spacing.xxs)
            .selectableGroup(),                               // TalkBack reads "tab 1 of 4"
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val selected = item.route == selectedRoute
            val background by animateColorAsState(
                targetValue = if (selected) colors.primary else Color.Transparent,
                animationSpec = motionSpec(),
                label = "navItemBackground",
            )
            val tint by animateColorAsState(
                targetValue = if (selected) colors.onPrimary else colors.inkSecondary,
                animationSpec = motionSpec(),
                label = "navItemTint",
            )
            Box(
                modifier = Modifier
                    .size(Dimens.navItem)
                    .clip(AppShapes.circle)
                    .background(background)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(item) }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = tint,
                    modifier = Modifier.size(Dimens.iconLg),
                )
            }
        }
    }
}

private val previewNavItems = listOf(
    NavItem("home", "Home", Icons.Outlined.Home),
    NavItem("labs", "Labs", Icons.Outlined.Terminal),
    NavItem("cheats", "Cheats", Icons.AutoMirrored.Outlined.ListAlt),
    NavItem("profile", "Profile", Icons.Outlined.Person),
)

@PreviewLightDark
@Composable
private fun PillBottomNavPreview() = ComponentPreview {
    PillBottomNav(items = previewNavItems, selectedRoute = "home", onSelect = {})
}

@PreviewLightDark
@Composable
private fun AppScaffoldPreview() = RocketDevOpsTheme {
    AppScaffold(
        bottomBar = { PillBottomNav(previewNavItems, "cheats", {}) },
    ) { padding ->
        Text(
            text = "Screen content",
            modifier = Modifier.padding(padding).padding(Spacing.md),
        )
    }
}
