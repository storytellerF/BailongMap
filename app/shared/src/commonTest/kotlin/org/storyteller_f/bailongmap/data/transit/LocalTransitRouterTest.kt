package org.storyteller_f.bailongmap.data.transit

import kotlinx.coroutines.test.runTest
import org.storyteller_f.bailongmap.data.model.*
import kotlin.test.*

class LocalTransitRouterTest {
    private val a = RoutePoint(1.0, 103.0)
    private val b = RoutePoint(1.02, 103.0)
    private val c = RoutePoint(1.04, 103.0)
    private val stops = mapOf(0 to TransitStop(0, "A", a), 1 to TransitStop(1, "B", b), 2 to TransitStop(2, "C", c))
    private fun connection(trip: String, from: Int, to: Int, departure: Long, arrival: Long, pickup: Boolean = true, dropoff: Boolean = true) =
        TransitConnection(trip, trip, true, from, to, departure, arrival, pickup, dropoff)

    @Test fun excludesNoticesFromUnusedConnectors() = runTest {
        fun connector(stop: Int, point: RoutePoint, seconds: Double, notice: String) =
            TransitConnector(stop, listOf(JourneyLeg(TravelMode.WALK, listOf(point, point),
                0.0, seconds, "A", "B")), notice)
        val plan = LocalTransitRouter(stops).plan(a, c, 0, listOf(connection("ride", 0, 2, 60, 600)),
            approaches = listOf(connector(0, a, 0.0, "离线道路规划"), connector(1, b, 1000.0, "在线道路规划")),
            exits = listOf(connector(2, c, 0.0, "离线道路规划"), connector(1, b, 1000.0, "在线道路规划")))
        assertEquals(600.0, plan.durationSeconds)
        assertContains(requireNotNull(plan.notice), "离线道路规划")
        assertFalse(plan.notice.contains("在线道路规划"))
    }

    @Test fun roadAccessTimeDeterminesCatchableTripAndKeepsRoadGeometry() = runTest {
        val access=JourneyLeg(TravelMode.WALK,listOf(a,b),300.0,100.0,"Origin","A")
        val exit=JourneyLeg(TravelMode.WALK,listOf(b,c),400.0,200.0,"C","Destination")
        val rows=listOf(connection("missed",0,2,60,500),connection("caught",0,2,180,600))
        val plan=LocalTransitRouter(stops).plan(a,c,0,rows,
            approaches=listOf(TransitConnector(0,listOf(access))),exits=listOf(TransitConnector(2,listOf(exit))))
        assertEquals(800.0,plan.durationSeconds)
        assertEquals(access,plan.legs.first())
        assertEquals(exit,plan.legs.last())
        assertEquals("caught",plan.legs[1].routeName)
    }
    @Test fun bicycleOnTransitRequiresExplicitPermissionOnEveryRide() = runTest {
        val access=JourneyLeg(TravelMode.BICYCLE,listOf(a,a),0.0,0.0,"Origin","A")
        val exit=JourneyLeg(TravelMode.BICYCLE,listOf(c,c),0.0,0.0,"C","Destination")
        suspend fun route(allowed: Boolean)=LocalTransitRouter(stops).plan(a,c,0,
            listOf(connection("ride",0,2,60,600).copy(bikesAllowed=allowed)),
            approaches=listOf(TransitConnector(0,listOf(access))),exits=listOf(TransitConnector(2,listOf(exit))),bicycleOnTransit=true)
        assertFailsWith<IllegalStateException> { route(false) }
        assertEquals(listOf(TravelMode.BICYCLE,TravelMode.SUBWAY,TravelMode.BICYCLE),route(true).legs.map { it.mode })
    }
    @Test fun plansSameStopTransferAndIncludesWaitingTime() = runTest {
        val plan = LocalTransitRouter(stops).plan(a, c, 0, listOf(connection("r1", 0, 1, 60, 600), connection("r2", 1, 2, 720, 1200)))
        assertEquals(listOf(TravelMode.WALK, TravelMode.SUBWAY, TravelMode.SUBWAY, TravelMode.WALK), plan.legs.map { it.mode })
        assertEquals(1200.0, plan.durationSeconds)
        assertEquals("B", plan.legs[1].toName)
        assertNotNull(plan.notice)
    }
    @Test fun missesTransferWithInsufficientTime() = runTest {
        assertFailsWith<IllegalStateException> {
            LocalTransitRouter(stops).plan(a, c, 0, listOf(connection("r1", 0, 1, 60, 600), connection("r2", 1, 2, 719, 1200)))
        }
    }
    @Test fun staysOnVehicleWithoutRequiringTransferAllowance() = runTest {
        val plan = LocalTransitRouter(stops).plan(a, c, 0, listOf(connection("r1", 0, 1, 60, 600, dropoff = false), connection("r1", 1, 2, 601, 1200, pickup = false)))
        assertEquals(3, plan.legs.size)
        assertEquals(listOf(a, b, c), plan.legs[1].points)
    }
    @Test fun doesNotBoardWherePickupIsForbidden() = runTest {
        assertFailsWith<IllegalStateException> { LocalTransitRouter(stops).plan(a, c, 0, listOf(connection("r1", 0, 2, 60, 600, pickup = false))) }
    }
    @Test fun doesNotAlightWhereDropoffIsForbidden() = runTest {
        assertFailsWith<IllegalStateException> { LocalTransitRouter(stops).plan(a, c, 0, listOf(connection("r1", 0, 2, 60, 600, dropoff = false))) }
    }
    @Test fun excludesAlreadyDepartedServices() = runTest {
        assertFailsWith<IllegalStateException> { LocalTransitRouter(stops).plan(a, c, 61, listOf(connection("r1", 0, 2, 60, 600))) }
    }
    @Test fun minimumTransferRuleOverridesDefaultAllowance() = runTest {
        val rule = TransitTransferRule(1,1,-1,-1,-1,-1,2,300)
        assertFailsWith<IllegalStateException> {
            LocalTransitRouter(stops,rules=listOf(rule)).plan(a,c,0,listOf(connection("one",0,1,60,600),connection("two",1,2,800,1200)))
        }
    }
    @Test fun laterArrivingTripRemainsAvailableWhenEarlierTripHasForbiddenTransfer() = runTest {
        val rule = TransitTransferRule(1,1,-1,1,-1,3,3,0)
        val rows = listOf(connection("early",0,1,60,600).copy(sourceTrip=1),connection("later",0,1,80,650).copy(sourceTrip=2),connection("target",1,2,800,1200).copy(sourceTrip=3))
        val plan=LocalTransitRouter(stops,rules=listOf(rule)).plan(a,c,0,rows)
        assertEquals("later",plan.legs[1].routeName)
    }
    @Test fun specificTripPermissionOverridesGenericProhibition() = runTest {
        val generic=TransitTransferRule(1,1,-1,-1,-1,-1,3,0)
        val specific=TransitTransferRule(1,1,-1,1,-1,2,2,0)
        val plan=LocalTransitRouter(stops,rules=listOf(generic,specific)).plan(a,c,0,listOf(connection("one",0,1,60,600).copy(sourceTrip=1),connection("two",1,2,601,1200).copy(sourceTrip=2)))
        assertEquals(1200.0,plan.durationSeconds)
    }
    @Test fun followsDirectedTransferFootpathBetweenPlatforms() = runTest {
        val d=RoutePoint(1.021,103.0)
        val nodes=stops+(3 to TransitStop(3,"Platform D",d,false))
        val path=TransitFootpath(1,3,180,"地下通道")
        val rows=listOf(connection("one",0,1,60,600),connection("two",3,2,800,1200))
        val plan=LocalTransitRouter(nodes,listOf(path)).plan(a,c,0,rows)
        assertEquals(5,plan.legs.size)
        assertEquals("地下通道",plan.legs[2].routeName)
        assertEquals(180.0,plan.legs[2].durationSeconds)
    }
    @Test fun cannotReverseOneWayPathway() = runTest {
        val nodes=stops+(3 to TransitStop(3,"Platform D",RoutePoint(1.021,103.0),false))
        assertFailsWith<IllegalStateException> {
            LocalTransitRouter(nodes,listOf(TransitFootpath(3,1,180,"单向通道"))).plan(a,c,0,listOf(connection("one",0,1,60,600),connection("two",3,2,800,1200)))
        }
    }
    @Test fun estimatesUnscheduledFrequencyUsingFullHeadway() = runTest {
        val frequency=TransitFrequency("f",listOf(connection("template",0,2,28800,29400)),60,3600,300)
        val plan=LocalTransitRouter(stops).plan(a,c,0,emptyList(),listOf(frequency))
        assertEquals(960.0,plan.durationSeconds)
        assertEquals("template（间隔估算）",plan.legs[1].routeName)
    }
    @Test fun frequencyStopsAcceptingBoardingsAtEndOfWindow() = runTest {
        val frequency=TransitFrequency("f",listOf(connection("template",0,2,0,600)),0,300,300)
        assertFailsWith<IllegalStateException> { LocalTransitRouter(stops).plan(a,c,0,emptyList(),listOf(frequency)) }
    }

    @Test fun cannotBypassForbiddenTransferByWalkingThroughIntermediateNodes() = runTest {
        val nodes=stops+(3 to TransitStop(3,"D",RoutePoint(1.021,103.0),false))+(4 to TransitStop(4,"E",RoutePoint(1.022,103.0),false))
        val paths=listOf(TransitFootpath(1,4,30,"first"),TransitFootpath(4,3,30,"second"))
        val rule=TransitTransferRule(1,3,-1,-1,-1,-1,3,0)
        assertFailsWith<IllegalStateException> {
            LocalTransitRouter(nodes,paths,listOf(rule)).plan(a,c,0,listOf(connection("one",0,1,60,600),connection("two",3,2,1000,1200)))
        }
    }

}
