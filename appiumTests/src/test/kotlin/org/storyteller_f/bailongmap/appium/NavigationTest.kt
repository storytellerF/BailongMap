package org.storyteller_f.bailongmap.appium

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("navigation")
class NavigationTest : BaseAppiumTest() {

    @Test
    fun selectsAndNavigatesMultiStageJourneyFromAdbMockLocation() {
        val routeRequested = AtomicBoolean(false)
        val routeServer = createRouteServer(routeRequested)
        routeServer.start()
        try {
            assertTrue(waitForText("搜索地点"), "Map screen should be visible")
            setAdbMockLocation(
                latitude = ORIGIN_LATITUDE,
                longitude = ORIGIN_LONGITUDE,
            )
            reverseTcpPort(DEVICE_ROUTE_SERVER_PORT, routeServer.address.port)

            val destinationName = encode("导航测试终点")
            val destinationAddress = encode("北京市测试地址")
            val otpGraphQlUrl =
                encode("http://127.0.0.1:$DEVICE_ROUTE_SERVER_PORT/otp/gtfs/v1")
            val styleUrl = encode("http://127.0.0.1:$DEVICE_ROUTE_SERVER_PORT/style.json")
            startDeepLink(
                "bailongmap://place" +
                    "?lat=$DESTINATION_LATITUDE" +
                    "&lon=$DESTINATION_LONGITUDE" +
                    "&name=$destinationName" +
                    "&address=$destinationAddress" +
                    "&otpGraphQlUrl=$otpGraphQlUrl" +
                    "&styleUrl=$styleUrl"
            )

            assertTrue(waitForText("导航测试终点"), "Destination details should be visible")
            findByText("导航").click()
            findById(LOCATION_PERMISSION_ALLOW_BUTTON).click()
            setAdbMockLocation(
                latitude = ORIGIN_LATITUDE,
                longitude = ORIGIN_LONGITUDE,
            )

            assertTrue(
                waitForText("选择行程方案", Duration.ofSeconds(60)),
                "Journey alternatives should be visible",
            )
            assertTrue(waitForText("方案 1 · 最快"), "Fastest journey should be labeled")
            assertTrue(waitForText("方案 2"), "Second journey alternative should be visible")
            assertTrue(waitForText("方案 3"), "Third journey alternative should be visible")
            assertTrue(
                waitForText("步行 → 地铁 → 自行车"),
                "Multi-stage journey should list every travel mode",
            )
            assertTrue(routeRequested.get(), "Local OTP fixture should receive the journey request")

            findByText("方案 1 · 最快").click()
            assertTrue(waitForText("当前阶段：步行"), "Walking should be the first active leg")
            assertTrue(waitForText("第 1/3 段"), "First of three legs should be active")
            assertTrue(waitForText("10 公里"), "Journey should show total distance")
            assertTrue(waitForText("35 分钟"), "Journey should show total duration")

            setAdbMockLocation(
                latitude = SUBWAY_ORIGIN_LATITUDE,
                longitude = SUBWAY_ORIGIN_LONGITUDE,
            )
            assertTrue(
                waitForText("当前阶段：地铁 2号线"),
                "Reaching the first endpoint should activate the subway leg",
            )

            setAdbMockLocation(
                latitude = BICYCLE_ORIGIN_LATITUDE,
                longitude = BICYCLE_ORIGIN_LONGITUDE,
            )
            assertTrue(
                waitForText("当前阶段：自行车"),
                "Reaching the subway endpoint should activate the bicycle leg",
            )

            findByDescription("结束导航").click()
            assertFalse(
                waitForText("结束导航", Duration.ofSeconds(3)),
                "Navigation summary should close",
            )
        } finally {
            routeServer.stop(0)
        }
    }

    companion object {
        private const val ORIGIN_LATITUDE = 39.9042
        private const val ORIGIN_LONGITUDE = 116.4074
        private const val SUBWAY_ORIGIN_LATITUDE = 39.9060
        private const val SUBWAY_ORIGIN_LONGITUDE = 116.4100
        private const val BICYCLE_ORIGIN_LATITUDE = 39.9110
        private const val BICYCLE_ORIGIN_LONGITUDE = 116.4140
        private const val DESTINATION_LATITUDE = 39.9142
        private const val DESTINATION_LONGITUDE = 116.4174
        private const val LOCATION_PERMISSION_ALLOW_BUTTON =
            "com.android.permissioncontroller:id/permission_allow_foreground_only_button"
        private const val DEVICE_ROUTE_SERVER_PORT = 18081
        private const val JOURNEY_RESPONSE = """
            {
              "data": {
                "plan": {
                  "itineraries": [
                    {
                      "duration": 2100.0,
                      "legs": [
                        {
                          "mode": "WALK",
                          "distance": 500.0,
                          "duration": 300.0,
                          "from": {"name": "出发点", "lat": 39.9042, "lon": 116.4074},
                          "to": {"name": "地铁站", "lat": 39.9060, "lon": 116.4100},
                          "legGeometry": {"points": "gxprFgyneUsDcGsDcG"}
                        },
                        {
                          "mode": "SUBWAY",
                          "distance": 8000.0,
                          "duration": 1200.0,
                          "from": {"name": "地铁站", "lat": 39.9060, "lon": 116.4100},
                          "to": {"name": "换乘站", "lat": 39.9110, "lon": 116.4140},
                          "route": {"shortName": "2号线", "longName": "地铁2号线"},
                          "legGeometry": {"points": "ocqrFoioeUsNoKsNoK"}
                        },
                        {
                          "mode": "BICYCLE",
                          "distance": 1500.0,
                          "duration": 600.0,
                          "from": {"name": "换乘站", "lat": 39.9110, "lon": 116.4140},
                          "to": {"name": "导航测试终点", "lat": 39.9142, "lon": 116.4174},
                          "legGeometry": {"points": "wbrrFobpeU_IsI_IsI"}
                        }
                      ]
                    },
                    {
                      "duration": 2400.0,
                      "legs": [
                        {
                          "mode": "WALK",
                          "distance": 700.0,
                          "duration": 480.0,
                          "from": {"name": "出发点", "lat": 39.9042, "lon": 116.4074},
                          "to": {"name": "地铁站", "lat": 39.9060, "lon": 116.4100},
                          "legGeometry": {"points": "gxprFgyneUsDcGsDcG"}
                        },
                        {
                          "mode": "SUBWAY",
                          "distance": 9000.0,
                          "duration": 1920.0,
                          "from": {"name": "地铁站", "lat": 39.9060, "lon": 116.4100},
                          "to": {"name": "导航测试终点", "lat": 39.9142, "lon": 116.4174},
                          "route": {"shortName": "1号线", "longName": "地铁1号线"},
                          "legGeometry": {"points": "gxprFgyneUg^g^g^g^"}
                        }
                      ]
                    },
                    {
                      "duration": 2700.0,
                      "legs": [
                        {
                          "mode": "BICYCLE",
                          "distance": 11000.0,
                          "duration": 2700.0,
                          "from": {"name": "出发点", "lat": 39.9042, "lon": 116.4074},
                          "to": {"name": "导航测试终点", "lat": 39.9142, "lon": 116.4174},
                          "legGeometry": {"points": "gxprFgyneUg^g^g^g^"}
                        }
                      ]
                    }
                  ]
                }
              }
            }
        """
        private const val STYLE_RESPONSE = """
            {
              "version": 8,
              "name": "Navigation test",
              "sources": {},
              "layers": [
                {
                  "id": "background",
                  "type": "background",
                  "paint": {
                    "background-color": "#e8eef5"
                  }
                }
              ]
            }
        """

        private fun encode(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8)

        private fun createRouteServer(routeRequested: AtomicBoolean): HttpServer =
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/style.json") { exchange ->
                    val body = STYLE_RESPONSE.trimIndent().toByteArray(StandardCharsets.UTF_8)
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                createContext("/otp/gtfs/v1") { exchange ->
                    routeRequested.set(true)
                    val body = JOURNEY_RESPONSE.trimIndent().toByteArray(StandardCharsets.UTF_8)
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
            }
    }
}
