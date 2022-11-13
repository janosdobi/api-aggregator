package home.janos.service

import home.janos.buffer.PricingBuffer
import home.janos.buffer.ShipmentsBuffer
import home.janos.buffer.TrackingBuffer
import home.janos.client.AggregatorClient
import home.janos.domain.AggregatorResponse
import io.micronaut.context.annotation.Context
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.scheduling.annotation.Scheduled
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.util.*

@Context
class BufferingAggregatorService(
    private val aggregatorClient: AggregatorClient,
    private val pricingBuffer: PricingBuffer,
    private val shipmentBuffer: ShipmentsBuffer,
    private val trackingBuffer: TrackingBuffer
) {

    private val logger = KotlinLogging.logger { }

    suspend fun enqueue(pricing: Set<String>, tracking: Set<String>, shipments: Set<String>) {
        pricingBuffer.putInRequestBuffer(pricing)
        trackingBuffer.putInRequestBuffer(tracking)
        shipmentBuffer.putInRequestBuffer(shipments)
        coroutineScope {
            if (pricingBuffer.size() >= 5) {
                launch {
                    pricingBuffer.storeResponses(callAPI(pricingBuffer.take(5), aggregatorClient::pricing))
                }
            }
            if (shipmentBuffer.size() >= 5) {
                launch {
                    shipmentBuffer.storeResponses(callAPI(shipmentBuffer.take(5), aggregatorClient::shipments))
                }
            }
            if (trackingBuffer.size() >= 5) {
                launch {
                    trackingBuffer.storeResponses(callAPI(trackingBuffer.take(5), aggregatorClient::tracking))
                }
            }
        }
    }

    private suspend fun <V : Any> callAPI(
        ids: Set<String>,
        apiCallFunction: suspend (Set<String>) -> Map<String, V?>?
    ): Map<String, Optional<V>> {
        return try {
            apiCallFunction.invoke(ids)
                ?.mapValues { if (it.value == null) Optional.empty() else Optional.of(it.value!!) }
                ?.also {
                    logger.info { "Backend call successful: $it" }
                } ?: ids.associateWith { Optional.empty<V>() }
        } catch (ex: HttpClientResponseException) {
            logger.error { "Backend call failed: ${ex.message}" }
            ids.associateWith { Optional.empty<V>() }
        }
    }

    fun poll(pricing: Set<String>, tracking: Set<String>, shipments: Set<String>): AggregatorResponse {

        while (!pricing.all { pricingBuffer.hasResponseFor(it) } ||
            !shipments.all { shipmentBuffer.hasResponseFor(it) } ||
            !tracking.all { trackingBuffer.hasResponseFor(it) }) {
            Thread.sleep(500)
        }

        return AggregatorResponse(
            pricing.associateWith { pricingBuffer.removeResponse(it) },
            tracking.associateWith { trackingBuffer.removeResponse(it) },
            shipments.associateWith { shipmentBuffer.removeResponse(it) }
        )
    }

    @Scheduled(fixedRate = "500ms")
    fun fireStuckRequests() {
        //TODO https://github.com/micronaut-projects/micronaut-core/issues/7224
        runBlocking {
            coroutineScope {
                if (!pricingBuffer.isEmpty() && pricingBuffer.secondsSinceLastUpdate() > 5) {
                    launch {
                        pricingBuffer.storeResponses(callAPI(pricingBuffer.take(5), aggregatorClient::pricing))
                    }
                }
                if (!shipmentBuffer.isEmpty() && shipmentBuffer.secondsSinceLastUpdate() > 5) {
                    launch {
                        shipmentBuffer.storeResponses(callAPI(shipmentBuffer.take(5), aggregatorClient::shipments))
                    }
                }
                if (!trackingBuffer.isEmpty() && trackingBuffer.secondsSinceLastUpdate() > 5) {
                    launch {
                        trackingBuffer.storeResponses(callAPI(trackingBuffer.take(5), aggregatorClient::tracking))
                    }
                }
            }
        }
    }
}
