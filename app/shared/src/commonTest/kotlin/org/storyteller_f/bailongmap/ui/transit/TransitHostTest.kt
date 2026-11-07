package org.storyteller_f.bailongmap.ui.transit

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.storyteller_f.bailongmap.data.model.*
import org.storyteller_f.bailongmap.data.transit.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class TransitHostTest {
    private class Packages : TransitPackages {
        val info = TransitPackageInfo("Test", "Example", "20261101", "20261130", 3, 2)
        override val installed = MutableStateFlow<TransitPackageInfo?>(null)
        var previews = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun load() {}
        override suspend fun preview(document: String): TransitPackageInfo { previews++; gate?.await(); return info }
        override suspend fun install() { installed.value = info }
        override suspend fun discard() {}
        override suspend fun remove() { installed.value = null }
        override suspend fun plans(origin: RoutePoint, destination: RoutePoint): List<JourneyPlan> = emptyList()
    }
    @Test fun importRequiresReviewBeforeInstallation() = runTest {
        val packages = Packages()
        val host = TransitHost(packages, StandardTestDispatcher(testScheduler))
        try {
            runCurrent()
            host.preview("document")
            runCurrent()
            assertIs<TransitImportState.Review>(host.state.value.importing)
            assertNull(packages.installed.value)
            host.install()
            runCurrent()
            assertEquals(packages.info, host.state.value.installed)
            assertIs<TransitImportState.Idle>(host.state.value.importing)
        } finally { host.close() }
    }
    @Test fun ignoresConcurrentImport() = runTest {
        val packages = Packages().also { it.gate = CompletableDeferred() }
        val host = TransitHost(packages, StandardTestDispatcher(testScheduler))
        runCurrent()
        host.preview("first")
        runCurrent()
        host.preview("second")
        runCurrent()
        assertEquals(1, packages.previews)
        host.close()
    }
    @Test fun closesCancelInFlightImportWithoutInstalling() = runTest {
        val packages = Packages().also { it.gate = CompletableDeferred() }
        val host = TransitHost(packages, StandardTestDispatcher(testScheduler))
        runCurrent()
        val job = host.preview("first")
        runCurrent()
        host.close()
        runCurrent()
        assertTrue(job.isCancelled)
        assertNull(packages.installed.value)
    }
}
