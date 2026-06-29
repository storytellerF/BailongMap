package org.storyteller_f.bailongmap.data.model

data class Place(
    val id: String,
    val name: String,
    val displayName: String,
    val lat: Double,
    val lon: Double,
    val type: String,
    val category: String,
)
