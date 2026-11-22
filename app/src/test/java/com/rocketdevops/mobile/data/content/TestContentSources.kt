package com.rocketdevops.mobile.data.content

import java.io.File

/** Reads content files from a folder on disk (unit tests have no Android AssetManager). */
class FileContentSource(private val root: File) : ContentSource {
    override fun read(path: String): String = File(root, path).readText()
}

/** Serves files from a map, for small hand-written test cases. */
class MapContentSource(private val files: Map<String, String>) : ContentSource {
    override fun read(path: String): String = files[path] ?: throw java.io.FileNotFoundException(path)
}

/**
 * The real content folder. Gradle runs unit tests from the app/ folder;
 * the second option covers IDEs that run them from the project root.
 */
fun realContentRoot(): File =
    listOf(File("src/main/assets/content"), File("app/src/main/assets/content"))
        .firstOrNull { it.isDirectory }
        ?: error("Content folder not found. Working directory: ${File(".").absolutePath}")
