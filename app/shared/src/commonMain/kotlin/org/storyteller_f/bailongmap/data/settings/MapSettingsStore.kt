package org.storyteller_f.bailongmap.data.settings

import kotlinx.coroutines.flow.Flow
enum class MapAppearance(val label: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }

interface MapSettingsStore {
    val defaultStyleIndex: Flow<Int?>
    val appearance: Flow<MapAppearance>

    suspend fun setAppearance(appearance: MapAppearance)

    suspend fun setDefaultStyleIndex(index: Int)
}

expect fun createMapSettingsStore(): MapSettingsStore
