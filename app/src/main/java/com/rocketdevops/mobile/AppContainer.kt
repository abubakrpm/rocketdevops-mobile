package com.rocketdevops.mobile

import android.content.Context
import com.rocketdevops.mobile.data.content.AssetContentSource
import com.rocketdevops.mobile.data.content.ContentLoadResult
import com.rocketdevops.mobile.data.content.ContentLoader
import com.rocketdevops.mobile.data.content.ContentRepository
import com.rocketdevops.mobile.data.prefs.UserPreferencesRepository
import com.rocketdevops.mobile.data.progress.LearningProgressStore
import com.rocketdevops.mobile.data.progress.ProgressRepository
import com.rocketdevops.mobile.data.progress.db.ProgressDatabase

/**
 * Manual dependency container (the spec allows this instead of Hilt).
 * Creates each shared object once. ViewModels get their dependencies from here.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val contentRepository: ContentRepository by lazy {
        ContentRepository(ContentLoader(AssetContentSource(appContext.assets)))
    }

    private val database: ProgressDatabase by lazy { ProgressDatabase.create(appContext) }

    /** Stage 6: Room-backed, survives closing the app, and drives every progress bar. */
    val progressRepository: ProgressRepository by lazy {
        ProgressRepository(
            dao = database.progressDao(),
            catalog = { (contentRepository.load() as? ContentLoadResult.Success)?.catalog },
        )
    }

    /** Lesson, quiz and lab screens only need the write side, so they keep using the interface. */
    val progressStore: LearningProgressStore get() = progressRepository

    val userPreferences: UserPreferencesRepository by lazy { UserPreferencesRepository(appContext) }
}
