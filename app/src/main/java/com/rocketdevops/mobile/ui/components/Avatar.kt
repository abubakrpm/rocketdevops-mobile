package com.rocketdevops.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rocketdevops.mobile.ui.theme.AppShapes
import com.rocketdevops.mobile.ui.theme.AppTheme
import com.rocketdevops.mobile.ui.theme.Dimens
import com.rocketdevops.mobile.ui.theme.Spacing

/**
 * 16. Avatar
 * A circle with the learner's initials ("MS"), or a person icon when no name was given.
 * Decorative (the greeting next to it already says the name), so TalkBack skips it.
 */
@Composable
fun Avatar(initials: String, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Box(
        modifier = modifier
            .size(Dimens.avatar)
            .clip(AppShapes.circle)
            .background(colors.surfaceStrong)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (initials.isBlank()) {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(Dimens.iconLg))
        } else {
            Text(initials, style = MaterialTheme.typography.labelLarge, color = colors.primaryText)
        }
    }
}

/**
 * 17. NameField
 * A single-line text field in the app's colours, for the learner's name
 * (Welcome screen and Settings). Capitalises words; the keyboard's Done key submits.
 */
@Composable
fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Your name",
    maxLength: Int = 24,
) {
    val colors = AppTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= maxLength) onValueChange(it) },
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        shape = AppShapes.input,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.ink,
            unfocusedTextColor = colors.ink,
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.line,
            focusedLabelColor = colors.primaryText,
            unfocusedLabelColor = colors.inkSecondary,
            cursorColor = colors.primary,
            focusedContainerColor = colors.background,
            unfocusedContainerColor = colors.background,
        ),
    )
}

@PreviewLightDark
@Composable
private fun AvatarPreview() = ComponentPreview {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Avatar("MS")
        Avatar("")
    }
}
