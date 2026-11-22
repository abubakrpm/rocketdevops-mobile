package com.rocketdevops.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rocketdevops.mobile.ui.theme.AppShapes
import com.rocketdevops.mobile.ui.theme.AppTheme
import com.rocketdevops.mobile.ui.theme.Dimens
import com.rocketdevops.mobile.ui.theme.Spacing

/**
 * One day in the strip.
 * @param dayLabel short weekday, e.g. "Wed"
 * @param dateLabel day of month, e.g. "19"
 * @param isActive the user finished at least one lesson, quiz or lab that day (counts for the streak)
 */
data class WeekDay(
    val dayLabel: String,
    val dateLabel: String,
    val isToday: Boolean,
    val isActive: Boolean,
)

/**
 * 13. WeekStrip
 * A row of day chips. Today is filled with the primary colour.
 * A small amber dot marks days that count towards the streak.
 * Usually placed inside a SurfaceCard titled "Daily streak".
 */
@Composable
fun WeekStrip(
    days: List<WeekDay>,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        days.forEach { day ->
            val background = if (day.isToday) colors.primary else colors.raised
            val primaryText = if (day.isToday) colors.onPrimary else colors.ink
            val secondaryText = if (day.isToday) colors.onPrimary else colors.inkSecondary
            val dotColor = when {
                !day.isActive -> Color.Transparent
                day.isToday -> colors.onPrimary
                else -> colors.warning.base
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(AppShapes.iconTile)
                    .background(background)
                    .padding(vertical = Spacing.xs)
                    // TalkBack reads one sentence per day instead of three fragments.
                    .clearAndSetSemantics {
                        contentDescription = buildString {
                            append("${day.dayLabel} ${day.dateLabel}")
                            if (day.isToday) append(", today")
                            if (day.isActive) append(", streak day")
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs / 2),
            ) {
                // maxLines = 1: at very large text sizes a label is clipped, never wrapped.
                Text(day.dayLabel, style = MaterialTheme.typography.bodySmall, color = secondaryText, maxLines = 1, softWrap = false)
                Text(day.dateLabel, style = MaterialTheme.typography.titleSmall, color = primaryText, maxLines = 1, softWrap = false)
                Box(
                    modifier = Modifier
                        .size(Dimens.weekDot)
                        .clip(AppShapes.circle)
                        .background(dotColor),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun WeekStripPreview() = ComponentPreview {
    SurfaceCard {
        Text("Daily streak", style = MaterialTheme.typography.titleSmall)
        WeekStrip(
            days = listOf(
                WeekDay("Mon", "17", isToday = false, isActive = true),
                WeekDay("Tue", "18", isToday = false, isActive = true),
                WeekDay("Wed", "19", isToday = true, isActive = true),
                WeekDay("Thu", "20", isToday = false, isActive = false),
                WeekDay("Fri", "21", isToday = false, isActive = false),
                WeekDay("Sat", "22", isToday = false, isActive = false),
                WeekDay("Sun", "23", isToday = false, isActive = false),
            ),
        )
    }
}
