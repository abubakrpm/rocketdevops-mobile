package com.rocketdevops.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rocketdevops.mobile.ui.theme.AppTheme
import com.rocketdevops.mobile.ui.theme.RocketDevOpsTheme
import com.rocketdevops.mobile.ui.theme.Spacing

/**
 * Wraps a component preview in the app theme on the app background.
 * Used with @PreviewLightDark, which renders every preview twice (light and dark):
 * the theme follows the preview's night mode automatically.
 */
@Composable
internal fun ComponentPreview(content: @Composable () -> Unit) {
    RocketDevOpsTheme {
        Box(
            modifier = Modifier
                .background(AppTheme.colors.background)
                .padding(Spacing.md),
        ) {
            content()
        }
    }
}
