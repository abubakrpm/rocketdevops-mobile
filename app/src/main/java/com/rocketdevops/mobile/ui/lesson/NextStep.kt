package com.rocketdevops.mobile.ui.lesson

import com.rocketdevops.mobile.data.content.ContentCatalog

/** What the "Next: ..." link at the end of a lesson opens. */
sealed interface NextStep {
    val id: String
    val title: String

    data class Lesson(override val id: String, override val title: String) : NextStep
    data class Lab(override val id: String, override val title: String) : NextStep
    data class Quiz(override val id: String, override val title: String) : NextStep
}

/**
 * The learning path inside a module is: its lessons in order, then its labs (practice),
 * then its quiz (check). After that, the first lesson of the next module.
 * Returns null after the very last item of a track.
 */
fun nextStepAfterLesson(ref: ContentCatalog.LessonRef): NextStep? {
    val module = ref.module
    val lessons = module.lessons
    val position = lessons.indexOfFirst { it.id == ref.lesson.id }

    lessons.getOrNull(position + 1)?.let { return NextStep.Lesson(it.id, it.title) }
    module.labs.firstOrNull()?.let { return NextStep.Lab(it.id, it.title) }
    module.quiz?.let { return NextStep.Quiz(it.id, it.title) }

    val modules = ref.track.modules
    val nextLesson = modules.drop(modules.indexOf(module) + 1).firstNotNullOfOrNull { it.lessons.firstOrNull() }
    return nextLesson?.let { NextStep.Lesson(it.id, it.title) }
}
