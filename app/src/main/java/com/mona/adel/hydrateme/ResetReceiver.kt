import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ResetReceiver : BroadcastReceiver() {

    companion object {
        val Context.dataStore by preferencesDataStore(name = "settings")
    }

    private val WATERINTAKEKEY = intPreferencesKey("water_intake")

    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let {
            // Call the reset function within a coroutine
            CoroutineScope(Dispatchers.IO).launch {
                resetWaterIntakeLevel(it)
            }
        }
    }

    private suspend fun resetWaterIntakeLevel(context: Context) {
        context.dataStore.edit { preferences ->
            preferences[WATERINTAKEKEY] = 0
        }
    }
}
