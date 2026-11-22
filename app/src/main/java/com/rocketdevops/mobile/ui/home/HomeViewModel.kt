package com.rocketdevops.mobile.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rocketdevops.mobile.RocketDevOpsApplication
import com.rocketdevops.mobile.data.content.ContentLoadResult
import com.rocketdevops.mobile.data.content.ContentRepository
import com.rocketdevops.mobile.data.prefs.UserPreferencesRepository
import com.rocketdevops.mobile.data.progress.ProgressRepository
import com.rocketdevops.mobile.ui.common.ContentState
import com.rocketdevops.mobile.ui.common.message
import com.rocketdevops.mobile.ui.common.todayFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Home: combines content, live progress (Room) and the learner's name (DataStore).
 * Any change in any of the three rebuilds the screen state, so finishing a lab
 * and pressing Back shows the new streak and percentages immediately.
 */
class HomeViewModel(
    content: ContentRepository,
    progress: ProgressRepository,
    prefs: UserPreferencesRepository,
) : ViewModel() {

    private val content = ContentState(viewModelScope, content)

    val state: StateFlow<HomeUiState> = combine(this.content.result, progress.snapshot, prefs.preferences, todayFlow()) { result, snapshot, user, today ->
        when (result) {
            null -> HomeUiState.Loading
            is ContentLoadResult.Failure -> HomeUiState.Error(result.message())
            is ContentLoadResult.Success -> buildHomeState(result.catalog, snapshot, user, today)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    fun retry() = content.load()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as RocketDevOpsApplication).container
                HomeViewModel(c.contentRepository, c.progressRepository, c.userPreferences)
            }
        }
    }
}
