package org.storyteller_f.bailongmap.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JourneyPlanTest {

    @Test
    fun aggregatesLegMetricsAndStages() {
        val plan = JourneyPlan(
            id = "journey",
            legs = listOf(
                leg(TravelMode.WALK, 500.0, 300.0),
                leg(TravelMode.SUBWAY, 8_000.0, 1_200.0),
                leg(TravelMode.BICYCLE, 1_500.0, 600.0),
            ),
        )

        assertEquals(10_000.0, plan.distanceMeters)
        assertEquals(2_100.0, plan.durationSeconds)
        assertEquals(2, plan.transferCount)
        assertTrue(plan.isMultiStage)
    }

    @Test
    fun rejectsEmptyJourney() {
        assertFailsWith<IllegalArgumentException> {
            JourneyPlan(id = "empty", legs = emptyList())
        }
    }

    @Test
    fun rejectsInvalidRoutePoint() {
        assertFailsWith<IllegalArgumentException> {
            RoutePoint(latitude = 91.0, longitude = 116.4)
        }
    }

    private fun leg(
        mode: TravelMode,
        distance: Double,
        duration: Double,
    ) = JourneyLeg(
        mode = mode,
        points = listOf(RoutePoint(39.9, 116.4), RoutePoint(39.91, 116.41)),
        distanceMeters = distance,
        durationSeconds = duration,
        fromName = "起点",
        toName = "终点",
    )
}
