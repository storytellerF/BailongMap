package org.storyteller_f.bailongmap.ui.navigation

import org.storyteller_f.bailongmap.data.model.JourneyPlan

enum class NavigationMode(val label: String, val description: String) {
    PREVIEW("预览导航", "路线总览与阶段清单，查看完整行程"),
    IMMERSIVE("沉浸导航", "大地图跟随当前路段，前进方向朝上"),
}

/** A mode belongs to one active journey. Only ending it permits another choice. */
sealed interface NavigationSession {
    data object Idle : NavigationSession
    data class Choosing(val plan: JourneyPlan) : NavigationSession
    data class Active(
        val plan: JourneyPlan,
        val mode: NavigationMode,
        val legIndex: Int = 0,
    ) : NavigationSession
}

internal fun NavigationSession.choose(plan: JourneyPlan): NavigationSession = when (this) {
    is NavigationSession.Active -> this
    else -> NavigationSession.Choosing(plan)
}

internal fun NavigationSession.start(mode: NavigationMode): NavigationSession = when (this) {
    is NavigationSession.Choosing -> NavigationSession.Active(plan, mode)
    else -> this
}

internal fun NavigationSession.advance(): NavigationSession = when (this) {
    is NavigationSession.Active -> if (legIndex < plan.legs.lastIndex) copy(legIndex = legIndex + 1)
        else NavigationSession.Idle
    else -> this
}
