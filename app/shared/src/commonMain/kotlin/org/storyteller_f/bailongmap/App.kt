package org.storyteller_f.bailongmap

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.storyteller_f.bailongmap.ui.map.MapViewModel
import org.storyteller_f.bailongmap.ui.theme.BailongTheme
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
    otpGraphQlUrl: String? = null,
) {
    val mapViewModel: MapViewModel = viewModel()
    val state by mapViewModel.uiState.collectAsStateWithLifecycle()
    BailongTheme(state.appearance) {
        MapScreen(
            hasLocationPermission = hasLocationPermission,
            onRequestLocationPermission = onRequestLocationPermission,
            openedPlace = openedPlace,
            onOpenedPlaceConsumed = onOpenedPlaceConsumed,
            offlineTestStyleUrl = offlineTestStyleUrl,
            otpGraphQlUrl = otpGraphQlUrl,
            viewModel = mapViewModel,
        )
    }
}
