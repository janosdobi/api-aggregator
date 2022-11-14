package home.janos.controller

import home.janos.domain.AggregatorResponse
import home.janos.service.BufferingAggregatorService
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import mu.KotlinLogging
import java.util.*

@Controller
class AggregatorController(
    private val bufferingAggregatorService: BufferingAggregatorService
) {

    private val logger = KotlinLogging.logger {  }

    @Get("/aggregation")
    suspend fun aggregateBuffered(
        @QueryValue pricing: List<String>,
        @QueryValue track: List<String>,
        @QueryValue shipments: List<String>
    ): HttpResponse<AggregatorResponse> {
        val uuid = UUID.randomUUID()
        val pricingSet = pricing.toSet()
        val shipmentSet = shipments.toSet()
        val trackingSet = track.toSet()
        logger.info{ "Request received, enqueuing. UUID: $uuid Pricing: $pricing, Track: $track, shipments: $shipments" }
        bufferingAggregatorService.enqueue(uuid, pricingSet, trackingSet, shipmentSet)
        return HttpResponse.ok(bufferingAggregatorService.poll(uuid, pricingSet, trackingSet, shipmentSet))
    }
}
