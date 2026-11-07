package org.storyteller_f.bailongmap.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.storyteller_f.bailongmap.data.transit.TransitPackageInfo
import org.storyteller_f.bailongmap.ui.transit.*
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransitSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun showsReviewAndWiresExplicitConfirmation() {
        val info = TransitPackageInfo("Example", "Synthetic", "20261101", "20261130", 3, 2)
        val state = mutableStateOf(TransitUiState(importing = TransitImportState.Idle))
        var installs = 0
        compose.setContent {
            MaterialTheme {
                TransitSettings(state.value,
                    onChoose = { state.value = TransitUiState(importing = TransitImportState.Review(info)) },
                    onInstall = { installs++; state.value = TransitUiState(installed = info, importing = TransitImportState.Idle) },
                    onDiscard = {}, onRemove = {})
            }
        }
        compose.onNodeWithText("导入公共交通包").performClick()
        compose.onNodeWithText("待导入：Example", substring = true).assertExists()
        assertEquals(0, installs)
        compose.onNodeWithText("确认导入").performClick()
        compose.onNodeWithText("Example · 3 站 · 2 条线路", substring = true).assertExists()
        assertEquals(1, installs)
    }
}
