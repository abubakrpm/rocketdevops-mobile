package com.rocketdevops.mobile.ui.home

import com.rocketdevops.mobile.data.content.ContentCatalog
import com.rocketdevops.mobile.data.content.TrackIcon
import com.rocketdevops.mobile.data.prefs.UserPreferences
import com.rocketdevops.mobile.data.progress.DayActivity
import com.rocketdevops.mobile.data.progress.ItemKind
import com.rocketdevops.mobile.data.progress.ProgressCalculator
import com.rocketdevops.mobile.data.progress.ProgressSnapshot
import com.rocketdevops.mobile.data.progress.Streaks
import com.rocketdevops.mobile.ui.common.OpenTarget
import com.rocketdevops.mobile.ui.common.plural
import java.time.LocalDate

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Error(val message: String) : HomeUiState
    data class Ready(
        val greeting: String,          // "Hi, Muhib"
        val initials: String,          // "M" ("" shows a person icon)
        val streakDays: Int,
        val week: List<DayActivity>,
        val continueCard: ContinueCard,
        val tracks: List<TrackRow>,
    ) : HomeUiState
}

/** The dark hero card. */
data class ContinueCard(
    val eyebrow: String,       // "Continue, Docker"
    val title: String,         // "Run your first container"
    val actionLabel: String,   // "Resume lab"
    val progress: Float?,      // the track's progress, or null to hide the bar
    val target: OpenTarget,
)

data class TrackRow(
    val id: String,
    val title: String,
    val icon: TrackIcon,
    val subtitle: String,      // "5 labs, 40% done"
    val fraction: Float,
)

/** Pure: builds the Home screen from content, progress and settings. Unit tested. */
fun buildHomeState(
    catalog: ContentCatalog,
    snapshot: ProgressSnapshot,
    prefs: UserPreferences,
    today: LocalDate,
): HomeUiState.Ready {
    val tracks = ProgressCalculator.allTracks(catalog, snapshot)
    val target = ProgressCalculator.continueTarget(catalog, snapshot)

    val card = if (target == null) {
        ContinueCard(
            eyebrow = "All tracks complete",
            title = "Keep your skills sharp in the labs",
            actionLabel = "Open labs",
            progress = null,
            target = OpenTarget.LabsTab,
        )
    } else {
        val verb = if (target.isFirstStart) "Start" else "Continue"
        ContinueCard(
            eyebrow = "$verb, ${target.track.track.title}",
            title = target.item.title,
            actionLabel = when (target.item.kind) {
                ItemKind.Lesson -> "Read lesson"
                ItemKind.Lab -> if (target.isFirstStart) "Start lab" else "Resume lab"
                ItemKind.Quiz -> "Take quiz"
            },
            progress = if (target.isFirstStart) null else target.track.fraction,
            target = OpenTarget.of(target.item.kind, target.item.id),
        )
    }

    return HomeUiState.Ready(
        greeting = prefs.greeting,
        initials = prefs.initials,
        streakDays = Streaks.current(snapshot.activeDays, today),
        week = Streaks.week(today, snapshot.activity),
        continueCard = card,
        tracks = tracks.map { t ->
            TrackRow(
                id = t.track.id,
                title = t.track.title,
                icon = t.track.icon,
                subtitle = "${plural(t.labCount, "lab")}, ${t.percent}% done",
                fraction = t.fraction,
            )
        },
    )
}
