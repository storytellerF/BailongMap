package org.storyteller_f.bailongmap.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import org.jetbrains.compose.resources.painterResource
import bailongmap.app.shared.generated.resources.Res
import bailongmap.app.shared.generated.resources.ic_check
import bailongmap.app.shared.generated.resources.ic_location_on
import bailongmap.app.shared.generated.resources.ic_map
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import bailongmap.app.shared.generated.resources.ic_layers
import bailongmap.app.shared.generated.resources.ic_settings
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.location.UserLocationState
import org.maplibre.compose.location.mostAccurateBearing
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberDefaultOrientationProvider
import org.maplibre.compose.location.rememberUserLocationState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.offline.OfflinePackDefinition
import org.maplibre.compose.offline.rememberOfflineManager
import org.maplibre.compose.offline.rememberOfflinePacksSource
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Feature.Companion.getStringProperty
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Geometry
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import org.storyteller_f.bailongmap.data.model.Place
import org.storyteller_f.bailongmap.ui.offline.OfflineCacheSheet
import org.storyteller_f.bailongmap.ui.place.PlaceDetailSheet
import org.storyteller_f.bailongmap.ui.search.SearchBarUi
import org.storyteller_f.bailongmap.ui.settings.MapSettingsSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    openedPlace: Place? = null,
    onOpenedPlaceConsumed: () -> Unit = {},
    offlineTestStyleUrl: String? = null,
    viewModel: MapViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val offlineSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val offlineManager = rememberOfflineManager()

    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(latitude = 39.9042, longitude = 116.4074),
            zoom = 10.0,
        )
    )

    // Holds location state once permission is granted (hoisted from map content block)
    var locationState by remember { mutableStateOf<UserLocationState?>(null) }

    var showStyleMenu by remember { mutableStateOf(false) }
    var showOfflineSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var isCreatingOfflinePack by remember { mutableStateOf(false) }
    val styleUrl = offlineTestStyleUrl ?: MAP_STYLES[uiState.styleIndex].second

    LaunchedEffect(openedPlace?.id) {
        val place = openedPlace ?: return@LaunchedEffect
        viewModel.onPlaceSelected(place)
        cameraState.animateTo(
            CameraPosition(
                target = Position(latitude = place.lat, longitude = place.lon),
                zoom = 15.0,
            )
        )
        onOpenedPlaceConsumed()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MaplibreMap(
            cameraState = cameraState,
            baseStyle = BaseStyle.Uri(styleUrl),
            modifier = Modifier.fillMaxSize(),
            onMapClick = { _, _ ->
                if (uiState.selectedPlace != null) {
                    viewModel.onPlaceDeselected()
                    ClickResult.Consume
                } else {
                    ClickResult.Pass
                }
            },
        ) {
            if (uiState.showOfflineRegions && offlineManager.packs.isNotEmpty()) {
                FillLayer(
                    id = "offline-packs",
                    source = rememberOfflinePacksSource(offlineManager.packs),
                    color = const(MaterialTheme.colorScheme.tertiary),
                    opacity = const(0.18f),
                )
            }

            // Search result markers
            val resultsCollection = remember(uiState.searchResults) {
                buildFeatureCollection(uiState.searchResults)
            }
            val resultsSource = rememberGeoJsonSource(
                data = GeoJsonData.Features(resultsCollection),
                options = GeoJsonOptions(synchronousUpdate = true),
            )

            if (uiState.showSearchMarkers && uiState.searchResults.isNotEmpty()) {
                CircleLayer(
                    id = "search-results",
                    source = resultsSource,
                    radius = const(9.dp),
                    color = const(MaterialTheme.colorScheme.primary),
                    strokeWidth = const(2.dp),
                    strokeColor = const(MaterialTheme.colorScheme.onPrimary),
                    onClick = { features ->
                        val id = features.firstOrNull()?.getStringProperty("id")
                        val place = viewModel.uiState.value.searchResults.find { it.id == id }
                        if (place != null) {
                            viewModel.onPlaceSelected(place)
                            coroutineScope.launch {
                                cameraState.animateTo(
                                    CameraPosition(
                                        target = Position(latitude = place.lat, longitude = place.lon),
                                        zoom = maxOf(cameraState.position.zoom, 14.0),
                                    )
                                )
                            }
                        }
                        ClickResult.Consume
                    },
                )
            }

            // Location puck — only when permission granted
            if (hasLocationPermission && uiState.showUserLocation) {
                val locationProvider = rememberDefaultLocationProvider()
                val orientationProvider = rememberDefaultOrientationProvider()
                val state = rememberUserLocationState(locationProvider, orientationProvider)

                LaunchedEffect(state) { locationState = state }

                LocationPuck(
                    idPrefix = "user",
                    location = state.location,
                    bearing = state.mostAccurateBearing(),
                    cameraState = cameraState,
                )
            }
        }

        // Search bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SearchBarUi(
                query = uiState.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                results = uiState.searchResults,
                isSearching = uiState.isSearching,
                isExpanded = uiState.isSearchExpanded,
                onResultClick = { place ->
                    viewModel.onPlaceSelected(place)
                    coroutineScope.launch {
                        cameraState.animateTo(
                            CameraPosition(
                                target = Position(latitude = place.lat, longitude = place.lon),
                                zoom = 14.0,
                            )
                        )
                    }
                },
            )
        }

        // FABs (bottom-right)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Box {
                SmallFloatingActionButton(
                    onClick = { showStyleMenu = !showStyleMenu },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Icon(painterResource(Res.drawable.ic_map), contentDescription = "切换地图样式")
                }
                DropdownMenu(
                    expanded = showStyleMenu,
                    onDismissRequest = { showStyleMenu = false },
                ) {
                    MAP_STYLES.forEachIndexed { index, (name, _) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            leadingIcon = if (index == uiState.styleIndex) {
                                { Icon(painterResource(Res.drawable.ic_check), contentDescription = null) }
                            } else null,
                            onClick = {
                                viewModel.onStyleChange(index)
                                showStyleMenu = false
                            },
                        )
                    }
                }
            }

            SmallFloatingActionButton(
                onClick = { showOfflineSheet = true },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Icon(painterResource(Res.drawable.ic_layers), contentDescription = "离线地图")
            }

            SmallFloatingActionButton(
                onClick = { showSettingsSheet = true },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Icon(painterResource(Res.drawable.ic_settings), contentDescription = "设置")
            }

            FloatingActionButton(
                onClick = {
                    if (hasLocationPermission) {
                        coroutineScope.launch {
                            val pos = locationState?.location?.position?.value
                            if (pos != null) {
                                cameraState.animateTo(CameraPosition(target = pos, zoom = 15.0))
                            }
                        }
                    } else {
                        onRequestLocationPermission()
                    }
                },
            ) {
                Icon(painterResource(Res.drawable.ic_location_on), contentDescription = "我的位置")
            }
        }

        // Error snackbar
        uiState.error?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                dismissAction = {
                    TextButton(onClick = viewModel::clearError) { Text("关闭") }
                },
            ) {
                Text(error)
            }
        }
    }

    // Place detail bottom sheet
    uiState.selectedPlace?.let { place ->
        PlaceDetailSheet(
            place = place,
            isFavorite = place.id in uiState.favorites,
            onDismiss = viewModel::onPlaceDeselected,
            onToggleFavorite = { viewModel.onToggleFavorite(place) },
            onShare = { viewModel.onSharePlace(place) },
            sheetState = sheetState,
        )
    }

    if (showOfflineSheet) {
        OfflineCacheSheet(
            offlineManager = offlineManager,
            currentZoom = cameraState.position.zoom,
            isCreatingPack = isCreatingOfflinePack,
            sheetState = offlineSheetState,
            onDismiss = { showOfflineSheet = false },
            onError = viewModel::showError,
            onDownloadVisibleRegion = {
                coroutineScope.launch {
                    isCreatingOfflinePack = true
                    runCatching {
                        val zoom = cameraState.position.zoom.toInt()
                        val pack = offlineManager.create(
                            definition = OfflinePackDefinition.TilePyramid(
                                styleUrl = styleUrl,
                                bounds = cameraState.awaitProjection().queryVisibleBoundingBox(),
                                minZoom = (zoom - 2).coerceAtLeast(0),
                                maxZoom = (zoom + 2).coerceAtMost(16),
                            ),
                            metadata = "离线区域 ${offlineManager.packs.size + 1}".encodeToByteArray(),
                        )
                        offlineManager.resume(pack)
                    }.onFailure {
                        viewModel.showError("创建离线区域失败")
                    }
                    isCreatingOfflinePack = false
                }
            },
        )
    }

    if (showSettingsSheet) {
        MapSettingsSheet(
            uiState = uiState,
            offlineManager = offlineManager,
            sheetState = settingsSheetState,
            onDismiss = { showSettingsSheet = false },
            onStyleChange = viewModel::onStyleChange,
            onShowOfflineRegionsChange = viewModel::onShowOfflineRegionsChange,
            onShowSearchMarkersChange = viewModel::onShowSearchMarkersChange,
            onShowUserLocationChange = viewModel::onShowUserLocationChange,
            onError = viewModel::showError,
        )
    }
}

@Suppress("UNCHECKED_CAST")
private fun buildFeatureCollection(places: List<Place>): FeatureCollection<Geometry, JsonObject> =
    FeatureCollection(
        places.map { place ->
            Feature(
                id = JsonPrimitive(place.id),
                geometry = Point(Position(longitude = place.lon, latitude = place.lat)),
                properties = buildJsonObject {
                    put("id", place.id)
                    put("name", place.name)
                },
            ) as Feature<Geometry, JsonObject>
        }
    )
