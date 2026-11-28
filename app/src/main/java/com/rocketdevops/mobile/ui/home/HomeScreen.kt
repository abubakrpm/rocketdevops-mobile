package com.rocketdevops.mobile.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rocketdevops.mobile.data.content.TrackIcon
import com.rocketdevops.mobile.data.progress.DayActivity
import com.rocketdevops.mobile.ui.common.OpenTarget
import com.rocketdevops.mobile.ui.common.plural
import com.rocketdevops.mobile.ui.common.toImageVector
import com.rocketdevops.mobile.ui.components.AppScaffold
import com.rocketdevops.mobile.ui.components.Avatar
import com.rocketdevops.mobile.ui.components.Chevron
import com.rocketdevops.mobile.ui.components.ErrorState
import com.rocketdevops.mobile.ui.components.HeroCard
import com.rocketdevops.mobile.ui.components.IconCircleButton
import com.rocketdevops.mobile.ui.components.IconTile
import com.rocketdevops.mobile.ui.components.ListRow
import com.rocketdevops.mobile.ui.components.LoadingSkeleton
import com.rocketdevops.mobile.ui.components.RollingText
import com.rocketdevops.mobile.ui.components.SurfaceCard
import com.rocketdevops.mobile.ui.components.WeekDay
import com.rocketdevops.mobile.ui.components.WeekStrip
import com.rocketdevops.mobile.ui.components.revealOnEnter
import com.rocketdevops.mobile.ui.theme.AppTheme
import com.rocketdevops.mobile.ui.theme.RocketDevOpsTheme
import com.rocketdevops.mobile.ui.theme.Spacing
import java.time.LocalDate

@Composable
fun HomeRoute(
    onOpen: (OpenTarget) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state, onOpen, onOpenSettings, onRetry = viewModel::retry)
}

/** Screen 1: greeting, daily streak, the dark Continue card and the track list. */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpen: (OpenTarget) -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        HomeUiState.Loading -> LoadingSkeleton(lines = 8, modifier = modifier.padding(Spacing.screenHorizontal))
        is HomeUiState.Error -> ErrorState(state.message, onRetry, modifier, title = "Content could not be loaded")
        is HomeUiState.Ready -> HomeContent(state, onOpen, onOpenSettings, modifier)
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState.Ready,
    onOpen: (OpenTarget) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier,
) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Header: avatar, greeting, settings
        // Each block fades in and rises slightly, top to bottom (revealOnEnter), on first visit.
        Row(
            modifier = Modifier.revealOnEnter(0),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Avatar(state.initials)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    state.greeting,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Text("Ready to ship?", style = MaterialTheme.typography.bodySmall, color = colors.inkSecondary)
            }
            IconCircleButton(Icons.Outlined.Settings, "Settings", onOpenSettings)
        }

        // Daily streak
        SurfaceCard(modifier = Modifier.fillMaxWidth().revealOnEnter(1)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Daily streak", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                RollingText(
                    text = if (state.streakDays == 0) "Start today" else plural(state.streakDays, "day"),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primaryText,
                )
            }
            WeekStrip(
                days = state.week.map {
                    WeekDay(it.label, it.dayOfMonth.toString(), isToday = it.isToday, isActive = it.isActive)
                },
            )
        }

        // Continue
        val card = state.continueCard
        HeroCard(
            eyebrow = card.eyebrow,
            title = card.title,
            actionLabel = card.actionLabel,
            onAction = { onOpen(card.target) },
            progress = card.progress,
            modifier = Modifier.revealOnEnter(2),
        )

        // The free terminal: one tap to try any command (handy for visitors at an exhibition).
        ListRow(
            modifier = Modifier.revealOnEnter(3),
            title = "Playground",
            subtitle = "Free terminal: try any Linux, Git or Docker command",
            leading = { IconTile(Icons.Outlined.Terminal) },
            trailing = { Chevron() },
            onClick = { onOpen(OpenTarget.Playground) },
            showDivider = false,
        )

        // Tracks
        Column {
            Text(
                "Tracks",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .revealOnEnter(4)
                    .semantics { heading() },
            )
            state.tracks.forEachIndexed { index, track ->
                ListRow(
                    modifier = Modifier.revealOnEnter(5 + index),
                    title = track.title,
                    subtitle = track.subtitle,
                    leading = { IconTile(track.icon.toImageVector()) },
                    trailing = { Chevron() },
                    onClick = { onOpen(OpenTarget.Track(track.id)) },
                    showDivider = index < state.tracks.lastIndex,
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
    }
}

private val previewToday = LocalDate.of(2026, 11, 19)

private val previewState = HomeUiState.Ready(
    greeting = "Hi, Ali",
    initials = "A",
    streakDays = 3,
    week = (0L..6L).map { i ->
        val d = previewToday.minusDays(3).plusDays(i)
        DayActivity(d, d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, if (i <= 3) 1 else 0, d == previewToday)
    },
    continueCard = ContinueCard("Continue, Docker", "Run your first container", "Resume lab", 0.4f, OpenTarget.Lab("x")),
    tracks = listOf(
        TrackRow("docker", "Docker", TrackIcon.CONTAINER, "3 labs, 40% done", 0.4f),
        TrackRow("linux", "Linux basics", TrackIcon.TERMINAL, "3 labs, 70% done", 0.7f),
    ),
)

@PreviewLightDark
@Composable
private fun HomeScreenPreview() = RocketDevOpsTheme {
    AppScaffold { padding ->
        HomeScreen(previewState, {}, {}, {}, Modifier.padding(padding))
    }
}
