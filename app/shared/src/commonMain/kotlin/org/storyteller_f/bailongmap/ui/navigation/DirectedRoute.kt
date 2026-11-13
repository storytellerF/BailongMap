package org.storyteller_f.bailongmap.ui.navigation

import kotlin.math.*
import org.storyteller_f.bailongmap.data.model.RoutePoint

data class RouteProgress(
    val position: RoutePoint,
    val bearing: Double,
    val segmentIndex: Int,
    val distanceFromRouteMeters: Double,
    val remainingMeters: Double,
    val distanceAlongMeters: Double,
)

/** Immutable geometry shared by a session; the session owns its confirmed distance cursor. */
internal class DirectedRoute(points: List<RoutePoint>) {
    private data class Segment(val index: Int, val from: RoutePoint, val to: RoutePoint,
        val length: Double, val start: Double, val bearing: Double, val directionEntry: Double)
    private val segments: List<Segment>
    private val length: Double

    init {
        var distance = 0.0
        var directionEntry = 0.0
        var entryBearing: Double? = null
        segments = buildList {
            points.zipWithNext().forEachIndexed { index, (a, b) ->
                val meters = a.distanceTo(b)
                if (meters > 0.001) {
                    val bearing = a.bearingTo(b)
                    val previousEntryBearing = entryBearing
                    if (previousEntryBearing == null || angleDifference(previousEntryBearing, bearing) > 45.0) {
                        directionEntry = distance
                        entryBearing = bearing
                    }
                    add(Segment(index, a, b, meters, distance, bearing, directionEntry))
                    distance += meters
                }
            }
        }
        length = distance
    }

    /** Ambiguous overlapping segments are resolved relative to confirmed progress, never the last index. */
    fun match(location: RoutePoint, cursorMeters: Double = 0.0, travelBearing: Double? = null): RouteProgress? {
        val scale = cos(location.latitude.radians()).coerceAtLeast(0.000001)
        val candidates = segments.map { segment ->
            val a = segment.from
            val b = segment.to
            val dx = longitudeDelta(b.longitude - a.longitude) * scale
            val dy = b.latitude - a.latitude
            val squared = dx * dx + dy * dy
            val t = if (squared > 0) ((longitudeDelta(location.longitude - a.longitude) * scale * dx +
                (location.latitude - a.latitude) * dy) / squared).coerceIn(0.0, 1.0) else 0.0
            val snapped = RoutePoint(a.latitude + dy * t,
                longitudeDelta(a.longitude + longitudeDelta(b.longitude - a.longitude) * t))
            val along = segment.start + segment.length * t
            RouteProgress(snapped, segment.bearing, segment.index, location.distanceTo(snapped),
                (length - along).coerceAtLeast(0.0), along)
        }
        val nearest = candidates.minOfOrNull { it.distanceFromRouteMeters } ?: return null
        // GPS uncertainty can place one fix on both sides of a crossing. Prefer the continuous
        // branch in that local ambiguity band; unambiguous fixes may still recover far ahead.
        val ambiguous = candidates.filter { it.distanceFromRouteMeters <= nearest + 8.0 }
        val continuity = compareBy<RouteProgress> { abs(it.distanceAlongMeters - cursorMeters).roundToLong() }
            .thenBy { if (it.distanceAlongMeters >= cursorMeters) 0 else 1 }
            .thenByDescending { it.segmentIndex }
        val continuous = ambiguous.minWithOrNull(continuity) ?: return null
        // Once the route reaches a turn, its next segment remains eligible even when the
        // next fix is far down that segment. Eligibility belongs to the entry vertex,
        // not a fixed distance window around the latest projected point.
        if (travelBearing != null && continuous.distanceAlongMeters < cursorMeters - 8.0 &&
            angleDifference(continuous.bearing, travelBearing) >= 120.0) {
            val enteredSegments = segments.takeWhile { it.directionEntry <= cursorMeters + 45.0 }
                .mapTo(mutableSetOf()) { it.index }
            val returning = ambiguous.filter { it.distanceAlongMeters >= cursorMeters &&
                it.segmentIndex in enteredSegments && angleDifference(it.bearing, travelBearing) <= 45.0 }
                .minWithOrNull(continuity)
            if (returning != null) return returning
        }
        return continuous
    }
}

internal fun routeProgress(points: List<RoutePoint>, location: RoutePoint): RouteProgress? =
    DirectedRoute(points).match(location)

internal fun RoutePoint.distanceTo(other: RoutePoint): Double {
    val lat = (other.latitude - latitude).radians()
    val lon = longitudeDelta(other.longitude - longitude).radians()
    val h = sin(lat / 2).pow(2) + cos(latitude.radians()) * cos(other.latitude.radians()) * sin(lon / 2).pow(2)
    return 2 * 6_371_000.0 * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

internal fun RoutePoint.bearingTo(other: RoutePoint): Double {
    val lon = longitudeDelta(other.longitude - longitude).radians()
    val y = sin(lon) * cos(other.latitude.radians())
    val x = cos(latitude.radians()) * sin(other.latitude.radians()) -
        sin(latitude.radians()) * cos(other.latitude.radians()) * cos(lon)
    return (atan2(y, x) * 180 / PI + 360) % 360
}

private fun Double.radians() = this * PI / 180
private fun longitudeDelta(value: Double) = ((value + 540) % 360) - 180

private fun angleDifference(a: Double, b: Double) = abs(((a - b + 540) % 360) - 180)
