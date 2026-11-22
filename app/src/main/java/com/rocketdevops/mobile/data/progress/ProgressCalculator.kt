package com.rocketdevops.mobile.data.progress

import com.rocketdevops.mobile.data.content.ContentCatalog
import com.rocketdevops.mobile.data.content.Module
import com.rocketdevops.mobile.data.content.Track
import kotlin.math.roundToInt

/** The three kinds of things a learner finishes. */
enum class ItemKind { Lesson, Lab, Quiz }

/** One lesson, lab or quiz inside a module, with whether it is done. */
data class ItemProgress(
    val kind: ItemKind,
    val id: String,
    val title: String,
    val done: Boolean,
    val minutes: Int,          // estimated time, for "time left"
)

enum class ModuleStatus { Done, InProgress, Locked }

data class ModuleProgress(
    val module: Module,
    val items: List<ItemProgress>,
    val status: ModuleStatus,
    val bestQuiz: QuizAttemptRecord?,
) {
    val doneCount: Int get() = items.count { it.done }
    val total: Int get() = items.size
}

data class TrackProgress(
    val track: Track,
    val modules: List<ModuleProgress>,
) {
    val items: List<ItemProgress> get() = modules.flatMap { it.items }
    val doneCount: Int get() = items.count { it.done }
    val total: Int get() = items.size
    val fraction: Float get() = if (total == 0) 0f else doneCount.toFloat() / total
    val percent: Int get() = (fraction * 100).roundToInt()
    val labCount: Int get() = items.count { it.kind == ItemKind.Lab }
    val labsDone: Int get() = items.count { it.kind == ItemKind.Lab && it.done }
    val modulesDone: Int get() = modules.count { it.status == ModuleStatus.Done }
    val minutesLeft: Int get() = items.filterNot { it.done }.sumOf { it.minutes }
    val isComplete: Boolean get() = total > 0 && doneCount == total
}

/** What the Home "Continue" card opens. */
data class ContinueTarget(val track: TrackProgress, val item: ItemProgress, val isFirstStart: Boolean)

/**
 * All progress maths in one place, so every screen agrees.
 *
 * Items in a module, in order: lessons, labs, quiz (the same order as "Next: ..." in lessons).
 * A quiz counts as done after one full attempt. A module is:
 *   Done        every item is done
 *   Locked      the previous module is not done yet and nothing in this one is started
 *   InProgress  anything else (including "unlocked, not started")
 * Labs can still be practised from the Labs tab at any time; locking only guides the order.
 */
object ProgressCalculator {

    const val LAB_MINUTES = 10
    const val MINUTES_PER_QUESTION = 1

    fun track(track: Track, snapshot: ProgressSnapshot): TrackProgress {
        val modules = mutableListOf<ModuleProgress>()
        for (module in track.modules) {
            val items = itemsOf(module, snapshot)
            val previousDone = modules.lastOrNull()?.status?.let { it == ModuleStatus.Done } ?: true
            val status = when {
                items.isNotEmpty() && items.all { it.done } -> ModuleStatus.Done
                !previousDone && items.none { it.done } -> ModuleStatus.Locked
                else -> ModuleStatus.InProgress
            }
            modules += ModuleProgress(module, items, status, module.quiz?.let { snapshot.bestScore(it.id) })
        }
        return TrackProgress(track, modules)
    }

    fun allTracks(catalog: ContentCatalog, snapshot: ProgressSnapshot): List<TrackProgress> =
        catalog.tracks.map { track(it, snapshot) }

    private fun itemsOf(module: Module, snapshot: ProgressSnapshot): List<ItemProgress> = buildList {
        module.lessons.forEach { add(ItemProgress(ItemKind.Lesson, it.id, it.title, it.id in snapshot.completedLessons, it.estimatedMinutes)) }
        module.labs.forEach { add(ItemProgress(ItemKind.Lab, it.id, it.title, it.id in snapshot.completedLabs, LAB_MINUTES)) }
        module.quiz?.let { add(ItemProgress(ItemKind.Quiz, it.id, it.title, snapshot.hasAttempted(it.id), it.questions.size * MINUTES_PER_QUESTION)) }
    }

    /**
     * The next unfinished item, preferring the track the learner used most recently.
     * Null when everything in every track is done.
     */
    fun continueTarget(catalog: ContentCatalog, snapshot: ProgressSnapshot): ContinueTarget? {
        val tracks = allTracks(catalog, snapshot)
        val lastTrackId = snapshot.lastFinishedItemId?.let { trackIdOf(catalog, it) }
        val ordered = tracks.sortedByDescending { it.track.id == lastTrackId } // stable: keeps content order otherwise
        val isFirstStart = snapshot.lastFinishedItemId == null
        for (track in ordered) {
            val next = track.modules
                .filter { it.status != ModuleStatus.Locked }
                .flatMap { it.items }
                .firstOrNull { !it.done }
            if (next != null) return ContinueTarget(track, next, isFirstStart)
        }
        return null
    }

    fun trackIdOf(catalog: ContentCatalog, itemId: String): String? =
        catalog.lesson(itemId)?.track?.id ?: catalog.lab(itemId)?.track?.id ?: catalog.quiz(itemId)?.track?.id

    /** Average of the best full attempt of every quiz tried, as a percentage. Null if no quiz yet. */
    fun averageQuizPercent(snapshot: ProgressSnapshot): Int? {
        val best = snapshot.quizAttempts.filter { !it.wasRetry && it.total > 0 }
            .groupBy { it.quizId }
            .map { (_, attempts) -> attempts.maxOf { it.score.toFloat() / it.total } }
        return if (best.isEmpty()) null else (best.average() * 100).roundToInt()
    }
}
