package com.rocketdevops.mobile.data.progress

import com.rocketdevops.mobile.data.content.ContentCatalog
import java.time.LocalDate

/** Which outline icon an achievement shows (mapped to a real icon in the UI layer). */
enum class AchievementIcon { Terminal, Book, Star, Calendar, Folder, Branch, Box }

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: AchievementIcon,
)

/**
 * The achievements engine: a list of rules, each a plain function of the learner's progress.
 * ProgressRepository evaluates them after every lesson, lab or quiz and stores newly unlocked ones.
 */
object Achievements {

    const val WEEK_WARRIOR_DAYS = 6

    val all: List<Achievement> = listOf(
        Achievement("first-command", "First command", "Finish your first lab", AchievementIcon.Terminal),
        Achievement("first-lesson", "Bookworm", "Finish your first lesson", AchievementIcon.Book),
        Achievement("quiz-ace", "Quiz ace", "Get every answer right in a quiz", AchievementIcon.Star),
        Achievement("week-warrior", "Week warrior", "Learn on 6 of the last 7 days", AchievementIcon.Calendar),
        Achievement("shell-explorer", "Shell explorer", "Finish every Linux lab", AchievementIcon.Folder),
        Achievement("branch-master", "Branch master", "Finish every Git lab", AchievementIcon.Branch),
        Achievement("container-captain", "Container captain", "Finish every Docker lab", AchievementIcon.Box),
    )

    fun byId(id: String): Achievement? = all.firstOrNull { it.id == id }

    /** Ids of every achievement the progress earns (already stored ones included). */
    fun earned(snapshot: ProgressSnapshot, catalog: ContentCatalog, today: LocalDate): Set<String> = buildSet {
        if (snapshot.completedLabs.isNotEmpty()) add("first-command")
        if (snapshot.completedLessons.isNotEmpty()) add("first-lesson")
        if (snapshot.quizAttempts.any { !it.wasRetry && it.total > 0 && it.score == it.total }) add("quiz-ace")

        val lastWeek = (0L..6L).map { today.minusDays(it).toEpochDay() }
        if (lastWeek.count { it in snapshot.activeDays } >= WEEK_WARRIOR_DAYS) add("week-warrior")

        fun allLabsDone(trackId: String): Boolean {
            val labs = catalog.labsOfTrack(trackId)
            return labs.isNotEmpty() && labs.all { it.lab.id in snapshot.completedLabs }
        }
        if (allLabsDone("linux")) add("shell-explorer")
        if (allLabsDone("git")) add("branch-master")
        if (allLabsDone("docker")) add("container-captain")
    }

    /** Earned now but not stored yet: these trigger the "Achievement unlocked" snackbar. */
    fun newlyEarned(snapshot: ProgressSnapshot, catalog: ContentCatalog, today: LocalDate): List<Achievement> =
        earned(snapshot, catalog, today).filter { it !in snapshot.achievements }.mapNotNull(::byId)
}
