package org.storyteller_f.bailongmap.ui.map

import org.storyteller_f.bailongmap.data.model.Place

/** One destination owns the entire origin workflow; replacing it cancels every previous step. */
sealed interface OriginRequest {
    data object None : OriginRequest
    data class Explain(val id: Long, val destination: Place) : OriginRequest
    data class Waiting(val id: Long, val destination: Place) : OriginRequest
    data class Manual(val id: Long, val destination: Place) : OriginRequest
}

internal fun OriginRequest.waitForLocation(id: Long): OriginRequest = when (this) {
    is OriginRequest.Explain -> if (this.id == id) OriginRequest.Waiting(id, destination) else this
    else -> this
}

internal fun OriginRequest.chooseManually(id: Long): OriginRequest = when (this) {
    is OriginRequest.Explain -> if (this.id == id) OriginRequest.Manual(id, destination) else this
    is OriginRequest.Waiting -> if (this.id == id) OriginRequest.Manual(id, destination) else this
    else -> this
}

internal fun OriginRequest.cancel(expected: OriginRequest): OriginRequest =
    if (this == expected) OriginRequest.None else this

internal fun MapUiState.selectPlace(place: Place): MapUiState = copy(
    selectedPlace = place, isSearchExpanded = false, originRequest = OriginRequest.None,
    recentPlaces = (listOf(place) + recentPlaces).distinctBy { it.id }.take(5),
)
