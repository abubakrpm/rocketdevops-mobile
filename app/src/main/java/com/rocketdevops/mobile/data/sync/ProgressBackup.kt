package com.rocketdevops.mobile.data.sync

import com.rocketdevops.mobile.data.progress.QuizAttemptRecord

/** A finished lesson or lab, with when it was finished. `kind` is "lesson" or "lab". */
data class CompletedRecord(val itemId: String, val kind: String, val atMillis: Long)

/**
 * Everything worth keeping about one learner's progress, in a plain form that can be
 * stored online and copied back into Room on any phone.
 *
 * A half-finished quiz is NOT included: it is a moment, not progress, and would only
 * confuse another phone.
 */
data class ProgressBackup(
    val completed: List<CompletedRecord> = emptyList(),
    val quizAttempts: List<QuizAttemptRecord> = emptyList(),
    /** epochDay -> things finished that day (streak and the 7-day chart). */
    val activity: Map<Long, Int> = emptyMap(),
    /** achievement id -> when it was unlocked. */
    val achievements: Map<String, Long> = emptyMap(),
) {
    val isEmpty: Boolean get() = completed.isEmpty() && quizAttempts.isEmpty() && activity.isEmpty() && achievements.isEmpty()

    companion object {
        val EMPTY = ProgressBackup()

        /**
         * Keeps the online document small (Firestore allows 1 MB per document). 500 attempts
         * is far more than the 13 quizzes need; the best scores are always kept.
         */
        const val MAX_ATTEMPTS = 500
    }
}

/**
 * Combines two copies of the same learner's progress, for example guest progress on this phone
 * with the account's online copy. Nothing that was earned is ever lost:
 *
 *  - lessons and labs: union; the later finish time wins (so "Continue" points at the newest work)
 *  - quiz attempts: union, duplicates (same quiz, time, score) counted once
 *  - daily activity: the larger count per day (the same day synced twice is not doubled)
 *  - achievements: union; the earlier unlock time wins
 *
 * Merging is symmetric and repeatable: merge(a, b) == merge(b, a), and merge(m, m) == m.
 */
fun mergeBackups(a: ProgressBackup, b: ProgressBackup): ProgressBackup {
    val completed = (a.completed + b.completed)
        .groupBy { it.itemId }
        .map { (_, records) -> records.maxWith(compareBy<CompletedRecord> { it.atMillis }.thenBy { it.kind }) }
        .sortedWith(compareBy({ it.atMillis }, { it.itemId }))

    val attempts = (a.quizAttempts + b.quizAttempts)
        .distinctBy { listOf(it.quizId, it.atMillis, it.score, it.total, it.wasRetry) }
        .sortedWith(compareBy({ it.atMillis }, { it.quizId }, { it.score }))
        .let(::capAttempts)

    val activity = (a.activity.keys + b.activity.keys).associateWith { day ->
        maxOf(a.activity[day] ?: 0, b.activity[day] ?: 0)
    }.filterValues { it > 0 }.toSortedMap()

    val achievements = (a.achievements.keys + b.achievements.keys).associateWith { id ->
        listOfNotNull(a.achievements[id], b.achievements[id]).min()
    }.toSortedMap()

    return ProgressBackup(completed, attempts, activity, achievements)
}

/** Drops the oldest attempts beyond the limit, but never the best full attempt of any quiz. */
private fun capAttempts(sorted: List<QuizAttemptRecord>): List<QuizAttemptRecord> {
    if (sorted.size <= ProgressBackup.MAX_ATTEMPTS) return sorted
    val bestOfEachQuiz = sorted.filter { !it.wasRetry }
        .groupBy { it.quizId }
        .mapNotNull { (_, attempts) -> attempts.maxByOrNull { it.score } }
        .toSet()
    val newest = sorted.takeLast(ProgressBackup.MAX_ATTEMPTS - bestOfEachQuiz.size).toSet()
    return sorted.filter { it in newest || it in bestOfEachQuiz }
}

// ---------------------------------------------------------------- Firestore format

/**
 * The online document, as plain maps and lists (no annotated classes, so R8 shrinking
 * cannot break it). Firestore map keys must be strings, so days are stored as text.
 *
 *  users/{uid} = {
 *    schema: 1, name: "Ali", updatedAt: 1764000000000,
 *    completed:    [ { id, kind, at } ],
 *    attempts:     [ { quiz, score, total, retry, at } ],
 *    activity:     { "20416": 3 },
 *    achievements: { "first-command": 1764000000000 }
 *  }
 */
object BackupFormat {
    const val SCHEMA = 1L

    fun toMap(backup: ProgressBackup, name: String, updatedAt: Long): Map<String, Any> = mapOf(
        "schema" to SCHEMA,
        "name" to name,
        "updatedAt" to updatedAt,
        "completed" to backup.completed.map { mapOf("id" to it.itemId, "kind" to it.kind, "at" to it.atMillis) },
        "attempts" to backup.quizAttempts.map {
            mapOf("quiz" to it.quizId, "score" to it.score.toLong(), "total" to it.total.toLong(), "retry" to it.wasRetry, "at" to it.atMillis)
        },
        "activity" to backup.activity.mapKeys { it.key.toString() }.mapValues { it.value.toLong() },
        "achievements" to backup.achievements,
    )

    /**
     * Reads a document back. Anything malformed is skipped instead of crashing, so a damaged
     * or newer-format document never stops the learner from signing in.
     */
    fun fromMap(data: Map<String, Any?>): Pair<String, ProgressBackup> {
        val name = data["name"] as? String ?: ""
        val completed = (data["completed"] as? List<*>).orEmpty().mapNotNull { raw ->
            val m = raw as? Map<*, *> ?: return@mapNotNull null
            val id = m["id"] as? String ?: return@mapNotNull null
            val kind = m["kind"] as? String ?: return@mapNotNull null
            val at = (m["at"] as? Number)?.toLong() ?: return@mapNotNull null
            CompletedRecord(id, kind, at)
        }
        val attempts = (data["attempts"] as? List<*>).orEmpty().mapNotNull { raw ->
            val m = raw as? Map<*, *> ?: return@mapNotNull null
            QuizAttemptRecord(
                quizId = m["quiz"] as? String ?: return@mapNotNull null,
                score = (m["score"] as? Number)?.toInt() ?: return@mapNotNull null,
                total = (m["total"] as? Number)?.toInt() ?: return@mapNotNull null,
                wasRetry = m["retry"] as? Boolean ?: false,
                atMillis = (m["at"] as? Number)?.toLong() ?: return@mapNotNull null,
            )
        }
        val activity = (data["activity"] as? Map<*, *>).orEmpty().mapNotNull { (k, v) ->
            val day = (k as? String)?.toLongOrNull() ?: return@mapNotNull null
            val count = (v as? Number)?.toInt() ?: return@mapNotNull null
            day to count
        }.toMap()
        val achievements = (data["achievements"] as? Map<*, *>).orEmpty().mapNotNull { (k, v) ->
            val id = k as? String ?: return@mapNotNull null
            val at = (v as? Number)?.toLong() ?: return@mapNotNull null
            id to at
        }.toMap()
        return name to ProgressBackup(completed, attempts, activity, achievements)
    }
}
