package org.storyteller_f.bailongmap

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import org.storyteller_f.bailongmap.data.model.Place
import org.storyteller_f.bailongmap.ui.map.MapScreen

@Composable
fun App(
    hasLocationPermission: Boolean = false,
    onRequestLocationPermission: () -> Unit = {},
    openedPlace: Place? = null,
    onOpenedPlaceConsumed: () -> Unit = {},
    offlineTestStyleUrl: String? = null,
) {
    MaterialTheme {
        MapScreen(
            hasLocationPermission = hasLocationPermission,
            onRequestLocationPermission = onRequestLocationPermission,
            openedPlace = openedPlace,
            onOpenedPlaceConsumed = onOpenedPlaceConsumed,
            offlineTestStyleUrl = offlineTestStyleUrl,
        )
    }
}
