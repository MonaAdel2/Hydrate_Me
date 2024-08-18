package com.mona.adel.hydrateme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ResetReceiver:BroadcastReceiver() {

    private val waterIntakeKey = "water_intake"
    private val sharedPreferencesKey = "water_tracker"

    override fun onReceive(context: Context?, intent: Intent?) {
        val sharedPreferences = context?.getSharedPreferences(sharedPreferencesKey, Context.MODE_PRIVATE)
        val editor = sharedPreferences?.edit()
        editor?.putInt(waterIntakeKey, 0) // Reset water intake to 0
        editor?.apply()

    }


}