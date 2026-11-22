package com.rocketdevops.mobile.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rocketdevops.mobile.BuildConfig
import com.rocketdevops.mobile.data.prefs.FontSize
import com.rocketdevops.mobile.data.prefs.UserPreferences
import com.rocketdevops.mobile.ui.components.AppScaffold
import com.rocketdevops.mobile.ui.components.Chevron
import com.rocketdevops.mobile.ui.components.IconCircleButton
import com.rocketdevops.mobile.ui.components.IconTile
import com.rocketdevops.mobile.ui.components.ListRow
import com.rocketdevops.mobile.ui.components.NameField
import com.rocketdevops.mobile.ui.components.SegmentedControl
import com.rocketdevops.mobile.ui.components.SurfaceCard
import com.rocketdevops.mobile.ui.components.TextAction
import com.rocketdevops.mobile.ui.theme.AppTheme
import com.rocketdevops.mobile.ui.theme.Dimens
import com.rocketdevops.mobile.ui.theme.RocketDevOpsTheme
import com.rocketdevops.mobile.ui.theme.Spacing
import com.rocketdevops.mobile.ui.theme.ThemeMode

/**
 * Static app facts shown in the About card. Nothing personal on purpose (no author name,
 * GitHub or email): the app is shown to many visitors, so it stays general. The spec asked
 * for the author's name and a GitHub link; both removed at the developer's request (Stages 7-8).
 */
object AppInfo {
    const val NAME = "RocketDevOps"
    const val TAGLINE = "Learn Linux, Git and Docker offline, one small step at a time."
    const val SIMULATION_NOTICE = "The terminal is simulated. Nothing runs on your device."
}

/** Every change the Settings screen can ask for. AppViewModel saves them in DataStore or Room. */
data class SettingsActions(
    val onNameChange: (String) -> Unit = {},
    val onThemeModeChange: (ThemeMode) -> Unit = {},
    val onFontSizeChange: (FontSize) -> Unit = {},
    val onQuizTimerChange: (Boolean) -> Unit = {},
    /** forgetName = also clear the name and show the Welcome screen (for the next visitor). */
    val onResetProgress: (forgetName: Boolean) -> Unit = {},
)

/**
 * Settings and About (screen 9): name, theme, font size, quiz timer, Reset progress,
 * the design system catalogue and the About card. Everything is saved at once (no Save button).
 */
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    actions: SettingsActions,
    onBack: () -> Unit,
    onOpenCatalog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    var editingName by rememberSaveable { mutableStateOf(false) }
    var confirmingReset by rememberSaveable { mutableStateOf(false) }

    if (editingName) {
        NameDialog(
            initial = preferences.name,
            onSave = { actions.onNameChange(it); editingName = false },
            onDismiss = { editingName = false },
        )
    }
    if (confirmingReset) {
        ResetDialog(
            onConfirm = { forgetName -> confirmingReset = false; actions.onResetProgress(forgetName) },
            onDismiss = { confirmingReset = false },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            IconCircleButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
            Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }

        ListRow(
            title = "Your name",
            subtitle = preferences.name.ifBlank { "Not set, Home says \"Hi there\"" },
            leading = { IconTile(Icons.Outlined.Person) },
            trailing = { Chevron() },
            onClick = { editingName = true },
            showDivider = false,
        )

        val themes = ThemeMode.entries
        LabelledSetting("Theme") {
            SegmentedControl(
                options = themes.map { it.name },          // "System", "Light", "Dark"
                selectedIndex = themes.indexOf(preferences.themeMode),
                onSelect = { actions.onThemeModeChange(themes[it]) },
            )
        }

        val sizes = FontSize.entries
        LabelledSetting("Font size") {
            SegmentedControl(
                options = sizes.map { it.label },
                selectedIndex = sizes.indexOf(preferences.fontSize),
                onSelect = { actions.onFontSizeChange(sizes[it]) },
            )
        }

        SwitchRow(
            title = "Quiz timer",
            subtitle = "30 seconds per question",
            checked = preferences.quizTimer,
            onCheckedChange = actions.onQuizTimerChange,
        )

        ListRow(
            title = "Reset progress",
            subtitle = "Start again from zero",
            leading = {
                IconTile(Icons.Outlined.RestartAlt, containerColor = colors.error.container, contentColor = colors.error.onContainer)
            },
            trailing = { Chevron() },
            onClick = { confirmingReset = true },
        )

        ListRow(
            title = "Design system",
            subtitle = "Every colour, font and component",
            leading = { IconTile(Icons.Outlined.Palette) },
            trailing = { Chevron() },
            onClick = onOpenCatalog,
            showDivider = false,
        )

        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Text(AppInfo.NAME, style = MaterialTheme.typography.titleSmall)
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = colors.inkSecondary)
            Text(AppInfo.SIMULATION_NOTICE, style = MaterialTheme.typography.bodySmall, color = colors.inkSecondary)
            Text(AppInfo.TAGLINE, style = MaterialTheme.typography.bodySmall, color = colors.inkSecondary)
        }
    }
}

@Composable
private fun LabelledSetting(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.inkSecondary)
        content()
    }
}

/**
 * A whole-row switch: tapping anywhere on the row flips it, and TalkBack reads it as one
 * switch ("Quiz timer, 30 seconds per question, off").
 */
@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouchTarget)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        IconTile(Icons.Outlined.Timer)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.inkSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = null, // the row handles taps
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                uncheckedThumbColor = colors.inkSecondary,
                uncheckedTrackColor = colors.surface,
                uncheckedBorderColor = colors.line,
            ),
        )
    }
}

/** A small dialog to change the name. Saving an empty name is allowed (Home then says "Hi there"). */
@Composable
private fun NameDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.colors.background,
        title = { Text("Your name", style = MaterialTheme.typography.titleMedium) },
        text = { NameField(value = value, onValueChange = { value = it }, onDone = { onSave(value) }) },
        confirmButton = { TextAction("Save", { onSave(value) }) },
        dismissButton = { TextAction("Cancel", onDismiss) },
    )
}

/**
 * Spec: Reset progress needs a confirmation dialog. The optional checkbox also clears the
 * name, so the next exhibition visitor starts at the Welcome screen.
 */
@Composable
private fun ResetDialog(onConfirm: (forgetName: Boolean) -> Unit, onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    var forgetName by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        title = { Text("Reset progress?", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    "This deletes every finished lesson, lab and quiz score, your streak and your achievements. It cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkSecondary,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.minTouchTarget)
                        .toggleable(value = forgetName, role = Role.Checkbox, onValueChange = { forgetName = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = forgetName,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = colors.primary, checkmarkColor = colors.onPrimary, uncheckedColor = colors.inkSecondary),
                    )
                    Text(
                        "Also forget the name (for the next person)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink,
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }
            }
        },
        confirmButton = {
            // Destructive action in the error colour so it is not pressed by accident.
            Text(
                "Reset",
                style = MaterialTheme.typography.labelLarge,
                color = colors.error.onContainer,
                modifier = Modifier
                    .heightIn(min = Dimens.minTouchTarget)
                    .clickable(role = Role.Button) { onConfirm(forgetName) }
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
        },
        dismissButton = { TextAction("Cancel", onDismiss) },
    )
}

@PreviewLightDark
@Composable
private fun SettingsScreenPreview() = RocketDevOpsTheme {
    AppScaffold { padding ->
        SettingsScreen(
            preferences = UserPreferences(name = "Ali", themeMode = ThemeMode.Light, quizTimer = true),
            actions = SettingsActions(),
            onBack = {},
            onOpenCatalog = {},
            modifier = Modifier.padding(padding),
        )
    }
}
