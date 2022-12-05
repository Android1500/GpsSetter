package com.android1500.gpssetter

import androidx.appcompat.app.AppCompatDelegate
import com.android1500.gpssetter.utils.PrefManager
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication
import com.kieronquinn.monetcompat.core.MonetCompat
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import timber.log.Timber

lateinit var gsApp: App


@HiltAndroidApp
class App : ModuleApplication() {
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
        MonetCompat.enablePaletteCompat()
        AppCompatDelegate.setDefaultNightMode(PrefManager.darkTheme)

    }


}