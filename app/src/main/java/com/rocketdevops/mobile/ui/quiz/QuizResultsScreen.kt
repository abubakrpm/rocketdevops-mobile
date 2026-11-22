package com.rocketdevops.mobile.ui.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rocketdevops.mobile.ui.components.AppScaffold
import com.rocketdevops.mobile.ui.components.IconCircleButton
import com.rocketdevops.mobile.ui.components.IconTile
import com.rocketdevops.mobile.ui.components.LoadingSkeleton
import com.rocketdevops.mobile.ui.components.PrimaryButton
import com.rocketdevops.mobile.ui.components.ProgressRing
import com.rocketdevops.mobile.ui.components.SecondaryButton
import com.rocketdevops.mobile.ui.components.revealOnEnter
import com.rocketdevops.mobile.ui.theme.AppTextStyles
import com.rocketdevops.mobile.ui.theme.AppTheme
import com.rocketdevops.mobile.ui.theme.Dimens
import com.rocketdevops.mobile.ui.theme.RocketDevOpsTheme
import com.rocketdevops.mobile.ui.theme.Spacing

@Composable
fun QuizResultsRoute(
    onBackToTrack: () -> Unit,
    onRetryWrong: (quizId: String, wrongIds: List<String>) -> Unit,
    onTryAgain: (quizId: String) -> Unit,
    viewModel: QuizResultsViewModel = viewModel(factory = QuizResultsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    QuizResultsScreen(state, onBackToTrack, onRetryWrong, onTryAgain)
}

/** Screen 5: quiz results. */
@Composable
fun QuizResultsScreen(
    state: QuizResultsUiState,
    onBackToTrack: () -> Unit,
    onRetryWrong: (quizId: String, wrongIds: List<String>) -> Unit,
    onTryAgain: (quizId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    when (state) {
        QuizResultsUiState.Loading -> LoadingSkeleton(lines = 6, modifier = modifier.padding(Spacing.screenHorizontal))
        is QuizResultsUiState.Ready -> {
            val r = state.results
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.screenHorizontal),
            ) {
                IconCircleButton(Icons.Outlined.Close, "Close results", onBackToTrack)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    ProgressRing(
                        progress = r.fraction,
                        diameter = Dimens.ringLg,
                        strokeWidth = Dimens.ringStrokeLg,
                        color = if (r.fraction >= PASS_MARK) colors.success.base else colors.primary,
                        trackColor = colors.surface,
                        label = "${r.score} / ${r.total}",
                        labelStyle = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.revealOnEnter(0), // the ring also fills up from zero
                    )
                    Text(
                        r.headline,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .revealOnEnter(1)
                            .semantics { heading() },
                    )
                    val subtitle = when {
                        r.wasRetry -> "Retry of the questions you missed in ${r.quizTitle}"
                        r.bestScore != null -> "Best score so far: ${r.bestScore} / ${r.total}"
                        else -> r.quizTitle
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.inkSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.revealOnEnter(2),
                    )

                    if (r.toReview.isNotEmpty()) {
                        Text(
                            "To review",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Spacing.md)
                                .revealOnEnter(3)
                                .semantics { heading() },
                        )
                        r.toReview.forEachIndexed { index, item ->
                            Box(modifier = Modifier.revealOnEnter(4 + index)) { ReviewRow(item) }
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(vertical = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    if (r.toReview.isNotEmpty()) {
                        PrimaryButton("Retry wrong ones", { onRetryWrong(r.quizId, r.toReview.map { it.questionId }) }, Modifier.fillMaxWidth())
                    } else {
                        PrimaryButton("Try again", { onTryAgain(r.quizId) }, Modifier.fillMaxWidth())
                    }
                    SecondaryButton("Back to track", onBackToTrack, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ReviewRow(item: ReviewItem) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        IconTile(Icons.Outlined.Close, containerColor = colors.error.container, contentColor = colors.error.onContainer)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(item.prompt, style = MaterialTheme.typography.titleSmall)
            Text("Answer: ${item.correctAnswer}", style = AppTextStyles.monoMedium, color = colors.ink)
            Text(item.explanation, style = MaterialTheme.typography.bodySmall, color = colors.inkSecondary)
        }
    }
}

private const val PASS_MARK = 0.8f

@PreviewLightDark
@Composable
private fun QuizResultsPreview() = RocketDevOpsTheme {
    AppScaffold { padding ->
        QuizResultsScreen(
            state = QuizResultsUiState.Ready(
                QuizResultsState(
                    quizId = "docker-run-quiz",
                    quizTitle = "Docker basics",
                    score = 3,
                    total = 4,
                    bestScore = 3,
                    toReview = listOf(
                        ReviewItem("q", "Which command lists running containers?", "docker ps", "docker ps shows only running containers."),
                    ),
                    wasRetry = false,
                ),
            ),
            onBackToTrack = {}, onRetryWrong = { _, _ -> }, onTryAgain = {},
            modifier = Modifier.padding(padding),
        )
    }
}
