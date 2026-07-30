package org.storyteller_f.bailongmap.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import org.storyteller_f.bailongmap.data.model.JourneyLeg
import org.storyteller_f.bailongmap.data.model.JourneyPlan
import org.storyteller_f.bailongmap.data.model.RoutePoint
import org.storyteller_f.bailongmap.data.model.TravelMode

class JourneyNavigationCardTest {

    @Test
    fun formatsMetersAndKilometers() {
        assertEquals("850 米", formatRouteDistance(850.0))
        assertEquals("1.3 公里", formatRouteDistance(1_250.0))
        assertEquals("12 公里", formatRouteDistance(12_000.0))
    }

    @Test
    fun formatsDurationRoundedUpToMinute() {
        assertEquals("1 分钟", formatRouteDuration(1.0))
        assertEquals("6 分钟", formatRouteDuration(301.0))
        assertEquals("1 小时", formatRouteDuration(3_600.0))
        assertEquals("1 小时 1 分钟", formatRouteDuration(3_601.0))
    }

    @Test
    fun summarizesMultiStageJourney() {
        val plan = JourneyPlan(
            id = "multi-stage",
            legs = listOf(
                leg(TravelMode.WALK, distance = 500.0, duration = 300.0),
                leg(TravelMode.SUBWAY, distance = 8_000.0, duration = 1_200.0),
                leg(TravelMode.BICYCLE, distance = 1_500.0, duration = 600.0),
            ),
        )

        assertEquals("10 公里 · 约 35 分钟", plan.summaryText())
        assertEquals("步行 → 地铁 → 自行车", plan.modeChainText())
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
