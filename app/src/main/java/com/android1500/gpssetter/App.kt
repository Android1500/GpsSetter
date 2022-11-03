package com.android1500.gpssetter

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

lateinit var gsApp: App


@HiltAndroidApp
class App : Application() {



    override fun onCreate() {
        super.onCreate()
        gsApp = this
        commonInit()

    }

    companion object {
        fun commonInit() {
            if (BuildConfig.DEBUG) {
                Timber.plant(Timber.DebugTree())
            }
        }

    }
}