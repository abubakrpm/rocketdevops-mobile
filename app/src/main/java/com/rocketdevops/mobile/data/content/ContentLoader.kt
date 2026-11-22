package com.rocketdevops.mobile.data.content

import android.content.res.AssetManager
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Where content files are read from. The app reads from APK assets; unit tests read the same
 * files straight from the disk (src/main/assets), so tests check the REAL content.
 */
fun interface ContentSource {
    /** @param path relative to the content folder, e.g. "tracks/linux.json" */
    fun read(path: String): String
}

/** Production source: files bundled inside the APK under assets/content. Works fully offline. */
class AssetContentSource(private val assets: AssetManager) : ContentSource {
    override fun read(path: String): String =
        assets.open("$CONTENT_ROOT/$path").bufferedReader().use { it.readText() }

    companion object {
        const val CONTENT_ROOT = "content"
    }
}

/** Shared JSON settings. Unknown fields are ignored so older app versions survive newer content. */
val ContentJson = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "type" // picks the LessonBlock / ObjectiveCheck subclass
}

/** The result of loading: either a ready catalog or a list of human-readable problems. */
sealed interface ContentLoadResult {
    data class Success(val catalog: ContentCatalog) : ContentLoadResult
    data class Failure(val problems: List<String>) : ContentLoadResult
}

/**
 * Reads index.json, then every track file and the cheat sheet, then validates everything.
 * Plain (blocking) code on purpose: easy to unit test. ContentRepository runs it off the main thread.
 */
class ContentLoader(private val source: ContentSource) {

    fun load(): ContentLoadResult {
        val bundle = try {
            parse()
        } catch (e: Exception) {
            // Bad JSON, a missing file or a missing required field ends up here.
            return ContentLoadResult.Failure(listOf(e.message ?: e.toString()))
        }
        val issues = ContentValidator.validate(bundle)
        return if (issues.isEmpty()) {
            ContentLoadResult.Success(ContentCatalog(bundle))
        } else {
            ContentLoadResult.Failure(issues.map { it.toString() })
        }
    }

    /** Parses all files. Throws with the file name in the message if anything is wrong. */
    fun parse(): ContentBundle {
        val index = readJson<ContentIndex>(INDEX_FILE)
        val tracks = index.tracks.map { path -> readJson<Track>(path) }
        val cheatSheet = readJson<List<CheatEntry>>(index.cheatSheet)
        return ContentBundle(tracks, cheatSheet)
    }

    private inline fun <reified T> readJson(path: String): T {
        val text = try {
            source.read(path)
        } catch (e: Exception) {
            throw IllegalStateException("$path: file could not be read (${e.message})", e)
        }
        return try {
            ContentJson.decodeFromString<T>(text)
        } catch (e: Exception) {
            throw IllegalStateException("$path: ${e.message}", e)
        }
    }

    companion object {
        const val INDEX_FILE = "index.json"
    }
}
