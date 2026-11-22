package com.rocketdevops.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rocketdevops.mobile.ui.theme.AppShapes
import com.rocketdevops.mobile.ui.theme.AppTextStyles
import com.rocketdevops.mobile.ui.theme.AppTheme
import com.rocketdevops.mobile.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Code in a lavender card with a copy button (lesson reader, spec screen 3).
 * After copying, the icon turns into a check for a moment so the learner sees it worked.
 * Long lines scroll sideways instead of wrapping, so commands keep their shape.
 */
@Composable
fun CodeBlock(
    code: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    val colors = AppTheme.colors
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_FEEDBACK_MS)
            copied = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.input)
            .background(colors.surface)
            .padding(start = Spacing.md, end = Spacing.xxs, top = Spacing.xxs, bottom = Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = code,
                style = AppTextStyles.mono,
                color = colors.ink,
                softWrap = false,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = Spacing.sm)
                    .horizontalScroll(rememberScrollState()),
            )
            IconCircleButton(
                icon = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                contentDescription = if (copied) "Copied" else "Copy code",
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                    copied = true
                },
                containerColor = colors.raised,
                contentColor = if (copied) colors.success.base else colors.primary,
            )
        }
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.bodySmall, color = colors.inkSecondary)
        }
    }
}

private const val COPIED_FEEDBACK_MS = 1500L

@PreviewLightDark
@Composable
private fun CodeBlockPreview() = ComponentPreview {
    CodeBlock(code = "docker run -d --name web -p 8080:80 nginx\ndocker ps", caption = "Start nginx, then list it")
}
