package org.storyteller_f.bailongmap.ui.navigation

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlin.math.max
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.seconds
import org.storyteller_f.bailongmap.data.model.RoutePoint

data class NavigationFix(val point: RoutePoint, val measuredAt: Instant, val accuracyMeters: Double?)

sealed interface NavigationLocation {
    data object Waiting : NavigationLocation
    data object Unreliable : NavigationLocation
    data class Ready(val fix: NavigationFix) : NavigationLocation
}

internal fun navigationLocation(fix: NavigationFix?, now: Instant): NavigationLocation {
    if (fix == null) return NavigationLocation.Waiting
    val age = now - fix.measuredAt
    val accuracy = fix.accuracyMeters
    return if (age >= (-5).seconds && age < 30.seconds &&
        (accuracy == null || (accuracy.isFinite() && accuracy in 0.0..80.0))) NavigationLocation.Ready(fix)
    else NavigationLocation.Unreliable
}

data class TrackingSession(val generation: Int, val navigation: NavigationSession.Active)

sealed interface SessionTracking {
    data object Idle : SessionTracking
    data class Active(val session: TrackingSession, val progress: RouteProgress? = null,
        val confirmed: RouteProgress? = null) : SessionTracking
}

data class NavigationTrackingState(
    val location: NavigationLocation = NavigationLocation.Waiting,
    val tracking: SessionTracking = SessionTracking.Idle,
)

/** Owns fix expiry and route continuity for the ViewModel lifetime, independent of screen recreation. */
internal class NavigationTrackingHost(
    coordination: CoroutineDispatcher,
    private val worker: CoroutineDispatcher,
    private val now: () -> Instant = { Clock.System.now() },
    private val prepareRoute: suspend (List<RoutePoint>) -> DirectedRoute = { DirectedRoute(it) },
) {
    private data class Tracker(val session: TrackingSession, val route: DirectedRoute,
        val cursor: Double = 0.0, val arrived: Boolean = false, val anchor: NavigationFix? = null)
    private val scope = CoroutineScope(SupervisorJob() + coordination)
    private val mutable = MutableStateFlow(NavigationTrackingState())
    val state = mutable.asStateFlow()
    private val arrivals = Channel<TrackingSession>(Channel.BUFFERED)
    val reached = arrivals.receiveAsFlow()
    private var tracker: Tracker? = null
    private var latestFix: NavigationFix? = null
    private var revision = 0L
    private var sessionRevision = 0L
    private var expiry: Job? = null

    fun setSession(session: TrackingSession?) = scope.launch {
        if ((mutable.value.tracking as? SessionTracking.Active)?.session == session) return@launch
        val generation = ++sessionRevision
        revision++
        tracker = null
        mutable.value = mutable.value.copy(tracking = session?.let { SessionTracking.Active(it) } ?: SessionTracking.Idle)
        if (session != null) {
            val leg = session.navigation.plan.legs[session.navigation.legIndex]
            val (route, initial) = withContext(worker) {
                val route = prepareRoute(leg.points)
                route to route.match(leg.points.first())
            }
            if (generation != sessionRevision) return@launch
            tracker = Tracker(session, route)
            mutable.value = mutable.value.copy(tracking = SessionTracking.Active(session, confirmed = initial))
            refresh()
        }
    }

    fun onFix(fix: NavigationFix?) = scope.launch {
        revision++
        latestFix = fix
        expiry?.cancel()
        if (fix != null && navigationLocation(fix, now()) is NavigationLocation.Ready) expiry = scope.launch {
            val remaining = 30.seconds - (now() - fix.measuredAt)
            if (remaining.isPositive()) delay(remaining)
            revision++
            refresh()
        }
        refresh()
    }

    private suspend fun refresh() {
        val version = revision
        val location = navigationLocation(latestFix, now())
        mutable.value = mutable.value.copy(location = location)
        val current = tracker ?: return
        val displayed = mutable.value.tracking as? SessionTracking.Active ?: return
        if (location !is NavigationLocation.Ready) {
            mutable.value = mutable.value.copy(tracking = displayed.copy(progress = null))
            return
        }
        val anchor = current.anchor
        val elapsed = anchor?.let { location.fix.measuredAt - it.measuredAt }
        val displacement = anchor?.point?.distanceTo(location.fix.point) ?: 0.0
        val minimumMovement = max(8.0, (location.fix.accuracyMeters ?: 3.0).coerceAtMost(15.0) * 2)
        val moving = elapsed != null && elapsed.isPositive() && elapsed <= 30.seconds && displacement >= minimumMovement
        val travelBearing = if (moving) anchor.point.bearingTo(location.fix.point) else null
        val match = withContext(worker) { current.route.match(location.fix.point, current.cursor, travelBearing) }
        if (version != revision || tracker?.session != current.session) return
        val accepted = match != null && match.distanceFromRouteMeters <= 80.0
        val leg = current.session.navigation.plan.legs[current.session.navigation.legIndex]
        val arrived = accepted && match.remainingMeters <= 45.0 && location.fix.point.distanceTo(leg.points.last()) <= 45.0
        tracker = current.copy(cursor = if (accepted) max(current.cursor, match.distanceAlongMeters) else current.cursor,
            arrived = current.arrived || arrived,
            anchor = if (accepted && (anchor == null || moving || elapsed == null || elapsed > 30.seconds)) location.fix else anchor)
        mutable.value = mutable.value.copy(tracking = displayed.copy(progress = match,
            confirmed = if (accepted) match else displayed.confirmed))
        if (arrived && !current.arrived) arrivals.send(current.session)
    }

    fun close() { scope.cancel() }
}
