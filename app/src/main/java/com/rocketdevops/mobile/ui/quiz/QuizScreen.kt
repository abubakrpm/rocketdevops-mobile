package com.rocketdevops.mobile.ui.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rocketdevops.mobile.data.content.QuestionType
import com.rocketdevops.mobile.ui.components.AppScaffold
import com.rocketdevops.mobile.ui.components.EmptyState
import com.rocketdevops.mobile.ui.components.ErrorState
import com.rocketdevops.mobile.ui.components.IconCircleButton
import com.rocketdevops.mobile.ui.components.LoadingSkeleton
import com.rocketdevops.mobile.ui.components.PrimaryButton
import com.rocketdevops.mobile.ui.components.ProgressBar
import com.rocketdevops.mobile.ui.components.ProgressRing
import com.rocketdevops.mobile.ui.components.QuizOptionRow
import com.rocketdevops.mobile.ui.components.QuizOptionState
import com.rocketdevops.mobile.ui.components.StatusChip
import com.rocketdevops.mobile.ui.components.TextAction
import com.rocketdevops.mobile.ui.theme.AppShapes
import com.rocketdevops.mobile.ui.theme.AppTextStyles
import com.rocketdevops.mobile.ui.theme.AppTheme
import com.rocketdevops.mobile.ui.theme.Dimens
import com.rocketdevops.mobile.ui.theme.LocalReduceMotion
import com.rocketdevops.mobile.ui.theme.Motion
import com.rocketdevops.mobile.ui.theme.RocketDevOpsTheme
import com.rocketdevops.mobile.ui.theme.Spacing
import kotlinx.coroutines.delay

/** Every event the quiz screen can send. */
data class QuizActions(
    val onSelectOption: (String) -> Unit = {},
    val onTypedAnswerChange: (String) -> Unit = {},
    val onSubmitTyped: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onRequestLeave: () -> Unit = {},
    val onDismissLeave: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onTick: () -> Unit = {},
)

@Composable
fun QuizRoute(
    onLeave: () -> Unit,
    onFinished: (QuizResult, isRetry: Boolean) -> Unit,
    viewModel: QuizViewModel = viewModel(factory = QuizViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        QuizActions(
            onSelectOption = viewModel::selectOption,
            onTypedAnswerChange = viewModel::onTypedAnswerChange,
            onSubmitTyped = viewModel::submitTypedAnswer,
            onNext = viewModel::next,
            onRequestLeave = viewModel::requestLeave,
            onDismissLeave = viewModel::dismissLeave,
            onRetry = viewModel::load,
            onTick = viewModel::tick,
        )
    }
    // Quiz timer: one tick per second, only while the screen is visible (RESUMED) and the
    // question is still open. Leaving the app or answering stops the clock.
    val ready = state as? QuizUiState.Ready
    val timerRunning = ready?.timer != null && !ready.screen.isLocked
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(timerRunning, lifecycle) {
        if (timerRunning) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(TICK_MS)
                    actions.onTick()
                }
            }
        }
    }
    // Navigate once, when the attempt finishes.
    val finished = state as? QuizUiState.Finished
    LaunchedEffect(finished) {
        finished?.let { onFinished(it.result, it.isRetry) }
    }
    QuizScreen(state, viewModel.typedAnswer, actions, onLeave)
}

/** Screen 4: live quiz. */
@Composable
fun QuizScreen(
    state: QuizUiState,
    typedAnswer: String,
    actions: QuizActions,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        QuizUiState.Loading, is QuizUiState.Finished -> Column(modifier.padding(horizontal = Spacing.screenHorizontal)) {
            IconCircleButton(Icons.Outlined.Close, "Leave quiz", onLeave)
            LoadingSkeleton(lines = 6, modifier = Modifier.padding(top = Spacing.md))
        }
        QuizUiState.NotFound -> EmptyState(
            title = "Quiz not found",
            message = "This quiz may have been renamed or removed.",
            icon = Icons.Outlined.SearchOff,
            actionLabel = "Go back",
            onAction = onLeave,
            modifier = modifier,
        )
        is QuizUiState.Error -> ErrorState(state.message, actions.onRetry, modifier, title = "This quiz could not be loaded")
        is QuizUiState.Ready -> {
            // The system back gesture asks first too, like the close button.
            BackHandler { actions.onRequestLeave() }
            QuizContent(state.screen, state.timer, typedAnswer, actions, modifier)
            if (state.showLeaveDialog) {
                AlertDialog(
                    onDismissRequest = actions.onDismissLeave,
                    title = { Text("Leave quiz?") },
                    text = { Text("Your progress is saved, so you can continue where you left off.") },
                    confirmButton = { TextAction("Leave", onLeave) },
                    dismissButton = { TextAction("Stay", actions.onDismissLeave) },
                )
            }
        }
    }
}

@Composable
private fun QuizContent(
    screen: QuizScreenState,
    timer: QuizTimerUi?,
    typedAnswer: String,
    actions: QuizActions,
    modifier: Modifier,
) {
    val colors = AppTheme.colors
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = Spacing.screenHorizontal),
    ) {
        // Header: close, title and position, live score.
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(Icons.Outlined.Close, "Leave quiz", actions.onRequestLeave)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.xs),
            ) {
                Text(screen.quizTitle, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "Question ${screen.questionNumber} of ${screen.total}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkSecondary,
                )
            }
            StatusChip(
                text = "${screen.score} correct",
                container = colors.success.container,
                onContainer = colors.success.onContainer,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (timer != null) {
                Spacer(Modifier.width(Spacing.xs))
                ProgressRing(
                    progress = timer.fraction,
                    diameter = Dimens.timerRing,
                    strokeWidth = Dimens.timerStroke,
                    color = if (timer.isRunningOut) colors.error.base else colors.primary,
                    trackColor = colors.surface,
                    label = timer.secondsLeft.toString(),
                    labelStyle = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.semantics { contentDescription = "${timer.secondsLeft} seconds left" },
                )
            }
        }
        ProgressBar(
            progress = screen.answeredCount.toFloat() / screen.total,
            modifier = Modifier.padding(vertical = Spacing.sm),
        )

        // Scrollable middle: question, answers, explanation.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (screen.streak >= STREAK_TO_SHOW) {
                StatusChip(
                    text = "${screen.streak} in a row",
                    container = colors.warning.container,
                    onContainer = colors.warning.onContainer,
                    icon = Icons.Outlined.LocalFireDepartment,
                )
            }
            Text(
                screen.prompt,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(vertical = Spacing.xs)
                    .semantics { heading() },
            )

            if (screen.type == QuestionType.TYPE_THE_COMMAND) {
                CommandAnswerField(screen, typedAnswer, actions)
            } else {
                screen.options.forEach { option ->
                    QuizOptionRow(
                        text = option.text,
                        state = option.state,
                        isCode = screen.isCode,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) // light tick
                            actions.onSelectOption(option.text)
                        },
                    )
                }
            }

            WhyCard(screen)
            Spacer(Modifier.padding(bottom = Spacing.xs))
        }

        // One primary action, always at the bottom.
        val typedAndOpen = screen.type == QuestionType.TYPE_THE_COMMAND && !screen.isLocked
        PrimaryButton(
            text = when {
                typedAndOpen -> "Check answer"
                screen.isLastQuestion -> "See results"
                else -> "Next question"
            },
            onClick = if (typedAndOpen) actions.onSubmitTyped else actions.onNext,
            enabled = screen.isLocked || typedAndOpen,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.sm),
        )
    }
}

/** Mono input for TYPE_THE_COMMAND questions, with a "$" prompt like a terminal. */
@Composable
private fun CommandAnswerField(screen: QuizScreenState, typedAnswer: String, actions: QuizActions) {
    val colors = AppTheme.colors
    val borderColor = when (screen.lastAnswerCorrect) {
        true -> colors.success.base
        false -> colors.error.base
        null -> if (screen.showInputError) colors.error.base else colors.line
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.minTouchTarget)
                .clip(AppShapes.input)
                .background(colors.surface)
                .border(Dimens.hairline, borderColor, AppShapes.input)
                .padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$", style = AppTextStyles.monoMedium, color = colors.primaryText)
            Spacer(Modifier.width(Spacing.xs))
            BasicTextField(
                value = if (screen.isLocked) screen.givenAnswer.orEmpty() else typedAnswer,
                onValueChange = actions.onTypedAnswerChange,
                enabled = !screen.isLocked,
                singleLine = true,
                textStyle = AppTextStyles.monoMedium.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { actions.onSubmitTyped() }),
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = "Your command"
                        if (screen.showInputError) error("Enter an answer first")
                    },
                decorationBox = { inner ->
                    Box {
                        if (typedAnswer.isEmpty() && !screen.isLocked) {
                            Text("type the command", style = AppTextStyles.mono, color = colors.inkSecondary)
                        }
                        inner()
                    }
                },
            )
            when (screen.lastAnswerCorrect) {
                true -> Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.success.base, modifier = Modifier.size(Dimens.iconMd))
                false -> Icon(Icons.Outlined.Close, contentDescription = null, tint = colors.error.base, modifier = Modifier.size(Dimens.iconMd))
                null -> Unit
            }
        }
        if (screen.showInputError) {
            Text("Enter an answer first", style = MaterialTheme.typography.bodySmall, color = colors.error.onContainer)
        }
    }
}

/** Appears after answering: right or wrong, the correct answer if needed, and why. */
@Composable
private fun WhyCard(screen: QuizScreenState) {
    val colors = AppTheme.colors
    val reduceMotion = LocalReduceMotion.current
    AnimatedVisibility(
        visible = screen.isLocked,
        enter = if (reduceMotion) EnterTransition.None else fadeIn(tween(Motion.MEDIUM)),
    ) {
        val correct = screen.lastAnswerCorrect == true
        val verdictColor = if (correct) colors.success.onContainer else colors.error.onContainer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.card)
                .background(colors.surface)
                .padding(Spacing.md)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (correct) Icons.Outlined.Check else Icons.Outlined.Close,
                    contentDescription = null,
                    tint = verdictColor,
                    modifier = Modifier.size(Dimens.iconMd),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = when {
                        correct -> "Correct"
                        screen.timedOut -> "Time's up"
                        else -> "Not quite"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = verdictColor,
                )
            }
            if (!correct && screen.type == QuestionType.TYPE_THE_COMMAND) {
                Text("Answer: ${screen.correctAnswer}", style = AppTextStyles.monoMedium, color = colors.ink)
            }
            Text("Why", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.xxs))
            Text(screen.explanation, style = MaterialTheme.typography.bodyMedium, color = colors.inkSecondary)
        }
    }
}

private const val STREAK_TO_SHOW = 2
private const val TICK_MS = 1_000L

// ---------------------------------------------------------------- previews

private val previewQuestion = QuizScreenState(
    quizTitle = "Docker basics",
    questionNumber = 4,
    total = 10,
    answeredCount = 4,
    prompt = "Which command lists running containers?",
    type = QuestionType.MULTIPLE_CHOICE,
    isCode = true,
    options = listOf(
        QuizOptionUi("docker images", QuizOptionState.Disabled),
        QuizOptionUi("docker ps", QuizOptionState.Correct),
        QuizOptionUi("docker ps -a", QuizOptionState.Wrong),
        QuizOptionUi("docker run", QuizOptionState.Disabled),
    ),
    isLocked = true,
    lastAnswerCorrect = false,
    givenAnswer = "docker ps -a",
    timedOut = false,
    correctAnswer = "docker ps",
    explanation = "docker ps shows only running containers. Adding -a also shows stopped ones.",
    score = 3,
    streak = 0,
    isLastQuestion = false,
    showInputError = false,
    isRetry = false,
)

@PreviewLightDark
@Composable
private fun QuizScreenPreview() = RocketDevOpsTheme {
    AppScaffold { padding ->
        QuizScreen(QuizUiState.Ready(previewQuestion), "", QuizActions(), onLeave = {}, modifier = Modifier.padding(padding))
    }
}

@PreviewLightDark
@Composable
private fun QuizTimerPreview() = RocketDevOpsTheme {
    AppScaffold { padding ->
        QuizScreen(
            QuizUiState.Ready(
                previewQuestion.copy(isLocked = false, lastAnswerCorrect = null, givenAnswer = null,
                    options = previewQuestion.options.map { it.copy(state = QuizOptionState.Idle) }),
                timer = QuizTimerUi(secondsLeft = 4, fraction = 4 / 30f, isRunningOut = true),
            ),
            "", QuizActions(), onLeave = {}, modifier = Modifier.padding(padding),
        )
    }
}

@PreviewLightDark
@Composable
private fun QuizTypedPreview() = RocketDevOpsTheme {
    AppScaffold { padding ->
        QuizScreen(
            QuizUiState.Ready(
                previewQuestion.copy(
                    prompt = "Type the command that shows the logs of a container named api.",
                    type = QuestionType.TYPE_THE_COMMAND,
                    options = emptyList(),
                    isLocked = false,
                    lastAnswerCorrect = null,
                    givenAnswer = null,
                    showInputError = true,
                    streak = 3,
                ),
            ),
            typedAnswer = "",
            actions = QuizActions(),
            onLeave = {},
            modifier = Modifier.padding(padding),
        )
    }
}
