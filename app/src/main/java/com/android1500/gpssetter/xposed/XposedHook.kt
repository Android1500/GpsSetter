package com.android1500.gpssetter.xposed

import android.app.AndroidAppHelper
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.android1500.gpssetter.BuildConfig
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.lsposed.hiddenapibypass.HiddenApiBypass
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.cos


class XposedHook : IXposedHookLoadPackage {

    companion object {
        const val pi = 3.14159265359
        var newlat: Double = 40.7128
        var newlng: Double = 74.0060
        private val rand = Random()
        private const val earth = 6378137.0
        private val settings = Xshare()
        var mLastUpdated: Long = 0
        private const val SHARED_PREFS_FILENAME = "${BuildConfig.APPLICATION_ID}_prefs"
    }



    private val context by lazy { AndroidAppHelper.currentApplication() as Context }
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {

        if(lpparam?.packageName == BuildConfig.APPLICATION_ID){ setupSelfHooks(lpparam.classLoader) }

        XposedHelpers.findAndHookMethod(Location::class.java,"getLatitude", object : XC_MethodHook(){
            override fun beforeHookedMethod(param: MethodHookParam?) {
                super.beforeHookedMethod(param)
                if (System.currentTimeMillis() - mLastUpdated > 200){ updateLocation() }
                if (settings.isStarted){
                    param?.result = newlat

                }


            }
        })

        XposedHelpers.findAndHookMethod(Location::class.java,"getLongitude", object : XC_MethodHook(){
            override fun beforeHookedMethod(param: MethodHookParam?) {
                super.beforeHookedMethod(param)
                if (System.currentTimeMillis() - mLastUpdated > 200){
                    updateLocation()
                }
                if (settings.isStarted){
                    param?.result = newlng
                }


            }
        })



        XposedHelpers.findAndHookMethod(Location::class.java, "set",
            Location::class.java, object : XC_MethodHook() {
                @RequiresApi(Build.VERSION_CODES.P)
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (System.currentTimeMillis() - mLastUpdated > 200){
                        updateLocation()
                    }
                    if (settings.isStarted){
                        lateinit var location: Location
                        lateinit var originLocation: Location

                        if (param.args[0] == null){
                            location = Location(LocationManager.GPS_PROVIDER)
                            location.time = System.currentTimeMillis() - (100..10000).random()
                        }else{
                            originLocation = param.args[0] as Location
                            location = Location(originLocation.provider)
                            location.time = originLocation.time
                            location.accuracy = originLocation.accuracy
                            location.bearing = originLocation.bearing
                            location.bearingAccuracyDegrees = originLocation.bearingAccuracyDegrees
                            location.elapsedRealtimeNanos = originLocation.elapsedRealtimeNanos
                            location.verticalAccuracyMeters = originLocation.verticalAccuracyMeters
                        }
                        location.latitude = newlat
                        location.longitude = newlng
                        location.altitude = 0.0
                        location.speed = 0F
                        location.speedAccuracyMetersPerSecond = 0F
                        XposedBridge.log("GS: lat: ${location.latitude}, lon: ${location.longitude}")
                        try {
                            HiddenApiBypass.invoke(location.javaClass, location, "setIsFromMockProvider", false)
                        } catch (e: Exception) {
                            XposedBridge.log("GS: Not possible to mock (Pre Q)! $e")
                        }
                        param.args[0] = location

                    }
                }

            })


    }

    private fun setupSelfHooks(classLoader: ClassLoader){
        XposedHelpers.findAndHookMethod("com.android1500.gpssetter.selfhook.XposedSelfHooks", classLoader, "isXposedModuleEnabled", object: XC_MethodReplacement(){
            override fun replaceHookedMethod(param: MethodHookParam): Any {
                param.result = true
                return true
            }
        })
        XposedHelpers.findAndHookMethod("com.android1500.gpssetter.selfhook.XposedSelfHooks", classLoader, "getXSharedPrefsPath", object: XC_MethodReplacement(){
            override fun replaceHookedMethod(param: MethodHookParam): Any {
                val path = XSharedPreferences(BuildConfig.APPLICATION_ID, SHARED_PREFS_FILENAME).file.absolutePath
                param.result = path
                return path
            }
        })
    }

    private fun updateLocation() {
        try {
            mLastUpdated = System.currentTimeMillis()
            val x = (rand.nextInt(50) - 25).toDouble()
            val y = (rand.nextInt(50) - 25).toDouble()
            val dlat = x / earth
            val dlng = y / (earth * cos(pi * settings.getLat / 180.0))
            newlat = settings.getLat + dlat * 180.0 / pi
            newlng = settings.getLng + dlng * 180.0 / pi
        }catch (e: Exception) {
            Timber.tag("GPS Setter").e(e, "Failed to get XposedSettings for %s", context.packageName)
        }

    }







}