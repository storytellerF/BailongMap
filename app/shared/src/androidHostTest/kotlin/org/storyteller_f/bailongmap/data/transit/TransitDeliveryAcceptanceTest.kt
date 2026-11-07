package org.storyteller_f.bailongmap.data.transit

import android.net.Uri
import java.io.File
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.storyteller_f.bailongmap.data.model.*
import org.storyteller_f.bailongmap.data.roads.BRouterRoads
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

/** Runs locally supplied delivery cases through the actual Android importer and planners. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransitDeliveryAcceptanceTest {
    @Test fun deliveredPackageSatisfiesDeclaredJourneys() = runTest(timeout = 5.minutes) {
        // BRouter/file IO uses real workers; connector deadlines must use real time too.
        withContext(Dispatchers.Default) {
        val path = System.getenv("BAILONGMAP_TRANSIT_ACCEPTANCE")
        assumeTrue("No external delivery was requested", !path.isNullOrBlank())
        val file = File(requireNotNull(path)).absoluteFile
        val resultFile = File(file.parentFile, "${file.nameWithoutExtension}.result.json")
        val spec = Json.parseToJsonElement(file.readText()).jsonObject
        fun input(name: String): File = File(name).let { if (it.isAbsolute) it else File(file.parentFile, name) }
        val packageFile = input(spec.getValue("package").jsonPrimitive.content)
        val roadFiles = spec["roads"]?.jsonArray.orEmpty().map { input(it.jsonPrimitive.content) }
        require((listOf(file, packageFile) + roadFiles).none { it.canonicalFile == resultFile.canonicalFile })
        check(!resultFile.exists() || resultFile.delete())
        val cases = spec.getValue("cases").jsonArray
        require(cases.isNotEmpty()) { "Acceptance needs at least one declared journey" }
        val context = RuntimeEnvironment.getApplication()
        val importer = AndroidTransitPackages(context)
        importer.preview(Uri.fromFile(packageFile).toString())
        importer.install()
        val roads = BRouterRoads(context)
        if (roadFiles.isNotEmpty()) roads.import(roadFiles.map { Uri.fromFile(it).toString() })
        val checkedModes = mutableSetOf<String>()
        val successfulModes = mutableSetOf<String>()
        for ((index, element) in cases.withIndex()) {
            val case = element.jsonObject
            val mode = case.getValue("mode").jsonPrimitive.content
            require(mode in setOf("TIMETABLE", "TRANSIT_WALK", "TRANSIT_BICYCLE", "CAR", "BICYCLE", "WALK"))
            require(mode == "TIMETABLE" || roadFiles.isNotEmpty()) { "Street acceptance requires road files" }
            fun point(name: String): RoutePoint = case.getValue(name).jsonArray.let {
                require(it.size == 2)
                RoutePoint(it[0].jsonPrimitive.double, it[1].jsonPrimitive.double)
            }
            val origin = point("origin")
            val destination = point("destination")
            val instant = Instant.parse(case.getValue("departure").jsonPrimitive.content)
            val packages = AndroidTransitPackages(context, Clock.fixed(instant, ZoneOffset.UTC))
            packages.load()
            val expected = case.getValue("expect").jsonPrimitive.content
            require(expected in setOf("route", "unavailable"))
            val expectedModes = case["modes"]?.jsonArray?.map { TravelMode.valueOf(it.jsonPrimitive.content) }
            require(expected != "route" || !expectedModes.isNullOrEmpty()) { "Positive cases must declare expected leg modes" }
            val minimum = case["min_seconds"]?.jsonPrimitive?.double ?: 0.0
            val maximum = case.getValue("max_seconds").jsonPrimitive.double
            require(minimum.isFinite() && maximum.isFinite() && minimum >= 0 && maximum > minimum)
            val options = try {
                when (mode) {
                    "TIMETABLE" -> packages.plans(origin, destination)
                    "TRANSIT_WALK" -> packages.plansWithAccess(origin, destination, roads.planner(TravelMode.WALK), TravelMode.WALK)
                    "TRANSIT_BICYCLE" -> packages.plansWithAccess(origin, destination, roads.planner(TravelMode.BICYCLE), TravelMode.BICYCLE)
                    else -> roads.planner(TravelMode.valueOf(mode)).plans(origin, destination)
                }
            } catch (missing: IllegalStateException) {
                if (expected == "route") throw AssertionError("Delivery journey ${index + 1} found no route", missing)
                emptyList()
            }
            if (expected == "unavailable") {
                assertTrue(options.isEmpty(), "Delivery journey ${index + 1} must be unavailable")
            } else {
                val matching = options.filter { plan ->
                    plan.legs.map { it.mode } == expectedModes && plan.durationSeconds in minimum..maximum &&
                        case["routes"]?.jsonArray.orEmpty().all { route ->
                            plan.legs.any { it.routeName?.startsWith(route.jsonPrimitive.content) == true }
                        }
                }
                assertTrue(matching.isNotEmpty(), "Delivery journey ${index + 1} did not meet its declared modes, routes or duration; found modes=${options.map { it.legs.map { leg -> leg.mode } }}, seconds=${options.map { it.durationSeconds }}")
                successfulModes += mode
            }
            checkedModes += mode
        }
        require(successfulModes.isNotEmpty()) { "Acceptance must include a successful journey" }
        fun sha(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buffer = ByteArray(65536)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        }
        val report = buildJsonObject {
            put("passed", true)
            put("engine", "android-host-offline")
            put("case_count", cases.size)
            put("spec_sha256", sha(file))
            put("package_sha256", sha(packageFile))
            putJsonArray("road_sha256") { roadFiles.forEach { add(sha(it)) } }
            putJsonArray("checked_modes") { checkedModes.sorted().forEach { add(it) } }
            putJsonArray("successful_modes") { successfulModes.sorted().forEach { add(it) } }
        }
        resultFile.writeText(report.toString())
        }
    }
}
