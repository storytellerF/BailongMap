package org.storyteller_f.bailongmap.data.settings

import kotlinx.coroutines.flow.Flow

interface MapSettingsStore {
    val defaultStyleIndex: Flow<Int?>

    suspend fun setDefaultStyleIndex(index: Int)
}

expect fun createMapSettingsStore(): MapSettingsStore
