package com.rocketdevops.mobile.ui.lesson

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rocketdevops.mobile.data.content.LessonBlock
import com.rocketdevops.mobile.ui.components.AppScaffold
import com.rocketdevops.mobile.ui.components.CodeBlock
import com.rocketdevops.mobile.ui.components.EmptyState
import com.rocketdevops.mobile.ui.components.ErrorState
import com.rocketdevops.mobile.ui.components.IconCircleButton
import com.rocketdevops.mobile.ui.components.LoadingSkeleton
import com.rocketdevops.mobile.ui.components.PrimaryButton
import com.rocketdevops.mobile.ui.components.ProgressBar
import com.rocketdevops.mobile.ui.components.StatusChip
import com.rocketdevops.mobile.ui.components.TextAction
import com.rocketdevops.mobile.ui.components.revealOnEnter
import com.rocketdevops.mobile.ui.theme.AppShapes
import com.rocketdevops.mobile.ui.theme.AppTheme
import com.rocketdevops.mobile.ui.theme.Dimens
import com.rocketdevops.mobile.ui.theme.RocketDevOpsTheme
import com.rocketdevops.mobile.ui.theme.Spacing

@Composable
fun LessonRoute(
    onClose: () -> Unit,
    onOpenNext: (NextStep) -> Unit,
    viewModel: LessonViewModel = viewModel(factory = LessonViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LessonScreen(state, onClose, onOpenNext, onMarkComplete = viewModel::markComplete, onRetry = viewModel::load)
}

/** Screen 3: lesson reader. */
@Composable
fun LessonScreen(
    state: LessonUiState,
    onClose: () -> Unit,
    onOpenNext: (NextStep) -> Unit,
    onMarkComplete: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        LessonUiState.Loading -> Column(modifier.padding(horizontal = Spacing.screenHorizontal)) {
            IconCircleButton(Icons.Outlined.Close, "Close lesson", onClose)
            LoadingSkeleton(lines = 8, modifier = Modifier.padding(top = Spacing.md))
        }
        LessonUiState.NotFound -> EmptyState(
            title = "Lesson not found",
            message = "This lesson may have been renamed or removed.",
            icon = Icons.Outlined.SearchOff,
            actionLabel = "Go back",
            onAction = onClose,
            modifier = modifier,
        )
        is LessonUiState.Error -> ErrorState(state.message, onRetry, modifier, title = "This lesson could not be loaded")
        is LessonUiState.Ready -> LessonContent(state.screen, onClose, onOpenNext, onMarkComplete, modifier)
    }
}

@Composable
private fun LessonContent(
    screen: LessonScreenState,
    onClose: () -> Unit,
    onOpenNext: (NextStep) -> Unit,
    onMarkComplete: () -> Unit,
    modifier: Modifier,
) {
    val colors = AppTheme.colors
    val scroll = rememberScrollState()
    // Reading progress = how far down the page the reader has scrolled.
    val readProgress by remember {
        derivedStateOf {
            when (scroll.maxValue) {
                Int.MAX_VALUE -> 0f // not measured yet
                0 -> 1f             // short lesson: everything is visible
                else -> scroll.value.toFloat() / scroll.maxValue
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            IconCircleButton(Icons.Outlined.Close, "Close lesson", onClose)
            ProgressBar(progress = readProgress, modifier = Modifier.weight(1f))
            Text("${screen.estimatedMinutes} min", style = MaterialTheme.typography.bodySmall, color = colors.inkSecondary)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(screen.trackTitle, style = MaterialTheme.typography.bodySmall, color = colors.primaryText)
            Text(
                screen.title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            // Paragraphs, code and tips appear one after another on first open.
            screen.blocks.forEachIndexed { index, block ->
                Box(modifier = Modifier.revealOnEnter(index)) { LessonBlockView(block) }
            }

            Spacer(Modifier.height(Spacing.md))
            if (screen.isComplete) {
                StatusChip(
                    text = "Completed",
                    container = colors.success.container,
                    onContainer = colors.success.onContainer,
                    icon = Icons.Outlined.Check,
                )
            } else {
                PrimaryButton("Mark complete", onMarkComplete, Modifier.fillMaxWidth())
            }
            screen.next?.let { next ->
                val kind = when (next) {
                    is NextStep.Lesson -> "lesson"
                    is NextStep.Lab -> "lab"
                    is NextStep.Quiz -> "quiz"
                }
                TextAction("Next $kind: ${next.title}", { onOpenNext(next) })
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

/** Draws one block of lesson content. */
@Composable
private fun LessonBlockView(block: LessonBlock) {
    val colors = AppTheme.colors
    when (block) {
        is LessonBlock.Heading -> Text(
            block.text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(top = Spacing.xs)
                .semantics { heading() },
        )
        is LessonBlock.Paragraph -> Text(block.text, style = MaterialTheme.typography.bodyMedium, color = colors.ink)
        is LessonBlock.Code -> CodeBlock(block.code, caption = block.caption)
        is LessonBlock.Tip -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.input)
                .background(colors.info.container)
                .padding(Spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Outlined.Lightbulb, contentDescription = "Tip", tint = colors.info.onContainer, modifier = Modifier.size(Dimens.iconMd))
            Spacer(Modifier.width(Spacing.sm))
            Text(block.text, style = MaterialTheme.typography.bodyMedium, color = colors.info.onContainer)
        }
    }
}

@PreviewLightDark
@Composable
private fun LessonScreenPreview() = RocketDevOpsTheme {
    AppScaffold { padding ->
        LessonScreen(
            state = LessonUiState.Ready(
                LessonScreenState(
                    trackTitle = "Docker",
                    title = "Run your first container",
                    estimatedMinutes = 5,
                    blocks = listOf(
                        LessonBlock.Paragraph("docker run starts a container from an image."),
                        LessonBlock.Heading("Ports"),
                        LessonBlock.Code("docker run -d --name web -p 8080:80 nginx", "Publish port 8080"),
                        LessonBlock.Tip("docker ps shows running containers. Add -a to see stopped ones."),
                    ),
                    isComplete = false,
                    next = NextStep.Lab("docker-lab-first-container", "Run your first container"),
                ),
            ),
            onClose = {}, onOpenNext = {}, onMarkComplete = {}, onRetry = {},
            modifier = Modifier.padding(padding),
        )
    }
}
