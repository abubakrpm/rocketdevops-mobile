package com.rocketdevops.mobile.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.ui.graphics.vector.ImageVector
import com.rocketdevops.mobile.data.content.TrackIcon

/**
 * Maps the icon name in the JSON to a generic outline icon.
 * No brand logos (spec 1.1): Git is a branching tree, Docker is a box.
 */
fun TrackIcon.toImageVector(): ImageVector = when (this) {
    TrackIcon.TERMINAL -> Icons.Outlined.Terminal
    TrackIcon.BRANCH -> Icons.Outlined.AccountTree
    TrackIcon.CONTAINER -> Icons.Outlined.Inventory2
}
