package com.rocketdevops.mobile.ui.theme

import androidx.compose.ui.unit.dp

/**
 * SPACING SCALE (spec 1.4): 4, 8, 12, 16, 20, 24, 32 dp.
 * Use these names instead of raw numbers so the whole app keeps one rhythm.
 */
object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Left and right padding of every screen. */
    val screenHorizontal = md
}

/** Fixed component sizes. Kept here so no screen or component contains a magic dp number. */
object Dimens {
    /** Accessibility minimum: anything tappable is at least this big. */
    val minTouchTarget = 48.dp
    val buttonMinHeight = 48.dp

    val iconSm = 16.dp
    val iconMd = 20.dp
    val iconLg = 24.dp

    /** Visible circle of IconCircleButton (its touch area is still 48dp). */
    val iconCircle = 40.dp
    /** Coloured square behind a list-row icon. */
    val iconTile = 40.dp
    val avatar = 44.dp

    val navItem = 48.dp
    val hairline = 1.dp
    val progressBar = 5.dp

    val ringSm = 56.dp
    val ringLg = 120.dp
    val ringStrokeSm = 6.dp
    val ringStrokeLg = 10.dp
    val timerRing = 40.dp          // quiz timer in the quiz header
    val timerStroke = 4.dp

    val segmentItemHeight = 40.dp
    val weekDot = 4.dp
    val chartHeight = 80.dp
    val terminalMinHeight = 220.dp
    val cursorWidth = 7.dp
    val cursorHeight = 15.dp
    val skeletonLine = 14.dp

    /** Lab success screen: the green circle and the check mark inside it. */
    val successBadge = 96.dp
    val successIcon = 48.dp
}
