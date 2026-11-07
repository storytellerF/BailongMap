package org.storyteller_f.bailongmap.data.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource
import kotlin.time.TimeMark

/** Shared per-service request-start limit required by routing.openstreetmap.de. */
internal object PublicRoadRequestGate {
    private val mutex = Mutex()
    private var previous: TimeMark? = null
    suspend fun awaitTurn() = mutex.withLock {
        previous?.let { delay((1000-it.elapsedNow().inWholeMilliseconds).coerceAtLeast(0)) }
        previous = TimeSource.Monotonic.markNow()
    }
}
