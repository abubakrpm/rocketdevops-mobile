package com.rocketdevops.mobile.ui.cheats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rocketdevops.mobile.RocketDevOpsApplication
import com.rocketdevops.mobile.data.content.CheatEntry
import com.rocketdevops.mobile.data.content.ContentLoadResult
import com.rocketdevops.mobile.data.content.ContentRepository
import com.rocketdevops.mobile.ui.common.ContentState
import com.rocketdevops.mobile.ui.common.message
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface CheatSheetUiState {
    data object Loading : CheatSheetUiState
    data class Error(val message: String) : CheatSheetUiState
    data class Ready(
        val query: String,
        val filter: CheatFilter,
        val results: List<CheatEntry>,
        val counts: Map<CheatFilter, Int>,
    ) : CheatSheetUiState
}

/**
 * Cheat sheet (screen 7). The search text and the selected chip live in SavedStateHandle,
 * so they survive rotation and the app being killed in the background.
 */
class CheatSheetViewModel(
    private val savedState: SavedStateHandle,
    content: ContentRepository,
) : ViewModel() {

    private val content = ContentState(viewModelScope, content)
    private val query = savedState.getStateFlow(KEY_QUERY, "")
    private val filterName = savedState.getStateFlow(KEY_FILTER, CheatFilter.All.name)

    val state: StateFlow<CheatSheetUiState> = combine(this.content.result, query, filterName) { result, q, name ->
        when (result) {
            null -> CheatSheetUiState.Loading
            is ContentLoadResult.Failure -> CheatSheetUiState.Error(result.message())
            is ContentLoadResult.Success -> {
                val filter = CheatFilter.entries.firstOrNull { it.name == name } ?: CheatFilter.All
                val all = result.catalog.cheatSheet
                CheatSheetUiState.Ready(q, filter, filterCheats(all, q, filter), countsPerFilter(all, q))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CheatSheetUiState.Loading)

    fun onQueryChange(text: String) {
        savedState[KEY_QUERY] = text.take(MAX_QUERY)
    }

    fun onFilterChange(filter: CheatFilter) {
        savedState[KEY_FILTER] = filter.name
    }

    /** "Clear search" on the empty state: clears the text and goes back to All. */
    fun clear() {
        savedState[KEY_QUERY] = ""
        savedState[KEY_FILTER] = CheatFilter.All.name
    }

    fun retry() = content.load()

    companion object {
        private const val KEY_QUERY = "query"
        private const val KEY_FILTER = "filter"
        private const val MAX_QUERY = 60

        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as RocketDevOpsApplication).container
                CheatSheetViewModel(createSavedStateHandle(), c.contentRepository)
            }
        }
    }
}
