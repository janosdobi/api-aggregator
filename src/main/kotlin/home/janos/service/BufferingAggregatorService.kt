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

    suspend fun enqueue(uuid: UUID, pricing: Set<String>, tracking: Set<String>, shipments: Set<String>) {
        pricingBuffer.putInRequestBuffer(uuid, pricing)
        trackingBuffer.putInRequestBuffer(uuid, tracking)
        shipmentBuffer.putInRequestBuffer(uuid, shipments)
        coroutineScope {
            if (pricingBuffer.size() >= 5) {
                val pricingRequests = pricingBuffer.take(5)
                launch {
                    pricingBuffer.storeResponses(pricingRequests, callAPI(pricingRequests, aggregatorClient::pricing))
                }
            }
            if (shipmentBuffer.size() >= 5) {
                val shipmentRequests = shipmentBuffer.take(5)
                launch {
                    shipmentBuffer.storeResponses(
                        shipmentRequests,
                        callAPI(shipmentRequests, aggregatorClient::shipments)
                    )
                }
            }
            if (trackingBuffer.size() >= 5) {
                val trackingRequests = trackingBuffer.take(5)
                launch {
                    trackingBuffer.storeResponses(
                        trackingRequests,
                        callAPI(trackingRequests, aggregatorClient::tracking)
                    )
                }
            }
        }
    }

    private suspend fun <V : Any> callAPI(
        ids: List<Pair<UUID, String>>,
        apiCallFunction: suspend (List<String>) -> Map<String, V?>?
    ): Map<String, Optional<V>> {
        val idStrings = ids.map { it.second }
        return try {
            apiCallFunction.invoke(idStrings)
                ?.mapValues { if (it.value == null) Optional.empty() else Optional.of(it.value!!) }
                ?.also {
                    logger.info { "Backend call successful: $it" }
                } ?: idStrings.associateWith { Optional.empty<V>() }
        } catch (ex: HttpClientResponseException) {
            logger.error { "Backend call failed: ${ex.message}" }
            idStrings.associateWith { Optional.empty<V>() }
        }
    }

    fun poll(uuid: UUID, pricing: Set<String>, tracking: Set<String>, shipments: Set<String>): AggregatorResponse {

        while (!pricing.all { pricingBuffer.hasResponseFor(uuid to it) } ||
            !shipments.all { shipmentBuffer.hasResponseFor(uuid to it) } ||
            !tracking.all { trackingBuffer.hasResponseFor(uuid to it) }) {
            Thread.sleep(500)
        }

        return AggregatorResponse(
            pricing.associateWith { pricingBuffer.removeResponse(uuid to it) },
            tracking.associateWith { trackingBuffer.removeResponse(uuid to it) },
            shipments.associateWith { shipmentBuffer.removeResponse(uuid to it) }
        )
    }

    @Scheduled(fixedRate = "500ms")
    fun fireStuckRequests() {
        //TODO https://github.com/micronaut-projects/micronaut-core/issues/7224
        runBlocking {
            coroutineScope {
                if (!pricingBuffer.isEmpty() && pricingBuffer.secondsSinceLastUpdate() > 5) {
                    val pricingRequests = pricingBuffer.take(5)
                    launch {
                        pricingBuffer.storeResponses(pricingRequests, callAPI(pricingRequests, aggregatorClient::pricing))
                    }
                }
                if (!shipmentBuffer.isEmpty() && shipmentBuffer.secondsSinceLastUpdate() > 5) {
                    val shipmentRequests = shipmentBuffer.take(5)
                    launch {
                        shipmentBuffer.storeResponses(
                            shipmentRequests,
                            callAPI(shipmentRequests, aggregatorClient::shipments)
                        )
                    }
                }
                if (!trackingBuffer.isEmpty() && trackingBuffer.secondsSinceLastUpdate() > 5) {
                    val trackingRequests = trackingBuffer.take(5)
                    launch {
                        trackingBuffer.storeResponses(
                            trackingRequests,
                            callAPI(trackingRequests, aggregatorClient::tracking)
                        )
                    }
                }
            }
        }
    }
}
