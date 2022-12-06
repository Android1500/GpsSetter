package com.android1500.gpssetter.xposed

import android.annotation.SuppressLint
import android.app.AndroidAppHelper
import android.app.PendingIntent
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import com.android1500.gpssetter.BuildConfig
import com.android1500.gpssetter.gsApp
import com.android1500.gpssetter.xposed.LocationHook.hook
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.loggerW
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.type.java.DoubleType
import com.highcapable.yukihookapi.hook.type.java.FloatType
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass
import timber.log.Timber
import java.util.*
import kotlin.math.cos

object LocationHook : YukiBaseHooker() {


    var newlat: Double = 40.7128
    var newlng: Double = 74.0060
    private const val pi = 3.14159265359
    private var accuracy : Float = 0.0f
    private val rand: Random = Random()
    private const val earth = 6378137.0
    private val settings = Xshare()
    var mLastUpdated: Long = 0
    private const val className = "android.location.Location"
    private val ignorePkg = arrayListOf("com.android.location.fused",BuildConfig.APPLICATION_ID)

    private val context by lazy { AndroidAppHelper.currentApplication() as Context }



    private fun updateLocation() {
        try {
            mLastUpdated = System.currentTimeMillis()
            val x = (rand.nextInt(50) - 15).toDouble()
            val y = (rand.nextInt(50) - 15).toDouble()
            val dlat = x / earth
            val dlng = y / (earth * cos(pi * settings.getLat / 180.0))
            newlat = if (settings.isRandomPosition) settings.getLat + (dlat * 180.0 / pi) else settings.getLat
            newlng = if (settings.isRandomPosition) settings.getLng + (dlng * 180.0 / pi) else settings.getLng
            accuracy = settings.accuracy!!.toFloat()

        }catch (e: Exception) {
            Timber.tag("GPS Setter").e(e, "Failed to get XposedSettings for %s", context.packageName)
        }

    }


    @SuppressLint("NewApi")
    override fun onHook() {

        loadSystem {
            if (settings.isStarted && (settings.isHookedSystem && !ignorePkg.contains(packageName))) {
                if (System.currentTimeMillis() - mLastUpdated > 200){
                    updateLocation()
                }

                findClass(  "com.android.server.LocationManagerService").hook {
                    injectMember {
                        method {
                            name = "getLastLocation"
                            param(
                                LocationRequest::class.java,
                                String::class.java
                            )
                        }
                        beforeHook {
                            val location = Location(LocationManager.GPS_PROVIDER)
                            location.time = System.currentTimeMillis() - 300
                            location.latitude = newlat
                            location.longitude = newlng
                            location.altitude = 0.0
                            location.speed = 0F
                            location.accuracy = accuracy
                            location.speedAccuracyMetersPerSecond = 0F
                            result = location
                        }
                    }

                    injectMember {
                        method {
                            name = "addGnssBatchingCallback"
                            returnType = BooleanType
                        }
                        replaceToFalse()
                    }
                    injectMember {
                        method {
                            name = "addGnssMeasurementsListener"
                            returnType = BooleanType
                        }
                        replaceToFalse()
                    }
                    injectMember {
                        method {
                            name = "addGnssNavigationMessageListener"
                            returnType = BooleanType
                        }
                        replaceToFalse()
                    }

                }
                findClass("com.android.server.LocationManagerService.Receiver").hook {
                    injectMember {
                        method {
                            name = "callLocationChangedLocked"
                            param(Location::class.java)
                        }
                        beforeHook {
                            lateinit var location: Location
                            lateinit var originLocation: Location
                            if (args[0] == null){
                                location = Location(LocationManager.GPS_PROVIDER)
                                location.time = System.currentTimeMillis() - 300
                            }else {
                                originLocation = args(0).any() as Location
                                location = Location(originLocation.provider)
                                location.time = originLocation.time
                                location.accuracy = accuracy
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
                                loggerW("LocationHook:- ","GS: Not possible to mock  $e")
                            }
                            args[0] = location


                        }
                    }
                }


            }
        }

        findClass(className).hook {
            injectMember {
                method {
                    name = "getLatitude"
                    returnType = DoubleType
                }
                beforeHook {
                    if (System.currentTimeMillis() - mLastUpdated > 200){
                        updateLocation()
                    }
                    if (settings.isStarted && !ignorePkg.contains(packageName)){
                        result = newlat
                    }
                }
            }

            injectMember {
                method {
                    name = "getLongitude"
                    returnType = DoubleType
                }
                beforeHook {
                    if (System.currentTimeMillis() - mLastUpdated > 200){
                        updateLocation()
                    }
                    if (settings.isStarted && !ignorePkg.contains(packageName)){
                        result = newlng
                    }
                }
            }

            injectMember {
                method {
                    name = "getAccuracy"
                    returnType = FloatType
                }
                beforeHook {
                    if (System.currentTimeMillis() - mLastUpdated > 200){
                        updateLocation()
                    }
                    if (settings.isStarted && !ignorePkg.contains(packageName)){
                        result = accuracy
                    }
                }
            }


            injectMember {
                method {
                    name = "set"
                    param(Location::class.java)
                }
                beforeHook {
                    if (System.currentTimeMillis() - mLastUpdated > 200){
                        updateLocation()
                    }
                    if (settings.isStarted && !ignorePkg.contains(packageName)){
                        lateinit var location: Location
                        lateinit var originLocation: Location
                        if (args[0] == null){
                            location = Location(LocationManager.GPS_PROVIDER)
                            location.time = System.currentTimeMillis() - 300
                        }else {
                            originLocation = args(0).any() as Location
                            location = Location(originLocation.provider)
                            location.time = originLocation.time
                            location.accuracy = accuracy
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
                            loggerW("LocationHook:- ","GS: Not possible to mock  $e")
                        }
                        args[0] = location

                    }

                }
            }

        }

        findClass("android.location.LocationManager").hook {
            injectMember {
                method {
                    name = "getLastKnownLocation"
                    param(String::class.java)
                }
                beforeHook {
                    if (System.currentTimeMillis() - mLastUpdated > 200){
                        updateLocation()
                    }
                    if (settings.isStarted && !ignorePkg.contains(packageName)) {
                        val provider = args[0] as String
                        val location = Location(provider)
                        location.time = System.currentTimeMillis() - 300
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
                        result = location


                    }

                }
            }
        }

    }

}