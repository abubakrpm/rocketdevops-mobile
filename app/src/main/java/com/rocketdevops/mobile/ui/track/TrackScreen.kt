package com.rocketdevops.mobile.ui.track

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rocketdevops.mobile.data.progress.ItemKind
import com.rocketdevops.mobile.data.progress.ModuleStatus
import com.rocketdevops.mobile.ui.common.OpenTarget
import com.rocketdevops.mobile.ui.components.AppScaffold
import com.rocketdevops.mobile.ui.components.ErrorState
import com.rocketdevops.mobile.ui.components.IconCircleButton
import com.rocketdevops.mobile.ui.components.IconTile
import com.rocketdevops.mobile.ui.components.ListRow
import com.rocketdevops.mobile.ui.components.LoadingSkeleton
import com.rocketdevops.mobile.ui.components.ProgressRing
import com.rocketdevops.mobile.ui.components.SurfaceCard
import com.rocketdevops.mobile.ui.components.revealOnEnter
import com.rocketdevops.mobile.ui.theme.AppTheme
import com.rocketdevops.mobile.ui.theme.Dimens
import com.rocketdevops.mobile.ui.theme.LocalReduceMotion
import com.rocketdevops.mobile.ui.theme.Motion
import com.rocketdevops.mobile.ui.theme.RocketDevOpsTheme
import com.rocketdevops.mobile.ui.theme.Spacing
import com.rocketdevops.mobile.ui.theme.motionSpec

@Composable
fun TrackRoute(
    onBack: () -> Unit,
    onOpen: (OpenTarget) -> Unit,
    onShowMessage: (String) -> Unit,
    viewModel: TrackViewModel = viewModel(factory = TrackViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TrackScreen(state, onBack, onOpen, onShowMessage, onRetry = viewModel::retry)
}

/**
 * Screen 2: a big progress ring, "2 of 5 modules", time left, and the module list.
 * Tapping a module opens it to show its lessons, labs and quiz. Tapping a locked module
 * shows "Finish X first" (labs stay free to practise from the Labs tab).
 */
@Composable
fun TrackScreen(
    state: TrackUiState,
    onBack: () -> Unit,
    onOpen: (OpenTarget) -> Unit,
    onShowMessage: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            IconCircleButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
            Text(
                text = (state as? TrackUiState.Ready)?.title ?: "Track",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
        }
        when (state) {
            TrackUiState.Loading -> LoadingSkeleton(lines = 6, modifier = Modifier.padding(Spacing.screenHorizontal))
            is TrackUiState.Error -> ErrorState(state.message, onRetry, title = "Track could not be loaded")
            is TrackUiState.Ready -> TrackContent(state, onOpen, onShowMessage)
        }
    }
}

@Composable
private fun TrackContent(
    state: TrackUiState.Ready,
    onOpen: (OpenTarget) -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val colors = AppTheme.colors
    // Which module is open. Starts on the first unfinished one; survives rotation.
    val reduceMotion = LocalReduceMotion.current
    val firstOpen = state.modules.firstOrNull { it.status == ModuleStatus.InProgress }?.id
    var expandedId by rememberSaveable { mutableStateOf(firstOpen) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs),
    ) {
        item(key = "summary") {
            SurfaceCard(modifier = Modifier.fillMaxWidth().revealOnEnter(0)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    ProgressRing(
                        progress = state.fraction,
                        diameter = Dimens.ringLg,
                        strokeWidth = Dimens.ringStrokeLg,
                        labelStyle = MaterialTheme.typography.titleLarge,
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Text(state.modulesLine, style = MaterialTheme.typography.titleMedium)
                        Text(state.timeLeft, style = MaterialTheme.typography.bodySmall, color = colors.inkSecondary)
                    }
                }
            }
        }
        item(key = "modules-heading") {
            Text(
                "Modules",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(top = Spacing.md)
                    .revealOnEnter(1)
                    .semantics { heading() },
            )
        }
        itemsIndexed(state.modules, key = { _, module -> module.id }) { index, module ->
            val expanded = expandedId == module.id
            // The chevron turns over instead of swapping icons.
            val chevronTurn by animateFloatAsState(if (expanded) 180f else 0f, motionSpec(Motion.MEDIUM), label = "chevron")
            Column(modifier = Modifier.revealOnEnter(2 + index)) {
                ListRow(
                    title = module.title,
                    subtitle = module.subtitle,
                    muted = module.status == ModuleStatus.Locked,
                    leading = { ModuleIcon(module.status) },
                    trailing = {
                        if (module.status != ModuleStatus.Locked) {
                            Icon(
                                Icons.Outlined.ExpandMore,
                                contentDescription = null,
                                tint = colors.inkSecondary,
                                modifier = Modifier.rotate(chevronTurn),
                            )
                        }
                    },
                    onClick = {
                        if (module.status == ModuleStatus.Locked) {
                            module.lockedMessage?.let(onShowMessage)
                        } else {
                            expandedId = if (expanded) null else module.id
                        }
                    },
                    modifier = Modifier.semantics {
                        stateDescription = when {
                            module.status == ModuleStatus.Locked -> "Locked"
                            expanded -> "Expanded"
                            else -> "Collapsed"
                        }
                    },
                )
                AnimatedVisibility(
                    visible = expanded && module.status != ModuleStatus.Locked,
                    enter = if (reduceMotion) EnterTransition.None else
                        expandVertically(tween(Motion.MEDIUM, easing = Motion.StandardEasing)) +
                            fadeIn(tween(Motion.MEDIUM, delayMillis = Motion.FADE_OUT)),
                    exit = if (reduceMotion) ExitTransition.None else
                        shrinkVertically(tween(Motion.MEDIUM, easing = Motion.StandardEasing)) + fadeOut(tween(Motion.FADE_OUT)),
                ) {
                    Column(modifier = Modifier.padding(start = Spacing.xl)) {
                        module.items.forEachIndexed { index, item ->
                            ListRow(
                                title = item.title,
                                subtitle = item.subtitle,
                                leading = { ItemIcon(item.kind, item.done) },
                                trailing = { if (item.done) DoneLabel() },
                                onClick = { onOpen(item.target) },
                                showDivider = index < module.items.lastIndex,
                            )
                        }
                    }
                }
            }
        }
        item(key = "bottom-space") { Spacer(Modifier.height(Spacing.xl)) }
    }
}

@Composable
private fun ModuleIcon(status: ModuleStatus) {
    val c = AppTheme.colors
    when (status) {
        ModuleStatus.Done -> IconTile(Icons.Outlined.Check, containerColor = c.success.container, contentColor = c.success.onContainer)
        ModuleStatus.InProgress -> IconTile(Icons.Outlined.PlayArrow, containerColor = c.primary, contentColor = c.onPrimary)
        ModuleStatus.Locked -> IconTile(Icons.Outlined.Lock, containerColor = c.surface, contentColor = c.inkSecondary)
    }
}

@Composable
private fun ItemIcon(kind: ItemKind, done: Boolean) {
    val c = AppTheme.colors
    if (done) {
        IconTile(Icons.Outlined.Check, containerColor = c.success.container, contentColor = c.success.onContainer)
    } else {
        IconTile(kindIcon(kind))
    }
}

private fun kindIcon(kind: ItemKind): ImageVector = when (kind) {
    ItemKind.Lesson -> Icons.AutoMirrored.Outlined.MenuBook
    ItemKind.Lab -> Icons.Outlined.Terminal
    ItemKind.Quiz -> Icons.Outlined.Quiz
}

@Composable
private fun DoneLabel() {
    Text("Done", style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.success.onContainer)
}

private val previewState = TrackUiState.Ready(
    title = "Docker",
    fraction = 0.4f,
    modulesLine = "2 of 5 modules",
    timeLeft = "About 1h 20m left",
    modules = listOf(
        ModuleRow("a", "Images and containers", ModuleStatus.Done, "Done, quiz 90%", null, emptyList()),
        ModuleRow(
            "b", "Ports and volumes", ModuleStatus.InProgress, "In progress, 2 of 5", null,
            listOf(
                ItemRow("l", ItemKind.Lesson, "Ports and volumes", "Lesson, 5 min", true, OpenTarget.Lesson("l")),
                ItemRow("x", ItemKind.Lab, "Publish a port", "Lab, simulated terminal", false, OpenTarget.Lab("x")),
                ItemRow("q", ItemKind.Quiz, "Docker quiz", "Quiz, 10 questions", false, OpenTarget.Quiz("q")),
            ),
        ),
        ModuleRow("c", "Writing a Dockerfile", ModuleStatus.Locked, "Locked", "Finish \"Ports and volumes\" first", emptyList()),
    ),
)

@PreviewLightDark
@Composable
private fun TrackScreenPreview() = RocketDevOpsTheme {
    AppScaffold { padding ->
        TrackScreen(previewState, {}, {}, {}, {}, Modifier.padding(padding))
    }
}
