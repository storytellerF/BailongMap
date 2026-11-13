package org.storyteller_f.bailongmap.ui.map

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.time.Duration.Companion.milliseconds
import org.maplibre.compose.camera.CameraAnimation
import org.storyteller_f.bailongmap.ui.navigation.*
import org.storyteller_f.bailongmap.ui.search.OriginSelectionSheet
import org.storyteller_f.bailongmap.ui.search.ManualOriginBar
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import bailongmap.app.shared.generated.resources.Res
import bailongmap.app.shared.generated.resources.ic_location_on
import bailongmap.app.shared.generated.resources.ic_settings
import bailongmap.app.shared.generated.resources.ic_navigation
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
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberLocationState
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.map.StyleLoadState
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
    val chooseRoads=rememberRoadImportPicker { roadHost.review(it) }
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
    val compactNavigation = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } < 600.dp
    val locationState = if (hasLocationPermission) {
        val locationProvider = rememberDefaultLocationProvider()
        rememberLocationState(
            provider = locationProvider,
        )
    } else {
        null
    }

    val currentLocation = locationState?.lastLocation?.position
    val trackingState by viewModel.trackingState.collectAsStateWithLifecycle()
    val reliableLocation = trackingState.location is NavigationLocation.Ready
    LaunchedEffect(hasLocationPermission, locationState?.lastLocation) {
        if (!hasLocationPermission) viewModel.onLocationFix(null)
        else locationState?.lastLocation?.let { viewModel.onLocationFix(it.navigationFix()) }
    }

    var showFavorites by remember { mutableStateOf(false) }
    val showOriginChoice = uiState.originRequest as? OriginRequest.Explain
    val manualOrigin = uiState.originRequest as? OriginRequest.Manual
    val pendingOrigin = uiState.originRequest as? OriginRequest.Waiting
    val modeSheetState = rememberMapSheetState()
    val favoritesSheetState = rememberMapSheetState()
    val originSheetState = rememberMapSheetState()
    val activeSession = uiState.navigation as? NavigationSession.Active
    val immersive = activeSession?.mode == NavigationMode.IMMERSIVE
    var followingRoute by remember(activeSession?.plan?.id) { mutableStateOf(true) }
    var northUp by remember(activeSession?.plan?.id) { mutableStateOf(false) }
    val sessionTracking = (trackingState.tracking as? SessionTracking.Active)?.takeIf {
        it.session.generation == uiState.navigationIdentity && it.session.navigation == activeSession
    }
    val progress = sessionTracking?.progress
    val trackedProgress = sessionTracking?.confirmed

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
                        color = const(if (index == uiState.activeJourneyLegIndex) MaterialTheme.colorScheme.primary else leg.mode.routeColor()),
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
                    }
                    ClickResult.Consume
                },
            )
        }

        locationState?.let { state ->
            if (uiState.showUserLocation && !immersive) {
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
        if (activeSession != null) {
            viewModel.showError("请先结束当前导航，再选择新的目的地")
            onOpenedPlaceConsumed()
            return@LaunchedEffect
        }
        viewModel.onPlaceSelected(place)
        onOpenedPlaceConsumed()
    }

    LaunchedEffect(uiState.selectedPlace) {
        val place = uiState.selectedPlace ?: return@LaunchedEffect
        mapState.animateCamera(CameraPosition(center = Position(place.lon, place.lat), zoom = 15.0).toCameraUpdate())
    }

    LaunchedEffect(uiState.selectedJourneyPlan, immersive) {
        uiState.selectedJourneyPlan?.takeUnless { immersive }?.let { plan ->
            mapState.animateCameraToBounds(
                boundingBox = plan.boundingBox(),
                fit = CameraFit(bearing = 0.0, pitch = 0.0, fitPadding = DpPadding(32.dp, 120.dp, 32.dp, 300.dp)),
            )
        }
    }

    LaunchedEffect(uiState.navigationDestination) {
        if (uiState.navigationDestination == null) mapState.setCameraPosition(
            mapState.cameraPosition.copy(bearing = 0.0, pitch = 0.0, padding = DpPadding.Zero))
    }

    LaunchedEffect(immersive, followingRoute, northUp, trackedProgress, activeSession?.legIndex, compactNavigation) {
        if (!immersive || !followingRoute) return@LaunchedEffect
        val tracked = trackedProgress ?: return@LaunchedEffect
        mapState.animateCamera(
            CameraPosition(
                center = Position(tracked.position.longitude, tracked.position.latitude),
                zoom = if (activeSession.plan.legs[activeSession.legIndex].mode == TravelMode.CAR) 16.0 else 17.0,
                bearing = if (northUp) 0.0 else tracked.bearing,
                pitch = 35.0,
                padding = DpPadding(left = 24.dp, top = if (compactNavigation) 115.dp else 210.dp, right = 24.dp, bottom = if (compactNavigation) 140.dp else 270.dp),
            ).toCameraUpdate(),
            animation = CameraAnimation.Ease { duration = 650.milliseconds },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MaplibreMap(
            state = mapState,
            modifier = Modifier.fillMaxSize(),
            interactions = MapInteractions {
                camera {
                    pan { onStart { if (immersive) followingRoute = false } }
                    zoom { onStart { if (immersive) followingRoute = false } }
                    rotate { onStart { if (immersive) followingRoute = false } }
                    pitch { onStart { if (immersive) followingRoute = false } }
                }
                callbacks {
                    longClick {
                        onEvent { event ->
                            val position = event.position
                            if (position != null && activeSession == null && manualOrigin == null) {
                                val longitude = ((position.longitude + 540) % 360) - 180
                                viewModel.onPlaceSelected(Place("map:${position.latitude},$longitude", "地图选点",
                                    "地图上选择的地点", position.latitude, longitude, "", ""))
                                ClickResult.Consume
                            } else ClickResult.Pass
                        }
                    }
                    click {
                        onEvent {
                            if (uiState.navigation is NavigationSession.Active || manualOrigin != null) {
                                ClickResult.Pass
                            } else if (uiState.selectedPlace != null) {
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

        if (immersive && uiState.showUserLocation) trackedProgress?.let { tracked ->
            mapState.screenLocationFromPosition(Position(tracked.position.longitude, tracked.position.latitude))?.let { offset ->
                Surface(
                    modifier = Modifier.offset(offset.x - 18.dp, offset.y - 18.dp).size(36.dp)
                        .graphicsLayer { rotationZ = (tracked.bearing - mapState.cameraPosition.bearing).toFloat() },
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                    border = androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.surface),
                    shadowElevation = 4.dp,
                ) { Icon(painterResource(Res.drawable.ic_navigation), "路线当前位置", Modifier.padding(5.dp)) }
            }
        }

        if (uiState.navigationDestination == null && manualOrigin == null) {
            Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                        Text("白龙地图", Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.weight(1f))
                    FilledIconButton(onClick = { showSettingsSheet = true }, colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)) {
                        Icon(painterResource(Res.drawable.ic_settings), "设置")
                    }
                }
                SearchBarUi(
                    query = uiState.searchQuery, onQueryChange = viewModel::onSearchQueryChange,
                    results = uiState.searchResults, isSearching = uiState.isSearching,
                    isExpanded = uiState.isSearchExpanded, searchState = uiState.search,
                    onRetry = { viewModel.onSearchQueryChange(uiState.searchQuery) },
                    onResultClick = viewModel::onPlaceSelected,
                )
                when (mapState.style.loadState) {
                    StyleLoadState.Loading, StyleLoadState.Pending -> Surface(shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("正在加载地图…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is StyleLoadState.Failed -> Surface(shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text("地图暂未加载，请检查连接或切换样式。", style = MaterialTheme.typography.bodySmall)
                            Row {
                                TextButton(onClick = { showSettingsSheet = true }) { Text("地图样式") }
                                TextButton(onClick = { showOfflineSheet = true }) { Text("离线中心") }
                            }
                        }
                    }
                    else -> Unit
                }
            }
            if (!uiState.isSearchExpanded) MapHomePanel(
                places = uiState.recentPlaces, onPlace = viewModel::onPlaceSelected,
                onFavorites = { showFavorites = true }, onOffline = { showOfflineSheet = true },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            FloatingActionButton(
                onClick = {
                    if (!hasLocationPermission) onRequestLocationPermission()
                    else currentLocation?.let { pos -> coroutineScope.launch {
                        mapState.animateCamera(CameraPosition(center = pos, zoom = 16.0).toCameraUpdate())
                    } } ?: viewModel.showError("正在获取当前位置，请稍后重试")
                }, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
                containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary,
            ) { Icon(painterResource(Res.drawable.ic_location_on), "我的位置") }
        }

        pendingOrigin?.let { pending ->
            Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
                shape = MaterialTheme.shapes.large, shadowElevation = 6.dp) {
                Column(Modifier.padding(16.dp)) {
                    Text("等待当前位置", style = MaterialTheme.typography.titleMedium)
                    Text("定位不可用或精度不足时，可手动选择起点。", style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { viewModel.onManualOriginRequested(pending.id) }) { Text("手动选择起点") }
                        TextButton(onClick = { viewModel.onOriginCancelled(pending) }) { Text("取消") }
                    }
                }
            }
        }

        manualOrigin?.let { manual ->
            Icon(painterResource(Res.drawable.ic_location_on), "地图中心起点", tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center).size(38.dp))
            ManualOriginBar(manual.destination.name,
                onConfirm = {
                    val point = mapState.cameraPosition.center
                    viewModel.onManualOriginConfirmed(manual.id,
                        RoutePoint(point.latitude, ((point.longitude + 540) % 360) - 180))
                }, onCancel = { viewModel.onOriginCancelled(manual) },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
        }

        uiState.navigationDestination?.let { destination ->
            val session = uiState.navigation
            if (session == NavigationSession.Idle) {
                JourneyOptionsCard(destination, uiState.journeyPlans, uiState.isJourneyPlanning,
                    viewModel::onJourneyPlanSelected, viewModel::onNavigationCancelled,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
                    groups = uiState.journeyGroups)
            } else if (session is NavigationSession.Active) {
                if (session.mode == NavigationMode.IMMERSIVE) {
                    ImmersiveNavigation(destination, session, progress, reliableLocation, followingRoute, northUp,
                        onRecenter = { followingRoute = true; northUp = false },
                        onNorthUp = { northUp = !northUp; followingRoute = true },
                        onNextLeg = viewModel::onJourneyLegCompleted, onEnd = viewModel::onNavigationCancelled, compact = compactNavigation)
                } else JourneyNavigationCard(destination, session.plan, session.legIndex,
                    viewModel::onJourneyLegCompleted, viewModel::onNavigationCancelled,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
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
            onNavigate = { viewModel.onNavigateToPlace(place) },
            onShare = { viewModel.onSharePlace(place) },
            sheetState = sheetState,
        )
    }

    (uiState.navigation as? NavigationSession.Choosing)?.let { choosing ->
        uiState.navigationDestination?.let { destination ->
            NavigationModeSheet(destination, choosing.plan, modeSheetState,
                onStart = viewModel::onNavigationStarted, onDismiss = viewModel::onNavigationCancelled)
        }
    }
    if (showFavorites) FavoritesSheet(uiState.favoritePlaces.map { it.place },
        onPlace = { showFavorites = false; viewModel.onPlaceSelected(it) },
        onDismiss = { showFavorites = false }, sheetState = favoritesSheetState)

    showOriginChoice?.let { request ->
        OriginSelectionSheet(request.destination.name, originSheetState,
            onLocate = { viewModel.onOriginLocationRequested(request.id); onRequestLocationPermission() },
            onManual = { viewModel.onManualOriginRequested(request.id) },
            onDismiss = { viewModel.onOriginCancelled(request) })
    }

    if (showOfflineSheet) {
        OfflineCacheSheet(
            offlineStorage = offlineStorage,
            currentZoom = mapState.cameraPosition.zoom,
            isCreatingPack = isCreatingOfflinePack,
            sheetState = offlineSheetState,
            onDismiss = { showOfflineSheet = false },
            errorMessage = uiState.error,
            dataSettings = {
                RoadSettings(roadState, chooseRoads, { roadHost.install() }, { roadHost.discard() })
                TransitSettings(transitState, chooseTransit, { transitHost.install() }, { transitHost.discard() }, { transitHost.remove() })
            },
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
            onAppearanceChange = viewModel::onAppearanceChange,
            onShowOfflineRegionsChange = viewModel::onShowOfflineRegionsChange,
            onShowSearchMarkersChange = viewModel::onShowSearchMarkersChange,
            onShowUserLocationChange = viewModel::onShowUserLocationChange,
            transitSettings = {
                TransitSettings(transitState, chooseTransit, { transitHost.install() }, { transitHost.discard() }, { transitHost.remove() })
                RoadSettings(roadState, chooseRoads, { roadHost.install() }, { roadHost.discard() })
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
