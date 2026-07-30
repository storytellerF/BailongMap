package org.storyteller_f.bailongmap.data.network

import org.storyteller_f.bailongmap.data.model.JourneyPlan
import org.storyteller_f.bailongmap.data.model.RoutePoint

fun interface JourneyPlanner {
    suspend fun plans(
        origin: RoutePoint,
        destination: RoutePoint,
    ): List<JourneyPlan>
}
