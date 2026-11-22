package com.rocketdevops.mobile.ui.components

/**
 * The four visual states of a quiz answer (see QuizOptionRow).
 * In its own file with no Compose imports, so plain-Kotlin code and tests can use it.
 */
enum class QuizOptionState {
    Idle,     // not answered yet, tappable
    Correct,  // the right answer (shown after locking, whether or not the user picked it)
    Wrong,    // the user's pick, and it was wrong
    Disabled, // any other option once the answer is locked
}
