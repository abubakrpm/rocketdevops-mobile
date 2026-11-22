package com.rocketdevops.mobile.ui.profile

import com.rocketdevops.mobile.data.content.ContentCatalog
import com.rocketdevops.mobile.data.progress.Achievement
import com.rocketdevops.mobile.data.progress.Achievements
import com.rocketdevops.mobile.data.progress.DayActivity
import com.rocketdevops.mobile.data.progress.ProgressCalculator
import com.rocketdevops.mobile.data.progress.ProgressSnapshot
import com.rocketdevops.mobile.data.progress.Streaks
import java.time.LocalDate

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Error(val message: String) : ProfileUiState
    data class Ready(
        val labsDone: Int,
        val averageQuiz: String,        // "84%" or "-" before the first quiz
        val streakDays: Int,
        val lastSevenDays: List<DayActivity>,
        val achievements: List<AchievementRow>,
    ) : ProfileUiState {
        val unlockedCount: Int get() = achievements.count { it.unlocked }
    }
}

data class AchievementRow(
    val achievement: Achievement,
    val unlocked: Boolean,
    val detail: String,                 // description, or live progress such as "4 of 7 days"
)

/** Pure: builds the Profile screen. Unit tested. */
fun buildProfileState(catalog: ContentCatalog, snapshot: ProgressSnapshot, today: LocalDate): ProfileUiState.Ready {
    val lastWeekActive = (0L..6L).count { today.minusDays(it).toEpochDay() in snapshot.activeDays }

    fun labsLeft(trackId: String): String {
        val labs = catalog.labsOfTrack(trackId)
        val done = labs.count { it.lab.id in snapshot.completedLabs }
        return "$done of ${labs.size} labs"
    }

    return ProfileUiState.Ready(
        labsDone = snapshot.completedLabs.size,
        averageQuiz = ProgressCalculator.averageQuizPercent(snapshot)?.let { "$it%" } ?: "-",
        streakDays = Streaks.current(snapshot.activeDays, today),
        lastSevenDays = Streaks.lastDays(today, snapshot.activity),
        achievements = Achievements.all.map { a ->
            val unlocked = a.id in snapshot.achievements
            val detail = if (unlocked) a.description else when (a.id) {
                "week-warrior" -> "$lastWeekActive of 7 days"
                "shell-explorer" -> labsLeft("linux")
                "branch-master" -> labsLeft("git")
                "container-captain" -> labsLeft("docker")
                else -> a.description
            }
            AchievementRow(a, unlocked, detail)
        },
    )
}
