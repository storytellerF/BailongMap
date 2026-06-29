package org.storyteller_f.bailongmap.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import org.storyteller_f.bailongmap.data.model.Place
import org.storyteller_f.bailongmap.data.network.NominatimClient
import org.storyteller_f.bailongmap.data.network.createHttpClient

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
    val favorites: Set<String> = emptySet(),
    val isSearching: Boolean = false,
    val isSearchExpanded: Boolean = false,
    val error: String? = null,
)

class MapViewModel : ViewModel() {
    private val httpClient = createHttpClient()
    private val nominatimClient = NominatimClient(httpClient)

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val searchQueryFlow = MutableStateFlow("")

    init {
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
        _uiState.update { it.copy(styleIndex = index) }
    }

    fun onToggleFavorite(place: Place) {
        _uiState.update { state ->
            val newFavorites =
                if (place.id in state.favorites) state.favorites - place.id
                else state.favorites + place.id
            state.copy(favorites = newFavorites)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        httpClient.close()
    }
}
