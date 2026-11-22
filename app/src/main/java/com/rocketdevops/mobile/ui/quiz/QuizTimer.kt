package com.rocketdevops.mobile.ui.quiz

/**
 * The optional per-question countdown (spec: 30 s ring, OFF by default, switched on in Settings).
 * Plain Kotlin: the screen calls tick() once a second while it is visible, so a phone call or
 * switching apps pauses the clock instead of quietly using up the learner's time.
 */
class QuizTimer(val totalSeconds: Int = DEFAULT_SECONDS, startAt: Int = totalSeconds) {

    var secondsLeft: Int = startAt.coerceIn(0, totalSeconds)
        private set

    val fraction: Float get() = secondsLeft.toFloat() / totalSeconds

    /** The last few seconds are shown in red. */
    val isRunningOut: Boolean get() = secondsLeft <= WARNING_SECONDS

    /** One second passed. Returns true exactly once: on the tick that reaches zero. */
    fun tick(): Boolean {
        if (secondsLeft == 0) return false
        secondsLeft--
        return secondsLeft == 0
    }

    /** A new question starts with the full time. */
    fun reset() {
        secondsLeft = totalSeconds
    }

    companion object {
        const val DEFAULT_SECONDS = 30
        const val WARNING_SECONDS = 5
    }
}
