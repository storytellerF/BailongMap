package org.storyteller_f.bailongmap.ui.navigation

import kotlin.test.*
import org.storyteller_f.bailongmap.data.model.RoutePoint

class RouteProgressTest {
    private val route = listOf(RoutePoint(0.0, 0.0), RoutePoint(0.01, 0.0), RoutePoint(0.01, 0.01))

    @Test fun northboundSegmentFacesScreenTopBeforeTurn() {
        val match = assertNotNull(routeProgress(route, RoutePoint(0.005, 0.00001)))
        assertEquals(0, match.segmentIndex)
        assertEquals(0.0, match.bearing, 0.001)
        assertEquals(0.0, match.position.longitude, 0.000001)
        assertTrue(match.remainingMeters in 1600.0..1700.0)
    }

    @Test fun cameraUsesEastboundRouteDirectionAfterRightTurn() {
        val match = assertNotNull(routeProgress(route, RoutePoint(0.01001, 0.005)))
        assertEquals(1, match.segmentIndex)
        assertEquals(90.0, match.bearing, 0.01)
        assertTrue(match.remainingMeters in 550.0..560.0)
    }

    @Test fun duplicateNodesDoNotInventANorthBearing() {
        val a = RoutePoint(0.0, 0.0)
        val match = assertNotNull(routeProgress(listOf(a, a, RoutePoint(0.0, 0.01)), a))
        assertEquals(90.0, match.bearing, 0.001)
        assertNull(routeProgress(listOf(a, a), a))
    }

    @Test fun offRouteDistanceCanPreventCameraJumps() {
        val match = assertNotNull(routeProgress(route, RoutePoint(1.0, 1.0)))
        assertTrue(match.distanceFromRouteMeters > 80.0)
    }

    @Test fun dateLineCrossingUsesTheShortDirectedSegment() {
        val match = assertNotNull(routeProgress(listOf(RoutePoint(0.0, 179.99), RoutePoint(0.0, -179.99)), RoutePoint(0.0, 180.0)))
        assertEquals(90.0, match.bearing, 0.001)
        assertTrue(match.remainingMeters in 1100.0..1120.0)
    }
}
