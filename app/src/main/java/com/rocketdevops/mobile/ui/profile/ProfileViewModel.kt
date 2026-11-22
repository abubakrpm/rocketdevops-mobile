package com.rocketdevops.mobile.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rocketdevops.mobile.RocketDevOpsApplication
import com.rocketdevops.mobile.data.content.ContentLoadResult
import com.rocketdevops.mobile.data.content.ContentRepository
import com.rocketdevops.mobile.data.progress.ProgressRepository
import com.rocketdevops.mobile.ui.common.ContentState
import com.rocketdevops.mobile.ui.common.message
import com.rocketdevops.mobile.ui.common.todayFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Profile: metrics, last 7 days and achievements, all live from Room. */
class ProfileViewModel(content: ContentRepository, progress: ProgressRepository) : ViewModel() {

    private val content = ContentState(viewModelScope, content)

    val state: StateFlow<ProfileUiState> = combine(this.content.result, progress.snapshot, todayFlow()) { result, snapshot, today ->
        when (result) {
            null -> ProfileUiState.Loading
            is ContentLoadResult.Failure -> ProfileUiState.Error(result.message())
            is ContentLoadResult.Success -> buildProfileState(result.catalog, snapshot, today)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState.Loading)

    fun retry() = content.load()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as RocketDevOpsApplication).container
                ProfileViewModel(c.contentRepository, c.progressRepository)
            }
        }
    }
}
