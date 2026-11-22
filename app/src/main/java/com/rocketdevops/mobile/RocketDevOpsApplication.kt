package com.rocketdevops.mobile

import android.app.Application

/**
 * Created by Android before any screen. Holds the AppContainer so every
 * ViewModel shares the same repositories. Registered in AndroidManifest.xml (android:name).
 */
class RocketDevOpsApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
