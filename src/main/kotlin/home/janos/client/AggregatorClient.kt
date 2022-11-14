package home.janos.client

import home.janos.domain.TrackingStatus
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client

@Client(value = "\${backend-service.url}")
interface AggregatorClient {
    @Get("/shipments")
    suspend fun shipments(@QueryValue("q") ids: List<String>): Map<String, List<String>>?

    @Get("/track")
    suspend fun tracking(@QueryValue("q") ids: List<String>): Map<String, TrackingStatus>?

    @Get("/pricing")
    suspend fun pricing(@QueryValue("q") countryCodes: List<String>): Map<String, Double>?
}
