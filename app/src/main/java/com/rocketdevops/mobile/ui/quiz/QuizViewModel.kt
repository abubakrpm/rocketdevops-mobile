package com.rocketdevops.mobile.ui.quiz

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.rocketdevops.mobile.data.content.Quiz
import com.rocketdevops.mobile.data.progress.LearningProgressStore
import com.rocketdevops.mobile.data.progress.QuizAttempt
import com.rocketdevops.mobile.data.progress.QuizProgress
import com.rocketdevops.mobile.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What the timer ring shows. */
data class QuizTimerUi(val secondsLeft: Int, val fraction: Float, val isRunningOut: Boolean)

sealed interface QuizUiState {
    data object Loading : QuizUiState
    data object NotFound : QuizUiState
    data class Error(val message: String) : QuizUiState
    data class Ready(
        val screen: QuizScreenState,
        val showLeaveDialog: Boolean = false,
        val timer: QuizTimerUi? = null, // null when the quiz timer is off in Settings
    ) : QuizUiState
    /** The attempt is over: the screen navigates to the results. */
    data class Finished(val result: QuizResult, val isRetry: Boolean) : QuizUiState
}

/**
 * Live quiz (screen 4), MVVM with StateFlow.
 * - Rotation: the ViewModel survives.
 * - Process death: seed, answers and position are in SavedStateHandle; QuizSession.restore()
 *   rebuilds the identical attempt.
 * - Leaving and coming back: every answer is also saved in the LearningProgressStore
 *   (in memory in Stage 5, Room from Stage 6), so "Leave quiz? Your progress is saved" is true.
 */
class QuizViewModel(
    private val savedState: SavedStateHandle,
    private val content: ContentRepository,
    private val progress: LearningProgressStore,
    /** Reads the "Quiz timer" switch from Settings (off by default). */
    private val timerEnabled: suspend () -> Boolean = { false },
) : ViewModel() {

    private val quizId: String = savedState.get<String>(Routes.ARG_QUIZ_ID).orEmpty()
    private val retryIds: List<String>? = savedState.get<String>(Routes.ARG_RETRY)
        ?.split(',')?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }

    private val _state = MutableStateFlow<QuizUiState>(QuizUiState.Loading)
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    /** Text typed for TYPE_THE_COMMAND questions (Compose state, so typing is instant). */
    var typedAnswer by mutableStateOf("")
        private set

    private var session: QuizSession? = null
    private var timer: QuizTimer? = null
    private var showInputError = false
    private var showLeaveDialog = false

    init {
        load()
    }

    fun load() {
        _state.value = QuizUiState.Loading
        viewModelScope.launch {
            when (val result = content.load()) {
                is ContentLoadResult.Failure -> _state.value = QuizUiState.Error(result.problems.joinToString("\n"))
                is ContentLoadResult.Success -> {
                    val quiz = result.catalog.quiz(quizId)?.quiz
                    if (quiz == null) {
                        _state.value = QuizUiState.NotFound
                        return@launch
                    }
                    session = createSession(quiz)
                    if (timerEnabled()) {
                        // After rotation or process death, continue with the seconds that were left.
                        timer = QuizTimer(startAt = savedState.get<Int>(KEY_TIMER) ?: QuizTimer.DEFAULT_SECONDS)
                    }
                    persist()
                    publish()
                }
            }
        }
    }

    /** Restores after process death, resumes a left quiz, or starts a fresh shuffled attempt. */
    private suspend fun createSession(quiz: Quiz): QuizSession {
        val savedSeed = savedState.get<Long>(KEY_SEED)
        if (savedSeed != null) {
            return QuizSession(quiz, savedSeed, retryIds).apply {
                restore(savedState.get<ArrayList<String>>(KEY_ANSWERS).orEmpty(), savedState.get<Int>(KEY_INDEX) ?: 0)
            }
        }
        if (retryIds == null) {
            progress.loadQuizProgress(quizId)?.let { saved ->
                return QuizSession(quiz, saved.seed, saved.onlyQuestionIds).apply { restore(saved.givenAnswers, saved.index) }
            }
        }
        return QuizSession(quiz, QuizSession.newSeed(), retryIds)
    }

    // ---------------------------------------------------------------- answering

    /** Tapping an option locks the answer at once. */
    fun selectOption(option: String) {
        val s = session ?: return
        if (s.answer(option) != null) {
            persist()
            publish()
        }
    }

    fun onTypedAnswerChange(text: String) {
        typedAnswer = text
        if (showInputError && text.isNotBlank()) {
            showInputError = false
            publish()
        }
    }

    /** "Check" for TYPE_THE_COMMAND questions. */
    fun submitTypedAnswer() {
        val s = session ?: return
        if (s.isLocked) return
        if (typedAnswer.isBlank()) {
            showInputError = true
        } else {
            s.answer(QuizSession.normalize(typedAnswer))
            persist()
        }
        publish()
    }

    /**
     * Called by the screen once a second while it is visible and the question is open.
     * The clock pauses while the "Leave quiz?" dialog is open. At zero the question is
     * locked as wrong and the Why card explains the answer, like any wrong pick.
     */
    fun tick() {
        val s = session ?: return
        val t = timer ?: return
        if (s.isLocked || showLeaveDialog) return
        if (t.tick()) {
            s.timeOut()
            persist()
        }
        savedState[KEY_TIMER] = t.secondsLeft
        publish()
    }

    /** "Next question", or the results after the last one. */
    fun next() {
        val s = session ?: return
        if (!s.isLocked) return
        if (s.isFinished) {
            finish(s)
        } else {
            s.next()
            typedAnswer = ""
            showInputError = false
            timer?.reset()
            savedState.remove<Int>(KEY_TIMER)
            persist()
            publish()
        }
    }

    private fun finish(s: QuizSession) {
        val result = s.result()
        viewModelScope.launch {
            // Saved BEFORE navigating, so the results screen can show the best score.
            if (retryIds == null) {
                progress.recordQuizAttempt(QuizAttempt(quizId, result.score, result.total, wasRetry = false))
                progress.clearQuizProgress(quizId)
            } else {
                progress.recordQuizAttempt(QuizAttempt(quizId, result.score, result.total, wasRetry = true))
            }
            listOf(KEY_SEED, KEY_ANSWERS, KEY_INDEX, KEY_TIMER).forEach { savedState.remove<Any>(it) }
            _state.value = QuizUiState.Finished(result, isRetry = retryIds != null)
        }
    }

    // ---------------------------------------------------------------- leave dialog

    fun requestLeave() {
        showLeaveDialog = true
        publish()
    }

    fun dismissLeave() {
        showLeaveDialog = false
        publish()
    }

    // ---------------------------------------------------------------- internals

    private fun persist() {
        val s = session ?: return
        val given = ArrayList(s.answers.map { it.given })
        savedState[KEY_SEED] = s.seed
        savedState[KEY_ANSWERS] = given
        savedState[KEY_INDEX] = s.index
        if (retryIds == null) {
            viewModelScope.launch {
                progress.saveQuizProgress(quizId, QuizProgress(s.seed, null, given, s.index))
            }
        }
    }

    private fun publish() {
        val s = session ?: return
        _state.value = QuizUiState.Ready(
            screen = s.toScreenState(showInputError = showInputError, isRetry = retryIds != null),
            showLeaveDialog = showLeaveDialog,
            timer = timer?.takeIf { !s.isLocked }?.let { QuizTimerUi(it.secondsLeft, it.fraction, it.isRunningOut) },
        )
    }

    companion object {
        private const val KEY_SEED = "quiz_seed"
        private const val KEY_ANSWERS = "quiz_answers"
        private const val KEY_INDEX = "quiz_index"
        private const val KEY_TIMER = "quiz_timer_seconds"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as RocketDevOpsApplication
                QuizViewModel(
                    savedState = createSavedStateHandle(),
                    content = app.container.contentRepository,
                    progress = app.container.progressStore,
                    timerEnabled = { app.container.userPreferences.preferences.first().quizTimer },
                )
            }
        }
    }
}
