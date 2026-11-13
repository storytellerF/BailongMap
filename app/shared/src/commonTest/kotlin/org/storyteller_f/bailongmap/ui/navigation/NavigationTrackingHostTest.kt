package org.storyteller_f.bailongmap.ui.navigation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Instant
import org.storyteller_f.bailongmap.data.model.*

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationTrackingHostTest {
    private val a = RoutePoint(0.0, 0.0)
    private val b = RoutePoint(0.01, 0.0)
    private val middle = RoutePoint(0.005, 0.0)
    private fun session(points: List<RoutePoint>, generation: Int = 1) = TrackingSession(generation,
        NavigationSession.Active(JourneyPlan("route", listOf(JourneyLeg(TravelMode.WALK, points,
            3000.0, 1000.0, "Origin", "Destination"))), NavigationMode.IMMERSIVE))
    private fun TestScope.host(prepare: suspend (List<RoutePoint>) -> DirectedRoute = { DirectedRoute(it) }) =
        NavigationTrackingHost(StandardTestDispatcher(testScheduler), StandardTestDispatcher(testScheduler),
            now = { Instant.fromEpochMilliseconds(testScheduler.currentTime) }, prepareRoute = prepare)
    private fun TestScope.fix(point: RoutePoint, accuracy: Double = 3.0) =
        NavigationFix(point, Instant.fromEpochMilliseconds(testScheduler.currentTime), accuracy)
    private fun NavigationTrackingHost.active() = assertIs<SessionTracking.Active>(state.value.tracking)

    @Test fun closedOutAndBackFollowsOutboundThenReturnAndArrivesOnlyAtTheEnd() = runTest {
        val host = host()
        val reached = mutableListOf<TrackingSession>()
        host.reached.onEach { reached += it }.launchIn(backgroundScope)
        try {
            val session = session(listOf(a, b, a))
            host.setSession(session); runCurrent()
            host.onFix(fix(a)); runCurrent()
            assertTrue(reached.isEmpty())
            assertTrue(assertNotNull(host.active().progress).remainingMeters > 2000)
            host.onFix(fix(middle)); runCurrent()
            assertEquals(0.0, assertNotNull(host.active().confirmed).bearing, 0.01)
            host.onFix(fix(b)); runCurrent()
            host.onFix(fix(middle)); runCurrent()
            assertEquals(180.0, assertNotNull(host.active().confirmed).bearing, 0.01)
            assertTrue(reached.isEmpty())
            host.onFix(fix(a)); runCurrent()
            assertEquals(listOf(session), reached)
        } finally { host.close() }
    }

    @Test fun crossingUsesTheBranchContinuousWithConfirmedProgress() = runTest {
        val host = host()
        val cross = RoutePoint(0.005, 0.005)
        val p1 = RoutePoint(0.01, 0.01)
        val p2 = RoutePoint(0.0, 0.01)
        try {
            host.setSession(session(listOf(a, p1, p2, b, RoutePoint(0.02, 0.0)))); runCurrent()
            host.onFix(fix(cross)); runCurrent()
            assertEquals(0, assertNotNull(host.active().confirmed).segmentIndex)
            host.onFix(fix(p1)); runCurrent()
            host.onFix(fix(p2)); runCurrent()
            host.onFix(fix(cross)); runCurrent()
            assertEquals(2, assertNotNull(host.active().confirmed).segmentIndex)
        } finally { host.close() }
    }

    @Test fun returnLegIsRecognizedWithoutASampleAtTheUTurnVertex() = verifyMissedTurn(listOf(a, b, a))

    @Test fun addingCollinearVerticesDoesNotChangeMissedTurnRecovery() =
        verifyMissedTurn(listOf(a, RoutePoint(0.004, 0.0), b, RoutePoint(0.009, 0.0), middle, a))

    private fun verifyMissedTurn(points: List<RoutePoint>) = runTest {
        val host = host()
        val reached = mutableListOf<TrackingSession>()
        host.reached.onEach { reached += it }.launchIn(backgroundScope)
        try {
            host.setSession(session(points)); runCurrent()
            for (latitude in listOf(0.0, 0.005, 0.00995, 0.008, 0.007, 0.005)) {
                advanceTimeBy(1000)
                host.onFix(fix(RoutePoint(latitude, 0.0))); runCurrent()
            }
            assertEquals(180.0, assertNotNull(host.active().confirmed).bearing, 0.01)
            advanceTimeBy(1000)
            host.onFix(fix(middle)); runCurrent()
            assertEquals(180.0, assertNotNull(host.active().confirmed).bearing, 0.01)
            advanceTimeBy(1000)
            host.onFix(fix(a)); runCurrent()
            assertEquals(1, reached.size)
        } finally { host.close() }
    }

    @Test fun reversingBeforeReachingTheTurnDoesNotJumpToAFutureReturnSegment() = runTest {
        val host = host()
        try {
            host.setSession(session(listOf(a, b, a))); runCurrent()
            for (latitude in listOf(0.0, 0.005, 0.004, 0.003, 0.0)) {
                advanceTimeBy(1000)
                host.onFix(fix(RoutePoint(latitude, 0.0))); runCurrent()
                assertEquals(0, assertNotNull(host.active().confirmed).segmentIndex)
                assertTrue(assertNotNull(host.active().confirmed).remainingMeters > 1000)
            }
        } finally { host.close() }
    }

    @Test fun expiredLocationRetainsConfirmedProgressForNewScreenObservers() = runTest {
        val host = host()
        try {
            host.setSession(session(listOf(a, b))); runCurrent()
            host.onFix(fix(middle)); runCurrent()
            val confirmed = host.active().confirmed
            advanceTimeBy(30_000); runCurrent()
            assertEquals(NavigationLocation.Unreliable, host.state.value.location)
            assertNull(host.active().progress)
            // A recreated screen subscribes to this same ViewModel-owned snapshot.
            host.setSession(session(listOf(a, b))); runCurrent()
            assertEquals(confirmed, host.active().confirmed)
            assertEquals(middle.latitude, assertNotNull(host.active().confirmed).position.latitude, 0.000001)
        } finally { host.close() }
    }

    @Test fun poorAccuracyCannotMoveTheCameraOrCompleteTheLeg() = runTest {
        val host = host()
        val reached = mutableListOf<TrackingSession>()
        host.reached.onEach { reached += it }.launchIn(backgroundScope)
        try {
            host.setSession(session(listOf(a, b))); runCurrent()
            host.onFix(fix(middle)); runCurrent()
            val confirmed = host.active().confirmed
            host.onFix(fix(b, accuracy = 1000.0)); runCurrent()
            assertEquals(NavigationLocation.Unreliable, host.state.value.location)
            assertEquals(confirmed, host.active().confirmed)
            assertTrue(reached.isEmpty())
        } finally { host.close() }
    }

    @Test fun replacedSessionRejectsLateGeometryPreparation() = runTest {
        val gate = CompletableDeferred<Unit>()
        val host = host { points -> if (points.first() == a) gate.await(); DirectedRoute(points) }
        try {
            host.setSession(session(listOf(a, b))); runCurrent()
            val newRoute = listOf(RoutePoint(1.0, 1.0), RoutePoint(1.01, 1.0))
            val current = session(newRoute, generation = 2)
            host.setSession(current); runCurrent()
            host.onFix(fix(RoutePoint(1.005, 1.0))); runCurrent()
            val confirmed = host.active().confirmed
            gate.complete(Unit); runCurrent()
            assertEquals(current, host.active().session)
            assertEquals(confirmed, host.active().confirmed)
        } finally { host.close() }
    }

    @Test fun oldFixExpiryDoesNotInvalidateANewerFix() = runTest {
        val host = host()
        try {
            host.onFix(fix(a)); runCurrent()
            advanceTimeBy(10_000)
            host.onFix(fix(middle)); runCurrent()
            advanceTimeBy(20_000); runCurrent()
            assertIs<NavigationLocation.Ready>(host.state.value.location)
            advanceTimeBy(10_000); runCurrent()
            assertEquals(NavigationLocation.Unreliable, host.state.value.location)
        } finally { host.close() }
    }

    @Test fun repeatedEndpointFixesEmitOnlyOneArrivalPerLeg() = runTest {
        val host = host()
        val reached = mutableListOf<TrackingSession>()
        host.reached.onEach { reached += it }.launchIn(backgroundScope)
        try {
            host.setSession(session(listOf(a, b))); runCurrent()
            host.onFix(fix(b)); runCurrent()
            host.onFix(fix(b)); runCurrent()
            assertEquals(1, reached.size)
        } finally { host.close() }
    }
}
