package org.storyteller_f.bailongmap.data.roads

import kotlinx.coroutines.flow.StateFlow
import org.storyteller_f.bailongmap.data.network.JourneyPlanner
import org.storyteller_f.bailongmap.data.model.TravelMode

interface OfflineRoads {
    val files: StateFlow<List<String>>
    suspend fun load()
    suspend fun import(documents: List<String>)
    fun planner(mode: TravelMode): JourneyPlanner
}
expect fun createOfflineRoads(): OfflineRoads
