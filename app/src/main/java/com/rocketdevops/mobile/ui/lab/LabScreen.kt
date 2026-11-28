package com.rocketdevops.mobile.ui.lab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rocketdevops.mobile.ui.components.AppScaffold
import com.rocketdevops.mobile.ui.components.EmptyState
import com.rocketdevops.mobile.ui.components.ErrorState
import com.rocketdevops.mobile.ui.components.IconCircleButton
import com.rocketdevops.mobile.ui.components.LoadingSkeleton
import com.rocketdevops.mobile.ui.components.PrimaryButton
import com.rocketdevops.mobile.ui.components.SecondaryButton
import com.rocketdevops.mobile.ui.components.StatusChip
import com.rocketdevops.mobile.ui.components.TerminalLine
import com.rocketdevops.mobile.ui.components.TerminalView
import com.rocketdevops.mobile.ui.components.TextAction
import com.rocketdevops.mobile.ui.theme.AppShapes
import com.rocketdevops.mobile.ui.theme.AppTextStyles
import com.rocketdevops.mobile.ui.theme.AppTheme
import com.rocketdevops.mobile.ui.theme.Dimens
import com.rocketdevops.mobile.ui.theme.LocalReduceMotion
import com.rocketdevops.mobile.ui.theme.Motion
import com.rocketdevops.mobile.ui.theme.RocketDevOpsTheme
import com.rocketdevops.mobile.ui.theme.Spacing

/** Every event the lab screen can send. Grouped so the screen has one parameter instead of fourteen. */
data class LabActions(
    val onInputChange: (TextFieldValue) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onInsert: (String) -> Unit = {},
    val onTab: () -> Unit = {},
    val onPreviousCommand: () -> Unit = {},
    val onNextCommand: () -> Unit = {},
    val onCancelLine: () -> Unit = {},
    val onHint: () -> Unit = {},
    val onHideHints: () -> Unit = {},
    val onRequestReset: () -> Unit = {},
    val onConfirmReset: () -> Unit = {},
    val onDismissReset: () -> Unit = {},
    val onDismissSuccess: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** Connects the ViewModel to the stateless LabScreen. */
@Composable
fun LabRoute(
    onBack: () -> Unit,
    viewModel: LabViewModel = viewModel(factory = LabViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        LabActions(
            onInputChange = viewModel::onInputChange,
            onSubmit = viewModel::submit,
            onInsert = viewModel::insert,
            onTab = viewModel::tab,
            onPreviousCommand = viewModel::previousCommand,
            onNextCommand = viewModel::nextCommand,
            onCancelLine = viewModel::cancelLine,
            onHint = viewModel::showNextHint,
            onHideHints = viewModel::hideHints,
            onRequestReset = viewModel::requestReset,
            onConfirmReset = viewModel::confirmReset,
            onDismissReset = viewModel::dismissReset,
            onDismissSuccess = viewModel::dismissSuccess,
            onRetry = viewModel::load,
        )
    }
    LabScreen(state = state, input = viewModel.input, actions = actions, onBack = onBack)
}

/** Screen 6: lab with the simulated terminal. Stateless. */
@Composable
fun LabScreen(
    state: LabUiState,
    input: TextFieldValue,
    actions: LabActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        LabUiState.Loading -> Column(modifier.padding(horizontal = Spacing.screenHorizontal)) {
            IconCircleButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
            LoadingSkeleton(lines = 8, modifier = Modifier.padding(top = Spacing.md))
        }
        LabUiState.NotFound -> EmptyState(
            title = "Lab not found",
            message = "This lab may have been renamed or removed.",
            icon = Icons.Outlined.SearchOff,
            actionLabel = "Back to labs",
            onAction = onBack,
            modifier = modifier,
        )
        is LabUiState.Error -> ErrorState(
            title = "This lab could not be loaded",
            message = state.message,
            onRetry = actions.onRetry,
            modifier = modifier,
        )
        is LabUiState.Ready -> LabContent(state.screen, state.showResetDialog, input, actions, onBack, modifier)
    }
}

@Composable
private fun LabContent(
    screen: LabScreenState,
    showResetDialog: Boolean,
    input: TextFieldValue,
    actions: LabActions,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val colors = AppTheme.colors
    val reduceMotion = LocalReduceMotion.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Hide the keyboard when the success screen appears.
    LaunchedEffect(screen.showSuccess) {
        if (screen.showSuccess) keyboard?.hide()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding() // keep the terminal and quick keys above the on-screen keyboard
                .padding(horizontal = Spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            LabHeader(screen, onBack, actions)
            Text(screen.scenario, style = MaterialTheme.typography.bodyMedium, color = colors.inkSecondary)
            ObjectiveChips(screen.objectives)

            AnimatedVisibility(
                visible = screen.hintsVisible && screen.hints.isNotEmpty(),
                enter = if (reduceMotion) EnterTransition.None else fadeIn(tween(Motion.MEDIUM)),
                exit = if (reduceMotion) ExitTransition.None else fadeOut(tween(Motion.SHORT)),
            ) {
                HintCard(screen.hints, screen.totalHints, actions)
            }

            TerminalView(
                lines = screen.lines,
                modifier = Modifier.weight(1f),
                prompt = screen.prompt,
                input = input,
                onInputChange = actions.onInputChange,
                onSubmit = actions.onSubmit,
                focusRequester = focusRequester,
            )
            QuickKeys(actions)
            Text(
                text = "Simulated terminal. Nothing runs on your phone.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkSecondary,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = actions.onDismissReset,
                title = { Text("Reset this lab?") },
                text = { Text("The terminal and the objectives go back to the start. Hints you opened stay open.") },
                confirmButton = { TextAction("Reset", actions.onConfirmReset) },
                dismissButton = { TextAction("Cancel", actions.onDismissReset) },
            )
        }

        AnimatedVisibility(
            visible = screen.showSuccess,
            enter = if (reduceMotion) EnterTransition.None else fadeIn(tween(Motion.LONG)),
            exit = if (reduceMotion) ExitTransition.None else fadeOut(tween(Motion.SHORT)),
        ) {
            SuccessOverlay(screen.title, onBack, actions.onDismissSuccess)
        }
    }
}

@Composable
private fun LabHeader(screen: LabScreenState, onBack: () -> Unit, actions: LabActions) {
    val colors = AppTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconCircleButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.xs),
        ) {
            Text(screen.caption, style = MaterialTheme.typography.bodySmall, color = colors.inkSecondary)
            Text(
                screen.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
        IconCircleButton(Icons.Outlined.Lightbulb, "Show a hint", actions.onHint)
        Box {
            IconCircleButton(Icons.Outlined.MoreVert, "More options", { menuOpen = true })
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Reset lab") },
                    leadingIcon = { Icon(Icons.Outlined.RestartAlt, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        actions.onRequestReset()
                    },
                )
            }
        }
    }
}

/** Objective chips. They turn green (animated, inside StatusChip) when completed. */
@Composable
private fun ObjectiveChips(objectives: List<ObjectiveChipState>) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .semantics { liveRegion = LiveRegionMode.Polite }, // TalkBack announces newly finished objectives
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        objectives.forEach { objective ->
            StatusChip(
                text = objective.label,
                container = if (objective.done) colors.success.container else colors.surface,
                onContainer = if (objective.done) colors.success.onContainer else colors.primaryText,
                icon = if (objective.done) Icons.Outlined.Check else null,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    stateDescription = if (objective.done) "Done" else "Not done yet"
                },
            )
        }
    }
}

@Composable
private fun HintCard(hints: List<String>, total: Int, actions: LabActions) {
    val info = AppTheme.colors.info
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.card)
            .background(info.container)
            .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = info.onContainer, modifier = Modifier.size(Dimens.iconMd))
            Spacer(Modifier.width(Spacing.xs))
            Text(
                "Hint ${hints.size} of $total",
                style = MaterialTheme.typography.labelLarge,
                color = info.onContainer,
                modifier = Modifier.weight(1f),
            )
            TextAction("Hide", actions.onHideHints)
        }
        hints.forEachIndexed { i, hint ->
            Text("${i + 1}. $hint", style = MaterialTheme.typography.bodyMedium, color = info.onContainer)
        }
        if (hints.size < total) TextAction("Next hint", actions.onHint)
    }
}

/** Keys that are awkward to type on a phone keyboard. Also used by the playground. */
@Composable
internal fun QuickKeys(actions: LabActions) {
    data class Key(val label: String, val description: String, val onClick: () -> Unit)

    val keys = listOf(
        Key("Tab", "Complete the command or file name", actions.onTab),
        Key("↑", "Previous command", actions.onPreviousCommand),
        Key("↓", "Next command", actions.onNextCommand),
        Key("Ctrl+C", "Cancel this line", actions.onCancelLine),
        Key("|", "Insert pipe", { actions.onInsert("|") }),
        Key("/", "Insert slash", { actions.onInsert("/") }),
        Key("~", "Insert tilde (home folder)", { actions.onInsert("~") }),
        Key("-", "Insert dash", { actions.onInsert("-") }),
    )
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        keys.forEach { key ->
            Box(
                modifier = Modifier
                    .heightIn(min = Dimens.minTouchTarget)
                    .widthIn(min = Dimens.minTouchTarget)
                    .clip(AppShapes.pill)
                    .background(colors.surface)
                    .clickable(onClick = key.onClick) // does not take focus, so the keyboard stays open
                    .semantics { contentDescription = key.description }
                    .padding(horizontal = Spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(key.label, style = AppTextStyles.monoMedium, color = colors.ink)
            }
        }
    }
}

/** Shown when every objective is done: a check that gently grows in, and two choices. */
@Composable
private fun SuccessOverlay(title: String, onBack: () -> Unit, onKeepPractising: () -> Unit) {
    val colors = AppTheme.colors
    val reduceMotion = LocalReduceMotion.current
    val scale = remember { Animatable(if (reduceMotion) 1f else SUCCESS_START_SCALE) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, tween(Motion.LONG, easing = Motion.EnterEasing))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .pointerInput(Unit) { detectTapGestures { } }, // stop taps reaching the terminal underneath
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(Dimens.successBadge)
                    .scale(scale.value)
                    .clip(AppShapes.circle)
                    .background(colors.success.container),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = colors.success.onContainer,
                    modifier = Modifier.size(Dimens.successIcon),
                )
            }
            Text(
                "Lab complete",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "You finished \"$title\". Every objective is done.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkSecondary,
                textAlign = TextAlign.Center,
            )
            PrimaryButton("Back to labs", onBack, Modifier.fillMaxWidth())
            SecondaryButton("Keep practising", onKeepPractising, Modifier.fillMaxWidth())
        }
    }
}

private const val SUCCESS_START_SCALE = 0.6f

// ---------------------------------------------------------------- previews

private val previewScreen = LabScreenState(
    labId = "docker-lab-first-container",
    caption = "Docker, Lab 2",
    title = "Run your first container",
    scenario = "Start the nginx web server in the background and make it reachable on port 8080.",
    objectives = listOf(
        ObjectiveChipState("pull", "Pull image", done = true),
        ObjectiveChipState("run", "Run container", done = true),
        ObjectiveChipState("port", "Map port", done = false),
    ),
    lines = listOf(
        TerminalLine("docker pull nginx", TerminalLine.Kind.Command, "lab ~ $"),
        TerminalLine("Status: Downloaded newer image for nginx:latest", TerminalLine.Kind.Output),
        TerminalLine("docker run -d --name web nginx", TerminalLine.Kind.Command, "lab ~ $"),
        TerminalLine("c3f2a91b7d04", TerminalLine.Kind.Output),
    ),
    prompt = "lab ~ $",
    hints = listOf("You need the nginx image first, then a running container with a published port."),
    totalHints = 3,
    hintsVisible = true,
    isComplete = false,
    showSuccess = false,
)

@PreviewLightDark
@Composable
private fun LabScreenPreview() = RocketDevOpsTheme {
    AppScaffold { padding ->
        LabScreen(
            state = LabUiState.Ready(previewScreen),
            input = TextFieldValue("docker ps"),
            actions = LabActions(),
            onBack = {},
            modifier = Modifier.padding(padding),
        )
    }
}

@PreviewLightDark
@Composable
private fun LabSuccessPreview() = RocketDevOpsTheme {
    AppScaffold { padding ->
        LabScreen(
            state = LabUiState.Ready(previewScreen.copy(showSuccess = true, isComplete = true)),
            input = TextFieldValue(""),
            actions = LabActions(),
            onBack = {},
            modifier = Modifier.padding(padding),
        )
    }
}
