package com.mona.adel.hydrateme

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File

class MyApp : Application() {
    private val datsStoredPreferencesKey = "water_tracker"


    val dataStore: DataStore<Preferences> by lazy {
        createDataStore(datsStoredPreferencesKey)
    }

    private fun createDataStore(name: String): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            produceFile = { File(filesDir, "datastore/$name.preferences_pb") }
        )
    }

    companion object {
        lateinit var instance: MyApp
        val NOTIFICATION_CHANNEl_ID = "notification_channel"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

    }
}