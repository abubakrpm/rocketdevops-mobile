package com.rocketdevops.mobile.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Corner radii from spec section 1.4. Screens use these, never RoundedCornerShape(n.dp) directly. */
object AppShapes {
    val card = RoundedCornerShape(20.dp)
    val hero = RoundedCornerShape(24.dp)
    val terminal = RoundedCornerShape(22.dp)
    val input = RoundedCornerShape(14.dp)
    val iconTile = RoundedCornerShape(12.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    /** Chips and buttons are fully rounded. */
    val pill = RoundedCornerShape(percent = 50)
    val circle = CircleShape
    /** Small radius for chart bars. */
    val bar = RoundedCornerShape(4.dp)
}

/** Material 3 components (dialogs, menus, snackbars) pick their corners from here. */
val AppMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
