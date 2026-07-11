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
import kotlin.io.path.toPath

@Testcontainers
@Tag("offline")
class OfflineMapDownloadTest : BaseAppiumTest() {

    @Test
    fun downloadsVisibleRegionFromLocalTileServer() {
        val serverPort = server.getMappedPort(80)
        reverseTcpPort(serverPort)
        val styleUrl = "http://127.0.0.1:$serverPort/style.json"
        val encodedStyleUrl = URLEncoder.encode(styleUrl, StandardCharsets.UTF_8)
        startDeepLink("bailongmap://offline-test?styleUrl=$encodedStyleUrl")

        assertTrue(waitForText("搜索地点"), "Map screen should be visible")

        findByDescription("离线地图").click()
        assertTrue(waitForText("下载当前区域"), "Offline sheet should show the download action")

        findByText("下载当前区域").click()

        assertTrue(waitForText("离线区域 1"), "Created offline pack should appear in the list")
        assertTrue(
            waitForAnyText("已完成", "下载中", "准备中", "已暂停"),
            "Offline pack should report a download state",
        )
    }

    companion object {
        private val offlineServerRoot: Path =
            checkNotNull(OfflineMapDownloadTest::class.java.getResource("/offline-server")) {
                "Missing offline-server test fixture"
            }.toURI().toPath()

        @Container
        @JvmStatic
        val server: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("nginx:1.27-alpine"))
                .withFileSystemBind(
                    offlineServerRoot.toString(),
                    "/usr/share/nginx/html",
                    BindMode.READ_ONLY,
                )
                .withExposedPorts(80)
    }
}
