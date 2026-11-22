package com.rocketdevops.mobile.ui.track

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rocketdevops.mobile.RocketDevOpsApplication
import com.rocketdevops.mobile.data.content.ContentLoadResult
import com.rocketdevops.mobile.data.content.ContentRepository
import com.rocketdevops.mobile.data.progress.ProgressRepository
import com.rocketdevops.mobile.navigation.Routes
import com.rocketdevops.mobile.ui.common.ContentState
import com.rocketdevops.mobile.ui.common.message
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Track screen. The track id comes from the route ("track/docker") through SavedStateHandle. */
class TrackViewModel(
    savedState: SavedStateHandle,
    content: ContentRepository,
    progress: ProgressRepository,
) : ViewModel() {

    private val trackId: String = savedState.get<String>(Routes.ARG_TRACK_ID).orEmpty()
    private val content = ContentState(viewModelScope, content)

    val state: StateFlow<TrackUiState> = combine(this.content.result, progress.snapshot) { result, snapshot ->
        when (result) {
            null -> TrackUiState.Loading
            is ContentLoadResult.Failure -> TrackUiState.Error(result.message())
            is ContentLoadResult.Success ->
                buildTrackState(result.catalog, snapshot, trackId) ?: TrackUiState.Error("Track \"$trackId\" was not found.")
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackUiState.Loading)

    fun retry() = content.load()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as RocketDevOpsApplication).container
                TrackViewModel(createSavedStateHandle(), c.contentRepository, c.progressRepository)
            }
        }
    }
}
