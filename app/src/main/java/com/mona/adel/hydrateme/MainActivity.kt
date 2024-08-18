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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val TAG = "MainActivity"

    private lateinit var editDialog: Dialog
    private lateinit var btnOkDialog: Button
    private lateinit var btnCancelDialog: Button
    private lateinit var etTargetDialog: TextInputLayout

    private val sharedPreferencesKey = "water_tracker"
    private val waterIntakeKey = "water_intake"
    private val waterGoalKey = "water_goal"
    private val isNotifiedKey = "apply_notification"

    private var waterIntake = 0
    private var waterGoal = 3200
    private var isNotified = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        val activityLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()){ isGranted->
            if ((isGranted)) {
                binding.btnNotify.isChecked = true
                Toast.makeText(this, "the permission is allowed", Toast.LENGTH_SHORT).show()
                scheduleHourlyReminder()
            }else{
                binding.btnNotify.isChecked = false
                cancelHourlyReminder()
                Toast.makeText(this, "You can't receive reminders unless you allow the app to send notifications.", Toast.LENGTH_SHORT).show()
            }

        }

        scheduleDailyReset()

        editDialog = Dialog(this)
        
        // Load data from SharedPreferences
        val sharedPreferences = getSharedPreferences(sharedPreferencesKey, Context.MODE_PRIVATE)
        waterIntake = sharedPreferences.getInt(waterIntakeKey, 0)
        waterGoal = sharedPreferences.getInt(waterGoalKey, 3200)

        isNotified = sharedPreferences.getBoolean(isNotifiedKey, false)
        binding.btnNotify.isChecked = isNotified

        updateProgress()

        binding.btnAddWater.setOnClickListener {
            waterIntake += 250 // Assuming a 250ml cup
            updateProgress()
            saveData()
        }

        binding.btnRemoveWater.setOnClickListener {
            waterIntake -= 250
            updateProgress()
            saveData()
        }

        binding.btnNotify.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked){
                when{
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED ->{

                        Toast.makeText(this, "The reminders will be sent.", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "onCreate: the permission is on")
                        scheduleHourlyReminder()
                    }

                    else ->{
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            activityLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            Log.d(TAG, "onCreate: the request is sent to the user")
                            isNotified = true
                            saveNotificationState()
                        }
                    }
                }
            }
            else{
                Log.d(TAG, "onCreate: the check button is not checked.")
                cancelHourlyReminder()
            }
        }

        binding.btnEditTarget.setOnClickListener {
            createDialog()
            showDialog()
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
                saveData()
                updateProgress()
//                binding.progressBar.progressMax = waterGoal.toFloat()
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

    private fun saveData() {
        val sharedPreferences = getSharedPreferences(sharedPreferencesKey, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putInt(waterIntakeKey, waterIntake)
        editor.putInt(waterGoalKey, waterGoal)
        editor.apply()
    }

    private fun saveNotificationState(){
        val sharedPreferences = getSharedPreferences(sharedPreferencesKey, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putBoolean(isNotifiedKey, isNotified)
        editor.apply()
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
//        alarmManager.setRepeating(
//            AlarmManager.RTC_WAKEUP,
//            calendar.timeInMillis,
//            AlarmManager.INTERVAL_HOUR,
//            pendingIntent
//        )
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

