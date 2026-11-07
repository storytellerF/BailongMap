package org.storyteller_f.bailongmap.data.network

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.storyteller_f.bailongmap.data.model.*
import org.storyteller_f.bailongmap.data.transit.*
import kotlin.test.*

class TransitJourneyPlannerTest {
    private val a=RoutePoint(1.0,103.0)
    private val b=RoutePoint(1.01,103.01)
    private fun plan(mode: TravelMode)=JourneyPlan("test",listOf(JourneyLeg(mode,listOf(a,b),1000.0,60.0,"A","B")))
    private val streets=JourneyPlanner { _,_ -> emptyList() }
    private class Packages(val permits: Boolean, val query: suspend (TravelMode)->List<JourneyPlan>): TransitPackages {
        override val installed=MutableStateFlow<TransitPackageInfo?>(null)
        override suspend fun allowsBicycles()=permits
        override suspend fun plansWithAccess(origin: RoutePoint,destination: RoutePoint,streets: JourneyPlanner,mode: TravelMode)=query(mode)
        override suspend fun load() {}
        override suspend fun plans(origin: RoutePoint,destination: RoutePoint)=emptyList<JourneyPlan>()
        override suspend fun preview(document: String): TransitPackageInfo=error("unused")
        override suspend fun install() {}
        override suspend fun discard() {}
        override suspend fun remove() {}
    }
    @Test fun unknownBicyclePermissionSkipsCyclingAndDoesNotReplaceWalking() = runTest {
        val modes=mutableListOf<TravelMode>()
        val transit=plan(TravelMode.SUBWAY)
        val packages=Packages(false) { mode -> modes+=mode;listOf(transit) }
        assertEquals(listOf(transit),TransitJourneyPlanner(packages,streets,streets).plans(a,b))
        assertEquals(listOf(TravelMode.WALK),modes)
    }
    @Test fun optionalBicycleTimeoutPreservesUsableTransit() = runTest {
        var stopped=false
        val transit=plan(TravelMode.TRANSIT)
        val packages=Packages(true) { mode ->
            if(mode==TravelMode.WALK) listOf(transit) else try { awaitCancellation() } finally { stopped=true }
        }
        assertEquals(listOf(transit),TransitJourneyPlanner(packages,streets,streets).plans(a,b))
        assertTrue(stopped)
    }
    @Test fun onlineWalkingOnlyOptionIsNotMisclassifiedAsPublicTransport() = runTest {
        val packages=Packages(false) { emptyList() }
        val online=JourneyPlanner { _,_ -> listOf(plan(TravelMode.WALK)) }
        assertTrue(TransitJourneyPlanner(packages,streets,streets,online).plans(a,b).isEmpty())
    }

    @Test fun slowWalkingResultSurvivesOuterProviderDeadline() = runTest {
        var cyclingStopped = false
        val transit = plan(TravelMode.TRANSIT)
        val packages = Packages(true) { mode ->
            if (mode == TravelMode.WALK) {
                delay(49000)
                listOf(transit)
            } else try { awaitCancellation() } finally { cyclingStopped = true }
        }
        val result = withTimeoutOrNull(60000) {
            TransitJourneyPlanner(packages, streets, streets).plans(a, b)
        }
        assertEquals(listOf(transit), result)
        assertTrue(cyclingStopped)
    }

    @Test fun cancellationStopsBothAccessSearches() = runTest {
        val started = mutableSetOf<TravelMode>()
        val stopped = mutableSetOf<TravelMode>()
        val bothStarted = CompletableDeferred<Unit>()
        val packages = Packages(true) { mode ->
            started += mode
            if (started.size == 2) bothStarted.complete(Unit)
            try { awaitCancellation() } finally { stopped += mode }
        }
        val request = launch { TransitJourneyPlanner(packages, streets, streets).plans(a, b) }
        bothStarted.await()
        request.cancelAndJoin()
        assertEquals(setOf(TravelMode.WALK, TravelMode.BICYCLE), stopped)
    }
}
