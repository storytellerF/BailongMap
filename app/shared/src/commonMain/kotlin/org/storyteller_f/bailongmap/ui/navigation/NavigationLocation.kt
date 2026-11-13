package org.storyteller_f.bailongmap.ui.navigation

import org.maplibre.compose.location.LocationMeasurement
import org.maplibre.spatialk.units.LengthUnit
import org.storyteller_f.bailongmap.data.model.RoutePoint

private val Meters = LengthUnit(1.0, "m")

internal fun LocationMeasurement.navigationFix(): NavigationFix = NavigationFix(
    RoutePoint(position.latitude, position.longitude), measuredAt, horizontalAccuracy?.toDouble(Meters),
)
