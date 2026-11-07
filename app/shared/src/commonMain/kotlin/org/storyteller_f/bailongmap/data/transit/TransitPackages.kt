package org.storyteller_f.bailongmap.data.transit

import kotlinx.coroutines.flow.StateFlow
import org.storyteller_f.bailongmap.data.network.JourneyPlanner
import org.storyteller_f.bailongmap.data.model.*

data class TransitPackageInfo(val name: String, val source: String, val start: String, val end: String, val stops: Int, val routes: Int)

interface TransitPackages : JourneyPlanner {
    val installed: StateFlow<TransitPackageInfo?>
    suspend fun allowsBicycles(): Boolean = false
    suspend fun plansWithAccess(origin: RoutePoint,destination: RoutePoint,streets: JourneyPlanner,mode: TravelMode): List<JourneyPlan> =
        if (mode==TravelMode.WALK) plans(origin,destination) else emptyList()
    suspend fun load()
    suspend fun preview(document: String): TransitPackageInfo
    suspend fun install()
    suspend fun discard()
    suspend fun remove()
}

expect fun createTransitPackages(): TransitPackages
