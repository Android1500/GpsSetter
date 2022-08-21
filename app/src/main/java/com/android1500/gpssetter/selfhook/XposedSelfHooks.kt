package com.android1500.gpssetter.selfhook

object XposedSelfHooks {

    fun isXposedModuleEnabled(): Boolean {
        return false
    }

    fun getXSharedPrefsPath(): String {
        return ""
    }

}