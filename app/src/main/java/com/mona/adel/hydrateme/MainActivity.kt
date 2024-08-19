package com.mona.adel.hydrateme

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Dialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.widget.Button
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputLayout
import com.mona.adel.hydrateme.databinding.ActivityMainBinding
import java.util.Calendar
import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() , CoroutineScope by MainScope(){

    private val sharedPreferencesKey = "water_tracker"
    val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name =sharedPreferencesKey)
    private lateinit var binding: ActivityMainBinding
    private val TAG = "MainActivity"

    private lateinit var editDialog: Dialog
    private lateinit var btnOkDialog: Button
    private lateinit var btnCancelDialog: Button
    private lateinit var etTargetDialog: TextInputLayout

    private var waterIntake = 0
    private var waterGoal = 3200
    private var isNotified = false

    val WATERINTAKEKEY = intPreferencesKey("water_intake")
    val WATERGOALKEY = intPreferencesKey("water_goal")
    val ISNOTIFIEDKEY = booleanPreferencesKey("apply_notification")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // load Data from dataStore
        loadDataFromDataStore()

        val activityLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()){ isGranted->
            if ((isGranted)) {
                binding.btnNotify.isChecked = true
                isNotified = true
//                Toast.makeText(this, "the permission is allowed", Toast.LENGTH_SHORT).show()
                scheduleHourlyReminder()
            }else{
                binding.btnNotify.isChecked = false
                isNotified = false
                cancelHourlyReminder()
                Toast.makeText(this, "You can't receive reminders unless you allow the app to send notifications.", Toast.LENGTH_SHORT).show()
            }
            launch {
                saveNotificationState()
            }

        }

        scheduleDailyReset()

        editDialog = Dialog(this)


        binding.btnAddWater.setOnClickListener {
            waterIntake += 250 // Assuming a 250ml cup
            updateProgress()
            launch {
                saveDataToDataStore()
            }
            
        }

        binding.btnRemoveWater.setOnClickListener {
            waterIntake -= 250
            updateProgress()
            launch {
                saveDataToDataStore()
            }
        }

        binding.btnNotify.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked){
                when{
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED ->{

                        Toast.makeText(this, "The reminders will be sent per hour.", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "onCreate: the permission is on")
                        scheduleHourlyReminder()
                        isNotified = true
                    }

                    else ->{
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            activityLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            Log.d(TAG, "onCreate: the request is sent to the user")
                            isNotified = true
                        }
                    }
                }
            }
            else{
                Log.d(TAG, "onCreate: the check button is not checked.")
                cancelHourlyReminder()
                isNotified = false
            }

            launch {
                saveNotificationState()
            }
        }

        binding.btnEditTarget.setOnClickListener {
            createDialog()
            showDialog()
        }
        
    }

    private suspend fun saveDataToDataStore() {
        dataStore.edit { preferences ->
            preferences[WATERINTAKEKEY] = waterIntake
            preferences[WATERGOALKEY] = waterGoal
            preferences[ISNOTIFIEDKEY] = isNotified
            Log.d(TAG, "saveDataToDataStore: waterIntake-> ${ preferences[WATERINTAKEKEY]} \n waterGoal -> ${preferences[WATERGOALKEY]} \n isNotified -> ${ preferences[ISNOTIFIEDKEY]}")
        }
    }

    private fun createDialog() {
        val dialogView = layoutInflater.inflate(R.layout.edit_dialog, null)

        // Initialize the dialog's views
        btnOkDialog = dialogView.findViewById(R.id.btn_ok_edit_target)
        btnCancelDialog = dialogView.findViewById(R.id.btn_cancel_edit_target)
        etTargetDialog = dialogView.findViewById(R.id.et_target_edit)

        // Set the dialog view and properties
        editDialog.setContentView(dialogView)
        editDialog.setCancelable(false)
        editDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Set up listeners for dialog buttons
        btnOkDialog.setOnClickListener {
            val newTarget = etTargetDialog.editText?.text.toString()

            if (!newTarget.isNullOrEmpty()) {
                waterGoal = newTarget.toInt()
                updateProgress()
                launch {
                    saveDataToDataStore()
                }

                Log.d(TAG, "createDialog: the max progress is ${ binding.progressBar.progressMax}")
                dismissDialog()
            } else {
                etTargetDialog.error = "Please enter a value for your target."
            }
        }

        btnCancelDialog.setOnClickListener {
            dismissDialog()
        }
    }

    private fun loadDataFromDataStore(){
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

    private fun showDialog(){
        editDialog.show()
    }


    private fun dismissDialog(){
        editDialog.dismiss()
    }

    private fun updateProgress() {
        val progress = (waterIntake * 100) / waterGoal
        binding.progressBar.progress = progress.toFloat()
        Log.d(TAG, "updateProgress: ${progress}")
        binding.tvWaterIntake.text = "$waterIntake / ${waterGoal} ml"
    }
    private fun updateNotificationCheck(){
        binding.btnNotify.isChecked = isNotified
    }

    private suspend fun saveNotificationState(){
        dataStore.edit { preferences ->
            preferences[ISNOTIFIEDKEY] = isNotified
            Log.d(TAG, "saveDataToDataStore: \n isNotified -> ${ preferences[ISNOTIFIEDKEY]}")
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
//            set(Calendar.MINUTE, 0)
//            set(Calendar.SECOND, 0)
//            set(Calendar.MILLISECOND, 0)
//            add(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, 1)

        }

        // Schedule the alarm to repeat every hour
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_HOUR,
            pendingIntent
        )
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            60 * 1000, // 1 minute interval
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

    private fun scheduleDailyReset() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ResetReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Set the alarm to trigger at midnight every day
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, 1)
        }

        // Schedule the alarm to repeat daily
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }




}

