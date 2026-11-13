package org.storyteller_f.bailongmap.appium

import io.appium.java_client.AppiumBy
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.openqa.selenium.interactions.PointerInput
import org.openqa.selenium.ScreenOrientation
import org.openqa.selenium.interactions.Sequence
import org.openqa.selenium.support.ui.ExpectedConditions

@Tag("map-ui")
class MapSheetTest : BaseAppiumTest() {
    @Test
    fun startsNativeMapWithAllSheetsHidden() {
        assertTrue(waitForText("想去哪里？"), "Native map screen should start without a linker crash")
        findByDescription("Map")
        assertTrue(driver.findElements(AppiumBy.accessibilityId("Drag handle")).isEmpty())
        findByDescription("设置")
        findByText("离线")
    }

    @ParameterizedTest
    @ValueSource(strings = ["设置", "离线地图"])
    fun mapSheetClosesWithBackAndReopensThenDismissesByDragging(description: String) {
        val content = if (description == "设置") "默认地图样式" else "离线中心"
        assertTrue(waitForText("想去哪里？"))

        openSheet(description)
        assertTrue(waitForText(content), "Sheet content should be visible")
        driver.navigate().back()
        waitForSheetDismissed()

        openSheet(description)
        assertTrue(waitForText(content), "Sheet should reopen")
        dismissByDraggingHandle()
        waitForSheetDismissed()
        findByDescription("Map")
    }

    @Test
    fun placeDetailsCloseAndReopenForTheSamePlace() {
        assertTrue(waitForText("想去哪里？"))
        val placeUrl = "bailongmap://place?lat=0&lon=0&name=SheetTest&address=TestAddress"

        repeat(2) {
            startDeepLink(placeUrl)
            assertTrue(waitForText("SheetTest"), "Requested place should be displayed")
            assertTrue(waitForText("TestAddress"), "Place address should be displayed")
            assertTrue(waitForText("导航"), "Place actions should be available")
            driver.navigate().back()
            waitForSheetDismissed()
        }
    }

    @Test
    fun consumedPlaceLinkDoesNotReplaceManualOriginAfterScreenRecreation() {
        assertTrue(waitForText("想去哪里？"))
        startDeepLink("bailongmap://place?lat=0&lon=0&name=OriginTest&address=TestAddress")
        assertTrue(waitForText("OriginTest"))
        findByText("导航").click()
        assertTrue(waitForText("从哪里出发？"))
        findByText("手动选择起点").click()
        assertTrue(waitForText("选择路线起点"))
        try {
            driver.rotate(ScreenOrientation.LANDSCAPE)
            assertTrue(waitForText("选择路线起点"), "Consumed link must not replay on recreation")
            driver.rotate(ScreenOrientation.PORTRAIT)
            assertTrue(waitForText("使用这个起点"))
            startDeepLink("bailongmap://place?lat=1&lon=1&name=Replacement&address=NewAddress")
            assertTrue(waitForText("Replacement"), "A new intent should still open a new place")
            driver.navigate().back()
            wait.until(ExpectedConditions.invisibilityOfElementLocated(
                AppiumBy.androidUIAutomator("""new UiSelector().text("选择路线起点")""")))
        } finally {
            driver.rotate(ScreenOrientation.PORTRAIT)
        }
    }

    private fun openSheet(description: String) {
        if (description == "设置") findByDescription(description).click() else findByText("离线").click()
    }

    private fun waitForSheetDismissed() {
        wait.until(ExpectedConditions.invisibilityOfElementLocated(AppiumBy.accessibilityId("Drag handle")))
    }

    private fun dismissByDraggingHandle() {
        val handle = findByDescription("Drag handle").rect
        val finger = PointerInput(PointerInput.Kind.TOUCH, "sheet-drag")
        val gesture = Sequence(finger, 0).apply {
            addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(),
                handle.x + handle.width / 2, handle.y + handle.height / 2))
            addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
            addAction(finger.createPointerMove(Duration.ofMillis(600), PointerInput.Origin.viewport(),
                handle.x + handle.width / 2, driver.manage().window().size.height - 100))
            addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()))
        }
        driver.perform(listOf(gesture))
    }
}
