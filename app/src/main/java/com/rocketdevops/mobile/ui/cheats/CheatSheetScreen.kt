package com.rocketdevops.mobile.ui.cheats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rocketdevops.mobile.data.content.CheatCategory
import com.rocketdevops.mobile.data.content.CheatEntry
import com.rocketdevops.mobile.data.content.CheatFlag
import com.rocketdevops.mobile.ui.components.AppScaffold
import com.rocketdevops.mobile.ui.components.EmptyState
import com.rocketdevops.mobile.ui.components.ErrorState
import com.rocketdevops.mobile.ui.components.FilterChipRow
import com.rocketdevops.mobile.ui.components.IconCircleButton
import com.rocketdevops.mobile.ui.components.LoadingSkeleton
import com.rocketdevops.mobile.ui.components.SearchField
import com.rocketdevops.mobile.ui.theme.AppTextStyles
import com.rocketdevops.mobile.ui.theme.AppTheme
import com.rocketdevops.mobile.ui.theme.Dimens
import com.rocketdevops.mobile.ui.theme.RocketDevOpsTheme
import com.rocketdevops.mobile.ui.theme.Spacing
import kotlinx.coroutines.delay

@Composable
fun CheatSheetRoute(viewModel: CheatSheetViewModel = viewModel(factory = CheatSheetViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CheatSheetScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onFilterChange = viewModel::onFilterChange,
        onClear = viewModel::clear,
        onRetry = viewModel::retry,
    )
}

/**
 * Screen 7: search, category chips, and one row per command.
 * The copy icon copies the example command. Tapping a row with flags shows them.
 */
@Composable
fun CheatSheetScreen(
    state: CheatSheetUiState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (CheatFilter) -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text("Cheat sheet", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if (state is CheatSheetUiState.Ready) {
                SearchField(state.query, onQueryChange, placeholder = "Search commands")
                val filters = CheatFilter.entries
                FilterChipRow(
                    // While searching, each chip shows how many results it has: "Git 2".
                    labels = filters.map { f ->
                        if (state.query.isBlank()) f.label else "${f.label} ${state.counts[f] ?: 0}"
                    },
                    selectedIndex = filters.indexOf(state.filter),
                    onSelect = { onFilterChange(filters[it]) },
                )
            }
        }

        when (state) {
            CheatSheetUiState.Loading -> LoadingSkeleton(lines = 8, modifier = Modifier.padding(Spacing.screenHorizontal))
            is CheatSheetUiState.Error -> ErrorState(state.message, onRetry, title = "Cheat sheet could not be loaded")
            is CheatSheetUiState.Ready -> if (state.results.isEmpty()) {
                EmptyState(
                    title = "No commands found",
                    message = if (state.query.isBlank()) "Nothing in this category yet." else "Nothing matches \"${state.query.trim()}\". Try another word or category.",
                    icon = Icons.Outlined.SearchOff,
                    actionLabel = "Clear search",
                    onAction = onClear,
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs)) {
                    itemsIndexed(state.results, key = { _, entry -> entry.id }) { index, entry ->
                        CheatRow(entry, showDivider = index < state.results.lastIndex)
                    }
                    item(key = "footer") {
                        Text(
                            "${state.results.size} ${if (state.results.size == 1) "command" else "commands"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkSecondary,
                            modifier = Modifier.padding(vertical = Spacing.md),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One command: the example in mono (what you would actually type), the description,
 * and a copy button. If the entry has common flags, tapping the row shows them.
 */
@Composable
private fun CheatRow(entry: CheatEntry, showDivider: Boolean) {
    val colors = AppTheme.colors
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    var copied by remember { mutableStateOf(false) }
    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    val hasFlags = entry.commonFlags.isNotEmpty()

    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_FEEDBACK_MS)
            copied = false
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (hasFlags) {
                        Modifier
                            .clickable { expanded = !expanded }
                            .semantics { stateDescription = if (expanded) "Flags shown" else "Tap to show flags" }
                    } else Modifier,
                )
                .heightIn(min = Dimens.minTouchTarget)
                .padding(vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs / 2)) {
                Text(
                    text = entry.example,
                    style = AppTextStyles.monoMedium,
                    color = colors.ink,
                    softWrap = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
                Text(entry.description, style = MaterialTheme.typography.bodySmall, color = colors.inkSecondary)
            }
            IconCircleButton(
                icon = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                contentDescription = if (copied) "Copied" else "Copy ${entry.example}",
                onClick = {
                    clipboard.setText(AnnotatedString(entry.example))
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    copied = true
                },
                contentColor = if (copied) colors.success.base else colors.primary,
            )
        }
        AnimatedVisibility(visible = expanded && hasFlags) {
            Column(
                modifier = Modifier.padding(bottom = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                entry.commonFlags.forEach { flag ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(flag.flag, style = AppTextStyles.mono, color = colors.primaryText)
                        Text(flag.meaning, style = MaterialTheme.typography.bodySmall, color = colors.inkSecondary, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        if (showDivider) HorizontalDivider(thickness = Dimens.hairline, color = colors.line)
    }
}

private const val COPIED_FEEDBACK_MS = 1500L

private val previewEntries = listOf(
    CheatEntry("a", "docker ps", "List all containers", "docker ps -a", listOf(CheatFlag("-a", "include stopped containers")), CheatCategory.DOCKER),
    CheatEntry("b", "git log", "Compact commit history", "git log --oneline", emptyList(), CheatCategory.GIT),
    CheatEntry("c", "chmod", "Make a file executable", "chmod +x file", emptyList(), CheatCategory.LINUX),
)

@PreviewLightDark
@Composable
private fun CheatSheetPreview() = RocketDevOpsTheme {
    AppScaffold { padding ->
        CheatSheetScreen(
            state = CheatSheetUiState.Ready("", CheatFilter.All, previewEntries, emptyMap()),
            onQueryChange = {}, onFilterChange = {}, onClear = {}, onRetry = {},
            modifier = Modifier.padding(padding),
        )
    }
}

@PreviewLightDark
@Composable
private fun CheatSheetEmptyPreview() = RocketDevOpsTheme {
    AppScaffold { padding ->
        CheatSheetScreen(
            state = CheatSheetUiState.Ready("kubectl", CheatFilter.All, emptyList(), CheatFilter.entries.associateWith { 0 }),
            onQueryChange = {}, onFilterChange = {}, onClear = {}, onRetry = {},
            modifier = Modifier.padding(padding),
        )
    }
}
