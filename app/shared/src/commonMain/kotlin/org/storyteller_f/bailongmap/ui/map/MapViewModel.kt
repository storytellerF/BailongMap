package org.storyteller_f.bailongmap.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.storyteller_f.bailongmap.data.favorite.createFavoriteStore
import org.storyteller_f.bailongmap.data.model.FavoritePlace
import org.storyteller_f.bailongmap.data.model.JourneyPlan
import org.storyteller_f.bailongmap.data.model.Place
import org.storyteller_f.bailongmap.data.model.RoutePoint
import org.storyteller_f.bailongmap.data.network.*
import org.storyteller_f.bailongmap.ui.navigation.*
import org.storyteller_f.bailongmap.data.settings.MapAppearance
import kotlinx.coroutines.delay
import org.storyteller_f.bailongmap.data.model.TravelMode
import org.storyteller_f.bailongmap.data.roads.createOfflineRoads
import org.storyteller_f.bailongmap.data.settings.createMapSettingsStore
import org.storyteller_f.bailongmap.platform.createPlaceShareService

val MAP_STYLES = listOf(
    "标准" to "https://tiles.openfreemap.org/styles/liberty",
    "明亮" to "https://tiles.openfreemap.org/styles/bright",
    "简约" to "https://tiles.openfreemap.org/styles/positron",
)

sealed interface PlaceSearch {
    data object Idle : PlaceSearch
    data object Loading : PlaceSearch
    data class Matches(val places: List<Place>) : PlaceSearch
    data class Failed(val message: String) : PlaceSearch
}

data class MapUiState(
    val styleIndex: Int = 0,
    val searchQuery: String = "",
    val search: PlaceSearch = PlaceSearch.Idle,
    val appearance: MapAppearance = MapAppearance.SYSTEM,
    val selectedPlace: Place? = null,
    val recentPlaces: List<Place> = emptyList(),
    val favoritePlaces: List<FavoritePlace> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val showOfflineRegions: Boolean = true,
    val showSearchMarkers: Boolean = true,
    val showUserLocation: Boolean = true,

    val isSearchExpanded: Boolean = false,
    val navigationDestination: Place? = null,
    val journeyPlans: List<JourneyPlan> = emptyList(),
    val journeyGroups: List<JourneyGroup> = emptyList(),
    val navigation: NavigationSession = NavigationSession.Idle,
    val navigationIdentity: Int = 0,
    val originRequest: OriginRequest = OriginRequest.None,
    val isJourneyPlanning: Boolean = false,
    val error: String? = null,
) {
    val searchResults: List<Place> get() = (search as? PlaceSearch.Matches)?.places.orEmpty()
    val isSearching: Boolean get() = search == PlaceSearch.Loading
    val selectedJourneyPlan: JourneyPlan? get() = when (val session = navigation) {
        NavigationSession.Idle -> null
        is NavigationSession.Choosing -> session.plan
        is NavigationSession.Active -> session.plan
    }
    val activeJourneyLegIndex: Int get() = (navigation as? NavigationSession.Active)?.legIndex ?: 0
}

class MapViewModel : ViewModel() {
    private val httpClient = createHttpClient()
    private val nominatimClient = NominatimClient(httpClient)
    private var onlineTransit: JourneyPlanner? = null
    private var onlineTransitUrl: String? = null
    private val roads = createOfflineRoads()
    private val driving = RoadJourneyPlanner(roads.planner(TravelMode.CAR), OsrmClient(httpClient))
    private val cycling = RoadJourneyPlanner(roads.planner(TravelMode.BICYCLE), OsrmClient(httpClient,
        baseUrl="https://routing.openstreetmap.de/routed-bike", mode=TravelMode.BICYCLE,
        beforeRequest={ PublicRoadRequestGate.awaitTurn() }))
    private val walking = RoadJourneyPlanner(roads.planner(TravelMode.WALK), OsrmClient(httpClient,
        baseUrl="https://routing.openstreetmap.de/routed-foot", mode=TravelMode.WALK,
        beforeRequest={ PublicRoadRequestGate.awaitTurn() }))
    private val transitPackages = org.storyteller_f.bailongmap.data.transit.createTransitPackages()
    private val favoriteStore = createFavoriteStore()
    private val settingsStore = createMapSettingsStore()
    private val placeShareService = createPlaceShareService()

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val trackingHost = NavigationTrackingHost(Dispatchers.Default.limitedParallelism(1), Dispatchers.Default)
    val trackingState = trackingHost.state
    private var originIdentity = 0L

    private var searchJob: Job? = null
    private var navigationJob: Job? = null
    private var navigationGeneration = 0

    init {
        trackingHost.reached.onEach { reached ->
            if (reached.generation == navigationGeneration && _uiState.value.navigation == reached.navigation) onJourneyLegCompleted()
        }.launchIn(viewModelScope)
        trackingHost.state.onEach { tracking ->
            val waiting = _uiState.value.originRequest as? OriginRequest.Waiting
            val ready = tracking.location as? NavigationLocation.Ready
            if (waiting != null && ready != null) onNavigationRequested(ready.fix.point, waiting.destination)
        }.launchIn(viewModelScope)
        favoriteStore.favorites
            .catch {
                _uiState.update { state -> state.copy(error = "读取收藏失败") }
                emit(emptyList())
            }
            .onEach { favorites ->
                _uiState.update {
                    it.copy(
                        favoritePlaces = favorites,
                        favorites = favorites.map { favorite -> favorite.place.id }.toSet(),
                    )
                }
            }
            .launchIn(viewModelScope)

        settingsStore.defaultStyleIndex
            .catch {
                _uiState.update { state -> state.copy(error = "读取设置失败") }
                emit(null)
            }
            .onEach { index ->
                if (index != null) {
                    _uiState.update { it.copy(styleIndex = index.coerceIn(MAP_STYLES.indices)) }
                }
            }
            .launchIn(viewModelScope)

        settingsStore.appearance
            .catch { emit(MapAppearance.SYSTEM) }
            .onEach { appearance -> _uiState.update { it.copy(appearance = appearance) } }
            .launchIn(viewModelScope)
    }

    fun onSearchQueryChange(query: String) {
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = query, isSearchExpanded = query.isNotEmpty(),
            search = if (query.trim().length >= 2) PlaceSearch.Loading else PlaceSearch.Idle) }
        if (query.trim().length < 2) return
        searchJob = viewModelScope.launch {
            delay(600)
            val result = try {
                PlaceSearch.Matches(nominatimClient.search(query.trim()))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                PlaceSearch.Failed("搜索需要网络，请检查连接；你仍可打开收藏或在地图上选点")
            }
            if (_uiState.value.searchQuery == query) _uiState.update { it.copy(search = result) }
        }
    }

    fun onAppearanceChange(appearance: MapAppearance) {
        _uiState.update { it.copy(appearance = appearance) }
        viewModelScope.launch {
            try { settingsStore.setAppearance(appearance) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { showError("保存外观设置失败") }
        }
    }

    fun onPlaceSelected(place: Place) {
        if (_uiState.value.navigation is NavigationSession.Active) { showError("请先结束当前导航，再选择新的目的地"); return }
        if (_uiState.value.navigationDestination != null) onNavigationCancelled()
        _uiState.update { it.selectPlace(place) }
    }

    fun onLocationFix(fix: NavigationFix?) { trackingHost.onFix(fix) }

    fun onNavigateToPlace(place: Place) {
        if (_uiState.value.navigation is NavigationSession.Active) return
        val ready = trackingState.value.location as? NavigationLocation.Ready
        if (ready != null) onNavigationRequested(ready.fix.point, place)
        else {
            val request = OriginRequest.Explain(++originIdentity, place)
            _uiState.update { it.copy(selectedPlace = null, originRequest = request) }
        }
    }

    fun onOriginLocationRequested(id: Long) {
        _uiState.update { it.copy(originRequest = it.originRequest.waitForLocation(id)) }
        val waiting = _uiState.value.originRequest as? OriginRequest.Waiting ?: return
        val ready = trackingState.value.location as? NavigationLocation.Ready ?: return
        onNavigationRequested(ready.fix.point, waiting.destination)
    }

    fun onManualOriginRequested(id: Long) {
        _uiState.update { it.copy(originRequest = it.originRequest.chooseManually(id)) }
    }

    fun onManualOriginConfirmed(id: Long, point: RoutePoint) {
        val manual = _uiState.value.originRequest as? OriginRequest.Manual ?: return
        if (manual.id == id) onNavigationRequested(point, manual.destination)
    }

    fun onOriginCancelled(expected: OriginRequest) {
        _uiState.update { it.copy(originRequest = it.originRequest.cancel(expected)) }
    }

    fun onPlaceDeselected() {
        _uiState.update { it.copy(selectedPlace = null) }
    }

    fun onStyleChange(index: Int) {
        if (index !in MAP_STYLES.indices) return
        _uiState.update { it.copy(styleIndex = index) }
        viewModelScope.launch {
            runCatching { settingsStore.setDefaultStyleIndex(index) }
                .onFailure { if (it is CancellationException) throw it; showError("保存默认地图样式失败") }
        }
    }

    fun onToggleFavorite(place: Place) {
        viewModelScope.launch {
            runCatching {
                if (place.id in _uiState.value.favorites) favoriteStore.remove(place.id)
                else favoriteStore.add(place)
            }.onFailure {
                if (it is CancellationException) throw it
                showError("更新收藏失败")
            }
        }
    }

    fun onSharePlace(place: Place) {
        runCatching { placeShareService.share(place) }
            .onFailure { showError("分享失败") }
    }

    fun onNavigationRequested(origin: RoutePoint, destination: Place) {
        if (_uiState.value.navigation is NavigationSession.Active) return
        navigationJob?.cancel()
        val generation = ++navigationGeneration
        val options = JourneyOptionsPlanner(listOf(
            JourneyProvider(JourneyCategory.CAR, driving, "暂未找到驾车路线，请检查道路覆盖或网络"),
            JourneyProvider(JourneyCategory.BICYCLE, cycling, "暂未找到骑行路线，请检查道路覆盖或网络"),
            JourneyProvider(JourneyCategory.TRANSIT,
                TransitJourneyPlanner(transitPackages,walking,cycling,onlineTransit),
                "暂无公共交通方案：请检查交通包的日期、线路覆盖和接驳道路数据"),
            JourneyProvider(JourneyCategory.WALK, walking, "暂未找到步行路线，请检查道路覆盖或网络"),
        ))
        _uiState.update { it.copy(selectedPlace=null, navigationDestination=destination,
            journeyPlans=emptyList(),journeyGroups=options.initial,navigation=NavigationSession.Idle,
            navigationIdentity=generation,originRequest=OriginRequest.None,
            isJourneyPlanning=true,error=null) }
        trackingHost.setSession(null)
        navigationJob = viewModelScope.launch {
            options.options(origin,RoutePoint(destination.lat,destination.lon)).collect { result ->
                if (generation == navigationGeneration) _uiState.update { state ->
                    val groups = state.journeyGroups.map { if (it.category==result.category) result else it }
                    state.copy(journeyGroups=groups,
                        journeyPlans=groups.flatMap { (it.availability as? JourneyAvailability.Available)?.plans.orEmpty() },
                        isJourneyPlanning=groups.any { it.availability==JourneyAvailability.Planning })
                }
            }
        }
    }

    internal fun configureJourneyPlanner(otpGraphQlUrl: String?) {
        val next = otpGraphQlUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (onlineTransitUrl == next) return
        onlineTransitUrl = next
        // Rebinding a screen is not a user cancellation. Existing requests retain the provider
        // snapshot they started with; a changed endpoint applies to the next planning request.
        onlineTransit = next?.let { OpenTripPlannerClient(httpClient, it) }
    }

    fun onJourneyPlanSelected(planId: String) {
        val plan = _uiState.value.journeyPlans.find { it.id == planId } ?: return
        _uiState.update { it.copy(navigation = it.navigation.choose(plan)) }
    }

    fun onNavigationStarted(mode: NavigationMode) {
        _uiState.update { it.copy(navigation = it.navigation.start(mode)) }
        synchronizeTracking()
    }

    fun onJourneyLegCompleted() {
        val advanced = _uiState.value.navigation.advance()
        if (advanced == NavigationSession.Idle) onNavigationCancelled()
        else {
            _uiState.update { it.copy(navigation = advanced) }
            synchronizeTracking()
        }
    }

    private fun synchronizeTracking() {
        val active = _uiState.value.navigation as? NavigationSession.Active
        trackingHost.setSession(active?.let { TrackingSession(navigationGeneration, it) })
    }

    fun onNavigationCancelled() {
        navigationGeneration++
        navigationJob?.cancel()
        navigationJob = null
        _uiState.update {
            it.copy(
                navigationDestination = null,
                journeyPlans = emptyList(),
                journeyGroups = emptyList(),
                navigation = NavigationSession.Idle,
                navigationIdentity = navigationGeneration,
                originRequest = OriginRequest.None,
                isJourneyPlanning = false,
            )
        }
        synchronizeTracking()
    }

    fun onShowOfflineRegionsChange(show: Boolean) {
        _uiState.update { it.copy(showOfflineRegions = show) }
    }

    fun onShowSearchMarkersChange(show: Boolean) {
        _uiState.update { it.copy(showSearchMarkers = show) }
    }

    fun onShowUserLocationChange(show: Boolean) {
        _uiState.update { it.copy(showUserLocation = show) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun showError(message: String) {
        _uiState.update { it.copy(error = message) }
    }

    override fun onCleared() {
        navigationJob?.cancel()
        searchJob?.cancel()
        trackingHost.close()
        super.onCleared()
        httpClient.close()
    }
}
