package org.storyteller_f.bailongmap.data.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.storyteller_f.bailongmap.data.model.*
import org.storyteller_f.bailongmap.data.transit.TransitPackages

/** Door-to-door transit with street routing; carrying a bicycle requires explicit GTFS permission. */
class TransitJourneyPlanner(
    private val packages: TransitPackages,
    private val walking: JourneyPlanner,
    private val cycling: JourneyPlanner,
    private val online: JourneyPlanner? = null,
) : JourneyPlanner {
    override suspend fun plans(origin: RoutePoint, destination: RoutePoint): List<JourneyPlan> = coroutineScope {
        suspend fun attempt(block: suspend () -> List<JourneyPlan>): List<JourneyPlan> =
            try { block() } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { emptyList() }
        // Start the optional budget with the request, rather than after walking:
        // a slow successful walking search must not gain another 12 seconds of work.
        val cyclingPlans = async {
            attempt {
                withTimeoutOrNull(12000) {
                    if (packages.allowsBicycles()) packages.plansWithAccess(origin,destination,cycling,TravelMode.BICYCLE)
                    else emptyList()
                }.orEmpty()
            }
        }
        val walkingPlans = attempt { packages.plansWithAccess(origin,destination,walking,TravelMode.WALK) }
        val local = walkingPlans + cyclingPlans.await()
        local.ifEmpty { online?.let { attempt { it.plans(origin,destination) } }.orEmpty() }
            .filter { plan -> plan.legs.any { it.mode==TravelMode.TRANSIT || it.mode==TravelMode.SUBWAY } }
    }
}
