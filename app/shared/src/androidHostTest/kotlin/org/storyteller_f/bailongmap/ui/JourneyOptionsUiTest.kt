package org.storyteller_f.bailongmap.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.storyteller_f.bailongmap.data.model.*
import org.storyteller_f.bailongmap.data.network.*
import org.storyteller_f.bailongmap.ui.navigation.JourneyOptionsCard
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class JourneyOptionsUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun completedRoadRemainsSelectableWhileTransitIsPending() {
        val plan=JourneyPlan("car",listOf(JourneyLeg(TravelMode.CAR,
            listOf(RoutePoint(1.0,103.0),RoutePoint(1.01,103.01)),1000.0,60.0,"A","B")))
        var selected: String?=null
        val groups=listOf(JourneyGroup(JourneyCategory.CAR,JourneyAvailability.Available(listOf(plan))),
            JourneyGroup(JourneyCategory.TRANSIT,JourneyAvailability.Planning))
        compose.setContent { MaterialTheme { JourneyOptionsCard(
            destination=Place("test","Destination","Destination",1.01,103.01,"test","test"),
            plans=listOf(plan),isLoading=true,onSelect={selected=it},onCancel={},groups=groups) } }
        assertNull(selected)
        compose.onAllNodesWithText("驾车").assertCountEquals(2)
        compose.onNodeWithText("公共交通").assertExists()
        compose.onNodeWithText("选择").performClick()
        assertEquals("car",selected)
    }
}
