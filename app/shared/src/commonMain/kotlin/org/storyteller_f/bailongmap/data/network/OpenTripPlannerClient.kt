package org.storyteller_f.bailongmap.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import org.storyteller_f.bailongmap.data.model.JourneyLeg
import org.storyteller_f.bailongmap.data.model.JourneyPlan
import org.storyteller_f.bailongmap.data.model.RoutePoint
import org.storyteller_f.bailongmap.data.model.TravelMode

@Serializable
internal data class OtpGraphQlRequest(
    val query: String,
    val variables: OtpPlanVariables,
)

@Serializable
internal data class OtpPlanVariables(
    val from: OtpInputCoordinates,
    val to: OtpInputCoordinates,
)

@Serializable
internal data class OtpInputCoordinates(
    val lat: Double,
    val lon: Double,
)

@Serializable
internal data class OtpGraphQlResponse(
    val data: OtpData? = null,
    val errors: List<OtpGraphQlError> = emptyList(),
)

@Serializable
internal data class OtpGraphQlError(
    val message: String,
)

@Serializable
internal data class OtpData(
    val plan: OtpPlan? = null,
)

@Serializable
internal data class OtpPlan(
    val itineraries: List<OtpItinerary> = emptyList(),
)

@Serializable
internal data class OtpItinerary(
    val duration: Double,
    val legs: List<OtpLeg> = emptyList(),
)

@Serializable
internal data class OtpLeg(
    val mode: String,
    val distance: Double,
    val duration: Double,
    val from: OtpPlace,
    val to: OtpPlace,
    val route: OtpRoute? = null,
    val legGeometry: OtpLegGeometry? = null,
)

@Serializable
internal data class OtpPlace(
    val name: String,
    val lat: Double,
    val lon: Double,
)

@Serializable
internal data class OtpRoute(
    val shortName: String? = null,
    val longName: String? = null,
)

@Serializable
internal data class OtpLegGeometry(
    val points: String,
)

class OpenTripPlannerClient(
    private val httpClient: HttpClient,
    private val graphQlUrl: String,
) : JourneyPlanner {
    init {
        require(graphQlUrl.isNotBlank()) { "OpenTripPlanner GraphQL URL must not be blank" }
    }

    override suspend fun plans(
        origin: RoutePoint,
        destination: RoutePoint,
    ): List<JourneyPlan> {
        val response: OtpGraphQlResponse = httpClient.post(graphQlUrl) {
            contentType(ContentType.Application.Json)
            header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            header("User-Agent", "BailongMap/1.0 (open-source map app)")
            setBody(
                OtpGraphQlRequest(
                    query = JOURNEY_PLANS_QUERY,
                    variables = OtpPlanVariables(
                        from = OtpInputCoordinates(origin.latitude, origin.longitude),
                        to = OtpInputCoordinates(destination.latitude, destination.longitude),
                    ),
                )
            )
        }.body()

        check(response.errors.isEmpty()) {
            response.errors.joinToString(
                prefix = "OpenTripPlanner returned GraphQL errors: ",
                transform = OtpGraphQlError::message,
            )
        }
        return response.toJourneyPlans()
    }

    companion object {
        private const val JOURNEY_PLANS_QUERY = """
            query BailongMapJourneyPlans(
              ${'$'}from: InputCoordinates!,
              ${'$'}to: InputCoordinates!
            ) {
              plan(
                from: ${'$'}from,
                to: ${'$'}to,
                numItineraries: 3,
                transportModes: [
                  { mode: WALK },
                  { mode: TRANSIT },
                  { mode: BICYCLE }
                ]
              ) {
                itineraries {
                  duration
                  legs {
                    mode
                    distance
                    duration
                    from {
                      name
                      lat
                      lon
                    }
                    to {
                      name
                      lat
                      lon
                    }
                    route {
                      shortName
                      longName
                    }
                    legGeometry {
                      points
                    }
                  }
                }
              }
            }
        """
    }
}

internal fun OtpGraphQlResponse.toJourneyPlans(): List<JourneyPlan> {
    val itineraries = data?.plan?.itineraries.orEmpty()
    check(itineraries.isNotEmpty()) { "OpenTripPlanner returned no journey plans" }

    return itineraries.mapIndexed { index, itinerary ->
        val legs = itinerary.legs.map(OtpLeg::toJourneyLeg)
        check(legs.isNotEmpty()) { "OpenTripPlanner itinerary contains no legs" }
        JourneyPlan(
            id = "otp-${index + 1}",
            legs = legs,
            durationSeconds = itinerary.duration,
        )
    }
}

private fun OtpLeg.toJourneyLeg(): JourneyLeg {
    val endpoints = listOf(from.toRoutePoint(), to.toRoutePoint())
    val points = legGeometry
        ?.points
        ?.let(::decodePolyline)
        ?.takeIf { it.size >= 2 }
        ?: endpoints
    return JourneyLeg(
        mode = mode.toTravelMode(),
        points = points,
        distanceMeters = distance,
        durationSeconds = duration,
        fromName = from.name.ifBlank { "阶段起点" },
        toName = to.name.ifBlank { "阶段终点" },
        routeName = route?.shortName?.takeIf(String::isNotBlank)
            ?: route?.longName?.takeIf(String::isNotBlank),
    )
}

private fun OtpPlace.toRoutePoint(): RoutePoint =
    RoutePoint(latitude = lat, longitude = lon)

private fun String.toTravelMode(): TravelMode = when (uppercase()) {
    "WALK" -> TravelMode.WALK
    "SUBWAY" -> TravelMode.SUBWAY
    "BICYCLE" -> TravelMode.BICYCLE
    "CAR" -> TravelMode.CAR
    else -> TravelMode.TRANSIT
}

internal fun decodePolyline(encoded: String): List<RoutePoint> {
    require(encoded.isNotEmpty()) { "Encoded polyline must not be empty" }
    val points = mutableListOf<RoutePoint>()
    var index = 0
    var latitude = 0
    var longitude = 0

    while (index < encoded.length) {
        val latitudeValue = decodePolylineValue(encoded, index)
        index = latitudeValue.nextIndex
        latitude += latitudeValue.delta

        val longitudeValue = decodePolylineValue(encoded, index)
        index = longitudeValue.nextIndex
        longitude += longitudeValue.delta

        points += RoutePoint(
            latitude = latitude / POLYLINE_PRECISION,
            longitude = longitude / POLYLINE_PRECISION,
        )
    }
    return points
}

private data class DecodedPolylineValue(
    val delta: Int,
    val nextIndex: Int,
)

private fun decodePolylineValue(encoded: String, startIndex: Int): DecodedPolylineValue {
    var index = startIndex
    var result = 0
    var shift = 0
    var chunk: Int
    do {
        require(index < encoded.length) { "Truncated encoded polyline" }
        chunk = encoded[index++].code - 63
        require(chunk >= 0) { "Invalid encoded polyline character" }
        result = result or ((chunk and 0x1f) shl shift)
        shift += 5
        require(shift <= 30) { "Encoded polyline value is too large" }
    } while (chunk >= 0x20)

    val delta = if (result and 1 != 0) {
        (result shr 1).inv()
    } else {
        result shr 1
    }
    return DecodedPolylineValue(delta = delta, nextIndex = index)
}

private const val POLYLINE_PRECISION = 100_000.0
