package org.storyteller_f.bailongmap.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.storyteller_f.bailongmap.data.favorite.createFavoriteStore
import org.storyteller_f.bailongmap.data.model.FavoritePlace
import org.storyteller_f.bailongmap.data.model.JourneyPlan
import org.storyteller_f.bailongmap.data.model.Place
import org.storyteller_f.bailongmap.data.model.RoutePoint
import org.storyteller_f.bailongmap.data.network.JourneyPlanner
import org.storyteller_f.bailongmap.data.network.NominatimClient
import org.storyteller_f.bailongmap.data.network.OpenTripPlannerClient
import org.storyteller_f.bailongmap.data.network.OsrmClient
import org.storyteller_f.bailongmap.data.network.createHttpClient
import org.storyteller_f.bailongmap.data.settings.createMapSettingsStore
import org.storyteller_f.bailongmap.platform.createPlaceShareService

val MAP_STYLES = listOf(
    "标准" to "https://tiles.openfreemap.org/styles/liberty",
    "明亮" to "https://tiles.openfreemap.org/styles/bright",
    "简约" to "https://tiles.openfreemap.org/styles/positron",
)

data class MapUiState(
    val styleIndex: Int = 0,
    val searchQuery: String = "",
    val searchResults: List<Place> = emptyList(),
    val selectedPlace: Place? = null,
    val favoritePlaces: List<FavoritePlace> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val showOfflineRegions: Boolean = true,
    val showSearchMarkers: Boolean = true,
    val showUserLocation: Boolean = true,
    val isSearching: Boolean = false,
    val isSearchExpanded: Boolean = false,
    val navigationDestination: Place? = null,
    val journeyPlans: List<JourneyPlan> = emptyList(),
    val selectedJourneyPlan: JourneyPlan? = null,
    val activeJourneyLegIndex: Int = 0,
    val isJourneyPlanning: Boolean = false,
    val error: String? = null,
)

class MapViewModel : ViewModel() {
    private val httpClient = createHttpClient()
    private val nominatimClient = NominatimClient(httpClient)
    private var journeyPlanner: JourneyPlanner = OsrmClient(httpClient)
    private val favoriteStore = createFavoriteStore()
    private val settingsStore = createMapSettingsStore()
    private val placeShareService = createPlaceShareService()

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val searchQueryFlow = MutableStateFlow("")
    private var navigationJob: Job? = null

    init {
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

        @OptIn(FlowPreview::class)
        searchQueryFlow
            .debounce(600)
            .filter { it.length >= 2 }
            .onEach { query ->
                _uiState.update { it.copy(isSearching = true, error = null) }
                try {
                    val results = nominatimClient.search(query)
                    _uiState.update { it.copy(searchResults = results, isSearching = false) }
                } catch (e: Exception) {
                    _uiState.update { it.copy(isSearching = false, error = "搜索失败，请检查网络") }
                }
            }
            .launchIn(viewModelScope)
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                isSearchExpanded = query.isNotEmpty(),
                searchResults = if (query.isEmpty()) emptyList() else it.searchResults,
            )
        }
        searchQueryFlow.value = query
    }

    fun onPlaceSelected(place: Place) {
        _uiState.update { it.copy(selectedPlace = place, isSearchExpanded = false) }
    }

    fun onPlaceDeselected() {
        _uiState.update { it.copy(selectedPlace = null) }
    }

    fun onStyleChange(index: Int) {
        if (index !in MAP_STYLES.indices) return
        _uiState.update { it.copy(styleIndex = index) }
        viewModelScope.launch {
            runCatching { settingsStore.setDefaultStyleIndex(index) }
                .onFailure { showError("保存默认地图样式失败") }
        }
    }

    fun onToggleFavorite(place: Place) {
        viewModelScope.launch {
            runCatching {
                if (place.id in _uiState.value.favorites) favoriteStore.remove(place.id)
                else favoriteStore.add(place)
            }.onFailure {
                showError("更新收藏失败")
            }
        }
    }

    fun onSharePlace(place: Place) {
        runCatching { placeShareService.share(place) }
            .onFailure { showError("分享失败") }
    }

    fun onNavigationRequested(origin: RoutePoint, destination: Place) {
        navigationJob?.cancel()
        navigationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedPlace = null,
                    navigationDestination = destination,
                    journeyPlans = emptyList(),
                    selectedJourneyPlan = null,
                    activeJourneyLegIndex = 0,
                    isJourneyPlanning = true,
                    error = null,
                )
            }
            try {
                val plans = journeyPlanner.plans(
                    origin = origin,
                    destination = RoutePoint(
                        latitude = destination.lat,
                        longitude = destination.lon,
                    ),
                )
                _uiState.update {
                    it.copy(
                        journeyPlans = plans,
                        selectedJourneyPlan = plans.singleOrNull(),
                        activeJourneyLegIndex = 0,
                        isJourneyPlanning = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        navigationDestination = null,
                        journeyPlans = emptyList(),
                        selectedJourneyPlan = null,
                        activeJourneyLegIndex = 0,
                        isJourneyPlanning = false,
                        error = "行程规划失败，请检查路由服务或稍后重试",
                    )
                }
            }
        }
    }

    internal fun configureJourneyPlanner(otpGraphQlUrl: String?) {
        navigationJob?.cancel()
        navigationJob = null
        journeyPlanner = if (otpGraphQlUrl.isNullOrBlank()) {
            OsrmClient(httpClient)
        } else {
            OpenTripPlannerClient(httpClient, otpGraphQlUrl)
        }
    }

    fun onJourneyPlanSelected(planId: String) {
        val plan = _uiState.value.journeyPlans.find { it.id == planId } ?: return
        _uiState.update {
            it.copy(
                selectedJourneyPlan = plan,
                activeJourneyLegIndex = 0,
            )
        }
    }

    fun onJourneyLegCompleted() {
        val state = _uiState.value
        val plan = state.selectedJourneyPlan ?: return
        val nextIndex = state.activeJourneyLegIndex + 1
        if (nextIndex < plan.legs.size) {
            _uiState.update { it.copy(activeJourneyLegIndex = nextIndex) }
        } else {
            onNavigationCancelled()
        }
    }

    fun onNavigationCancelled() {
        navigationJob?.cancel()
        navigationJob = null
        _uiState.update {
            it.copy(
                navigationDestination = null,
                journeyPlans = emptyList(),
                selectedJourneyPlan = null,
                activeJourneyLegIndex = 0,
                isJourneyPlanning = false,
            )
        }
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
        super.onCleared()
        httpClient.close()
    }
}
