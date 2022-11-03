package com.android1500.gpssetter.repository

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.android1500.gpssetter.BuildConfig
import com.android1500.gpssetter.gsApp
import com.android1500.gpssetter.selfhook.XposedSelfHooks
import dagger.Reusable
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File


@SuppressLint("WorldReadableFiles")
@Reusable
object SettingsRepository   {

    private const val start = false
    private const val lat = 40.7128
    private const val lng = 74.0060
    private const val isHookedSystem = "isHookedSystem"
    private const val isRndPosition = "random_position"

     private val pref: SharedPreferences by lazy {
         try {
             val prefsFile = "${BuildConfig.APPLICATION_ID}_prefs"
             gsApp.getSharedPreferences(
                 prefsFile,
                 Context.MODE_WORLD_READABLE
             )
         }catch (e:SecurityException){
             val prefsFile = "${BuildConfig.APPLICATION_ID}_prefs"
             gsApp.getSharedPreferences(
                 prefsFile,
                 Context.MODE_PRIVATE
             )
         }

    }


    val isStarted : Boolean
    get() = pref.getBoolean("start", start)

    val getLat : Double
    get() = pref.getFloat("latitude", lat.toFloat()).toDouble()

    val getLng : Double
    get() = pref.getFloat("longitude", lng.toFloat()).toDouble()

    var isHookSystem : Boolean
    get() = pref.getBoolean( isHookedSystem, false)
    set(value) { pref.edit().putBoolean(isHookedSystem,value).apply() }

    var isRandomPosition :Boolean
    get() = pref.getBoolean(isRndPosition, false)
    set(value) { pref.edit().putBoolean(isRndPosition, value).apply() }



    fun update(start:Boolean, la: Double, ln: Double) {
        runInBackground {
            val prefEditor = pref.edit()
            prefEditor.putFloat("latitude", la.toFloat())
            prefEditor.putFloat("longitude", ln.toFloat())
            prefEditor.putBoolean("start", start)
            prefEditor.apply()
            makeWorldReadable()
        }

    }


    /**
     *  Make the redirected prefs file world readable ourselves - fixes a bug in Ed/lsposed
     *
     *  This requires the XSharedPreferences file path, which we get via a self hook. It does nothing
     *  when the Xposed module is not enabled.
     */
    @SuppressLint("SetWorldReadable")
    private fun makeWorldReadable(){
        XposedSelfHooks.getXSharedPrefsPath().let {
            if(it.isNotEmpty()){
                File(it).setReadable(true, false)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun runInBackground(method: suspend () -> Unit){
        GlobalScope.launch(Dispatchers.IO) {
            method.invoke()
        }
    }





}