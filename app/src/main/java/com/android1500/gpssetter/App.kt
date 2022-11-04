package com.android1500.gpssetter

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.android1500.gpssetter.utils.PrefManager
import dagger.hilt.android.HiltAndroidApp
import rikka.material.app.DayNightDelegate
import timber.log.Timber

lateinit var gsApp: App


@HiltAndroidApp
class App : Application() {

    companion object {
        fun commonInit() {
            if (BuildConfig.DEBUG) {
                Timber.plant(Timber.DebugTree())
            }
        }

    }


    override fun onCreate() {
        super.onCreate()
        gsApp = this
        commonInit()
        AppCompatDelegate.setDefaultNightMode(PrefManager.darkTheme)

    }


}