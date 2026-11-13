package org.storyteller_f.bailongmap.ui.navigation

import kotlin.test.*
import org.storyteller_f.bailongmap.data.model.*

class NavigationSessionTest {
    private val plan = JourneyPlan("test", listOf(
        JourneyLeg(TravelMode.WALK, listOf(RoutePoint(0.0, 0.0), RoutePoint(0.01, 0.0)), 1000.0, 600.0, "A", "B"),
        JourneyLeg(TravelMode.SUBWAY, listOf(RoutePoint(0.01, 0.0), RoutePoint(0.02, 0.0)), 1000.0, 180.0, "B", "C"),
    ))

    @Test fun choosingDoesNotStartNavigation() {
        val choosing = NavigationSession.Idle.choose(plan)
        assertIs<NavigationSession.Choosing>(choosing)
        assertEquals(plan, choosing.plan)
    }

    @Test fun activeJourneyRejectsAnotherModeAndPlanChoice() {
        val active = NavigationSession.Idle.choose(plan).start(NavigationMode.IMMERSIVE)
        assertEquals(active, active.start(NavigationMode.PREVIEW))
        assertEquals(active, active.choose(plan.copy(id = "other")))
    }

    @Test fun advancingPreservesTheLockedModeUntilJourneyFinishes() {
        val active = NavigationSession.Idle.choose(plan).start(NavigationMode.PREVIEW)
        val next = assertIs<NavigationSession.Active>(active.advance())
        assertEquals(1, next.legIndex)
        assertEquals(NavigationMode.PREVIEW, next.mode)
        assertEquals(NavigationSession.Idle, next.advance())
    }

    @Test fun nextJourneyCanChooseAnotherMode() {
        val finished = NavigationSession.Idle.choose(plan).start(NavigationMode.PREVIEW).advance().advance()
        val next = assertIs<NavigationSession.Active>(finished.choose(plan).start(NavigationMode.IMMERSIVE))
        assertEquals(NavigationMode.IMMERSIVE, next.mode)
    }
}
