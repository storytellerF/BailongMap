package org.storyteller_f.bailongmap.appium

import io.appium.java_client.AppiumBy
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.options.UiAutomator2Options
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.RegisterExtension
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Base64

abstract class BaseAppiumTest {
    private var isScreenRecording = false
    private var isMockLocationActive = false
    private val reversedTcpPorts = mutableSetOf<Int>()
    protected lateinit var driver: AndroidDriver
    protected lateinit var wait: WebDriverWait

    @JvmField
    @RegisterExtension
    val failureArtifacts = FailureArtifactsExtension()

    @BeforeEach
    fun setUp() {
        val serverUrl = System.getenv("APPIUM_SERVER_URL") ?: "http://127.0.0.1:4723/"
        clearAppData(appPackage())

        val options = UiAutomator2Options()
            .setPlatformName("Android")
            .setAutomationName("UiAutomator2")
            .setAppPackage(appPackage())
            .setAppActivity(System.getenv("APP_ACTIVITY") ?: ".MainActivity")
            .setAutoGrantPermissions(true)
            .setNoReset(true)

        System.getenv("ANDROID_DEVICE_NAME")?.let(options::setDeviceName)
        System.getenv("ANDROID_UDID")?.let(options::setUdid)

        driver = AndroidDriver(URI(serverUrl).toURL(), options)
        failureArtifacts.driver = driver
        driver.startRecordingScreen()
        isScreenRecording = true
        wait = WebDriverWait(driver, Duration.ofSeconds(30))
    }

    @AfterEach
    fun tearDown(testInfo: TestInfo) {
        saveScreenRecording(testInfo)
        resetMockLocation()
        resetReversedTcpPorts()
        if (::driver.isInitialized) {
            driver.quit()
        }
    }

    protected fun findByText(text: String): WebElement =
        wait.until(ExpectedConditions.elementToBeClickable(
            AppiumBy.androidUIAutomator("""new UiSelector().text("$text")"""),
        ))

    protected fun findByDescription(description: String): WebElement =
        wait.until(ExpectedConditions.elementToBeClickable(
            AppiumBy.androidUIAutomator("""new UiSelector().description("$description")"""),
        ))

    protected fun findById(resourceId: String): WebElement =
        wait.until(ExpectedConditions.elementToBeClickable(AppiumBy.id(resourceId)))

    protected fun waitForText(text: String, timeout: Duration = Duration.ofSeconds(30)): Boolean {
        val w = if (timeout.seconds == 30L) wait else WebDriverWait(driver, timeout)
        return runCatching {
            w.until {
                it.findElements(AppiumBy.androidUIAutomator("""new UiSelector().textContains("$text")""")).isNotEmpty()
            }
        }.getOrDefault(false)
    }

    protected fun waitForAnyText(vararg texts: String, timeout: Duration = Duration.ofSeconds(30)): Boolean {
        val w = if (timeout.seconds == 30L) wait else WebDriverWait(driver, timeout)
        return runCatching {
            w.until { driver ->
                texts.any { text ->
                    driver.findElements(
                        AppiumBy.androidUIAutomator("""new UiSelector().textContains("$text")"""),
                    ).isNotEmpty()
                }
            }
        }.getOrDefault(false)
    }

    protected fun startDeepLink(url: String) {
        val command = adbCommand()
        command += listOf(
            "shell",
            "am",
            "start",
            "-a",
            "android.intent.action.VIEW",
            "-d",
            shellQuote(url),
            "-n",
            "${appPackage()}/.MainActivity",
        )
        runAdb(command, "adb deep link failed")
    }

    protected fun reverseTcpPort(devicePort: Int, hostPort: Int = devicePort) {
        val command = adbCommand()
        command += listOf("reverse", "tcp:$devicePort", "tcp:$hostPort")
        runAdb(command, "adb reverse failed")
        reversedTcpPorts += devicePort
    }

    protected fun setAdbMockLocation(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double = 3.0,
    ) {
        check(latitude.isFinite() && latitude in -90.0..90.0) {
            "Latitude must be finite and between -90 and 90"
        }
        check(longitude.isFinite() && longitude in -180.0..180.0) {
            "Longitude must be finite and between -180 and 180"
        }
        check(accuracyMeters.isFinite() && accuracyMeters >= 0.0) {
            "Accuracy must be finite and non-negative"
        }

        val appOpsCommand = adbCommand()
        appOpsCommand += listOf(
            "shell",
            "appops",
            "set",
            APPIUM_SETTINGS_PACKAGE,
            "android:mock_location",
            "allow",
        )
        runAdb(appOpsCommand, "enabling adb mock location failed")

        val locationCommand = adbCommand()
        locationCommand += listOf(
            "shell",
            "am",
            "start-foreground-service",
            "-e",
            "longitude",
            longitude.toString(),
            "-e",
            "latitude",
            latitude.toString(),
            "-e",
            "accuracy",
            accuracyMeters.toString(),
            APPIUM_LOCATION_SERVICE,
        )
        runAdb(locationCommand, "injecting adb mock location failed")
        isMockLocationActive = true
    }

    private fun adbCommand(): MutableList<String> {
        val command = mutableListOf(resolveAdbPath())
        System.getenv("ANDROID_UDID")?.let {
            command += "-s"
            command += it
        }
        return command
    }

    private fun runAdb(command: List<String>, errorMessage: String) {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        check(exitCode == 0) { "$errorMessage ($exitCode): $output" }
    }

    private fun resetMockLocation() {
        if (!isMockLocationActive) return

        val stopServiceCommand = adbCommand()
        stopServiceCommand += listOf(
            "shell",
            "am",
            "stopservice",
            APPIUM_LOCATION_SERVICE,
        )
        runCatching {
            runAdb(stopServiceCommand, "stopping adb mock location service failed")
        }

        val appOpsCommand = adbCommand()
        appOpsCommand += listOf(
            "shell",
            "appops",
            "set",
            APPIUM_SETTINGS_PACKAGE,
            "android:mock_location",
            "deny",
        )
        runCatching {
            runAdb(appOpsCommand, "resetting adb mock location permission failed")
        }
        isMockLocationActive = false
    }

    private fun resetReversedTcpPorts() {
        reversedTcpPorts.forEach { devicePort ->
            val command = adbCommand()
            command += listOf("reverse", "--remove", "tcp:$devicePort")
            runCatching {
                runAdb(command, "removing adb reverse for tcp:$devicePort failed")
            }
        }
        reversedTcpPorts.clear()
    }

    protected fun appPackage(): String =
        System.getenv("APP_PACKAGE") ?: "org.storyteller_f.bailongmap"

    private fun clearAppData(pkg: String) {
        val command = mutableListOf(resolveAdbPath())
        System.getenv("ANDROID_UDID")?.let {
            command += "-s"
            command += it
        }
        command += listOf("shell", "pm", "clear", pkg)
        ProcessBuilder(command).redirectErrorStream(true).start().waitFor()
    }

    private fun saveScreenRecording(testInfo: TestInfo) {
        val outputDir = Path.of(System.getProperty("project.root.dir"), "build", "appium-videos")
        Files.createDirectories(outputDir)

        val videoPath = outputDir.resolve("${testFileStem(testInfo)}.mp4")
        val errorPath = outputDir.resolve("${testFileStem(testInfo)}.record-error.txt")
        Files.deleteIfExists(videoPath)
        Files.deleteIfExists(errorPath)

        runCatching {
            check(::driver.isInitialized && isScreenRecording) { "Appium screen recording was not started" }
            val decoded = Base64.getDecoder().decode(driver.stopRecordingScreen())
            Files.write(videoPath, decoded)
        }.onFailure { error ->
            Files.writeString(errorPath, "Failed to save Appium screen recording: ${error.message}\n")
        }.also {
            isScreenRecording = false
        }
    }

    protected fun testFileStem(testInfo: TestInfo): String =
        testInfo.displayName
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "appium-test" }

    companion object {
        private const val APPIUM_SETTINGS_PACKAGE = "io.appium.settings"
        private const val APPIUM_LOCATION_SERVICE =
            "$APPIUM_SETTINGS_PACKAGE/.LocationService"

        private fun shellQuote(value: String): String =
            "'${value.replace("'", "'\\''")}'"

        fun resolveAdbPath(): String {
            val candidates = listOfNotNull(
                System.getenv("ADB"),
                System.getenv("ANDROID_HOME")?.let { "$it/platform-tools/adb" },
                System.getenv("ANDROID_SDK_ROOT")?.let { "$it/platform-tools/adb" },
                "adb",
            )
            return candidates.firstOrNull { candidate ->
                candidate == "adb" || File(candidate).canExecute()
            } ?: "adb"
        }
    }

    class FailureArtifactsExtension : AfterTestExecutionCallback {
        var driver: AndroidDriver? = null

        override fun afterTestExecution(context: ExtensionContext) {
            if (!context.executionException.isPresent) return
            val d = driver ?: return
            val stem = context.displayName
                .replace(Regex("[^A-Za-z0-9._-]+"), "_")
                .trim('_')
                .ifBlank { "appium-test" }
            val outputDir = Path.of(System.getProperty("project.root.dir"), "build", "appium-failures")
            Files.createDirectories(outputDir)

            runCatching {
                Files.writeString(outputDir.resolve("$stem-view-tree.xml"), d.pageSource)
            }.onFailure { error ->
                Files.writeString(outputDir.resolve("$stem-view-tree-error.txt"), error.message.orEmpty())
            }
        }
    }
}
