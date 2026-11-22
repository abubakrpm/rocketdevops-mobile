package com.rocketdevops.mobile.ui.lab

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
import com.rocketdevops.mobile.data.progress.LearningProgressStore
import com.rocketdevops.mobile.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LabUiState {
    data object Loading : LabUiState
    data object NotFound : LabUiState
    data class Error(val message: String) : LabUiState
    data class Ready(val screen: LabScreenState, val showResetDialog: Boolean = false) : LabUiState
}

/**
 * Lab screen ViewModel (MVVM).
 * - Survives rotation automatically (ViewModels outlive configuration changes).
 * - Survives process death: the typed commands and revealed hints are saved in SavedStateHandle,
 *   and replayed on restart. The simulator is deterministic, so the lab comes back identical.
 */
class LabViewModel(
    private val savedState: SavedStateHandle,
    private val content: ContentRepository,
    private val progress: LearningProgressStore,
) : ViewModel() {

    private val labId: String = savedState.get<String>(Routes.ARG_LAB_ID).orEmpty()

    private val _state = MutableStateFlow<LabUiState>(LabUiState.Loading)
    val state: StateFlow<LabUiState> = _state.asStateFlow()

    /**
     * The text being typed. Kept as Compose state (not in the StateFlow) because text fields
     * must update instantly on every key press. TextFieldValue also remembers the cursor position.
     */
    var input by mutableStateOf(TextFieldValue(""))
        private set

    private var controller: LabController? = null
    private var showResetDialog = false

    init {
        load()
    }

    fun load() {
        _state.value = LabUiState.Loading
        viewModelScope.launch {
            when (val result = content.load()) {
                is ContentLoadResult.Failure -> _state.value = LabUiState.Error(result.problems.joinToString("\n"))
                is ContentLoadResult.Success -> {
                    val ref = result.catalog.lab(labId)
                    if (ref == null) {
                        _state.value = LabUiState.NotFound
                    } else {
                        controller = LabController(
                            ref = ref,
                            restoredInputs = savedState.get<ArrayList<String>>(KEY_TYPED).orEmpty(),
                            restoredHintsShown = savedState.get<Int>(KEY_HINTS) ?: 0,
                        )
                        publish()
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- typing

    fun onInputChange(value: TextFieldValue) {
        input = value
    }

    fun submit() {
        val c = controller ?: return
        val wasComplete = c.snapshot().isComplete
        c.submit(input.text)
        input = TextFieldValue("")
        save()
        publish()
        if (!wasComplete && c.snapshot().isComplete) {
            // Recorded for progress, streaks and achievements (Room-backed from Stage 6).
            viewModelScope.launch { progress.markLabComplete(labId) }
        }
    }

    /** Quick keys like "|", "/" and "~": inserted at the cursor. */
    fun insert(text: String) {
        val value = input
        val start = value.selection.min
        val end = value.selection.max
        val newText = value.text.replaceRange(start, end, text)
        input = TextFieldValue(newText, TextRange(start + text.length))
    }

    fun tab() {
        val c = controller ?: return
        val completion = c.complete(input.text)
        setInput(completion.input)
        if (completion.candidates.size > 1) publish()
    }

    fun previousCommand() {
        controller?.previousCommand()?.let(::setInput)
    }

    fun nextCommand() {
        controller?.nextCommand()?.let(::setInput)
    }

    fun cancelLine() {
        val c = controller ?: return
        c.cancel(input.text)
        input = TextFieldValue("")
        publish()
    }

    private fun setInput(text: String) {
        input = TextFieldValue(text, TextRange(text.length)) // cursor at the end
    }

    // ---------------------------------------------------------------- hints, reset, success

    fun showNextHint() {
        controller?.showNextHint()
        save()
        publish()
    }

    fun hideHints() {
        controller?.hideHints()
        publish()
    }

    fun requestReset() {
        showResetDialog = true
        publish()
    }

    fun dismissReset() {
        showResetDialog = false
        publish()
    }

    fun confirmReset() {
        showResetDialog = false
        controller?.reset()
        input = TextFieldValue("")
        save()
        publish()
    }

    fun dismissSuccess() {
        controller?.dismissSuccess()
        publish()
    }

    // ---------------------------------------------------------------- internals

    private fun publish() {
        val c = controller ?: return
        _state.value = LabUiState.Ready(c.snapshot(), showResetDialog)
    }

    private fun save() {
        val c = controller ?: return
        savedState[KEY_TYPED] = ArrayList(c.typed)
        savedState[KEY_HINTS] = c.hintsShown
    }

    companion object {
        private const val KEY_TYPED = "lab_typed_commands"
        private const val KEY_HINTS = "lab_hints_shown"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as RocketDevOpsApplication
                // createSavedStateHandle() also contains the route argument labId.
                LabViewModel(createSavedStateHandle(), app.container.contentRepository, app.container.progressStore)
            }
        }
    }
}
