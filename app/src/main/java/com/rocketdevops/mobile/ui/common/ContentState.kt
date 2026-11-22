package com.rocketdevops.mobile.ui.common

import com.rocketdevops.mobile.data.content.ContentLoadResult
import com.rocketdevops.mobile.data.content.ContentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Loads the content once for a ViewModel and allows Retry.
 * `result` is null while loading. ViewModels combine it with the progress Flow.
 */
class ContentState(private val scope: CoroutineScope, private val repository: ContentRepository) {
    private val _result = MutableStateFlow<ContentLoadResult?>(null)
    val result: StateFlow<ContentLoadResult?> = _result.asStateFlow()

    init {
        load()
    }

    fun load() {
        _result.value = null
        scope.launch { _result.value = repository.load() }
    }
}

/** The text shown on an error state when content fails to load. */
fun ContentLoadResult.Failure.message(): String = problems.joinToString("\n")
