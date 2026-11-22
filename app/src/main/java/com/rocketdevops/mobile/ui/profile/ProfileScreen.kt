package com.rocketdevops.mobile.ui.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rocketdevops.mobile.data.progress.Achievement
import com.rocketdevops.mobile.data.progress.AchievementIcon
import com.rocketdevops.mobile.data.progress.DayActivity
import com.rocketdevops.mobile.ui.components.AppScaffold
import com.rocketdevops.mobile.ui.components.ErrorState
import com.rocketdevops.mobile.ui.components.IconCircleButton
import com.rocketdevops.mobile.ui.components.IconTile
import com.rocketdevops.mobile.ui.components.LoadingSkeleton
import com.rocketdevops.mobile.ui.components.MetricTile
import com.rocketdevops.mobile.ui.components.SurfaceCard
import com.rocketdevops.mobile.ui.components.revealOnEnter
import com.rocketdevops.mobile.ui.theme.AppShapes
import com.rocketdevops.mobile.ui.theme.AppTheme
import com.rocketdevops.mobile.ui.theme.Dimens
import com.rocketdevops.mobile.ui.theme.LocalReduceMotion
import com.rocketdevops.mobile.ui.theme.Motion
import com.rocketdevops.mobile.ui.theme.RocketDevOpsTheme
import com.rocketdevops.mobile.ui.theme.Spacing
import com.rocketdevops.mobile.ui.theme.motionSpec
import java.time.LocalDate
import kotlinx.coroutines.delay

@Composable
fun ProfileRoute(
    onOpenSettings: () -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileScreen(state, onOpenSettings, onRetry = viewModel::retry)
}

/** Screen 8: metric tiles, the last-7-days chart and achievements. */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Your progress",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            IconCircleButton(Icons.Outlined.Settings, "Settings", onOpenSettings)
        }
        when (state) {
            ProfileUiState.Loading -> LoadingSkeleton(lines = 6, modifier = Modifier.padding(Spacing.screenHorizontal))
            is ProfileUiState.Error -> ErrorState(state.message, onRetry, title = "Progress could not be loaded")
            is ProfileUiState.Ready -> ProfileContent(state)
        }
    }
}

@Composable
private fun ProfileContent(state: ProfileUiState.Ready) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(modifier = Modifier.revealOnEnter(0), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            MetricTile(state.labsDone.toString(), "Labs", Modifier.weight(1f))
            MetricTile(state.averageQuiz, "Avg quiz", Modifier.weight(1f))
            MetricTile(state.streakDays.toString(), "Streak", Modifier.weight(1f))
        }

        SurfaceCard(modifier = Modifier.fillMaxWidth().revealOnEnter(1)) {
            Text("Last 7 days", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            ActivityChart(state.lastSevenDays)
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(modifier = Modifier.revealOnEnter(2), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Achievements",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                Text(
                    "${state.unlockedCount} of ${state.achievements.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primaryText,
                )
            }
            state.achievements.forEachIndexed { index, row ->
                AchievementTile(row, Modifier.revealOnEnter(3 + index))
            }
        }
        Spacer(Modifier.height(Spacing.md))
    }
}

/**
 * A small bar chart drawn on a Canvas (no chart library needed).
 * Today's bar is primary violet, the other days are muted. Empty days show a short stub
 * so the row of days is still readable. TalkBack reads one summary sentence.
 */
@Composable
private fun ActivityChart(days: List<DayActivity>) {
    val colors = AppTheme.colors
    val max = (days.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
    val summary = days.joinToString(", ") { "${it.label} ${it.count}" }

    Column(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Things finished per day: $summary" },
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.chartHeight),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEachIndexed { index, day ->
                val target = if (day.count == 0) 0.06f else day.count.toFloat() / max
                // Bars grow from the bottom one after another (left to right) when Profile opens,
                // then slide to new heights when activity changes.
                val reduceMotion = LocalReduceMotion.current
                val bar = remember { Animatable(if (reduceMotion) target else 0f) }
                val growSpec = motionSpec<Float>(Motion.LONG)
                LaunchedEffect(target) {
                    if (bar.value == 0f) delay(Motion.STAGGER.toLong() * index)
                    bar.animateTo(target, growSpec)
                }
                val fraction = bar.value
                val color = when {
                    day.isToday -> colors.primary
                    day.count == 0 -> colors.line
                    else -> colors.chartBarMuted
                }
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                ) {
                    val barHeight = size.height * fraction
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(0f, size.height - barHeight),
                        size = Size(size.width, barHeight),
                        cornerRadius = CornerRadius(Spacing.xxs.toPx()),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            days.forEach { day ->
                Text(
                    text = day.label.take(1),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (day.isToday) colors.primaryText else colors.inkSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Unlocked: a filled card. Locked: a dashed outline with muted text, so it reads as "not yet". */
@Composable
private fun AchievementTile(row: AchievementRow, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val shape = AppShapes.card
    val dashColor = colors.line
    val base = modifier
        .fillMaxWidth()
        .semantics(mergeDescendants = true) {
            contentDescription = "${row.achievement.title}, ${if (row.unlocked) "unlocked" else "locked"}, ${row.detail}"
        }
    val decorated = if (row.unlocked) {
        base.clip(shape).background(colors.surface)
    } else {
        base.drawBehind {
            val stroke = Dimens.hairline.toPx() * 1.5f
            val radius = Spacing.lg.toPx()
            drawRoundRect(
                color = dashColor,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(radius),
                style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))),
            )
        }
    }
    Row(
        modifier = decorated.padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        val tileBg by animateColorAsState(if (row.unlocked) colors.primary else colors.background, motionSpec(Motion.LONG), label = "badgeBg")
        val tileFg by animateColorAsState(if (row.unlocked) colors.onPrimary else colors.inkSecondary, motionSpec(Motion.LONG), label = "badgeFg")
        IconTile(
            icon = row.achievement.icon.toImageVector(),
            containerColor = tileBg,
            contentColor = tileFg,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.achievement.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (row.unlocked) colors.ink else colors.inkSecondary,
            )
            Text(row.detail, style = MaterialTheme.typography.bodySmall, color = colors.inkSecondary)
        }
    }
}

fun AchievementIcon.toImageVector(): ImageVector = when (this) {
    AchievementIcon.Terminal -> Icons.Outlined.Terminal
    AchievementIcon.Book -> Icons.AutoMirrored.Outlined.MenuBook
    AchievementIcon.Star -> Icons.Outlined.Star
    AchievementIcon.Calendar -> Icons.Outlined.CalendarMonth
    AchievementIcon.Folder -> Icons.Outlined.Folder
    AchievementIcon.Branch -> Icons.Outlined.AccountTree
    AchievementIcon.Box -> Icons.Outlined.Inventory2
}

private val previewToday = LocalDate.of(2026, 11, 19)

private val previewState = ProfileUiState.Ready(
    labsDone = 9,
    averageQuiz = "84%",
    streakDays = 6,
    lastSevenDays = (6L downTo 0L).map { back ->
        val d = previewToday.minusDays(back)
        DayActivity(d, d.dayOfWeek.name.take(3), listOf(2, 0, 3, 1, 4, 2, 3)[(6 - back).toInt()], back == 0L)
    },
    achievements = listOf(
        AchievementRow(Achievement("a", "First command", "Finish your first lab", AchievementIcon.Terminal), true, "Finish your first lab"),
        AchievementRow(Achievement("b", "Week warrior", "", AchievementIcon.Calendar), false, "4 of 7 days"),
        AchievementRow(Achievement("c", "Container captain", "", AchievementIcon.Box), false, "2 of 3 labs"),
    ),
)

@PreviewLightDark
@Composable
private fun ProfileScreenPreview() = RocketDevOpsTheme {
    AppScaffold { padding ->
        ProfileScreen(previewState, {}, {}, Modifier.padding(padding))
    }
}
