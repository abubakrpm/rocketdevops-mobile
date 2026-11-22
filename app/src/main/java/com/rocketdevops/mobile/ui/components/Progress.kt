package com.rocketdevops.mobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import com.rocketdevops.mobile.ui.theme.AppShapes
import com.rocketdevops.mobile.ui.theme.AppTheme
import com.rocketdevops.mobile.ui.theme.Dimens
import com.rocketdevops.mobile.ui.theme.Motion
import com.rocketdevops.mobile.ui.theme.Spacing
import com.rocketdevops.mobile.ui.theme.motionSpec
import kotlin.math.roundToInt

/**
 * 10a. ProgressBar
 * A 5dp rounded bar. When `progress` changes the fill slides to the new value,
 * which is what makes progress feel "live" across the app.
 * `progress` is 0.0 to 1.0 (values outside that range are clamped).
 */
@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.primary,
    trackColor: Color = AppTheme.colors.line,
) {
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = motionSpec(Motion.LONG),
        label = "progressBar",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.progressBar)
            .clip(AppShapes.pill)
            .background(trackColor)
            // TalkBack announces this as a progress bar with a percentage.
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(AppShapes.pill)
                .background(color),
        )
    }
}

/**
 * 10b. ProgressRing
 * A circular progress indicator with text in the middle (default: the percentage).
 * Drawn on a Canvas: a full grey circle (track) and a coloured arc starting at 12 o'clock.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = Dimens.ringSm,
    strokeWidth: Dp = Dimens.ringStrokeSm,
    color: Color = AppTheme.colors.primary,
    trackColor: Color = AppTheme.colors.raised,
    label: String = "${(progress.coerceIn(0f, 1f) * 100).roundToInt()}%",
    labelStyle: TextStyle = MaterialTheme.typography.labelLarge,
) {
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = motionSpec(Motion.LONG),
        label = "progressRing",
    )
    Box(
        modifier = modifier
            .size(diameter)
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = strokeWidth.toPx()
            // Inset by half the stroke so the ring is not cut off at the edges.
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            if (animated > 0f) { // a zero-length arc with round caps would still draw a dot
                drawArc(
                    color = color,
                    startAngle = -90f,                 // start at the top
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(text = label, style = labelStyle, color = AppTheme.colors.ink)
    }
}

@PreviewLightDark
@Composable
private fun ProgressPreview() = ComponentPreview {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        ProgressBar(progress = 0.6f)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(progress = 0.4f)
            ProgressRing(
                progress = 0.8f,
                diameter = Dimens.ringLg,
                strokeWidth = Dimens.ringStrokeLg,
                trackColor = AppTheme.colors.surface,
                label = "8 / 10",
                labelStyle = MaterialTheme.typography.titleLarge,
            )
        }
    }
}
