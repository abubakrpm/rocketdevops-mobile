package com.rocketdevops.mobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rocketdevops.mobile.ui.theme.AppShapes
import com.rocketdevops.mobile.ui.theme.AppTextStyles
import com.rocketdevops.mobile.ui.theme.AppTheme
import com.rocketdevops.mobile.ui.theme.Dimens
import com.rocketdevops.mobile.ui.theme.LocalReduceMotion
import com.rocketdevops.mobile.ui.theme.Motion
import com.rocketdevops.mobile.ui.theme.Spacing

/**
 * 12. TerminalView
 * A near-black rounded box showing the scrollback (`lines`) and an input line with a violet prompt.
 * The caller sets the height (a fixed height, or Modifier.weight(1f) on the lab screen).
 *
 * It is stateless: the caller owns the lines and the input (the lab ViewModel).
 * - Interactive: pass `onInputChange` and `onSubmit`. Tapping anywhere in the box opens the keyboard.
 * - Read-only (previews, catalog): leave `onInputChange` null. A blinking block cursor is drawn instead.
 *
 * IMPORTANT: this only DISPLAYS text. Commands are interpreted by the in-app simulator
 * (package terminal); nothing is ever executed on the phone.
 */
@Composable
fun TerminalView(
    lines: List<TerminalLine>,
    modifier: Modifier = Modifier,
    prompt: String = "lab ~ $",
    input: TextFieldValue = TextFieldValue(""),
    onInputChange: ((TextFieldValue) -> Unit)? = null,
    onSubmit: () -> Unit = {},
    focusRequester: FocusRequester? = null,
    placeholder: String = "Tap here to type a command",
) {
    val colors = AppTheme.colors
    val listState = rememberLazyListState()
    val requester = focusRequester ?: remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Keep the newest line visible: scroll to the input row whenever output is added.
    LaunchedEffect(lines.size) {
        listState.scrollToItem(lines.size)
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.terminal)
            .background(colors.terminalBg)
            .then(
                if (onInputChange != null) {
                    // A tap anywhere on the terminal focuses the input and opens the keyboard.
                    Modifier.pointerInput(Unit) {
                        detectTapGestures {
                            requester.requestFocus()
                            keyboard?.show()
                        }
                    }
                } else {
                    Modifier
                },
            )
            .padding(Spacing.md),
    ) {
        itemsIndexed(lines) { _, line ->
            when (line.kind) {
                TerminalLine.Kind.Command -> Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = colors.terminalPrompt)) { append(line.prompt ?: prompt) }
                        append(" ")
                        withStyle(SpanStyle(color = colors.terminalText)) { append(line.text) }
                    },
                    style = AppTextStyles.mono,
                )
                TerminalLine.Kind.Output -> Text(line.text, style = AppTextStyles.mono, color = colors.terminalMuted)
                TerminalLine.Kind.Error -> Text(line.text, style = AppTextStyles.mono, color = colors.error.base)
            }
        }
        // Last row: the prompt and what the user is typing.
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(prompt, style = AppTextStyles.mono, color = colors.terminalPrompt)
                Spacer(Modifier.width(Spacing.xs))
                if (onInputChange != null) {
                    BasicTextField(
                        value = input,
                        onValueChange = onInputChange,
                        singleLine = true,
                        textStyle = AppTextStyles.mono.copy(color = colors.terminalText),
                        cursorBrush = SolidColor(colors.terminalPrompt),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,       // "ls" must not become "Is"
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Send,
                        ),
                        keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(requester)
                            .semantics { contentDescription = "Terminal input" },
                        decorationBox = { innerTextField ->
                            Box {
                                if (input.text.isEmpty()) {
                                    Text(placeholder, style = AppTextStyles.mono, color = colors.terminalMuted)
                                }
                                innerTextField()
                            }
                        },
                    )
                } else {
                    Text(input.text, style = AppTextStyles.mono, color = colors.terminalText)
                    BlinkingCursor()
                }
            }
        }
    }
}

/** Block cursor that blinks. Stays solid when the user has turned animations off. */
@Composable
private fun BlinkingCursor() {
    val reduceMotion = LocalReduceMotion.current
    val alpha = if (reduceMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "cursor")
        val animatedAlpha by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(Motion.BLINK, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "cursorAlpha",
        )
        animatedAlpha
    }
    Box(
        modifier = Modifier
            .size(width = Dimens.cursorWidth, height = Dimens.cursorHeight)
            .alpha(alpha)
            .background(AppTheme.colors.terminalPrompt),
    )
}

@PreviewLightDark
@Composable
private fun TerminalViewPreview() = ComponentPreview {
    TerminalView(
        modifier = Modifier.height(Dimens.terminalMinHeight),
        lines = listOf(
            TerminalLine("docker pull nginx", TerminalLine.Kind.Command, "lab ~ $"),
            TerminalLine("Downloaded newer image for nginx:latest", TerminalLine.Kind.Output),
            TerminalLine("docker run -d --name web nginx", TerminalLine.Kind.Command, "lab ~ $"),
            TerminalLine("c3f2a91b7d04", TerminalLine.Kind.Output),
            TerminalLine("dokcer ps", TerminalLine.Kind.Command, "lab ~ $"),
            TerminalLine("dokcer: command not found", TerminalLine.Kind.Error),
        ),
    )
}
