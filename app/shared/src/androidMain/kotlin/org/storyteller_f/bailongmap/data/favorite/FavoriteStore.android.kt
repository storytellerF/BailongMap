package org.storyteller_f.bailongmap.data.favorite

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.storyteller_f.bailongmap.data.model.FavoritePlace
import org.storyteller_f.bailongmap.data.model.Place
import org.storyteller_f.bailongmap.platform.AndroidAppContext

private val Context.favoriteDataStore by preferencesDataStore(name = "favorite_places")

private val favoritePlacesKey = stringPreferencesKey("places")

private val favoriteJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

actual fun createFavoriteStore(): FavoriteStore =
    AndroidFavoriteStore(AndroidAppContext.context)

private class AndroidFavoriteStore(context: Context) : FavoriteStore {
    private val dataStore = context.favoriteDataStore
    private val serializer = ListSerializer(FavoritePlace.serializer())

    override val favorites: Flow<List<FavoritePlace>> =
        dataStore.data
            .map { preferences ->
                preferences[favoritePlacesKey]?.let { value ->
                    favoriteJson.decodeFromString(serializer, value)
                } ?: emptyList()
            }
            .catch { emit(emptyList()) }

    override suspend fun add(place: Place) {
        dataStore.edit { preferences ->
            val current = preferences[favoritePlacesKey].decodeFavorites()
            val next = listOf(FavoritePlace(place, System.currentTimeMillis())) +
                current.filterNot { it.place.id == place.id }
            preferences[favoritePlacesKey] = favoriteJson.encodeToString(serializer, next)
        }
    }

    override suspend fun remove(placeId: String) {
        dataStore.edit { preferences ->
            val next = preferences[favoritePlacesKey].decodeFavorites()
                .filterNot { it.place.id == placeId }
            preferences[favoritePlacesKey] = favoriteJson.encodeToString(serializer, next)
        }
    }

    private fun String?.decodeFavorites(): List<FavoritePlace> =
        runCatching {
            if (isNullOrBlank()) emptyList()
            else favoriteJson.decodeFromString(serializer, this)
        }.getOrDefault(emptyList())
}
