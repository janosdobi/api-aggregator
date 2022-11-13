package home.janos.buffer

import home.janos.domain.TrackingStatus
import io.micronaut.context.annotation.Context

@Context
class TrackingBuffer : AggregatorBuffer<TrackingStatus>()