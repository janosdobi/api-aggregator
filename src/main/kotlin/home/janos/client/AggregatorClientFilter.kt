package home.janos.client

import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import org.reactivestreams.Publisher
import java.net.URI

@Filter("/**")
class AggregatorClientFilter : HttpClientFilter {
    override fun doFilter(request: MutableHttpRequest<*>, chain: ClientFilterChain): Publisher<out HttpResponse<*>> {
        request.uri(URI.create(request.uri.toString().replace("%2C", ",")))
        return chain.proceed(request)
    }
}
