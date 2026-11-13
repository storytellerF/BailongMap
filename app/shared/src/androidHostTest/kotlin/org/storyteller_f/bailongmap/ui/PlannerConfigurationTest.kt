package org.storyteller_f.bailongmap.ui

import androidx.lifecycle.ViewModelStore
import kotlin.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.storyteller_f.bailongmap.data.model.Place
import org.storyteller_f.bailongmap.platform.AndroidAppContext
import org.storyteller_f.bailongmap.ui.map.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlannerConfigurationTest {
    private val store = ViewModelStore()
    private lateinit var model: MapViewModel
    private val place = Place("test", "Destination", "Example address", 0.0, 0.0, "", "")

    @Before fun setUp() {
        AndroidAppContext.initialize(RuntimeEnvironment.getApplication())
        model = MapViewModel()
        store.put("map", model)
    }

    @After fun tearDown() { store.clear() }

    @Test fun rebindingSameConfigurationPreservesManualOriginSelection() {
        model.onNavigateToPlace(place)
        val request = assertIs<OriginRequest.Explain>(model.uiState.value.originRequest)
        model.onManualOriginRequested(request.id)
        val manual = model.uiState.value.originRequest
        model.configureJourneyPlanner(null)
        assertEquals(manual, model.uiState.value.originRequest)
    }

    @Test fun rebindingSameConfigurationPreservesWaitingForLocation() {
        model.onNavigateToPlace(place)
        val request = assertIs<OriginRequest.Explain>(model.uiState.value.originRequest)
        model.onOriginLocationRequested(request.id)
        val waiting = model.uiState.value.originRequest
        model.configureJourneyPlanner(null)
        assertEquals(waiting, model.uiState.value.originRequest)
    }
}
