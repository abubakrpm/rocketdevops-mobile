package com.rocketdevops.mobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rocketdevops.mobile.ui.theme.AppShapes
import com.rocketdevops.mobile.ui.theme.AppTextStyles
import com.rocketdevops.mobile.ui.theme.AppTheme
import com.rocketdevops.mobile.ui.theme.Dimens
import com.rocketdevops.mobile.ui.theme.Spacing
import com.rocketdevops.mobile.ui.theme.motionSpec

/**
 * 14. QuizOptionRow
 * One answer option. Only the Idle state is tappable: once an answer is locked the
 * QuizViewModel (Stage 5) switches every row to Correct, Wrong or Disabled.
 * `isCode` renders the text in the mono font (for answers that are commands).
 */
@Composable
fun QuizOptionRow(
    text: String,
    state: QuizOptionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCode: Boolean = false,
) {
    val colors = AppTheme.colors
    val (targetBg, targetBorder, targetText) = when (state) {
        QuizOptionState.Idle -> Triple(colors.background, colors.line, colors.ink)
        QuizOptionState.Correct -> Triple(colors.success.container, colors.success.base, colors.success.onContainer)
        QuizOptionState.Wrong -> Triple(colors.error.container, colors.error.base, colors.error.onContainer)
        QuizOptionState.Disabled -> Triple(colors.background, colors.line, colors.inkSecondary)
    }
    val bg by animateColorAsState(targetBg, motionSpec(), label = "optionBackground")
    val border by animateColorAsState(targetBorder, motionSpec(), label = "optionBorder")
    val content by animateColorAsState(targetText, motionSpec(), label = "optionText")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouchTarget)
            .clip(AppShapes.input)
            .background(bg)
            .border(Dimens.hairline, border, AppShapes.input)
            .clickable(enabled = state == QuizOptionState.Idle, role = Role.Button, onClick = onClick)
            .semantics {
                stateDescription = when (state) {
                    QuizOptionState.Idle -> "Not answered"
                    QuizOptionState.Correct -> "Correct answer"
                    QuizOptionState.Wrong -> "Your answer, wrong"
                    QuizOptionState.Disabled -> "Not chosen"
                }
            }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            style = if (isCode) AppTextStyles.monoMedium else MaterialTheme.typography.bodyMedium,
            color = content,
            modifier = Modifier.weight(1f),
        )
        // Icons are decorative here: stateDescription above already tells TalkBack the result.
        when (state) {
            QuizOptionState.Correct -> Icon(Icons.Outlined.Check, null, tint = content, modifier = Modifier.size(Dimens.iconMd))
            QuizOptionState.Wrong -> Icon(Icons.Outlined.Close, null, tint = content, modifier = Modifier.size(Dimens.iconMd))
            else -> Unit
        }
    }
}

@PreviewLightDark
@Composable
private fun QuizOptionRowPreview() = ComponentPreview {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        QuizOptionRow("docker images", QuizOptionState.Disabled, {}, isCode = true)
        QuizOptionRow("docker ps", QuizOptionState.Correct, {}, isCode = true)
        QuizOptionRow("docker ps -a", QuizOptionState.Wrong, {}, isCode = true)
        QuizOptionRow("docker run", QuizOptionState.Idle, {}, isCode = true)
    }
}
