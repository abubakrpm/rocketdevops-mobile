package com.rocketdevops.mobile.ui.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rocketdevops.mobile.R
import com.rocketdevops.mobile.ui.components.AppScaffold
import com.rocketdevops.mobile.ui.components.NameField
import com.rocketdevops.mobile.ui.components.PrimaryButton
import com.rocketdevops.mobile.ui.components.SecondaryButton
import com.rocketdevops.mobile.ui.components.TextAction
import com.rocketdevops.mobile.ui.components.revealOnEnter
import com.rocketdevops.mobile.ui.theme.AppTheme
import com.rocketdevops.mobile.ui.theme.Dimens
import com.rocketdevops.mobile.ui.theme.RocketDevOpsTheme
import com.rocketdevops.mobile.ui.theme.Spacing

/**
 * Shown once, on first launch: asks what to call the learner (e.g. "Muhib", "Abu Bakr").
 * "Skip" is allowed; Home then says "Hi there". The name can be changed later in Settings.
 * Not in the mockups: added so every visitor at the exhibition sees their own name.
 */
@Composable
fun WelcomeScreen(
    onFinish: (name: String) -> Unit,
    modifier: Modifier = Modifier,
    /** Null when accounts are not set up in this build: the account links are hidden. */
    onSignIn: (() -> Unit)? = null,
    onCreateAccount: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    var name by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding() // keep the button above the keyboard
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.padding(top = Spacing.xxl))
        // The launcher icon's rocket (decorative: the heading below says the app's name).
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(Dimens.successBadge)
                .revealOnEnter(0),
        )
        Text(
            "Welcome to RocketDevOps",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .revealOnEnter(1)
                .semantics { heading() },
        )
        Text(
            "Learn Linux, Git and Docker with short lessons, quizzes and a simulated terminal. Works fully offline.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.revealOnEnter(2),
        )
        Spacer(Modifier.padding(top = Spacing.xs))
        Text("What should we call you?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.revealOnEnter(3))
        NameField(
            value = name,
            onValueChange = { name = it },
            onDone = { if (name.isNotBlank()) onFinish(name) },
            modifier = Modifier
                .fillMaxWidth()
                .revealOnEnter(4),
        )
        PrimaryButton(
            text = "Continue",
            onClick = { onFinish(name) },
            enabled = name.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .revealOnEnter(5),
        )
        TextAction("Skip for now", { onFinish("") }, modifier = Modifier.revealOnEnter(6))

        // Optional accounts: progress is kept online and comes back after signing in again.
        if (onSignIn != null && onCreateAccount != null) {
            Text(
                "Already learning with an account?",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkSecondary,
                modifier = Modifier
                    .padding(top = Spacing.sm)
                    .revealOnEnter(7),
            )
            SecondaryButton(
                text = "Sign in",
                onClick = onSignIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .revealOnEnter(8),
            )
            TextAction("Create an account to save progress online", onCreateAccount, modifier = Modifier.revealOnEnter(8))
        }
    }
}

@PreviewLightDark
@Composable
private fun WelcomeScreenPreview() = RocketDevOpsTheme {
    AppScaffold { padding -> WelcomeScreen(onFinish = {}, modifier = Modifier.padding(padding), onSignIn = {}, onCreateAccount = {}) }
}
