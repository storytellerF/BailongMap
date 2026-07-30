package org.storyteller_f.bailongmap.data.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OsrmClientTest {

    @Test
    fun mapsFirstRouteAndGeoJsonCoordinates() {
        val response = OsrmResponse(
            code = "Ok",
            routes = listOf(
                OsrmRoute(
                    distance = 1_250.5,
                    duration = 320.0,
                    geometry = OsrmGeometry(
                        coordinates = listOf(
                            listOf(116.40, 39.90),
                            listOf(116.41, 39.91),
                            listOf(116.42, 39.92),
                        )
                    ),
                )
            ),
        )

        val plan = response.toJourneyPlan()
        val route = plan.legs.single()

        assertEquals(1_250.5, route.distanceMeters)
        assertEquals(320.0, route.durationSeconds)
        assertEquals(39.90, route.points.first().latitude)
        assertEquals(116.40, route.points.first().longitude)
        assertEquals(3, route.points.size)
    }

    @Test
    fun rejectsResponseWithoutRoute() {
        assertFailsWith<IllegalStateException> {
            OsrmResponse(code = "Ok").toJourneyPlan()
        }
    }

    @Test
    fun rejectsInvalidCoordinate() {
        val response = OsrmResponse(
            code = "Ok",
            routes = listOf(
                OsrmRoute(
                    distance = 10.0,
                    duration = 5.0,
                    geometry = OsrmGeometry(
                        coordinates = listOf(
                            listOf(181.0, 39.90),
                            listOf(116.41, 39.91),
                        )
                    ),
                )
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            response.toJourneyPlan()
        }
    }
}
