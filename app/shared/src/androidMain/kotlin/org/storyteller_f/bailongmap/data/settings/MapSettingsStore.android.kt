package org.storyteller_f.bailongmap.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.storyteller_f.bailongmap.platform.AndroidAppContext

private val Context.mapSettingsDataStore by preferencesDataStore(name = "map_settings")

private val defaultStyleIndexKey = intPreferencesKey("default_style_index")

actual fun createMapSettingsStore(): MapSettingsStore =
    AndroidMapSettingsStore(AndroidAppContext.context)

private class AndroidMapSettingsStore(context: Context) : MapSettingsStore {
    private val dataStore = context.mapSettingsDataStore

    override val defaultStyleIndex: Flow<Int?> =
        dataStore.data.map { preferences -> preferences[defaultStyleIndexKey] }

    override suspend fun setDefaultStyleIndex(index: Int) {
        dataStore.edit { preferences ->
            preferences[defaultStyleIndexKey] = index
        }
    }
}
