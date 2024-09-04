package com.mona.adel.hydrateme

import android.Manifest
import android.app.AlarmManager
import android.app.Dialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.airbnb.lottie.LottieAnimationView
import com.mona.adel.hydrateme.databinding.ActivityMainBinding
import com.mona.adel.hydrateme.databinding.CupOptionsDialogBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    private val dataStore by lazy {
        (application as MyApp).dataStore
    }
    private lateinit var binding: ActivityMainBinding

    private lateinit var activityLauncher: ActivityResultLauncher<String>

    private val TAG = "MainActivity"

    private lateinit var cupOptionsDialog: Dialog

    private var waterIntake = 0
    private var waterGoal = 3200
    private var isNotified = false
    private var drinkCupSize = 250
    private var drinkCupSizeIcon = R.drawable.medium_cup

    val WATERINTAKEKEY = intPreferencesKey("water_intake")
    val WATERGOALKEY = intPreferencesKey("water_goal")
    val ISNOTIFIEDKEY = booleanPreferencesKey("apply_notification")

    private lateinit var sharedPreferences: SharedPreferences
    private val IS_DARK_THEME_KEY = "dark_theme"
    private var isDarkTheme = false
    private val LAST_LOGGED_DATE = "last_logged_date"

    private val DRINKCUPSIZE = "drink_cup_size"
    private val DRINKCUPSIZEICON = "drink_cup_icon"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        // Load saved theme preference
        sharedPreferences = getSharedPreferences("AppModes", MODE_PRIVATE)
        isDarkTheme = sharedPreferences.getBoolean(IS_DARK_THEME_KEY, false)
        drinkCupSize = sharedPreferences.getInt(DRINKCUPSIZE, 250)
        drinkCupSizeIcon = sharedPreferences.getInt(DRINKCUPSIZEICON, R.drawable.medium_cup)

        sharedPreferences.edit().putString(LAST_LOGGED_DATE, getCurrentDate()).apply()

        setCupSizeOnButton()

        if (isDarkTheme) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            Log.d(TAG, "onCreate: it is a dark mode")
        } else {
            Log.d(TAG, "onCreate: it is a light mode")
        }

        cupOptionsDialog = Dialog(this)

        // load Data from dataStore
        loadDataFromDataStore()

        // Register the ActivityResultLauncher in onCreate
        activityLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    binding.btnNotify.isChecked = true
                    isNotified = true
                    scheduleHourlyReminder()
                } else {
                    binding.btnNotify.isChecked = false
                    isNotified = false
                    cancelHourlyReminder()
                    Toast.makeText(
                        this,
                        "You can't receive reminders unless you allow the app to send notifications.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                launch {
                    saveNotificationState()
                }
            }



        binding.btnAddWater.setOnClickListener {
            val size = extractNumberFromText(binding.btnCupSize.text.toString())
            Log.d("TAG", "onCreate: cup size is ${size}")
            waterIntake += size!!
            updateProgress()
            checkAndAnimateGoal()
            launch {
                saveDataToDataStore()
            }

        }

        binding.btnRemoveWater.setOnClickListener {
            val size = extractNumberFromText(binding.btnCupSize.text.toString())
            waterIntake -= size!!
            updateProgress()
            launch {
                saveDataToDataStore()
            }
        }

        binding.btnNotify.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                when {
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED -> {

                        Log.d(TAG, "onCreate: the permission is on")
                        scheduleHourlyReminder()
                        isNotified = true
                    }

                    else -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            activityLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            Log.d(TAG, "onCreate: the request is sent to the user")
                            isNotified = true
                        }
                    }
                }
            } else {
                Log.d(TAG, "onCreate: the check button is not checked.")
                cancelHourlyReminder()
                isNotified = false
            }

            launch {
                saveNotificationState()
            }
        }

        binding.btnNotify.setOnClickListener {
            if (binding.btnNotify.isChecked) {
                Toast.makeText(this, "You will receive an hourly reminder.", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))

        }

        binding.btnCupSize.setOnClickListener {
            openCupSizesOptionDialog()
            cupOptionsDialog.show()
        }

    }

    private fun openCupSizesOptionDialog() {
        val dialogViewBinding = CupOptionsDialogBinding.inflate(layoutInflater)

        // Set the dialog view and properties
        cupOptionsDialog.setContentView(dialogViewBinding.root)
        cupOptionsDialog.setCancelable(true)
        cupOptionsDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))


        dialogViewBinding.tvLargeCup.setOnClickListener {
            drinkCupSize = 350
            drinkCupSizeIcon = R.drawable.large_cup
            saveCupSizeToSharedPreference()

            binding.btnCupSize.text = drinkCupSize.toString() + " ml"
            binding.btnCupSize.icon = getDrawable(R.drawable.large_cup)
            cupOptionsDialog.dismiss()
        }

        dialogViewBinding.tvMediumCup.setOnClickListener {
            drinkCupSize = 250
            drinkCupSizeIcon = R.drawable.medium_cup
            saveCupSizeToSharedPreference()

            binding.btnCupSize.text = drinkCupSize.toString() + "ml"
            binding.btnCupSize.icon = getDrawable(R.drawable.medium_cup)
            cupOptionsDialog.dismiss()
        }

        dialogViewBinding.tvSmallCup.setOnClickListener {
            drinkCupSize = 150
            drinkCupSizeIcon = R.drawable.small_cup
            saveCupSizeToSharedPreference()

            binding.btnCupSize.text = drinkCupSize.toString() + " ml"
            binding.btnCupSize.icon = getDrawable(R.drawable.small_cup)
            cupOptionsDialog.dismiss()
        }

        dialogViewBinding.imgLargeCup.setOnClickListener {
            drinkCupSize = 350
            drinkCupSizeIcon = R.drawable.large_cup
            saveCupSizeToSharedPreference()

            binding.btnCupSize.text = drinkCupSize.toString() + " ml"
            binding.btnCupSize.icon = getDrawable(R.drawable.large_cup)
            cupOptionsDialog.dismiss()
        }

        dialogViewBinding.imgMediumCup.setOnClickListener {
            drinkCupSize = 250
            drinkCupSizeIcon = R.drawable.medium_cup
            saveCupSizeToSharedPreference()

            binding.btnCupSize.text = drinkCupSize.toString() + " ml"
            binding.btnCupSize.icon = getDrawable(R.drawable.medium_cup)
            cupOptionsDialog.dismiss()
        }

        dialogViewBinding.imgSmallCup.setOnClickListener {
            drinkCupSize = 150
            drinkCupSizeIcon = R.drawable.small_cup
            saveCupSizeToSharedPreference()

            binding.btnCupSize.text = drinkCupSize.toString() + " ml"
            binding.btnCupSize.icon = getDrawable(R.drawable.small_cup)
            cupOptionsDialog.dismiss()
        }
    }


    private fun saveCupSizeToSharedPreference() {
        sharedPreferences.edit().putInt(DRINKCUPSIZE, drinkCupSize).apply()
        sharedPreferences.edit().putInt(DRINKCUPSIZEICON, drinkCupSizeIcon).apply()
    }

    private fun setCupSizeOnButton() {
        binding.btnCupSize.text = drinkCupSize.toString() + " ml"

        try {
            binding.btnCupSize.icon = getDrawable(drinkCupSizeIcon)
        } catch (e: Exception) {
            Log.d(TAG, "setCupSizeOnButton: icon id is not found")
        }
    }


    private fun extractNumberFromText(buttonText: String): Int? {
        val regex = Regex("\\d+")

        val match = regex.find(buttonText)
        return match?.value?.toInt()
    }

    private suspend fun saveDataToDataStore() {
        dataStore.edit { preferences ->
            preferences[WATERINTAKEKEY] = waterIntake
        }
    }


    private fun loadDataFromDataStore() {
        val waterIntakeFlow: Flow<Int> = dataStore.data
            .map { preferences ->
                preferences[WATERINTAKEKEY] ?: 0
            }

        val waterGoalFlow: Flow<Int> = dataStore.data
            .map { preferences ->
                preferences[WATERGOALKEY] ?: 3200
            }

        val isNotifiedFlow: Flow<Boolean> = dataStore.data
            .map { preferences ->
                preferences[ISNOTIFIEDKEY] ?: false
            }


        launch {
            waterIntakeFlow.collect { value ->
                waterIntake = value
                updateProgress()
                Log.d(TAG, "Collected value for water intake: $waterIntake")
            }

        }
        launch {
            waterGoalFlow.collect { value ->
                waterGoal = value
                updateProgress()
                Log.d(TAG, "Collected value for gaol: $waterGoal")
            }
        }
        launch {
            isNotifiedFlow.collect { value ->
                isNotified = value
                updateNotificationCheck()
                Log.d(TAG, "Collected value for notification: $isNotified")
            }
        }

        binding.btnNotify.isChecked = isNotified
        updateProgress()
    }


    private fun updateProgress() {
        val progress = (waterIntake * 100) / waterGoal
        binding.progressBar.progress = progress.toFloat()
        Log.d(TAG, "updateProgress: ${progress}")
        binding.tvWaterIntake.text = "$waterIntake / ${waterGoal} ml"
    }

    private fun updateNotificationCheck() {
        binding.btnNotify.isChecked = isNotified
    }

    private suspend fun saveNotificationState() {
        dataStore.edit { preferences ->
            preferences[ISNOTIFIEDKEY] = isNotified
            Log.d(TAG, "saveDataToDataStore: \n isNotified -> ${preferences[ISNOTIFIEDKEY]}")
        }
    }

    private fun scheduleHourlyReminder() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Set the alarm to start at the next hour
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.HOUR_OF_DAY, 1)

        }

        // Schedule the alarm to repeat every hour
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_HOUR,
            pendingIntent
        )
    }

    private fun cancelHourlyReminder() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel the alarm using the same PendingIntent
        alarmManager.cancel(pendingIntent)

        // Optionally, you can also cancel the pending intent itself if you won't use it anymore
        pendingIntent.cancel()
    }


    private fun checkAndAnimateGoal() {
        if (waterIntake >= waterGoal) {
            val animationView = findViewById<LottieAnimationView>(R.id.animationView)

            // Play animation
            animationView.playAnimation()

            // Show a message to the user
            Toast.makeText(this, "Goal reached! Great job!", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun resetTotal() {
        Log.d("TAG", "resetTotal: the value returned from the checkIsToday is ${checkIsToday()}")
        if (!checkIsToday()) {
            Log.d("TAG", "resetTotal: the value is not false")
            dataStore.edit { preferences ->
                preferences[WATERINTAKEKEY] = 0
            }
        }


    }

    private fun checkIsToday(): Boolean {
        val currentDate = getCurrentDate()
        Log.d("TAG", "checkIsToday: current date is $currentDate")
        // get the last logged date
        val lastDate = sharedPreferences.getString(LAST_LOGGED_DATE, "")
        Log.d("TAG", "checkIsToday: last logged date from shared preferences is $lastDate")

        // compare between two dates
        if (currentDate != lastDate) {
            return false
        } else {
            return true
        }

    }

    private fun getCurrentDate(): String? {
        val current = LocalDateTime.now()

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val formatted = current.format(formatter)

        return formatted
    }


    override fun onResume() {
        super.onResume()
        launch {
            resetTotal()
        }
    }

}

