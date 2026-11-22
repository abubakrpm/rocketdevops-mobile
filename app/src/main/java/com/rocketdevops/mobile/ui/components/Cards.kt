package com.rocketdevops.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rocketdevops.mobile.ui.theme.AppShapes
import com.rocketdevops.mobile.ui.theme.AppTheme
import com.rocketdevops.mobile.ui.theme.Dimens
import com.rocketdevops.mobile.ui.theme.Spacing

/**
 * 4. SurfaceCard
 * The standard lavender card: surface colour, 20dp corners, no border, no shadow.
 * Pass `onClick` to make the whole card tappable.
 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.md),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(Spacing.xs),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(AppShapes.card)
            .background(AppTheme.colors.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/**
 * 5. HeroCard
 * The one dark card per screen (Home "Continue" card). White text and one white pill button.
 * `progress` is optional: pass null to hide the bar.
 */
@Composable
fun HeroCard(
    eyebrow: String,
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.hero)
            .background(colors.heroDark)
            .padding(Spacing.lg),
    ) {
        Text(text = eyebrow, style = MaterialTheme.typography.bodySmall, color = colors.accent)
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onHero,
            modifier = Modifier.semantics { heading() },
        )
        if (progress != null) {
            Spacer(Modifier.height(Spacing.sm))
            ProgressBar(progress = progress, color = colors.accent, trackColor = colors.heroTrack)
        }
        Spacer(Modifier.height(Spacing.md))
        val interaction = remember { MutableInteractionSource() }
        Button(
            onClick = onAction,
            modifier = Modifier
                .pressScale(interaction)
                .heightIn(min = Dimens.buttonMinHeight),
            interactionSource = interaction,
            shape = AppShapes.pill,
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.onHero,
                contentColor = colors.heroDark,
            ),
        ) {
            Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * 8. MetricTile
 * A big number with a small label underneath (Profile: "9 / Labs").
 * Use Modifier.weight(1f) from a Row to make tiles share the width equally.
 */
@Composable
fun MetricTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    SurfaceCard(
        modifier = modifier.semantics(mergeDescendants = true) {},   // read as "9, Labs" in one go
        contentPadding = PaddingValues(vertical = Spacing.md, horizontal = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        // Rolls up when the number changes, e.g. right after finishing a lab.
        RollingText(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = AppTheme.colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.colors.inkSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewLightDark
@Composable
private fun CardsPreview() = ComponentPreview {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        HeroCard(
            eyebrow = "Continue, Docker",
            title = "Run your first container",
            actionLabel = "Resume lab",
            onAction = {},
            progress = 0.4f,
        )
        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Text("Daily streak", style = MaterialTheme.typography.titleSmall)
            Text("A plain surface card", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.inkSecondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            MetricTile("9", "Labs", Modifier.weight(1f))
            MetricTile("84%", "Avg quiz", Modifier.weight(1f))
            MetricTile("6", "Streak", Modifier.weight(1f))
        }
    }
}
