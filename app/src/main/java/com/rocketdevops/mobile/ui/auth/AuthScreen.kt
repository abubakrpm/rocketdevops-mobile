package com.rocketdevops.mobile.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rocketdevops.mobile.ui.components.AppScaffold
import com.rocketdevops.mobile.ui.components.FieldKind
import com.rocketdevops.mobile.ui.components.FormField
import com.rocketdevops.mobile.ui.components.IconCircleButton
import com.rocketdevops.mobile.ui.components.IconTile
import com.rocketdevops.mobile.ui.components.PrimaryButton
import com.rocketdevops.mobile.ui.components.SurfaceCard
import com.rocketdevops.mobile.ui.components.TextAction
import com.rocketdevops.mobile.ui.components.revealOnEnter
import com.rocketdevops.mobile.ui.theme.AppTheme
import com.rocketdevops.mobile.ui.theme.RocketDevOpsTheme
import com.rocketdevops.mobile.ui.theme.Spacing

@Composable
fun AuthRoute(
    onBack: () -> Unit,
    onSignedIn: () -> Unit,
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onSignedIn() }
    AuthScreen(
        state = state,
        onNameChange = viewModel::onNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        onSwitchMode = viewModel::switchMode,
        onForgotPassword = viewModel::forgotPassword,
        onBack = onBack,
    )
}

/**
 * Sign in or create an account, so progress is kept online and comes back after signing out,
 * reinstalling, or on another phone. Accounts are optional: everything works as a guest.
 */
@Composable
fun AuthScreen(
    state: AuthUiState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSwitchMode: () -> Unit,
    onForgotPassword: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val focus = LocalFocusManager.current
    val signUp = state.mode == AuthMode.SignUp

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            IconCircleButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
            Text(
                if (signUp) "Create account" else "Sign in",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
        }

        SurfaceCard(modifier = Modifier.fillMaxWidth().revealOnEnter(0)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Outlined.CloudSync)
                Text(
                    if (signUp) "Your progress on this phone is saved to your new account. Sign in on any phone to get it back."
                    else "Your lessons, labs, quiz scores, streak and achievements come back exactly as you left them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkSecondary,
                )
            }
        }

        AnimatedVisibility(visible = signUp) {
            FormField(
                value = state.name,
                onValueChange = onNameChange,
                label = "Your name",
                kind = FieldKind.Name,
                errorText = state.nameError,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        FormField(
            value = state.email,
            onValueChange = onEmailChange,
            label = "Email",
            kind = FieldKind.Email,
            errorText = state.emailError,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
        FormField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = if (signUp) "Password (6 or more characters)" else "Password",
            kind = FieldKind.Password,
            errorText = state.passwordError,
            enabled = !state.busy,
            imeAction = ImeAction.Done,
            onImeAction = { focus.clearFocus(); onSubmit() },
            modifier = Modifier.fillMaxWidth(),
        )

        // Firebase problems and "reset link sent": read aloud by TalkBack when they appear.
        val message = state.formError ?: state.info
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.formError != null) colors.error.onContainer else colors.success.onContainer,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        PrimaryButton(
            text = when {
                state.busy -> "Please wait..."
                signUp -> "Create account"
                else -> "Sign in"
            },
            onClick = { focus.clearFocus(); onSubmit() },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!signUp) {
            TextAction("Forgot password?", onForgotPassword, enabled = !state.busy)
        }
        TextAction(
            if (signUp) "Already have an account? Sign in" else "New here? Create an account",
            onSwitchMode,
            enabled = !state.busy,
        )
    }
}

@PreviewLightDark
@Composable
private fun AuthScreenPreview() = RocketDevOpsTheme {
    AppScaffold { padding ->
        AuthScreen(
            state = AuthUiState(AuthMode.SignIn, email = "ali@example.com", formError = "Email or password is not correct."),
            onNameChange = {}, onEmailChange = {}, onPasswordChange = {}, onSubmit = {},
            onSwitchMode = {}, onForgotPassword = {}, onBack = {},
            modifier = Modifier.padding(padding),
        )
    }
}
