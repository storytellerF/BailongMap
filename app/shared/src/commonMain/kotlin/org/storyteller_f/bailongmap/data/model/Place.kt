package org.storyteller_f.bailongmap.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Place(
    val id: String,
    val name: String,
    val displayName: String,
    val lat: Double,
    val lon: Double,
    val type: String,
    val category: String,
)

@Serializable
data class FavoritePlace(
    val place: Place,
    val createdAtEpochMillis: Long,
)

fun Place.toDeepLink(): String =
    "bailongmap://place?lat=$lat&lon=$lon&name=${name.encodeUrlParameter()}&address=${displayName.encodeUrlParameter()}"

private fun String.encodeUrlParameter(): String =
    encodeToByteArray().joinToString("") { byte ->
        val value = byte.toInt() and 0xff
        when (value.toChar()) {
            in 'A'..'Z', in 'a'..'z', in '0'..'9', '-', '_', '.', '~' -> value.toChar().toString()
            else -> "%${value.toString(16).uppercase().padStart(2, '0')}"
        }
    }
