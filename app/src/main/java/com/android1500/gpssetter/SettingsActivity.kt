package com.android1500.gpssetter

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.datastore.core.DataStore
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import com.android1500.gpssetter.databinding.SettingsActivityBinding
import com.android1500.gpssetter.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.prefs.Preferences
import javax.inject.Inject

class SettingsActivity : AppCompatActivity() {
    private val binding by lazy {
        SettingsActivityBinding.inflate(layoutInflater)
    }

    class SettingPreferenceDataStore() : PreferenceDataStore() {
        override fun getBoolean(key: String?, defValue: Boolean): Boolean {
           return when(key) {
               "isHookedSystem" -> SettingsRepository.isHookSystem
               else -> throw IllegalArgumentException("Invalid key $key")
           }
        }

        override fun putBoolean(key: String?, value: Boolean) {
            return when(key) {
                "isHookedSystem" -> SettingsRepository.isHookSystem = value
                else -> throw IllegalArgumentException("Invalid key $key")
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager?.preferenceDataStore = SettingPreferenceDataStore()
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
        }
    }
}