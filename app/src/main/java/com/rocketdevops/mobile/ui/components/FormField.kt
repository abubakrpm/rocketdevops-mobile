package com.rocketdevops.mobile.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rocketdevops.mobile.ui.theme.AppShapes
import com.rocketdevops.mobile.ui.theme.AppTheme

/** What kind of text a FormField takes: sets the keyboard and hides passwords. */
enum class FieldKind { Name, Email, Password }

/**
 * 20. FormField
 * A labelled text field for forms (account screen). Shows an error line under the field and
 * tells TalkBack about it. Password fields hide the text and have a show/hide eye button.
 */
@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    kind: FieldKind,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    enabled: Boolean = true,
) {
    val colors = AppTheme.colors
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val isPassword = kind == FieldKind.Password

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.semantics { if (errorText != null) error(errorText) },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = errorText != null,
        supportingText = errorText?.let { { Text(it) } },
        shape = AppShapes.input,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = colors.inkSecondary,
                    )
                }
            }
        } else null,
        keyboardOptions = KeyboardOptions(
            capitalization = if (kind == FieldKind.Name) KeyboardCapitalization.Words else KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = when (kind) {
                FieldKind.Name -> KeyboardType.Text
                FieldKind.Email -> KeyboardType.Email
                FieldKind.Password -> KeyboardType.Password
            },
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
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
            errorBorderColor = colors.error.onContainer,
            errorLabelColor = colors.error.onContainer,
            errorSupportingTextColor = colors.error.onContainer,
            errorContainerColor = colors.background,
        ),
    )
}

@PreviewLightDark
@Composable
private fun FormFieldPreview() = ComponentPreview {
    Column {
        FormField("ali@example.com", {}, "Email", FieldKind.Email)
        FormField("secret", {}, "Password", FieldKind.Password, errorText = "Choose a longer password (at least 6 characters).")
    }
}
