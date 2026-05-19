package ru.rkhamatyarov.workshop

import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.ConcurrentHashMap

@ApplicationScoped
class CompiledConfigCache {
    private val cache = ConcurrentHashMap<String, ByteArray>()

    fun put(
        checksum: String,
        bytes: ByteArray,
    ) {
        cache[checksum] = bytes
    }

    fun get(checksum: String): ByteArray? = cache[checksum]

    fun size(): Int = cache.size
}
