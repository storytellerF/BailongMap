package org.storyteller_f.bailongmap.data.transit

import kotlinx.coroutines.flow.StateFlow
import org.storyteller_f.bailongmap.data.network.JourneyPlanner
import org.storyteller_f.bailongmap.data.model.*

data class TransitPackageInfo(val name: String, val source: String, val start: String, val end: String, val stops: Int, val routes: Int, val timeZone: String = "UTC")

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

/** Current YYYYMMDD service date in the package's own time zone. */
expect fun currentServiceDate(timeZone: String): String

enum class ServiceDateStatus(val label: String) { UPCOMING("尚未生效"), VALID("日期范围内"), EXPIRED("已过期") }

fun TransitPackageInfo.serviceDateStatus(today: String): ServiceDateStatus = when {
    today < start -> ServiceDateStatus.UPCOMING
    today > end -> ServiceDateStatus.EXPIRED
    else -> ServiceDateStatus.VALID
}
