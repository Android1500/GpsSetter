package com.android1500.gpssetter.repository

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.android1500.gpssetter.BuildConfig
import com.android1500.gpssetter.selfhook.XposedSelfHooks
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject


@SuppressLint("WorldReadableFiles")
@Reusable
class SettingsRepository  @Inject constructor(
    @ApplicationContext private val context: Context)  {

    companion object {
        private const val start = false
        private const val lat = 40.7128
        private const val lng = 74.0060
    }

     private val sharedPreferences: SharedPreferences by lazy {
         try {
             val prefsFile = "${BuildConfig.APPLICATION_ID}_prefs"
             context.getSharedPreferences(
                 prefsFile,
                 Context.MODE_WORLD_READABLE
             )
         }catch (e:SecurityException){
             val prefsFile = "${BuildConfig.APPLICATION_ID}_prefs"
             context.getSharedPreferences(
                 prefsFile,
                 Context.MODE_PRIVATE
             )
         }

    }
    val isStarted : Boolean
    get() = sharedPreferences.getBoolean("start", start)

    val getLat : Double
    get() = sharedPreferences.getFloat("latitude", lat.toFloat()).toDouble()

    val getLng : Double
    get() = sharedPreferences.getFloat("longitude", lng.toFloat()).toDouble()


    fun update(start:Boolean, la: Double, ln: Double) {
        runInBackground {
            val prefEditor = sharedPreferences.edit()
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