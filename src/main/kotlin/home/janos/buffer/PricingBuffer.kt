package home.janos.buffer

import io.micronaut.context.annotation.Context

@Context
class PricingBuffer : AggregatorBuffer<Double>()