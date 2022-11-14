package home.janos.buffer

import mu.KotlinLogging
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicLong

abstract class AggregatorBuffer<V> {
    private val requestQueue = ArrayBlockingQueue<Pair<UUID, String>>(10)
    //Optional is used as null values cannot be inserted in concurrent maps
    private val responseMap: ConcurrentMap<Pair<UUID, String>, Optional<V>> = ConcurrentHashMap()
    private val logger = KotlinLogging.logger {  }

    private val lastUpdatedAt = AtomicLong(0)

    fun size(): Int {
        return requestQueue.size
    }

    fun storeResponses(requestIds: List<Pair<UUID, String>>, response: Map<String, Optional<V>>) {
        responseMap.putAll(requestIds.associateWith { response[it.second] })
    }

    fun take(count: Int): List<Pair< UUID, String>> {
        val ids = mutableListOf<Pair< UUID, String>>()
        requestQueue.drainTo(ids, count)
        return ids
    }

    fun putInRequestBuffer(uuid: UUID, ids: Set<String>) {
        for (id in ids) {
            requestQueue.put(uuid to id)
        }
        lastUpdatedAt.set(OffsetDateTime.now().toEpochSecond())
    }

    fun isEmpty(): Boolean {
        return requestQueue.isEmpty()
    }

    fun hasResponseFor(id: Pair< UUID, String>): Boolean {
        return responseMap.containsKey(id)
    }

    fun removeResponse(id: Pair< UUID, String>): V? {
        return responseMap.remove(id)!!.orElse(null)
    }

    fun secondsSinceLastUpdate(): Int {
        val epochNow = OffsetDateTime.now().toEpochSecond()
        val lastUpdatedEpoch = lastUpdatedAt.get()
        return (epochNow - lastUpdatedEpoch).toInt()
    }
}
