package com.rocketdevops.mobile.ui.labs

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rocketdevops.mobile.data.content.TrackIcon
import com.rocketdevops.mobile.ui.common.toImageVector
import com.rocketdevops.mobile.ui.components.AppScaffold
import com.rocketdevops.mobile.ui.components.Chevron
import com.rocketdevops.mobile.ui.components.ErrorState
import com.rocketdevops.mobile.ui.components.IconTile
import com.rocketdevops.mobile.ui.components.ListRow
import com.rocketdevops.mobile.ui.components.LoadingSkeleton
import com.rocketdevops.mobile.ui.components.StatusChip
import com.rocketdevops.mobile.ui.components.revealOnEnter
import com.rocketdevops.mobile.ui.theme.AppTheme
import com.rocketdevops.mobile.ui.theme.Motion
import com.rocketdevops.mobile.ui.theme.RocketDevOpsTheme
import com.rocketdevops.mobile.ui.theme.Spacing
import com.rocketdevops.mobile.ui.theme.motionSpec

/**
 * Labs tab, "route" version: gets the ViewModel and passes plain state down.
 * Keeping LabsScreen free of the ViewModel makes it easy to preview and test.
 */
@Composable
fun LabsRoute(
    onOpenLab: (labId: String) -> Unit,
    viewModel: LabsViewModel = viewModel(factory = LabsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LabsScreen(state = state, onOpenLab = onOpenLab, onRetry = viewModel::load)
}

/** Every lab, grouped by track. Stateless: it only draws `state`. */
@Composable
fun LabsScreen(
    state: LabsUiState,
    onOpenLab: (labId: String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text("Labs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(
                "Simulated terminal. Nothing runs on your phone.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkSecondary,
            )
        }

        when (state) {
            LabsUiState.Loading -> LoadingSkeleton(
                lines = 6,
                modifier = Modifier.padding(Spacing.screenHorizontal),
            )
            is LabsUiState.Error -> ErrorState(
                title = "Labs could not be loaded",
                message = state.message,
                onRetry = onRetry,
            )
            is LabsUiState.Ready -> LazyColumn(
                contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs),
            ) {
                // Position on screen, top to bottom, for the staggered entry.
                var order = 0
                state.sections.forEach { section ->
                    val headerOrder = order++
                    val firstLabOrder = order
                    order += section.labs.size
                    item(key = "header-${section.trackId}") {
                        Text(
                            text = section.trackTitle,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .padding(top = Spacing.md, bottom = Spacing.xxs)
                                .revealOnEnter(headerOrder)
                                .semantics { heading() },
                        )
                    }
                    itemsIndexed(section.labs, key = { _, lab -> lab.id }) { index, lab ->
                        ListRow(
                            modifier = Modifier.revealOnEnter(firstLabOrder + index),
                            title = lab.title,
                            subtitle = lab.subtitle,
                            leading = { IconTile(section.icon.toImageVector()) },
                            trailing = {
                                // A lab finished a moment ago cross-fades from the chevron to "Done".
                                Crossfade(targetState = lab.done, animationSpec = motionSpec(Motion.MEDIUM), label = "labDone") { done ->
                                    if (done) {
                                        StatusChip("Done", colors.success.container, colors.success.onContainer, icon = Icons.Outlined.Check)
                                    } else {
                                        Chevron()
                                    }
                                }
                            },
                            onClick = { onOpenLab(lab.id) },
                            showDivider = index < section.labs.lastIndex,
                        )
                    }
                }
                item(key = "bottom-space") { Spacer(Modifier.height(Spacing.xl)) }
            }
        }
    }
}

private val previewState = LabsUiState.Ready(
    listOf(
        LabSection(
            "linux", "Linux basics", TrackIcon.TERMINAL,
            listOf(
                LabItem("a", "Find your way around", "Lab 1, 3 objectives", done = true),
                LabItem("b", "Write a to-do list", "Lab 2, 3 objectives"),
            ),
        ),
        LabSection(
            "docker", "Docker", TrackIcon.CONTAINER,
            listOf(LabItem("c", "Run your first container", "Lab 2, 3 objectives")),
        ),
    ),
)

@PreviewLightDark
@Composable
private fun LabsScreenPreview() = RocketDevOpsTheme {
    AppScaffold { padding ->
        LabsScreen(previewState, onOpenLab = {}, onRetry = {}, modifier = Modifier.padding(padding))
    }
}

@PreviewLightDark
@Composable
private fun LabsScreenErrorPreview() = RocketDevOpsTheme {
    AppScaffold { padding ->
        LabsScreen(
            LabsUiState.Error("tracks/docker.json: Unexpected JSON token at offset 120"),
            onOpenLab = {}, onRetry = {}, modifier = Modifier.padding(padding),
        )
    }
}
