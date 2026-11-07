package org.storyteller_f.bailongmap.data.transit

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.storyteller_f.bailongmap.data.model.RoutePoint
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransitPackagesTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val clock = Clock.fixed(Instant.parse("2026-11-01T23:59:00Z"), ZoneOffset.UTC)
    private fun packages() = AndroidTransitPackages(context, clock)
    private fun fixture(name: String = "example.bltransit"): File = File(context.cacheDir, "example.bltransit").also { target ->
        requireNotNull(javaClass.getResourceAsStream("/transit/$name")).use { input -> target.outputStream().use { input.copyTo(it) } }
    }

    @Test fun compiledPackageImportsPersistsAndPlansOfflineTransfer() = runTest {
        val packages = packages()
        packages.remove()
        val info = packages.preview(Uri.fromFile(fixture()).toString())
        assertEquals(3, info.stops)
        assertNull(packages.installed.value)
        packages.install()
        val restarted = packages()
        restarted.load()
        assertEquals(info, restarted.installed.value)
        val plan = restarted.plans(RoutePoint(1.0, 103.0), RoutePoint(1.02, 103.0)).single()
        assertEquals(listOf("Metro 1", "Bus 2"), plan.legs.drop(1).dropLast(1).map { it.routeName })
        assertEquals(1260.0, plan.durationSeconds)
    }

    @Test fun corruptImportKeepsInstalledPackage() = runTest {
        val packages = packages()
        packages.remove()
        packages.preview(Uri.fromFile(fixture()).toString())
        packages.install()
        val original = packages.installed.value
        val broken = File(context.cacheDir, "broken.bltransit")
        ZipInputStream(fixture().inputStream()).use { input ->
            ZipOutputStream(broken.outputStream()).use { output ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    output.putNextEntry(ZipEntry(entry.name))
                    val bytes = input.readBytes()
                    output.write(if (entry.name == "network.sqlite") bytes + byteArrayOf(1) else bytes)
                    output.closeEntry()
                }
            }
        }
        assertFailsWith<IllegalArgumentException> { packages.preview(Uri.fromFile(broken).toString()) }
        val restarted = packages()
        restarted.load()
        assertEquals(original, restarted.installed.value)
    }

    @Test fun expiredPackageDoesNotInventDepartures() = runTest {
        val packages = packages()
        packages.remove()
        packages.preview(Uri.fromFile(fixture()).toString())
        packages.install()
        val future = AndroidTransitPackages(context, Clock.fixed(Instant.parse("2026-12-01T00:00:00Z"), ZoneOffset.UTC))
        assertFailsWith<IllegalArgumentException> { future.plans(RoutePoint(1.0, 103.0), RoutePoint(1.02, 103.0)) }
    }
    @Test fun acceptsPreviouslyGeneratedVersionOnePackage() = runTest {
        val packages=packages()
        packages.remove()
        packages.preview(Uri.fromFile(fixture("legacy-v1.bltransit")).toString())
        packages.install()
        assertEquals(1260.0,packages.plans(RoutePoint(1.0,103.0),RoutePoint(1.02,103.0)).single().durationSeconds)
    }

    @Test fun acceptsPreviousVersionTwoWithoutAssumingBicyclesAreAllowed() = runTest {
        val packages=packages()
        packages.remove()
        packages.preview(Uri.fromFile(fixture("legacy-v2.bltransit")).toString())
        packages.install()
        assertFalse(packages.allowsBicycles())
        assertEquals(1260.0,packages.plans(RoutePoint(1.0,103.0),RoutePoint(1.02,103.0)).single().durationSeconds)
    }
    @Test fun importsBicyclePermissionAndPlansDoorToDoorWithStreetLegs() = runTest {
        val packages=packages()
        packages.remove()
        packages.preview(Uri.fromFile(fixture("bicycle.bltransit")).toString())
        packages.install()
        assertTrue(packages.allowsBicycles())
        val streets=org.storyteller_f.bailongmap.data.network.JourneyPlanner { a,b ->
            listOf(org.storyteller_f.bailongmap.data.model.JourneyPlan("road",listOf(
                org.storyteller_f.bailongmap.data.model.JourneyLeg(org.storyteller_f.bailongmap.data.model.TravelMode.BICYCLE,listOf(a,b),100.0,10.0,"A","B"))))
        }
        val plan=packages.plansWithAccess(RoutePoint(0.9999,103.0),RoutePoint(1.0201,103.0),streets,org.storyteller_f.bailongmap.data.model.TravelMode.BICYCLE).single()
        assertEquals(org.storyteller_f.bailongmap.data.model.TravelMode.BICYCLE,plan.legs.first().mode)
        assertEquals(org.storyteller_f.bailongmap.data.model.TravelMode.BICYCLE,plan.legs.last().mode)
    }

    @Test fun frequencyAndPathwaysPackagePlansEstimatedMetroAndScheduledBus() = runTest {
        val packages=packages()
        packages.remove()
        packages.preview(Uri.fromFile(fixture("complex.bltransit")).toString())
        packages.install()
        val plan=packages.plans(RoutePoint(39.9042,116.4074),RoutePoint(39.9442,116.4074)).single()
        assertEquals(5,plan.legs.size)
        assertEquals("测试地铁线（间隔估算）",plan.legs[1].routeName)
        assertEquals("测试站内换乘通道",plan.legs[2].routeName)
        assertEquals(150.0,plan.legs[2].durationSeconds)
        assertEquals(1200.0,plan.durationSeconds)
    }

}
