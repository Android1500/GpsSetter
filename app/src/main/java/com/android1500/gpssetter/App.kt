package com.android1500.gpssetter

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.android1500.gpssetter.utils.PrefManager
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import rikka.material.app.DayNightDelegate
import timber.log.Timber

lateinit var gsApp: App


@HiltAndroidApp
class App : Application() {
    val globalScope = CoroutineScope(Dispatchers.Default)

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