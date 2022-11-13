package home.janos.domain

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.core.annotation.Introspected

@Introspected
data class AggregatorResponse(
    val pricing: Map<String, Double?>,
    val tracking: Map<String, TrackingStatus?>,
    val shipments: Map<String, List<String>?>
)

@Introspected
enum class TrackingStatus {
    NEW,

    @JsonProperty("IN TRANSIT")
    IN_TRANSIT,
    COLLECTING,
    COLLECTED,
    DELIVERING,
    DELIVERED
}