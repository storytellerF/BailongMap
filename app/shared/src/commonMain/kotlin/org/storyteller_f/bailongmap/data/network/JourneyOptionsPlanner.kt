package org.storyteller_f.bailongmap.data.network

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import org.storyteller_f.bailongmap.data.model.*

enum class JourneyCategory(val label: String) { CAR("驾车"), BICYCLE("骑行"), TRANSIT("公共交通"), WALK("步行") }
sealed interface JourneyAvailability {
    data object Planning : JourneyAvailability
    data class Available(val plans: List<JourneyPlan>) : JourneyAvailability
    data class Unavailable(val explanation: String) : JourneyAvailability
}
data class JourneyGroup(val category: JourneyCategory, val availability: JourneyAvailability)
data class JourneyProvider(val category: JourneyCategory, val planner: JourneyPlanner, val unavailable: String)

/** Providers are independent. A miss or failure never changes another category's route. */
class JourneyOptionsPlanner(
    private val providers: List<JourneyProvider>,
    private val timeoutMillis: Long = 60000,
    private val coordination: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
) {
    init { require(providers.map { it.category }.distinct().size == providers.size) }
    val initial = providers.map { JourneyGroup(it.category, JourneyAvailability.Planning) }
    fun options(origin: RoutePoint, destination: RoutePoint): Flow<JourneyGroup> = channelFlow {
        providers.forEach { provider -> launch {
            val plans = try {
                withTimeoutOrNull(timeoutMillis) { provider.planner.plans(origin,destination) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            val result = if (plans.isNullOrEmpty()) JourneyAvailability.Unavailable(provider.unavailable)
                else JourneyAvailability.Available(plans.mapIndexed { index, plan -> plan.copy(id="${provider.category.name}:$index:${plan.id}") })
            send(JourneyGroup(provider.category,result))
        } }
    }.flowOn(coordination)
}

/** Prefer imported road data, then use the category's own online provider if available. */
class RoadJourneyPlanner(private val offline: JourneyPlanner, private val online: JourneyPlanner) : JourneyPlanner {
    override suspend fun plans(origin: RoutePoint, destination: RoutePoint): List<JourneyPlan> {
        try { offline.plans(origin,destination).takeIf { it.isNotEmpty() }?.let { return it } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { }
        return online.plans(origin,destination).map { it.copy(notice=listOfNotNull(it.notice,"在线道路规划").joinToString("；")) }
    }
}
