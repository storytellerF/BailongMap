package org.storyteller_f.bailongmap.data.network

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.storyteller_f.bailongmap.data.model.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class JourneyOptionsPlannerTest {
    private val a=RoutePoint(1.0,103.0)
    private val b=RoutePoint(1.01,103.01)
    private fun plan(mode: TravelMode)=JourneyPlan("same-id",listOf(JourneyLeg(mode,listOf(a,b),1000.0,60.0,"A","B")))
    @Test fun slowTransitAndFailuresDoNotHoldRoadResults() = runTest {
        val planner=JourneyOptionsPlanner(listOf(
            JourneyProvider(JourneyCategory.CAR,JourneyPlanner { _,_ -> listOf(plan(TravelMode.CAR)) },"car miss"),
            JourneyProvider(JourneyCategory.TRANSIT,JourneyPlanner { _,_ -> delay(10000);error("no coverage") },"transit miss"),
            JourneyProvider(JourneyCategory.WALK,JourneyPlanner { _,_ -> emptyList() },"walk miss"),
        ),coordination=StandardTestDispatcher(testScheduler))
        val results=mutableListOf<JourneyGroup>()
        val job=launch { planner.options(a,b).toList(results) }
        runCurrent()
        assertEquals(setOf(JourneyCategory.CAR,JourneyCategory.WALK),results.map { it.category }.toSet())
        assertIs<JourneyAvailability.Available>(results.first { it.category==JourneyCategory.CAR }.availability)
        advanceUntilIdle()
        assertEquals("transit miss",assertIs<JourneyAvailability.Unavailable>(results.last().availability).explanation)
        job.join()
    }
    @Test fun providerTimeoutIsLocalAndCancellationStopsPendingWork() = runTest {
        var stopped=false
        val planner=JourneyOptionsPlanner(listOf(JourneyProvider(JourneyCategory.TRANSIT,JourneyPlanner { _,_ ->
            try { awaitCancellation() } finally { stopped=true }
        },"timeout")),timeoutMillis=100,coordination=StandardTestDispatcher(testScheduler))
        assertIs<JourneyAvailability.Unavailable>(planner.options(a,b).single().availability)
        assertTrue(stopped)
        stopped=false
        val results=mutableListOf<JourneyGroup>()
        val job=launch { planner.options(a,b).toList(results) }
        runCurrent();job.cancelAndJoin()
        assertTrue(stopped)
        assertTrue(results.isEmpty())
    }
    @Test fun roadFailureFallsBackToSameModeAndPreservesCancellation() = runTest {
        val fallback=RoadJourneyPlanner(JourneyPlanner { _,_ -> error("missing tile") },JourneyPlanner { _,_ -> listOf(plan(TravelMode.BICYCLE)) })
        assertEquals(TravelMode.BICYCLE,fallback.plans(a,b).single().legs.single().mode)
        var onlineCalled=false
        val cancelled=RoadJourneyPlanner(JourneyPlanner { _,_ -> throw CancellationException() },JourneyPlanner { _,_ -> onlineCalled=true;emptyList() })
        assertFailsWith<CancellationException> { cancelled.plans(a,b) }
        assertFalse(onlineCalled)
    }
}
