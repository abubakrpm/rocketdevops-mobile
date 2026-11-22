package com.rocketdevops.mobile.ui.quiz

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

/** A wrong answer to look at again. */
data class ReviewItem(val questionId: String, val prompt: String, val correctAnswer: String, val explanation: String)

data class QuizResultsState(
    val quizId: String,
    val quizTitle: String,
    val score: Int,
    val total: Int,
    val bestScore: Int?,          // best full attempt so far (null if this was the first, or only retries)
    val toReview: List<ReviewItem>,
    val wasRetry: Boolean,
) {
    val fraction: Float get() = if (total == 0) 0f else score.toFloat() / total

    /** A short, honest message for the score. */
    val headline: String
        get() = when {
            total == 0 -> "Quiz complete"
            score == total -> "Perfect score"
            fraction >= GOOD_SCORE -> "Great work"
            fraction >= OK_SCORE -> "Good effort"
            else -> "Keep practising"
        }

    private companion object {
        const val GOOD_SCORE = 0.8f
        const val OK_SCORE = 0.5f
    }
}

sealed interface QuizResultsUiState {
    data object Loading : QuizResultsUiState
    data class Ready(val results: QuizResultsState) : QuizResultsUiState
}

/**
 * Screen 5. The score and the wrong question ids arrive in the route
 * (quiz-results/docker-run-quiz?score=8&total=10&wrong=q1,q2), so the screen still works
 * after the app was killed and restored.
 */
class QuizResultsViewModel(
    savedState: SavedStateHandle,
    private val content: ContentRepository,
    private val progress: LearningProgressStore,
) : ViewModel() {

    private val quizId = savedState.get<String>(Routes.ARG_QUIZ_ID).orEmpty()
    private val score = savedState.get<Int>(Routes.ARG_SCORE) ?: 0
    private val total = savedState.get<Int>(Routes.ARG_TOTAL) ?: 0
    private val wrongIds = savedState.get<String>(Routes.ARG_WRONG).orEmpty().split(',').filter { it.isNotBlank() }
    private val wasRetry = savedState.get<Boolean>(Routes.ARG_WAS_RETRY) ?: false

    private val _state = MutableStateFlow<QuizResultsUiState>(QuizResultsUiState.Loading)
    val state: StateFlow<QuizResultsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val quiz = (content.load() as? ContentLoadResult.Success)?.catalog?.quiz(quizId)?.quiz
            val questionsById = quiz?.questions?.associateBy { it.id }.orEmpty()
            _state.value = QuizResultsUiState.Ready(
                QuizResultsState(
                    quizId = quizId,
                    quizTitle = quiz?.title.orEmpty(),
                    score = score,
                    total = total,
                    bestScore = progress.bestQuizScore(quizId),
                    toReview = wrongIds.mapNotNull { id ->
                        questionsById[id]?.let { q ->
                            ReviewItem(q.id, q.prompt, q.correctAnswers.firstOrNull().orEmpty(), q.explanation)
                        }
                    },
                    wasRetry = wasRetry,
                ),
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as RocketDevOpsApplication
                QuizResultsViewModel(createSavedStateHandle(), app.container.contentRepository, app.container.progressStore)
            }
        }
    }
}
