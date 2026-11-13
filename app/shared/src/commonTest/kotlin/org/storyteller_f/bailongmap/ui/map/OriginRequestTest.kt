package org.storyteller_f.bailongmap.ui.map

import kotlin.test.*
import kotlin.time.Instant
import org.storyteller_f.bailongmap.data.model.*
import org.storyteller_f.bailongmap.ui.navigation.*

class OriginRequestTest {
    private val a = Place("a", "A", "A", 0.0, 0.0, "", "")
    private val b = a.copy(id = "b", name = "B")

    @Test fun selectingNewPlaceCancelsManualOriginAndRejectsItsLateActions() {
        val previous = OriginRequest.Manual(1, a)
        val state = MapUiState(originRequest = previous).selectPlace(b)
        assertEquals(b, state.selectedPlace)
        assertEquals(OriginRequest.None, state.originRequest)
        assertEquals(OriginRequest.None, state.originRequest.cancel(previous))
        assertEquals(OriginRequest.None, state.originRequest.chooseManually(1))
    }

    @Test fun selectingNewPlaceCancelsWaitingForThePreviousDestination() {
        val state = MapUiState(originRequest = OriginRequest.Waiting(1, a)).selectPlace(b)
        assertEquals(OriginRequest.None, state.originRequest)
        assertEquals(b, state.selectedPlace)
    }

    @Test fun staleSheetDismissalCannotCancelTheNextOriginStep() {
        val explanation = OriginRequest.Explain(1, a)
        val manual = explanation.chooseManually(1)
        assertEquals(manual, manual.cancel(explanation))
    }

    @Test fun originReadinessRejectsStaleCoarseAndFutureFixes() {
        val now = Instant.fromEpochMilliseconds(60_000)
        val point = RoutePoint(0.0, 0.0)
        assertIs<NavigationLocation.Ready>(navigationLocation(NavigationFix(point, now, 3.0), now))
        assertEquals(NavigationLocation.Unreliable,
            navigationLocation(NavigationFix(point, Instant.fromEpochMilliseconds(0), 3.0), now))
        assertEquals(NavigationLocation.Unreliable, navigationLocation(NavigationFix(point, now, 1000.0), now))
        assertEquals(NavigationLocation.Unreliable,
            navigationLocation(NavigationFix(point, Instant.fromEpochMilliseconds(120_000), 3.0), now))
    }
}
