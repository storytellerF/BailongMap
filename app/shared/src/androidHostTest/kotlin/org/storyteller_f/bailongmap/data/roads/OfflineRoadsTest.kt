package org.storyteller_f.bailongmap.data.roads

import android.net.Uri
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.storyteller_f.bailongmap.data.model.*
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class OfflineRoadsTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private fun fixture(): File=File(context.cacheDir,"E100_N0.rd5").also { target ->
        requireNotNull(javaClass.getResourceAsStream("/roads/E100_N0.rd5")).use { input -> target.outputStream().use { input.copyTo(it) } }
    }
    @Test fun importsPersistsAndRoutesAllModesUsingUpstreamEngine() = runTest {
        val roads=BRouterRoads(context)
        roads.import(listOf(Uri.fromFile(fixture()).toString()))
        val restarted=BRouterRoads(context)
        restarted.load()
        assertEquals(listOf("E100_N0.rd5"),restarted.files.value)
        val a=RoutePoint(1.0,103.0)
        val c=RoutePoint(1.01,103.01)
        val plans=listOf(TravelMode.CAR,TravelMode.BICYCLE,TravelMode.WALK).associateWith { mode ->
            restarted.planner(mode).plans(a,c).single().also {
                assertEquals(mode,it.legs.single().mode)
                assertTrue(it.durationSeconds>0)
                assertTrue(it.points.size>=2)
            }
        }
        assertTrue(plans.getValue(TravelMode.WALK).distanceMeters < plans.getValue(TravelMode.CAR).distanceMeters)
        assertTrue(plans.getValue(TravelMode.WALK).distanceMeters < plans.getValue(TravelMode.BICYCLE).distanceMeters)
    }
    @Test fun invalidReplacementPreservesActiveRoads() = runTest {
        val roads=BRouterRoads(context)
        roads.import(listOf(Uri.fromFile(fixture()).toString()))
        val corrupt=File(context.cacheDir,"E105_N0.rd5").apply { writeText("broken") }
        assertFails { roads.import(listOf(Uri.fromFile(corrupt).toString())) }
        roads.load()
        assertEquals(listOf("E100_N0.rd5"),roads.files.value)
        assertTrue(roads.planner(TravelMode.WALK).plans(RoutePoint(1.0,103.0),RoutePoint(1.01,103.01)).isNotEmpty())
    }
    @Test fun rejectsIncompleteTrackFromIslandRecovery() = runTest {
        val roads=BRouterRoads(context)
        roads.import(listOf(Uri.fromFile(fixture()).toString()))
        val failure=assertFailsWith<IllegalStateException> {
            roads.planner(TravelMode.CAR).plans(RoutePoint(1.0001,103.0001),RoutePoint(1.01,103.01))
        }
        assertEquals("离线道路未能连接请求的起终点",failure.message)
    }

    @Test fun walkingRefreshesProfileFromPreviouslyImportedGeneration() = runTest {
        BRouterRoads(context).import(listOf(Uri.fromFile(fixture()).toString()))
        val root = File(context.filesDir, "roads")
        val profile = File(File(root, File(root, "active").readText().trim()), "shortest.brf")
        profile.writeText("obsolete profile")

        val restarted = BRouterRoads(context)
        val route = restarted.planner(TravelMode.WALK).plans(RoutePoint(1.0,103.0), RoutePoint(1.01,103.01))

        assertTrue(route.isNotEmpty())
        assertEquals(context.assets.open("brouter/shortest.brf").bufferedReader().use { it.readText() }, profile.readText())
    }
}
