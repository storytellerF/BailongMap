package org.storyteller_f.bailongmap.appium

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.toPath

@Testcontainers
@Tag("offline")
class OfflineMapDownloadTest : BaseAppiumTest() {

    @Test
    fun downloadsVisibleRegionFromLocalTileServer() {
        val serverPort = server.getMappedPort(80)
        reverseTcpPort(DEVICE_SERVER_PORT, serverPort)
        val styleUrl = "http://127.0.0.1:$DEVICE_SERVER_PORT/style.json"
        val encodedStyleUrl = URLEncoder.encode(styleUrl, StandardCharsets.UTF_8)
        startDeepLink("bailongmap://offline-test?styleUrl=$encodedStyleUrl")

        assertTrue(waitForText("搜索地点"), "Map screen should be visible")

        findByDescription("离线地图").click()
        assertTrue(waitForText("下载当前区域"), "Offline sheet should show the download action")

        findByText("下载当前区域").click()

        assertTrue(waitForText("离线区域 1"), "Created offline pack should appear in the list")
        assertTrue(
            waitForText("已完成", Duration.ofSeconds(60)),
            "Offline pack should finish downloading",
        )
        assertTrue(
            server.logs.contains("GET /tiles/"),
            "Local tile server should receive at least one tile request",
        )
    }

    companion object {
        private const val DEVICE_SERVER_PORT = 18080

        private val offlineServerRoot: Path =
            checkNotNull(OfflineMapDownloadTest::class.java.getResource("/offline-server")) {
                "Missing offline-server test fixture"
            }.toURI().toPath()

        private val projectRoot: Path =
            Path.of(checkNotNull(System.getProperty("project.root.dir")))

        private val tileFixture: Path =
            projectRoot.resolve("app/androidApp/src/main/res/mipmap-mdpi/ic_launcher.png")

        @Container
        @JvmStatic
        val server: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("nginx:1.27-alpine"))
                .withFileSystemBind(
                    offlineServerRoot.toString(),
                    "/usr/share/nginx/html",
                    BindMode.READ_ONLY,
                )
                .withFileSystemBind(
                    tileFixture.toString(),
                    "/etc/nginx/tile.png",
                    BindMode.READ_ONLY,
                )
                .withFileSystemBind(
                    offlineServerRoot.resolve("nginx.conf").toString(),
                    "/etc/nginx/nginx.conf",
                    BindMode.READ_ONLY,
                )
                .withExposedPorts(80)
    }
}
