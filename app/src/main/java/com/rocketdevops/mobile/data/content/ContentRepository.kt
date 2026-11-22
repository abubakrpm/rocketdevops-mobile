package com.rocketdevops.mobile.data.content

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The single place screens get content from.
 * Loads the JSON once (on a background thread, so the UI never freezes) and keeps it in memory.
 * If loading fails, nothing is cached, so pressing Retry tries again.
 */
class ContentRepository(
    private val loader: ContentLoader,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex() // two screens asking at once still load the files only once
    private var cached: ContentCatalog? = null

    suspend fun load(): ContentLoadResult = mutex.withLock {
        val existing = cached
        if (existing != null) {
            ContentLoadResult.Success(existing)
        } else {
            val result = withContext(ioDispatcher) { loader.load() }
            if (result is ContentLoadResult.Success) cached = result.catalog
            result
        }
    }
}
