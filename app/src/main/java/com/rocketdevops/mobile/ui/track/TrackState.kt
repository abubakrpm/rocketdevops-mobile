package com.rocketdevops.mobile.ui.track

import com.rocketdevops.mobile.data.content.ContentCatalog
import com.rocketdevops.mobile.data.progress.ItemKind
import com.rocketdevops.mobile.data.progress.ModuleStatus
import com.rocketdevops.mobile.data.progress.ProgressCalculator
import com.rocketdevops.mobile.data.progress.ProgressSnapshot
import com.rocketdevops.mobile.ui.common.OpenTarget
import com.rocketdevops.mobile.ui.common.formatTimeLeft
import com.rocketdevops.mobile.ui.common.plural

sealed interface TrackUiState {
    data object Loading : TrackUiState
    data class Error(val message: String) : TrackUiState
    data class Ready(
        val title: String,
        val fraction: Float,
        val modulesLine: String,     // "2 of 5 modules"
        val timeLeft: String,        // "About 1h 20m left"
        val modules: List<ModuleRow>,
    ) : TrackUiState
}

data class ModuleRow(
    val id: String,
    val title: String,
    val status: ModuleStatus,
    val subtitle: String,            // "Done, quiz 90%", "In progress, 2 of 5", "Locked"
    val lockedMessage: String?,      // snackbar text when a locked module is tapped
    val items: List<ItemRow>,
)

data class ItemRow(
    val id: String,
    val kind: ItemKind,
    val title: String,
    val subtitle: String,            // "Lesson, 5 min"
    val done: Boolean,
    val target: OpenTarget,
)

/** Pure: builds the Track screen. Returns null if the track id does not exist. */
fun buildTrackState(catalog: ContentCatalog, snapshot: ProgressSnapshot, trackId: String): TrackUiState.Ready? {
    val track = catalog.track(trackId) ?: return null
    val progress = ProgressCalculator.track(track, snapshot)

    val rows = progress.modules.mapIndexed { index, module ->
        val quizText = module.bestQuiz?.let { best ->
            if (best.total > 0) "quiz ${best.score * 100 / best.total}%" else null
        }
        val subtitle = when (module.status) {
            ModuleStatus.Done -> listOfNotNull("Done", quizText).joinToString(", ")
            ModuleStatus.InProgress ->
                if (module.doneCount == 0) "Up next, ${plural(module.total, "step")}"
                else "In progress, ${module.doneCount} of ${module.total}"
            ModuleStatus.Locked -> "Locked"
        }
        ModuleRow(
            id = module.module.id,
            title = module.module.title,
            status = module.status,
            subtitle = subtitle,
            lockedMessage = if (module.status == ModuleStatus.Locked && index > 0) {
                "Finish \"${progress.modules[index - 1].module.title}\" first"
            } else null,
            items = module.items.map { item ->
                ItemRow(
                    id = item.id,
                    kind = item.kind,
                    title = item.title,
                    subtitle = when (item.kind) {
                        ItemKind.Lesson -> "Lesson, ${item.minutes} min"
                        ItemKind.Lab -> "Lab, simulated terminal"
                        ItemKind.Quiz -> module.bestQuiz?.let { "Quiz, best ${it.score} of ${it.total}" }
                            ?: "Quiz, ${plural(item.minutes, "question")}"
                    },
                    done = item.done,
                    target = OpenTarget.of(item.kind, item.id),
                )
            },
        )
    }

    return TrackUiState.Ready(
        title = track.title,
        fraction = progress.fraction,
        modulesLine = "${progress.modulesDone} of ${plural(progress.modules.size, "module")}",
        timeLeft = formatTimeLeft(progress.minutesLeft),
        modules = rows,
    )
}
