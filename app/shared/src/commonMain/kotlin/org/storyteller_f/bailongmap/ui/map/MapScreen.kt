package org.storyteller_f.bailongmap.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import bailongmap.app.shared.generated.resources.Res
import bailongmap.app.shared.generated.resources.ic_check
import bailongmap.app.shared.generated.resources.ic_layers
import bailongmap.app.shared.generated.resources.ic_location_on
import bailongmap.app.shared.generated.resources.ic_map
import bailongmap.app.shared.generated.resources.ic_settings
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.painterResource
import org.maplibre.compose.camera.CameraFit
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.LocationIndicatorLayer
import org.maplibre.compose.location.rememberDefaultHeadingProvider
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberLocationState
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.map.LocalMapState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.offline.OfflinePackDefinition
import org.maplibre.compose.offline.OfflineStorageState
import org.maplibre.compose.offline.offlineStorage
import org.maplibre.compose.offline.rememberOfflinePacksSource
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.DpPadding
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Feature.Companion.getStringProperty
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Geometry
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import org.storyteller_f.bailongmap.data.model.JourneyLeg
import org.storyteller_f.bailongmap.data.model.JourneyPlan
import org.storyteller_f.bailongmap.data.model.Place
import org.storyteller_f.bailongmap.data.model.RoutePoint
import org.storyteller_f.bailongmap.data.model.TravelMode
import org.storyteller_f.bailongmap.ui.navigation.JourneyNavigationCard
import org.storyteller_f.bailongmap.ui.navigation.JourneyOptionsCard
import org.storyteller_f.bailongmap.ui.offline.MaplibreOfflineOperations
import org.storyteller_f.bailongmap.ui.offline.OfflineCacheSheet
import org.storyteller_f.bailongmap.ui.offline.OfflineDispatchers
import org.storyteller_f.bailongmap.ui.offline.OfflineHost
import org.storyteller_f.bailongmap.ui.place.PlaceDetailSheet
import org.storyteller_f.bailongmap.ui.search.SearchBarUi
import org.storyteller_f.bailongmap.ui.settings.MapSettingsSheet
import org.storyteller_f.bailongmap.data.transit.createTransitPackages
import org.storyteller_f.bailongmap.data.transit.rememberTransitImportPicker
import org.storyteller_f.bailongmap.ui.transit.TransitHost
import org.storyteller_f.bailongmap.ui.transit.TransitSettings
import org.storyteller_f.bailongmap.data.roads.createOfflineRoads
import org.storyteller_f.bailongmap.data.roads.rememberRoadImportPicker
import org.storyteller_f.bailongmap.ui.transit.RoadHost
import org.storyteller_f.bailongmap.ui.transit.RoadSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    openedPlace: Place? = null,
    onOpenedPlaceConsumed: () -> Unit = {},
    offlineTestStyleUrl: String? = null,
    otpGraphQlUrl: String? = null,
    viewModel: MapViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val transitHost = remember { TransitHost(createTransitPackages(), OfflineDispatchers.coordination) }
    val transitState by transitHost.state.collectAsStateWithLifecycle()
    val roadHost=remember { RoadHost(createOfflineRoads(),OfflineDispatchers.coordination) }
    val roadState by roadHost.state.collectAsStateWithLifecycle()
    val chooseRoads=rememberRoadImportPicker { roadHost.import(it) }
    DisposableEffect(roadHost) { onDispose { roadHost.close() } }
    val chooseTransit = rememberTransitImportPicker { transitHost.preview(it) }
    DisposableEffect(transitHost) { onDispose { transitHost.close() } }
    val transitLifecycle = LocalLifecycleOwner.current
    LaunchedEffect(transitHost, transitLifecycle) {
        transitLifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            transitHost.effects.collect(viewModel::showError)
        }
    }
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberMapSheetState()
    val offlineSheetState = rememberMapSheetState()
    val settingsSheetState = rememberMapSheetState()
    val offlineStorage = DefaultMapRuntime.instance.offlineStorage
    val offlineState by offlineStorage.state.collectAsStateWithLifecycle()
    val offlinePacks = (offlineState as? OfflineStorageState.Ready)?.packs.orEmpty()
    val pixelRatio = LocalDensity.current.density
    val locationState = if (hasLocationPermission) {
        val locationProvider = rememberDefaultLocationProvider()
        val headingProvider = rememberDefaultHeadingProvider()
        rememberLocationState(
            provider = locationProvider,
            headingProvider = headingProvider,
        )
    } else {
        null
    }

    var pendingNavigationDestination by remember { mutableStateOf<Place?>(null) }
    val currentLocation = locationState?.lastLocation?.position

    var showStyleMenu by remember { mutableStateOf(false) }
    var showOfflineSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    val offlineHost = remember(offlineStorage) {
        OfflineHost(MaplibreOfflineOperations(offlineStorage), OfflineDispatchers.coordination)
    }
    val isCreatingOfflinePack by offlineHost.isCreatingPack.collectAsStateWithLifecycle()
    DisposableEffect(offlineHost) {
        onDispose { offlineHost.close() }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(offlineHost, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            offlineHost.effects.collect(viewModel::showError)
        }
    }
    val styleUrl = offlineTestStyleUrl ?: MAP_STYLES[uiState.styleIndex].second
    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(styleUrl),
        initialCameraPosition = CameraPosition(
            center = Position(latitude = 39.9042, longitude = 116.4074),
            zoom = 10.0,
        ),
    ) {
        val currentMapState = checkNotNull(LocalMapState.current)

        if (uiState.showOfflineRegions && offlinePacks.isNotEmpty()) {
            FillLayer(
                id = "offline-packs",
                source = rememberOfflinePacksSource(offlinePacks),
                color = const(MaterialTheme.colorScheme.tertiary),
                opacity = const(0.18f),
            )
        }

        uiState.selectedJourneyPlan?.let { plan ->
            plan.legs.forEachIndexed { index, leg ->
                key(plan.id, index) {
                    val routeCollection = remember(leg) {
                        buildJourneyLegFeatureCollection(leg, index)
                    }
                    val routeSource = rememberGeoJsonSource(
                        data = GeoJsonData.Features(routeCollection),
                    )
                    LineLayer(
                        id = "journey-leg-$index-casing",
                        source = routeSource,
                        color = const(Color.White),
                        width = const(8.dp),
                        cap = const(LineCap.Round),
                        join = const(LineJoin.Round),
                    )
                    LineLayer(
                        id = "journey-leg-$index",
                        source = routeSource,
                        color = const(leg.mode.routeColor()),
                        width = const(
                            if (index == uiState.activeJourneyLegIndex) 6.dp else 5.dp
                        ),
                        cap = const(LineCap.Round),
                        join = const(LineJoin.Round),
                    )
                }
            }
        }

        val resultsCollection = remember(uiState.searchResults) {
            buildFeatureCollection(uiState.searchResults)
        }
        val resultsSource = rememberGeoJsonSource(
            data = GeoJsonData.Features(resultsCollection),
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
                            currentMapState.animateCamera(
                                CameraPosition(
                                    center = Position(latitude = place.lat, longitude = place.lon),
                                    zoom = maxOf(currentMapState.cameraPosition.zoom, 14.0),
                                ).toCameraUpdate()
                            )
                        }
                    }
                    ClickResult.Consume
                },
            )
        }

        locationState?.let { state ->
            if (uiState.showUserLocation) {
                LocationIndicatorLayer(
                    id = "user-location",
                    locationState = state,
                )
            }
        }
    }

    LaunchedEffect(otpGraphQlUrl) {
        viewModel.configureJourneyPlanner(otpGraphQlUrl)
    }

    LaunchedEffect(openedPlace?.id) {
        val place = openedPlace ?: return@LaunchedEffect
        viewModel.onPlaceSelected(place)
        mapState.animateCamera(
            CameraPosition(
                center = Position(latitude = place.lat, longitude = place.lon),
                zoom = 15.0,
            ).toCameraUpdate()
        )
        onOpenedPlaceConsumed()
    }

    LaunchedEffect(uiState.selectedJourneyPlan) {
        uiState.selectedJourneyPlan?.let { plan ->
            mapState.animateCameraToBounds(
                boundingBox = plan.boundingBox(),
                fit = CameraFit(fitPadding = DpPadding(48.dp, 48.dp, 48.dp, 48.dp)),
            )
        }
    }

    LaunchedEffect(
        hasLocationPermission,
        currentLocation,
        pendingNavigationDestination?.id,
    ) {
        val destination = pendingNavigationDestination ?: return@LaunchedEffect
        val position = currentLocation
        if (!hasLocationPermission || position == null) return@LaunchedEffect

        pendingNavigationDestination = null
        viewModel.onNavigationRequested(
            origin = RoutePoint(
                latitude = position.latitude,
                longitude = position.longitude,
            ),
            destination = destination,
        )
    }

    LaunchedEffect(
        currentLocation,
        uiState.selectedJourneyPlan?.id,
        uiState.activeJourneyLegIndex,
    ) {
        val location = currentLocation ?: return@LaunchedEffect
        val plan = uiState.selectedJourneyPlan ?: return@LaunchedEffect
        val activeLeg = plan.legs.getOrNull(uiState.activeJourneyLegIndex)
            ?: return@LaunchedEffect
        if (location.distanceMetersTo(activeLeg.points.last()) <= LEG_COMPLETION_DISTANCE_METERS) {
            viewModel.onJourneyLegCompleted()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MaplibreMap(
            state = mapState,
            modifier = Modifier.fillMaxSize(),
            interactions = MapInteractions {
                callbacks {
                    click {
                        onEvent {
                            if (uiState.selectedPlace != null) {
                                viewModel.onPlaceDeselected()
                                ClickResult.Consume
                            } else {
                                ClickResult.Pass
                            }
                        }
                    }
                }
            },
        )

        // Search bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
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
                        mapState.animateCamera(
                            CameraPosition(
                                center = Position(latitude = place.lat, longitude = place.lon),
                                zoom = 14.0,
                            ).toCameraUpdate()
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
                            val pos = locationState?.lastLocation?.position
                            if (pos != null) {
                                mapState.animateCamera(CameraPosition(center = pos, zoom = 15.0).toCameraUpdate())
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

        uiState.navigationDestination?.let { destination ->
            val modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 88.dp, bottom = 24.dp)
            val selectedPlan = uiState.selectedJourneyPlan
            if (selectedPlan == null) {
                JourneyOptionsCard(
                    destination = destination,
                    plans = uiState.journeyPlans,
                    groups = uiState.journeyGroups,
                    isLoading = uiState.isJourneyPlanning,
                    onSelect = viewModel::onJourneyPlanSelected,
                    onCancel = viewModel::onNavigationCancelled,
                    modifier = modifier,
                )
            } else {
                JourneyNavigationCard(
                    destination = destination,
                    plan = selectedPlan,
                    activeLegIndex = uiState.activeJourneyLegIndex,
                    onNextLeg = viewModel::onJourneyLegCompleted,
                    onCancel = viewModel::onNavigationCancelled,
                    modifier = modifier,
                )
            }
        }

        // Error snackbar
        uiState.error?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = if (uiState.navigationDestination == null) 16.dp else 112.dp,
                    ),
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
            onNavigate = {
                if (!hasLocationPermission) {
                    pendingNavigationDestination = place
                    onRequestLocationPermission()
                } else {
                    val position = currentLocation
                    if (position == null) {
                        viewModel.showError("正在获取当前位置，请稍后重试")
                    } else {
                        viewModel.onNavigationRequested(
                            origin = RoutePoint(
                                latitude = position.latitude,
                                longitude = position.longitude,
                            ),
                            destination = place,
                        )
                    }
                }
            },
            onShare = { viewModel.onSharePlace(place) },
            sheetState = sheetState,
        )
    }

    if (showOfflineSheet) {
        OfflineCacheSheet(
            offlineStorage = offlineStorage,
            currentZoom = mapState.cameraPosition.zoom,
            isCreatingPack = isCreatingOfflinePack,
            sheetState = offlineSheetState,
            onDismiss = { showOfflineSheet = false },
            onClearCache = { offlineHost.clearCache() },
            onPause = { offlineHost.pause(it) },
            onResume = { offlineHost.resume(it) },
            onDelete = { offlineHost.delete(it) },
            onDownloadVisibleRegion = {
                val viewport = mapState.viewport
                if (viewport == null) {
                    viewModel.showError("地图尚未就绪，请稍后重试")
                } else {
                    val zoom = mapState.cameraPosition.zoom.toInt().coerceIn(0, 16)
                    offlineHost.create(
                        definition = OfflinePackDefinition.TilePyramid(
                            styleUrl = styleUrl,
                            bounds = viewport.visibleBounds.toBoundingBox(),
                            pixelRatio = pixelRatio,
                            minZoom = (zoom - 2).coerceAtLeast(0).toDouble(),
                            maxZoom = (zoom + 2).coerceAtMost(16).toDouble(),
                        ),
                        title = "离线区域 ${offlinePacks.size + 1}",
                    )
                }
            },
        )
    }

    if (showSettingsSheet) {
        MapSettingsSheet(
            uiState = uiState,
            onClearCache = { offlineHost.clearCache() },
            sheetState = settingsSheetState,
            onDismiss = { showSettingsSheet = false },
            onStyleChange = viewModel::onStyleChange,
            onShowOfflineRegionsChange = viewModel::onShowOfflineRegionsChange,
            onShowSearchMarkersChange = viewModel::onShowSearchMarkersChange,
            onShowUserLocationChange = viewModel::onShowUserLocationChange,
            transitSettings = {
                TransitSettings(transitState, chooseTransit, { transitHost.install() }, { transitHost.discard() }, { transitHost.remove() })
                RoadSettings(roadState,chooseRoads)
            },
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

@Suppress("UNCHECKED_CAST")
private fun buildJourneyLegFeatureCollection(
    leg: JourneyLeg,
    index: Int,
): FeatureCollection<Geometry, JsonObject> =
    FeatureCollection(
        listOf(
            Feature(
                id = JsonPrimitive("journey-leg-$index"),
                geometry = LineString(
                    leg.points.map { point ->
                        Position(longitude = point.longitude, latitude = point.latitude)
                    }
                ),
                properties = buildJsonObject {
                    put("mode", leg.mode.name)
                    put("distance", leg.distanceMeters)
                    put("duration", leg.durationSeconds)
                },
            ) as Feature<Geometry, JsonObject>
        )
    )

private fun JourneyPlan.boundingBox(): BoundingBox {
    val west = points.minOf { it.longitude }
    val east = points.maxOf { it.longitude }
    val south = points.minOf { it.latitude }
    val north = points.maxOf { it.latitude }
    val longitudePadding = if (west == east) 0.001 else 0.0
    val latitudePadding = if (south == north) 0.001 else 0.0
    return BoundingBox(
        west - longitudePadding,
        south - latitudePadding,
        east + longitudePadding,
        north + latitudePadding,
    )
}

private fun TravelMode.routeColor(): Color = when (this) {
    TravelMode.WALK -> Color(0xFF616161)
    TravelMode.SUBWAY -> Color(0xFF7B1FA2)
    TravelMode.BICYCLE -> Color(0xFF00897B)
    TravelMode.TRANSIT -> Color(0xFF1565C0)
    TravelMode.CAR -> Color(0xFF5E35B1)
}

private fun Position.distanceMetersTo(point: RoutePoint): Double {
    val latitudeRadians = latitude.toRadians()
    val pointLatitudeRadians = point.latitude.toRadians()
    val latitudeDelta = pointLatitudeRadians - latitudeRadians
    val longitudeDelta = (point.longitude - longitude).toRadians()
    val haversine = kotlin.math.sin(latitudeDelta / 2).let { it * it } +
        kotlin.math.cos(latitudeRadians) *
        kotlin.math.cos(pointLatitudeRadians) *
        kotlin.math.sin(longitudeDelta / 2).let { it * it }
    return 2 * EARTH_RADIUS_METERS * kotlin.math.asin(kotlin.math.sqrt(haversine))
}

private fun Double.toRadians(): Double = this * kotlin.math.PI / 180.0

private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val LEG_COMPLETION_DISTANCE_METERS = 45.0
