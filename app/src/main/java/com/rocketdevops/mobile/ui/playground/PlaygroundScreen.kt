package com.rocketdevops.mobile.ui.playground

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rocketdevops.mobile.terminal.Playground
import com.rocketdevops.mobile.ui.components.AppScaffold
import com.rocketdevops.mobile.ui.components.IconCircleButton
import com.rocketdevops.mobile.ui.components.TerminalLine
import com.rocketdevops.mobile.ui.components.TerminalView
import com.rocketdevops.mobile.ui.components.TextAction
import com.rocketdevops.mobile.ui.lab.LabActions
import com.rocketdevops.mobile.ui.lab.QuickKeys
import com.rocketdevops.mobile.ui.theme.AppShapes
import com.rocketdevops.mobile.ui.theme.AppTextStyles
import com.rocketdevops.mobile.ui.theme.AppTheme
import com.rocketdevops.mobile.ui.theme.Dimens
import com.rocketdevops.mobile.ui.theme.RocketDevOpsTheme
import com.rocketdevops.mobile.ui.theme.Spacing

/** Every event the playground screen can send. */
data class PlaygroundActions(
    val terminal: LabActions = LabActions(), // typing, Enter, Tab, history, Ctrl+C, quick keys
    val onSuggestion: (String) -> Unit = {},
    val onRequestReset: () -> Unit = {},
    val onConfirmReset: () -> Unit = {},
    val onDismissReset: () -> Unit = {},
)

/** Connects the ViewModel to the stateless PlaygroundScreen. */
@Composable
fun PlaygroundRoute(
    onBack: () -> Unit,
    viewModel: PlaygroundViewModel = viewModel(factory = PlaygroundViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        PlaygroundActions(
            terminal = LabActions(
                onInputChange = viewModel::onInputChange,
                onSubmit = viewModel::submit,
                onInsert = viewModel::insert,
                onTab = viewModel::tab,
                onPreviousCommand = viewModel::previousCommand,
                onNextCommand = viewModel::nextCommand,
                onCancelLine = viewModel::cancelLine,
            ),
            onSuggestion = viewModel::useSuggestion,
            onRequestReset = viewModel::requestReset,
            onConfirmReset = viewModel::confirmReset,
            onDismissReset = viewModel::dismissReset,
        )
    }
    PlaygroundScreen(state, viewModel.input, actions, onBack)
}

/**
 * The free terminal: any Linux, Git or Docker command, in any order, with no objectives.
 * Same simulator as the labs, so nothing ever runs on the phone. Stateless.
 */
@Composable
fun PlaygroundScreen(
    state: PlaygroundUiState,
    input: TextFieldValue,
    actions: PlaygroundActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val focusRequester = remember { FocusRequester() }
    val screen = state.screen

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = Spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircleButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Spacing.xs),
                ) {
                    Text("Free terminal · Linux, Git, Docker", style = MaterialTheme.typography.bodySmall, color = colors.inkSecondary)
                    Text("Playground", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                }
                IconCircleButton(Icons.Outlined.RestartAlt, "Reset the playground", actions.onRequestReset)
            }

            Text(
                "No objectives: try anything. Tap an idea to fill the line, then press Enter.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkSecondary,
            )
            Suggestions(screen.suggestions, actions.onSuggestion)

            TerminalView(
                lines = screen.lines,
                modifier = Modifier.weight(1f),
                prompt = screen.prompt,
                input = input,
                onInputChange = actions.terminal.onInputChange,
                onSubmit = actions.terminal.onSubmit,
                focusRequester = focusRequester,
            )
            QuickKeys(actions.terminal)
            Text(
                "Simulated terminal. Nothing runs on your phone.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkSecondary,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
        }

        if (state.showResetDialog) {
            AlertDialog(
                onDismissRequest = actions.onDismissReset,
                title = { Text("Reset the playground?") },
                text = { Text("The machine goes back to its starting files. Your repositories, containers and history are cleared.") },
                confirmButton = { TextAction("Reset", actions.onConfirmReset) },
                dismissButton = { TextAction("Cancel", actions.onDismissReset) },
            )
        }
    }
}

/** Tap-to-fill command ideas. They only fill the line; Enter runs them. */
@Composable
private fun Suggestions(suggestions: List<String>, onSuggestion: (String) -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        suggestions.forEach { command ->
            Box(
                modifier = Modifier
                    .heightIn(min = Dimens.minTouchTarget)
                    .clip(AppShapes.pill)
                    .background(colors.surfaceStrong)
                    .clickable { onSuggestion(command) } // does not take focus: the keyboard stays open
                    .semantics { contentDescription = "Fill the line with $command" }
                    .padding(horizontal = Spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(command, style = AppTextStyles.monoMedium, color = colors.primaryText) // AA contrast on surfaceStrong
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun PlaygroundScreenPreview() = RocketDevOpsTheme {
    AppScaffold { padding ->
        PlaygroundScreen(
            state = PlaygroundUiState(
                PlaygroundScreenState(
                    lines = Playground.welcome.map { TerminalLine(it, TerminalLine.Kind.Output) } + listOf(
                        TerminalLine("ls", TerminalLine.Kind.Command, "playground ~ $"),
                        TerminalLine("README.md  downloads  notes.txt  projects", TerminalLine.Kind.Output),
                    ),
                    prompt = "playground ~ $",
                    suggestions = Playground.suggestions,
                    commandCount = 1,
                ),
            ),
            input = TextFieldValue("docker ps"),
            actions = PlaygroundActions(),
            onBack = {},
            modifier = Modifier.padding(padding),
        )
    }
}
