package org.storyteller_f.bailongmap.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.storyteller_f.bailongmap.data.model.*
import org.storyteller_f.bailongmap.ui.navigation.*
import org.storyteller_f.bailongmap.ui.map.rememberMapSheetState
import org.storyteller_f.bailongmap.ui.theme.BailongTheme
import kotlin.test.*

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NavigationModeUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val place = Place("test", "Library", "Example address", 0.01, 0.0, "library", "amenity")
    private val plan = JourneyPlan("test", listOf(JourneyLeg(TravelMode.WALK,
        listOf(RoutePoint(0.0, 0.0), RoutePoint(0.01, 0.0)), 1100.0, 600.0, "A", "B")))

    @Test fun selectingModeRequiresExplicitStart() {
        var started: NavigationMode? = null
        compose.setContent { BailongTheme {
            NavigationModeSheet(place, plan, rememberMapSheetState(), { started = it }, {})
        } }
        compose.onNodeWithText("预览导航").performClick()
        compose.runOnIdle { assertNull(started) }
        compose.onNodeWithText("以预览模式开始导航").performClick()
        compose.runOnIdle { assertEquals(NavigationMode.PREVIEW, started) }
    }

    @Test fun immersiveDetailsKeepTheNavigationModeLocked() {
        var ended = false
        compose.setContent { BailongTheme { Box {
            ImmersiveNavigation(place, NavigationSession.Active(plan, NavigationMode.IMMERSIVE),
                null, false, true, false, {}, {}, {}, { ended = true })
        } } }
        compose.onNodeWithText("行程信息").performClick()
        compose.onNodeWithText("当前为沉浸导航，本次模式已锁定。关闭后继续导航。").assertExists()
        compose.onNodeWithText("继续导航").performClick()
        compose.onNodeWithText("沉浸导航 · 模式已锁定").assertExists()
        compose.onNodeWithText("预览导航").assertDoesNotExist()
        compose.runOnIdle { assertFalse(ended) }
    }
}
