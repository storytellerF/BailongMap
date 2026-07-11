package org.storyteller_f.bailongmap.platform

import org.storyteller_f.bailongmap.data.model.Place

interface PlaceShareService {
    fun share(place: Place)
}

expect fun createPlaceShareService(): PlaceShareService
