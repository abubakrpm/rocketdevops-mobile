package com.rocketdevops.mobile.ui.playground

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rocketdevops.mobile.RocketDevOpsApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The playground screen: the terminal snapshot plus whether the reset dialog is open. */
data class PlaygroundUiState(val screen: PlaygroundScreenState, val showResetDialog: Boolean = false)

/**
 * Playground ViewModel (MVVM).
 * The session is restored from PlaygroundMemory (leaving and reopening the screen) or from
 * SavedStateHandle (Android closed the app in the background), whichever has more commands.
 */
class PlaygroundViewModel(
    private val savedState: SavedStateHandle,
    private val memory: PlaygroundMemory,
) : ViewModel() {

    private val controller: PlaygroundController = run {
        val saved = savedState.get<ArrayList<String>>(KEY_TYPED).orEmpty()
        val remembered = memory.typed
        PlaygroundController(if (saved.size > remembered.size) saved else remembered)
    }

    private var showResetDialog = false

    private val _state = MutableStateFlow(PlaygroundUiState(controller.snapshot()))
    val state: StateFlow<PlaygroundUiState> = _state.asStateFlow()

    /** The text being typed; Compose state so every key press shows instantly. */
    var input by mutableStateOf(TextFieldValue(""))
        private set

    fun onInputChange(value: TextFieldValue) {
        input = value
    }

    fun submit() {
        controller.submit(input.text)
        input = TextFieldValue("")
        save()
        publish()
    }

    /** Quick keys like "|" and "~": inserted at the cursor. */
    fun insert(text: String) {
        val value = input
        val start = value.selection.min
        val newText = value.text.replaceRange(start, value.selection.max, text)
        input = TextFieldValue(newText, TextRange(start + text.length))
    }

    /** A suggestion chip: fills the line (the visitor still presses Enter to run it). */
    fun useSuggestion(command: String) = setInput(command)

    fun tab() {
        val completion = controller.complete(input.text)
        setInput(completion.input)
        if (completion.candidates.size > 1) publish()
    }

    fun previousCommand() {
        controller.previousCommand()?.let(::setInput)
    }

    fun nextCommand() = setInput(controller.nextCommand())

    fun cancelLine() {
        controller.cancel(input.text)
        input = TextFieldValue("")
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
        controller.reset()
        input = TextFieldValue("")
        save()
        publish()
    }

    private fun setInput(text: String) {
        input = TextFieldValue(text, TextRange(text.length))
    }

    private fun publish() {
        _state.value = PlaygroundUiState(controller.snapshot(), showResetDialog)
    }

    private fun save() {
        memory.typed = controller.typed.toList()
        savedState[KEY_TYPED] = ArrayList(controller.typed)
    }

    companion object {
        private const val KEY_TYPED = "playground_typed_commands"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as RocketDevOpsApplication
                PlaygroundViewModel(createSavedStateHandle(), app.container.playgroundMemory)
            }
        }
    }
}
