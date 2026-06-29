package org.storyteller_f.bailongmap

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import org.storyteller_f.bailongmap.ui.map.MapScreen

@Composable
fun App(
    hasLocationPermission: Boolean = false,
    onRequestLocationPermission: () -> Unit = {},
) {
    MaterialTheme {
        MapScreen(
            hasLocationPermission = hasLocationPermission,
            onRequestLocationPermission = onRequestLocationPermission,
        )
    }
}
