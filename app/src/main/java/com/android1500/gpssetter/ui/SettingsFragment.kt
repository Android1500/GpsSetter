package com.android1500.gpssetter.ui

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import by.kirich1409.viewbindingdelegate.viewBinding
import com.android1500.gpssetter.R
import com.android1500.gpssetter.databinding.FragmentSettingBinding
import com.android1500.gpssetter.utils.PrefManager
import com.android1500.gpssetter.utils.ext.navController
import com.android1500.gpssetter.utils.ext.setupToolbar
import rikka.preference.SimpleMenuPreference

class SettingsFragment : Fragment(R.layout.fragment_setting) {

    private val binding by viewBinding<FragmentSettingBinding>()



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar(
            toolbar = binding.toolbar,
            title = getString(R.string.settings),
            navigationIcon = R.drawable.ic_back_arrow,
            navigationOnClick = { }
        )

        if (childFragmentManager.findFragmentById(R.id.settings_container) == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsPreferenceFragment())
                .commit()
        }
    }


    class SettingPreferenceDataStore() : PreferenceDataStore() {
        override fun getBoolean(key: String?, defValue: Boolean): Boolean {
            return when(key) {
                "isHookedSystem" -> PrefManager.isHookSystem
                "random_position" -> PrefManager.isRandomPosition
                "disable_update" -> PrefManager.disableUpdate
                else -> throw IllegalArgumentException("Invalid key $key")
            }
        }

        override fun putBoolean(key: String?, value: Boolean) {
            return when(key) {
                "isHookedSystem" -> PrefManager.isHookSystem = value
                "random_position" -> PrefManager.isRandomPosition = value
                "disable_update" -> PrefManager.disableUpdate = value
                else -> throw IllegalArgumentException("Invalid key $key")
            }
        }

        override fun getString(key: String?, defValue: String?): String? {
            return when(key){
                "accuracy_settings" -> PrefManager.accuracy
                "map_type" -> PrefManager.mapType.toString()
                "darkTheme" -> PrefManager.darkTheme.toString()
                else -> throw IllegalArgumentException("Invalid key $key")
            }
        }

        override fun putString(key: String?, value: String?) {
            return when(key){
                "accuracy_settings" -> PrefManager.accuracy = value
                "map_type" -> PrefManager.mapType = value!!.toInt()
                "darkTheme" -> PrefManager.darkTheme = value!!.toInt()
                else -> throw IllegalArgumentException("Invalid key $key")
            }
        }


    }

    class SettingsPreferenceFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager?.preferenceDataStore = SettingPreferenceDataStore()
            setPreferencesFromResource(R.xml.setting, rootKey)

            findPreference<EditTextPreference>("accuracy_settings")?.let {
                it.summary = "${PrefManager.accuracy} m."
                it.setOnBindEditTextListener { editText ->
                    editText.inputType = InputType.TYPE_CLASS_NUMBER;
                    editText.keyListener = DigitsKeyListener.getInstance("0123456789.,");
                    editText.addTextChangedListener(getCommaReplacerTextWatcher(editText));
                }

                it.setOnPreferenceChangeListener { preference, newValue ->
                    try {
                        newValue as String?
                        preference.summary = "$newValue  m."
                    }catch (n : NumberFormatException){
                        n.printStackTrace()
                        Toast.makeText(requireContext(), getString(R.string.enter_valid_input), Toast.LENGTH_SHORT).show()
                        false
                    }
                    true
                }
            }


            findPreference<SimpleMenuPreference>("darkTheme")?.setOnPreferenceChangeListener { _, newValue ->
                val newMode = (newValue as String).toInt()
                if (PrefManager.darkTheme != newMode) {
                    AppCompatDelegate.setDefaultNightMode(newMode)
                    activity?.recreate()
                }
                true
            }
            findPreference<SimpleMenuPreference>("map_type")?.setOnPreferenceChangeListener { _, _ ->
                activity?.recreate()
                true
            }


        }





        private fun getCommaReplacerTextWatcher(editText: EditText): TextWatcher {
            return object : TextWatcher {
                override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
                override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
                override fun afterTextChanged(editable: Editable) {
                    val text = editable.toString()
                    if (text.contains(",")) {
                        editText.setText(text.replace(",", "."))
                        editText.setSelection(editText.text.length)
                    }
                }
            }
        }
    }

}