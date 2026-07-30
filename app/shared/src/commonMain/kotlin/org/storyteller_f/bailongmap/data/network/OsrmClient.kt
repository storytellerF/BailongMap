package org.storyteller_f.bailongmap.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable
import org.storyteller_f.bailongmap.data.model.JourneyLeg
import org.storyteller_f.bailongmap.data.model.JourneyPlan
import org.storyteller_f.bailongmap.data.model.RoutePoint
import org.storyteller_f.bailongmap.data.model.TravelMode

@Serializable
internal data class OsrmResponse(
    val code: String,
    val routes: List<OsrmRoute> = emptyList(),
)

@Serializable
internal data class OsrmRoute(
    val distance: Double,
    val duration: Double,
    val geometry: OsrmGeometry,
)

@Serializable
internal data class OsrmGeometry(
    val coordinates: List<List<Double>>,
)

class OsrmClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://router.project-osrm.org",
) : JourneyPlanner {
    override suspend fun plans(
        origin: RoutePoint,
        destination: RoutePoint,
    ): List<JourneyPlan> {
        val coordinates =
            "${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}"
        val response: OsrmResponse =
            httpClient.get("$baseUrl/route/v1/driving/$coordinates") {
                parameter("overview", "full")
                parameter("geometries", "geojson")
                parameter("steps", "false")
                header("User-Agent", "BailongMap/1.0 (open-source map app)")
            }.body()

        return listOf(response.toJourneyPlan())
    }
}

internal fun OsrmResponse.toJourneyPlan(): JourneyPlan {
    check(code == "Ok") { "OSRM returned status $code" }
    val route = routes.firstOrNull() ?: error("OSRM returned no routes")
    val points = route.geometry.coordinates.map { coordinate ->
        require(coordinate.size >= 2) { "Invalid route coordinate" }
        val longitude = coordinate[0]
        val latitude = coordinate[1]
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "Invalid route longitude"
        }
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "Invalid route latitude"
        }
        RoutePoint(latitude = latitude, longitude = longitude)
    }
    return JourneyPlan(
        id = "osrm-driving",
        legs = listOf(
            JourneyLeg(
                mode = TravelMode.CAR,
                points = points,
                distanceMeters = route.distance,
                durationSeconds = route.duration,
                fromName = "当前位置",
                toName = "目的地",
            )
        ),
    )
}
