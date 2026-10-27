package ru.rkhamatyarov.telemetry

import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.util.UUID

/** Tokens live only as long as their originating WebSocket; cooldowns are shared by the room. */
@ApplicationScoped
class CaptureAccess {
    @ConfigProperty(name = "remimho.capture.enabled", defaultValue = "true")
    var enabled: Boolean = true

    private val grants = mutableMapOf<String, Grant>()
    private val nextAllowed = mutableMapOf<String, Long>()

    @Synchronized
    fun issue(
        connectionId: String,
        roomId: String,
    ): String? {
        if (!enabled) return null
        val token = UUID.randomUUID().toString()
        grants[connectionId] = Grant(roomId, token)
        return token
    }

    @Synchronized
    fun revoke(connectionId: String) {
        grants.remove(connectionId)
    }

    @Synchronized
    fun acquire(
        roomId: String,
        token: String?,
        nowNs: Long = System.nanoTime(),
    ): CapturePermission {
        if (!enabled) return CapturePermission.DISABLED
        if (token == null || grants.values.none { it.roomId == roomId && it.token == token }) {
            return CapturePermission.FORBIDDEN
        }
        if (nextAllowed[roomId]?.let { nowNs < it } == true) return CapturePermission.COOLDOWN
        nextAllowed[roomId] = nowNs + 10_000_000_000L
        return CapturePermission.ALLOWED
    }

    @Synchronized
    fun clear() {
        grants.clear()
        nextAllowed.clear()
    }

    private data class Grant(val roomId: String, val token: String)
}

enum class CapturePermission { ALLOWED, DISABLED, FORBIDDEN, COOLDOWN }
