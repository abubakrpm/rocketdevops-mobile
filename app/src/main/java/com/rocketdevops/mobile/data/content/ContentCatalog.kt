package com.rocketdevops.mobile.data.content

/**
 * Validated content plus fast lookups by id.
 * Screens receive only an id in their route (for example "lab/docker-lab-run"),
 * so they ask the catalog for the item and its parents (track and module).
 */
class ContentCatalog(bundle: ContentBundle) {

    val tracks: List<Track> = bundle.tracks
    val cheatSheet: List<CheatEntry> = bundle.cheatSheet

    /** A lab together with where it lives. `numberInTrack` gives captions like "Docker, Lab 3". */
    data class LabRef(val track: Track, val module: Module, val lab: Lab, val numberInTrack: Int)
    data class LessonRef(val track: Track, val module: Module, val lesson: Lesson)
    data class QuizRef(val track: Track, val module: Module, val quiz: Quiz)

    private val tracksById: Map<String, Track> = tracks.associateBy { it.id }

    /** Every lab in display order: track by track, module by module. */
    val allLabs: List<LabRef> = tracks.flatMap { track ->
        track.modules
            .flatMap { module -> module.labs.map { lab -> module to lab } }
            .mapIndexed { index, (module, lab) -> LabRef(track, module, lab, numberInTrack = index + 1) }
    }

    private val labsById: Map<String, LabRef> = allLabs.associateBy { it.lab.id }

    private val lessonsById: Map<String, LessonRef> = tracks.flatMap { track ->
        track.modules.flatMap { module -> module.lessons.map { LessonRef(track, module, it) } }
    }.associateBy { it.lesson.id }

    private val quizzesById: Map<String, QuizRef> = tracks.flatMap { track ->
        track.modules.mapNotNull { module -> module.quiz?.let { QuizRef(track, module, it) } }
    }.associateBy { it.quiz.id }

    fun track(id: String): Track? = tracksById[id]
    fun lab(id: String): LabRef? = labsById[id]
    fun lesson(id: String): LessonRef? = lessonsById[id]
    fun quiz(id: String): QuizRef? = quizzesById[id]

    /** Labs of one track, used by the "Container captain" achievement (all Docker labs done). */
    fun labsOfTrack(trackId: String): List<LabRef> = allLabs.filter { it.track.id == trackId }
}
