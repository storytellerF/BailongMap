package org.storyteller_f.bailongmap.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.storyteller_f.bailongmap.data.model.Place

@Serializable
private data class NominatimResult(
    @SerialName("place_id") val placeId: Long,
    @SerialName("display_name") val displayName: String,
    val lat: String,
    val lon: String,
    val type: String = "",
    val category: String = "",
    val name: String? = null,
)

class NominatimClient(private val httpClient: HttpClient) {
    suspend fun search(query: String): List<Place> {
        val results: List<NominatimResult> =
            httpClient.get("https://nominatim.openstreetmap.org/search") {
                parameter("q", query)
                parameter("format", "json")
                parameter("limit", "10")
                header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                header("User-Agent", "BailongMap/1.0 (open-source map app)")
            }.body()

        return results.map { r ->
            Place(
                id = r.placeId.toString(),
                name = r.name ?: r.displayName.substringBefore(",").trim(),
                displayName = r.displayName,
                lat = r.lat.toDouble(),
                lon = r.lon.toDouble(),
                type = r.type,
                category = r.category,
            )
        }
    }
}
