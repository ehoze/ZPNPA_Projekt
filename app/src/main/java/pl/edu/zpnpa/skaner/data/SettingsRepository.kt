package pl.edu.zpnpa.skaner.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException

private val Context.settingsStore by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {
    // Read errors must fail closed: require authentication instead of disabling an existing lock.
    val lockEnabled = context.settingsStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences().toMutablePreferences().apply { this[LOCK] = true })
        else throw error
    }.map { it[LOCK] ?: false }

    suspend fun setLockEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[LOCK] = enabled }
    }

    private companion object { val LOCK = booleanPreferencesKey("archive_lock") }
}
