package com.rocketdevops.mobile.ui.lesson

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
import com.rocketdevops.mobile.data.content.LessonBlock
import com.rocketdevops.mobile.data.progress.LearningProgressStore
import com.rocketdevops.mobile.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LessonScreenState(
    val trackTitle: String,
    val title: String,
    val estimatedMinutes: Int,
    val blocks: List<LessonBlock>,
    val isComplete: Boolean,
    val next: NextStep?,
)

sealed interface LessonUiState {
    data object Loading : LessonUiState
    data object NotFound : LessonUiState
    data class Error(val message: String) : LessonUiState
    data class Ready(val screen: LessonScreenState) : LessonUiState
}

class LessonViewModel(
    savedState: SavedStateHandle,
    private val content: ContentRepository,
    private val progress: LearningProgressStore,
) : ViewModel() {

    private val lessonId: String = savedState.get<String>(Routes.ARG_LESSON_ID).orEmpty()

    private val _state = MutableStateFlow<LessonUiState>(LessonUiState.Loading)
    val state: StateFlow<LessonUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = LessonUiState.Loading
        viewModelScope.launch {
            _state.value = when (val result = content.load()) {
                is ContentLoadResult.Failure -> LessonUiState.Error(result.problems.joinToString("\n"))
                is ContentLoadResult.Success -> {
                    val ref = result.catalog.lesson(lessonId)
                    if (ref == null) {
                        LessonUiState.NotFound
                    } else {
                        LessonUiState.Ready(
                            LessonScreenState(
                                trackTitle = ref.track.title,
                                title = ref.lesson.title,
                                estimatedMinutes = ref.lesson.estimatedMinutes,
                                blocks = ref.lesson.body,
                                isComplete = progress.isLessonComplete(lessonId),
                                next = nextStepAfterLesson(ref),
                            ),
                        )
                    }
                }
            }
        }
    }

    /** "Mark complete". Stage 6 also updates the streak and every progress bar from here. */
    fun markComplete() {
        viewModelScope.launch {
            progress.markLessonComplete(lessonId)
            _state.update { current ->
                if (current is LessonUiState.Ready) current.copy(screen = current.screen.copy(isComplete = true)) else current
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as RocketDevOpsApplication
                LessonViewModel(createSavedStateHandle(), app.container.contentRepository, app.container.progressStore)
            }
        }
    }
}
