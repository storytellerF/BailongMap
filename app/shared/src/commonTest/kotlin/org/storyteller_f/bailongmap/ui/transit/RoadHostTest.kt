package org.storyteller_f.bailongmap.ui.transit

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*
import org.storyteller_f.bailongmap.data.model.TravelMode
import org.storyteller_f.bailongmap.data.network.JourneyPlanner
import org.storyteller_f.bailongmap.data.roads.OfflineRoads

@OptIn(ExperimentalCoroutinesApi::class)
class RoadHostTest {
    private class Roads : OfflineRoads {
        override val files = MutableStateFlow(listOf("existing.rd5"))
        var imports = 0
        var fail = false
        override suspend fun load() = Unit
        override suspend fun import(documents: List<String>) {
            imports++
            if (fail) error("Invalid data")
            files.value = documents
        }
        override fun planner(mode: TravelMode): JourneyPlanner = error("Not used by data management")
    }

    @Test fun choosingDocumentsRequiresConfirmationBeforeImport() = runTest {
        val roads = Roads()
        val host = RoadHost(roads, StandardTestDispatcher(testScheduler))
        try {
            runCurrent()
            host.review(listOf("new.rd5")); runCurrent()
            assertIs<RoadImportStatus.Review>(host.state.value.status)
            assertEquals(0, roads.imports)
            host.install(); runCurrent()
            assertEquals(listOf("new.rd5"), host.state.value.files)
            assertEquals(1, roads.imports)
        } finally { host.close() }
    }

    @Test fun cancellingReviewRetainsInstalledRoads() = runTest {
        val roads = Roads()
        val host = RoadHost(roads, StandardTestDispatcher(testScheduler))
        try {
            runCurrent()
            host.review(listOf("new.rd5")); runCurrent()
            host.discard(); runCurrent()
            assertEquals(RoadImportStatus.Idle, host.state.value.status)
            assertEquals(listOf("existing.rd5"), host.state.value.files)
            assertEquals(0, roads.imports)
        } finally { host.close() }
    }

    @Test fun failedReplacementReportsFailureAndRetainsInstalledRoads() = runTest {
        val roads = Roads().apply { fail = true }
        val host = RoadHost(roads, StandardTestDispatcher(testScheduler))
        try {
            runCurrent()
            host.review(listOf("invalid.rd5")); runCurrent()
            host.install(); runCurrent()
            assertIs<RoadImportStatus.Failed>(host.state.value.status)
            assertEquals(listOf("existing.rd5"), host.state.value.files)
        } finally { host.close() }
    }
}
