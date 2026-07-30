package org.storyteller_f.bailongmap.data.model

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "Latitude must be finite and between -90 and 90"
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "Longitude must be finite and between -180 and 180"
        }
    }
}

enum class TravelMode {
    WALK,
    SUBWAY,
    BICYCLE,
    TRANSIT,
    CAR,
}

data class JourneyLeg(
    val mode: TravelMode,
    val points: List<RoutePoint>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val fromName: String,
    val toName: String,
    val routeName: String? = null,
) {
    init {
        require(points.size >= 2) { "A journey leg must contain at least two points" }
        require(distanceMeters.isFinite() && distanceMeters >= 0.0) {
            "Journey leg distance must be finite and non-negative"
        }
        require(durationSeconds.isFinite() && durationSeconds >= 0.0) {
            "Journey leg duration must be finite and non-negative"
        }
        require(fromName.isNotBlank()) { "Journey leg origin must not be blank" }
        require(toName.isNotBlank()) { "Journey leg destination must not be blank" }
    }
}

data class JourneyPlan(
    val id: String,
    val legs: List<JourneyLeg>,
    val durationSeconds: Double = legs.sumOf(JourneyLeg::durationSeconds),
) {
    init {
        require(id.isNotBlank()) { "Journey plan id must not be blank" }
        require(legs.isNotEmpty()) { "A journey plan must contain at least one leg" }
        require(durationSeconds.isFinite() && durationSeconds >= 0.0) {
            "Journey plan duration must be finite and non-negative"
        }
    }

    val distanceMeters: Double = legs.sumOf(JourneyLeg::distanceMeters)
    val isMultiStage: Boolean = legs.size > 1
    val transferCount: Int = (legs.size - 1).coerceAtLeast(0)
    val points: List<RoutePoint> = legs.flatMap(JourneyLeg::points)
}
