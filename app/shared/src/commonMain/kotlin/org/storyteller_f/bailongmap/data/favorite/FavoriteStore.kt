package org.storyteller_f.bailongmap.data.favorite

import kotlinx.coroutines.flow.Flow
import org.storyteller_f.bailongmap.data.model.FavoritePlace
import org.storyteller_f.bailongmap.data.model.Place

interface FavoriteStore {
    val favorites: Flow<List<FavoritePlace>>

    suspend fun add(place: Place)

    suspend fun remove(placeId: String)
}

expect fun createFavoriteStore(): FavoriteStore
