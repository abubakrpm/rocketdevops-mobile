package com.rocketdevops.mobile.ui.labs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rocketdevops.mobile.RocketDevOpsApplication
import com.rocketdevops.mobile.data.content.ContentCatalog
import com.rocketdevops.mobile.data.content.ContentLoadResult
import com.rocketdevops.mobile.data.content.ContentRepository
import com.rocketdevops.mobile.data.content.TrackIcon
import com.rocketdevops.mobile.data.progress.ProgressRepository
import com.rocketdevops.mobile.ui.common.ContentState
import com.rocketdevops.mobile.ui.common.message
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Everything the Labs screen can show. The screen draws exactly one of these at a time. */
sealed interface LabsUiState {
    data object Loading : LabsUiState
    data class Error(val message: String) : LabsUiState
    data class Ready(val sections: List<LabSection>) : LabsUiState
}

/** One track's labs, shown under a heading. */
data class LabSection(
    val trackId: String,
    val trackTitle: String,
    val icon: TrackIcon,
    val labs: List<LabItem>,
)

/** One row. `done` comes live from ProgressRepository. */
data class LabItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val done: Boolean = false,
)

/**
 * MVVM: the ViewModel combines content with live progress and turns them into LabsUiState.
 * The screen only observes `state` and sends events back (retry, open lab).
 * It survives rotation because ViewModels outlive the screen's recomposition.
 * Finishing a lab writes to Room, the snapshot Flow re-emits, and the row shows "Done".
 */
class LabsViewModel(content: ContentRepository, progress: ProgressRepository) : ViewModel() {

    private val content = ContentState(viewModelScope, content)

    val state: StateFlow<LabsUiState> = combine(this.content.result, progress.snapshot) { result, snapshot ->
        when (result) {
            null -> LabsUiState.Loading
            is ContentLoadResult.Failure -> LabsUiState.Error(result.message())
            is ContentLoadResult.Success -> LabsUiState.Ready(buildLabSections(result.catalog, snapshot.completedLabs))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LabsUiState.Loading)

    /** Called by the Retry button on the error state. */
    fun load() = content.load()

    companion object {
        /** Tells Compose how to create this ViewModel with its dependencies from AppContainer. */
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as RocketDevOpsApplication).container
                LabsViewModel(c.contentRepository, c.progressRepository)
            }
        }
    }
}

/** Pure function (no Android), so it is unit tested directly. Groups labs by track, in content order. */
fun buildLabSections(catalog: ContentCatalog, completedLabs: Set<String> = emptySet()): List<LabSection> =
    catalog.allLabs
        .groupBy { it.track.id }
        .map { (_, refs) ->
            val track = refs.first().track
            LabSection(
                trackId = track.id,
                trackTitle = track.title,
                icon = track.icon,
                labs = refs.map { ref ->
                    val count = ref.lab.objectives.size
                    LabItem(
                        id = ref.lab.id,
                        title = ref.lab.title,
                        subtitle = "Lab ${ref.numberInTrack}, $count ${if (count == 1) "objective" else "objectives"}",
                        done = ref.lab.id in completedLabs,
                    )
                },
            )
        }
