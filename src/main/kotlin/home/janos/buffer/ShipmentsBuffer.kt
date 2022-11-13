package home.janos.buffer

import io.micronaut.context.annotation.Context

@Context
class ShipmentsBuffer : AggregatorBuffer<List<String>>()