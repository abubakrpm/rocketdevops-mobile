package com.rocketdevops.mobile.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rocketdevops.mobile.ui.components.AppScaffold
import com.rocketdevops.mobile.ui.components.Avatar
import com.rocketdevops.mobile.ui.components.Chevron
import com.rocketdevops.mobile.ui.components.EmptyState
import com.rocketdevops.mobile.ui.components.ErrorState
import com.rocketdevops.mobile.ui.components.FilterChipRow
import com.rocketdevops.mobile.ui.components.HeroCard
import com.rocketdevops.mobile.ui.components.IconCircleButton
import com.rocketdevops.mobile.ui.components.IconTile
import com.rocketdevops.mobile.ui.components.ListRow
import com.rocketdevops.mobile.ui.components.LoadingSkeleton
import com.rocketdevops.mobile.ui.components.MetricTile
import com.rocketdevops.mobile.ui.components.PrimaryButton
import com.rocketdevops.mobile.ui.components.ProgressBar
import com.rocketdevops.mobile.ui.components.ProgressRing
import com.rocketdevops.mobile.ui.components.QuizOptionRow
import com.rocketdevops.mobile.ui.components.QuizOptionState
import com.rocketdevops.mobile.ui.components.SearchField
import com.rocketdevops.mobile.ui.components.SecondaryButton
import com.rocketdevops.mobile.ui.components.SegmentedControl
import com.rocketdevops.mobile.ui.components.StatusChip
import com.rocketdevops.mobile.ui.components.SurfaceCard
import com.rocketdevops.mobile.ui.components.TerminalLine
import com.rocketdevops.mobile.ui.components.TerminalView
import com.rocketdevops.mobile.ui.components.TextAction
import com.rocketdevops.mobile.ui.components.WeekDay
import com.rocketdevops.mobile.ui.components.WeekStrip
import com.rocketdevops.mobile.ui.theme.AppShapes
import com.rocketdevops.mobile.ui.theme.AppTextStyles
import com.rocketdevops.mobile.ui.theme.AppTheme
import com.rocketdevops.mobile.ui.theme.Dimens
import com.rocketdevops.mobile.ui.theme.RocketDevOpsTheme
import com.rocketdevops.mobile.ui.theme.Spacing

/**
 * A single page that shows every token and every shared component.
 * Open it from Settings to check light and dark mode, font scale 1.3 and TalkBack
 * before any real screen is built. A few items are interactive so the animations can be seen.
 */
@Composable
fun DesignSystemCatalogScreen(onBack: () -> Unit) {
    val colors = AppTheme.colors
    var progress by remember { mutableFloatStateOf(0.4f) }
    var segment by remember { mutableIntStateOf(1) }
    var answered by remember { mutableStateOf(false) }
    var objectiveDone by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var chip by remember { mutableIntStateOf(0) }

    AppScaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                IconCircleButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
                Text("Design system", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            }

            Section("Colour tokens")
            val swatches = listOf(
                "background" to colors.background, "surface" to colors.surface,
                "surfaceStrong" to colors.surfaceStrong, "raised" to colors.raised,
                "primary" to colors.primary, "primaryText" to colors.primaryText,
                "accent" to colors.accent, "ink" to colors.ink,
                "inkSecondary" to colors.inkSecondary, "line" to colors.line,
                "heroDark" to colors.heroDark, "terminalBg" to colors.terminalBg,
                "success" to colors.success.base, "error" to colors.error.base,
                "warning" to colors.warning.base, "info" to colors.info.base,
            )
            swatches.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    row.forEach { (name, color) -> Swatch(name, color, Modifier.weight(1f)) }
                }
            }

            Section("Typography")
            TypeSample("Display 26", MaterialTheme.typography.headlineMedium)
            TypeSample("Title 20", MaterialTheme.typography.titleLarge)
            TypeSample("Section 16", MaterialTheme.typography.titleMedium)
            TypeSample("Row title 14", MaterialTheme.typography.titleSmall)
            TypeSample("Body 14. A container is isolated by default.", MaterialTheme.typography.bodyMedium)
            TypeSample("Label 13", MaterialTheme.typography.labelLarge)
            TypeSample("Caption 12", MaterialTheme.typography.bodySmall)
            TypeSample("docker run -d -p 8080:80 nginx", AppTextStyles.mono)

            Section("Buttons")
            PrimaryButton("Mark complete", {}, Modifier.fillMaxWidth())
            SecondaryButton("Back to track", {}, Modifier.fillMaxWidth())
            TextAction("Show a hint", {})

            Section("Cards and metrics")
            HeroCard("Continue, Docker", "Run your first container", "Resume lab", {}, progress = progress)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                MetricTile("9", "Labs", Modifier.weight(1f))
                MetricTile("84%", "Avg quiz", Modifier.weight(1f))
                MetricTile("6", "Streak", Modifier.weight(1f))
            }

            Section("Progress (tap to animate)")
            SurfaceCard(modifier = Modifier.fillMaxWidth(), onClick = { progress = if (progress >= 1f) 0.1f else progress + 0.2f }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    ProgressRing(progress)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text("2 of 5 modules", style = MaterialTheme.typography.titleSmall)
                        ProgressBar(progress)
                    }
                }
            }

            Section("Chips and segmented control (tap the chip)")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                StatusChip("Pull image", colors.success.container, colors.success.onContainer, icon = Icons.Outlined.Check)
                StatusChip(
                    text = "Map port",
                    container = if (objectiveDone) colors.success.container else colors.surface,
                    onContainer = if (objectiveDone) colors.success.onContainer else colors.primaryText,
                    icon = if (objectiveDone) Icons.Outlined.Check else null,
                    modifier = Modifier
                        .clip(AppShapes.pill)
                        .clickable { objectiveDone = !objectiveDone },
                )
            }
            SegmentedControl(listOf("System", "Light", "Dark"), segment, { segment = it })

            Section("Search, filter chips and avatar")
            SearchField(search, { search = it }, placeholder = "Search commands")
            FilterChipRow(listOf("All", "Linux", "Git", "Docker"), chip, { chip = it })
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Avatar("MS")
                Avatar("")
            }

            Section("Week strip")
            SurfaceCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Daily streak", style = MaterialTheme.typography.titleSmall)
                    Text("6 days", style = MaterialTheme.typography.bodySmall, color = colors.inkSecondary)
                }
                WeekStrip(sampleWeek)
            }

            Section("List rows")
            Column {
                ListRow("Docker", subtitle = "5 labs, 40% done", leading = { IconTile(Icons.Outlined.Inventory2) }, trailing = { Chevron() }, onClick = {})
                ListRow("Linux basics", subtitle = "6 labs, 70% done", leading = { IconTile(Icons.Outlined.Terminal) }, trailing = { Chevron() }, onClick = {})
                ListRow("Ports and volumes", subtitle = "In progress, 2 of 5", leading = { IconTile(Icons.Outlined.PlayArrow, containerColor = colors.primary, contentColor = colors.onPrimary) })
                ListRow("Writing a Dockerfile", subtitle = "Locked", muted = true, showDivider = false, leading = { IconTile(Icons.Outlined.Lock, containerColor = colors.surface, contentColor = colors.inkSecondary) })
            }

            Section("Quiz options (tap one)")
            listOf("docker images", "docker ps", "docker ps -a", "docker run").forEachIndexed { index, option ->
                val state = when {
                    !answered -> QuizOptionState.Idle
                    index == 1 -> QuizOptionState.Correct
                    index == 2 -> QuizOptionState.Wrong
                    else -> QuizOptionState.Disabled
                }
                QuizOptionRow(option, state, onClick = { answered = true }, isCode = true)
            }
            if (answered) TextAction("Reset", { answered = false })

            Section("Terminal")
            TerminalView(
                lines = sampleTerminal,
                // A fixed height is required: TerminalView scrolls itself, so it cannot sit
                // inside this scrolling page without a height limit.
                modifier = Modifier.height(Dimens.terminalMinHeight),
            )
            Text("Simulated terminal. Nothing runs on your phone.", style = MaterialTheme.typography.bodySmall, color = colors.inkSecondary)

            Section("States")
            LoadingSkeleton()
            EmptyState("No commands found", "Try another search or pick a different category.")
            ErrorState("Content could not be loaded.", onRetry = {})

            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(top = Spacing.xs)
            .semantics { heading() },
    )
}

@Composable
private fun TypeSample(text: String, style: TextStyle) {
    Text(text = text, style = style, color = AppTheme.colors.ink)
}

@Composable
private fun Swatch(name: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.iconTile)
                .clip(AppShapes.iconTile)
                .background(color)
                .border(Dimens.hairline, AppTheme.colors.line, AppShapes.iconTile),
        )
        Text(name, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.inkSecondary, maxLines = 1)
    }
}

private val sampleWeek = listOf(
    WeekDay("Mon", "17", isToday = false, isActive = true),
    WeekDay("Tue", "18", isToday = false, isActive = true),
    WeekDay("Wed", "19", isToday = true, isActive = true),
    WeekDay("Thu", "20", isToday = false, isActive = false),
    WeekDay("Fri", "21", isToday = false, isActive = false),
    WeekDay("Sat", "22", isToday = false, isActive = false),
    WeekDay("Sun", "23", isToday = false, isActive = false),
)

private val sampleTerminal = listOf(
    TerminalLine("docker pull nginx", TerminalLine.Kind.Command),
    TerminalLine("Downloaded newer image for nginx:latest", TerminalLine.Kind.Output),
    TerminalLine("docker run -d --name web nginx", TerminalLine.Kind.Command),
    TerminalLine("c3f2a91b7d04", TerminalLine.Kind.Output),
)

@PreviewLightDark
@Composable
private fun DesignSystemCatalogPreview() = RocketDevOpsTheme {
    DesignSystemCatalogScreen(onBack = {})
}
