package org.storyteller_f.bailongmap.data.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.storyteller_f.bailongmap.data.model.TravelMode

class OpenTripPlannerClientTest {

    @Test
    fun mapsAlternativeMultiStageItineraries() {
        val response = OtpGraphQlResponse(
            data = OtpData(
                plan = OtpPlan(
                    itineraries = listOf(
                        OtpItinerary(
                            duration = 2_400.0,
                            legs = listOf(
                                leg("WALK", "出发点", "地铁站", 500.0, 300.0),
                                leg(
                                    mode = "SUBWAY",
                                    fromName = "地铁站",
                                    toName = "换乘站",
                                    distance = 8_000.0,
                                    duration = 1_200.0,
                                    route = OtpRoute(shortName = "2号线"),
                                ),
                                leg("BICYCLE", "换乘站", "目的地", 1_500.0, 600.0),
                            ),
                        ),
                        OtpItinerary(
                            duration = 2_400.0,
                            legs = listOf(
                                leg("WALK", "出发点", "目的地", 2_000.0, 2_400.0),
                            ),
                        ),
                    )
                )
            )
        )

        val plans = response.toJourneyPlans()

        assertEquals(2, plans.size)
        assertEquals(
            listOf(TravelMode.WALK, TravelMode.SUBWAY, TravelMode.BICYCLE),
            plans.first().legs.map { it.mode },
        )
        assertEquals("2号线", plans.first().legs[1].routeName)
        assertEquals(2_400.0, plans.first().durationSeconds)
        assertEquals(TravelMode.WALK, plans[1].legs.single().mode)
    }

    @Test
    fun decodesGooglePolyline() {
        val points = decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")

        assertEquals(3, points.size)
        assertEquals(38.5, points[0].latitude)
        assertEquals(-120.2, points[0].longitude)
        assertEquals(43.252, points[2].latitude)
        assertEquals(-126.453, points[2].longitude)
    }

    @Test
    fun rejectsResponseWithoutItineraries() {
        assertFailsWith<IllegalStateException> {
            OtpGraphQlResponse(data = OtpData(plan = OtpPlan())).toJourneyPlans()
        }
    }

    private fun leg(
        mode: String,
        fromName: String,
        toName: String,
        distance: Double,
        duration: Double,
        route: OtpRoute? = null,
    ) = OtpLeg(
        mode = mode,
        distance = distance,
        duration = duration,
        from = OtpPlace(fromName, 39.90, 116.40),
        to = OtpPlace(toName, 39.91, 116.41),
        route = route,
        legGeometry = null,
    )
}
