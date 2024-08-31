package com.mona.adel.hydrateme

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.mona.adel.hydrateme.databinding.ActivitySettingsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    private val dataStore by lazy {
        (application as MyApp).dataStore
    }

    private val TAG = "SettingsActivity"
    private lateinit var binding: ActivitySettingsBinding

    private lateinit var sharedPreferences: SharedPreferences
    private val IS_DARK_THEME_KEY = "dark_theme"
    private var isDarkTheme = false

    val WATERGOALKEY = intPreferencesKey("water_goal")

    private var waterGoal = 3200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load saved theme preference
        sharedPreferences = getSharedPreferences("AppModes", MODE_PRIVATE)
        isDarkTheme = sharedPreferences.getBoolean(IS_DARK_THEME_KEY, false)
        binding.switchModeBtn.isChecked = isDarkTheme


        if (isDarkTheme) {
            binding.switchModeBtn.isChecked = true
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            Log.d(TAG, "onCreate: it is a dark mode")
        } else {
            binding.switchModeBtn.isChecked = false
            Log.d(TAG, "onCreate: it is a light mode")
        }


        binding.switchModeBtn.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                binding.switchModeBtn.isChecked = true
                sharedPreferences.edit().putBoolean(IS_DARK_THEME_KEY, true).apply()
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                Log.d(TAG, "onCreate: it is a dark mode00")
            } else {
                binding.switchModeBtn.isChecked = false
                sharedPreferences.edit().putBoolean(IS_DARK_THEME_KEY, false).apply()
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                Log.d(TAG, "onCreate: it is a light mode00")
            }
        }


        binding.btnOkEditTarget.setOnClickListener {
            val newTarget = binding.etTargetEdit.editText?.text.toString()

            if (!newTarget.isNullOrEmpty()) {
                waterGoal = newTarget.toInt()
                launch {
                    saveWaterGoalToDataStore()
                }

                binding.etTargetEdit.isEnabled = false

            } else {
                binding.etTargetEdit.error = "Please enter a value for your target."
            }
        }
        binding.tvSettings.setOnClickListener {
            goToNotificationSettings()

        }

    }

    private fun goToNotificationSettings() {
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, MyApp.NOTIFICATION_CHANNEl_ID)
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening settings", e)
        }
    }

    private suspend fun saveWaterGoalToDataStore() {
        dataStore.edit { preferences ->
            preferences[WATERGOALKEY] = waterGoal
            Log.d(TAG, "saveDataToDataStore: waterGoal -> ${preferences[WATERGOALKEY]} ")
        }
    }

}