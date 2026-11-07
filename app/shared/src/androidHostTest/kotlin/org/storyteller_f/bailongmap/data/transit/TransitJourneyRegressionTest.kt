package org.storyteller_f.bailongmap.data.transit

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.storyteller_f.bailongmap.data.model.*
import org.storyteller_f.bailongmap.data.network.JourneyPlanner
import org.storyteller_f.bailongmap.data.network.RoadJourneyPlanner
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransitJourneyRegressionTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val a = RoutePoint(1.0, 103.0)
    private val c = RoutePoint(1.02, 103.0)

    private suspend fun installed(at: String): AndroidTransitPackages {
        val file = File(context.cacheDir, "regression.bltransit")
        requireNotNull(javaClass.getResourceAsStream("/transit/example.bltransit")).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return AndroidTransitPackages(context, Clock.fixed(Instant.parse(at), ZoneOffset.UTC)).also {
            it.preview(Uri.fromFile(file).toString())
            it.install()
        }
    }

    private suspend fun overnight(firstDeparture: Int = 86340, transfer: Boolean = false,
        at: String = "2026-11-01T16:00:00Z"): AndroidTransitPackages {
        val packages = installed(at) // Fixture timezone is Asia/Shanghai.
        SQLiteDatabase.openDatabase(File(context.filesDir, "transit/active.sqlite").path,
            null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("DELETE FROM connections")
            db.execSQL("DELETE FROM service_dates WHERE date != '20261102'")
            db.execSQL("INSERT INTO connections VALUES (0,0,0,1,?,86460,0,0)", arrayOf(firstDeparture))
            db.execSQL("INSERT INTO connections VALUES (?,1,1,2,86640,86700,0,0)", arrayOf(if (transfer) 1 else 0))
        }
        return packages
    }

    @Test fun keepsSameVehicleContinuationBeyondBoardingWindow() = runTest {
        val plan = overnight().plans(a, c).single()
        assertEquals(86700.0, plan.durationSeconds)
        assertEquals(listOf(a, RoutePoint(1.01, 103.0), c), plan.legs[1].points)
        assertEquals(3, plan.legs.size)
    }

    @Test fun allowsBoardingExactlyAtWindowEnd() = runTest {
        assertEquals(86700.0, overnight(firstDeparture = 86400).plans(a, c).single().durationSeconds)
    }

    @Test fun rejectsFirstBoardingBeyondWindow() = runTest {
        assertFailsWith<IllegalStateException> { overnight(firstDeparture = 86401).plans(a, c) }
    }

    @Test fun rejectsTransferToAnotherVehicleBeyondWindow() = runTest {
        assertFailsWith<IllegalStateException> { overnight(transfer = true).plans(a, c) }
    }

    @Test fun crossesMidnightWithoutWaitingTwentyFourHours() = runTest {
        val plan = overnight(at = "2026-11-02T15:58:00Z").plans(a, c).single()
        assertEquals(420.0, plan.durationSeconds)
    }

    private suspend fun withRoadFallback(onlineApproach: Boolean, onlineExit: Boolean): JourneyPlan {
        val packages = installed("2026-11-01T23:59:00Z")
        fun road(from: RoutePoint, to: RoutePoint) = listOf(JourneyPlan("road", listOf(
            JourneyLeg(TravelMode.WALK, listOf(from, to), 20.0, 10.0, "A", "B"))))
        // Only the end stops have usable street connectors in this synthetic network.
        fun connects(from: RoutePoint, to: RoutePoint) = to == a || from == c
        val offline = JourneyPlanner { from, to ->
            if (!connects(from, to) || (to == a && onlineApproach) || (from == c && onlineExit)) emptyList()
            else road(from, to)
        }
        val online = JourneyPlanner { from, to -> if (connects(from, to)) road(from, to) else emptyList() }
        return packages.plansWithAccess(RoutePoint(0.9999, 103.0), RoutePoint(1.0201, 103.0),
            RoadJourneyPlanner(offline, online), TravelMode.WALK).single()
    }

    @Test fun preservesOnlineApproachNoticeInTransitPlan() = runTest {
        assertContains(requireNotNull(withRoadFallback(true, false).notice), "在线道路规划")
    }

    @Test fun preservesOnlineExitNoticeInTransitPlan() = runTest {
        assertContains(requireNotNull(withRoadFallback(false, true).notice), "在线道路规划")
    }

    @Test fun offlineConnectorsDoNotClaimOnlineRouting() = runTest {
        assertFalse(requireNotNull(withRoadFallback(false, false).notice).contains("在线道路规划"))
    }

    @Test fun deduplicatesSharedOnlineConnectorNotice() = runTest {
        val notice = requireNotNull(withRoadFallback(true, true).notice)
        assertEquals(1, Regex("在线道路规划").findAll(notice).count())
    }
}
