package ru.rkhamatyarov.service.mvi

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal data class LineCollisionResult(
    val puck: MviPuck,
    val line: MviLine? = null,
)

internal object LineCollisionPhysics {
    fun resolve(
        puck: MviPuck,
        lines: List<MviLine>,
        teleports: Map<String, String>,
        elapsedNs: Long,
    ): LineCollisionResult {
        for (line in lines) {
            val collision = resolveLine(puck, line, lines, teleports, elapsedNs)
            if (collision != null) return collision
        }
        return LineCollisionResult(puck)
    }

    private fun resolveLine(
        puck: MviPuck,
        line: MviLine,
        lines: List<MviLine>,
        teleports: Map<String, String>,
        elapsedNs: Long,
    ): LineCollisionResult? {
        for ((a, b) in line.points.zipWithNext()) {
            if (!segmentCircleIntersects(a, b, puck)) continue
            return resolveCollision(puck, line, lines, teleports, elapsedNs, a, b)
        }
        return null
    }

    private fun resolveCollision(
        puck: MviPuck,
        line: MviLine,
        lines: List<MviLine>,
        teleports: Map<String, String>,
        elapsedNs: Long,
        a: MviPoint,
        b: MviPoint,
    ): LineCollisionResult {
        val partner = teleports[line.id]?.let { id -> lines.firstOrNull { it.id == id } }
        return if (partner == null) {
            deflect(puck, line, a, b)
        } else {
            teleport(puck, line, partner, elapsedNs, a, b)
        }
    }

    private fun deflect(
        puck: MviPuck,
        line: MviLine,
        a: MviPoint,
        b: MviPoint,
    ): LineCollisionResult {
        MviDomainEvents.record(MviDomainEvent.LineDeflect)
        val (vx, vy) = reflectVelocity(puck.vx, puck.vy, a, b)
        return LineCollisionResult(
            puck = puck.copy(vx = vx, vy = vy, spin = puck.spin * LINE_SPIN_RETENTION),
            line = line,
        )
    }

    private fun teleport(
        puck: MviPuck,
        line: MviLine,
        partner: MviLine,
        elapsedNs: Long,
        a: MviPoint,
        b: MviPoint,
    ): LineCollisionResult {
        val pairId = teleportPairId(line.id, partner.id)
        if (!canUseTeleport(puck, pairId, elapsedNs)) return LineCollisionResult(puck)

        val midpoint = lineMidpoint(partner)
        val (vx, vy) = rotateVelocityThroughPortal(puck.vx, puck.vy, a, b, partner)
        return LineCollisionResult(
            puck =
                puck.copy(
                    x = midpoint.x,
                    y = midpoint.y,
                    vx = vx,
                    vy = vy,
                    teleportCooldownUntilNs = elapsedNs + TELEPORT_COOLDOWN_NS,
                    lastTeleportPairId = pairId,
                ),
            line = line,
        )
    }

    private fun canUseTeleport(
        puck: MviPuck,
        pairId: String,
        elapsedNs: Long,
    ): Boolean = puck.lastTeleportPairId != pairId || elapsedNs >= puck.teleportCooldownUntilNs

    private fun teleportPairId(
        firstLineId: String,
        secondLineId: String,
    ): String =
        if (firstLineId <= secondLineId) {
            "$firstLineId:$secondLineId"
        } else {
            "$secondLineId:$firstLineId"
        }

    private fun rotateVelocityThroughPortal(
        vx: Double,
        vy: Double,
        entryA: MviPoint,
        entryB: MviPoint,
        exitLine: MviLine,
    ): Pair<Double, Double> {
        val exitSegment = exitLine.firstValidSegment() ?: return vx to vy
        val entryAngle = segmentAngle(entryA, entryB) ?: return vx to vy
        val exitAngle = segmentAngle(exitSegment.first, exitSegment.second) ?: return vx to vy
        val rotation = exitAngle - entryAngle + PI
        return rotate(vx, vy, rotation)
    }

    private fun rotate(
        vx: Double,
        vy: Double,
        angle: Double,
    ): Pair<Double, Double> =
        (vx * cos(angle) - vy * sin(angle)) to
            (vx * sin(angle) + vy * cos(angle))

    private fun MviLine.firstValidSegment(): Pair<MviPoint, MviPoint>? =
        points
            .zipWithNext()
            .firstOrNull { (a, b) -> segmentLengthSquared(a, b) >= MIN_SEGMENT_LENGTH_SQUARED }

    private fun segmentAngle(
        a: MviPoint,
        b: MviPoint,
    ): Double? {
        if (segmentLengthSquared(a, b) < MIN_SEGMENT_LENGTH_SQUARED) return null
        return atan2(b.y - a.y, b.x - a.x)
    }

    private fun segmentCircleIntersects(
        a: MviPoint,
        b: MviPoint,
        puck: MviPuck,
    ): Boolean {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val fx = a.x - puck.x
        val fy = a.y - puck.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared < MIN_SEGMENT_LENGTH_SQUARED) return false

        val position = ((-fx * dx - fy * dy) / lengthSquared).coerceIn(0.0, 1.0)
        val closestX = a.x + position * dx - puck.x
        val closestY = a.y + position * dy - puck.y
        return closestX * closestX + closestY * closestY <= puck.radius * puck.radius
    }

    private fun reflectVelocity(
        vx: Double,
        vy: Double,
        a: MviPoint,
        b: MviPoint,
    ): Pair<Double, Double> {
        val normalX = -(b.y - a.y)
        val normalY = b.x - a.x
        val length = hypot(normalX, normalY)
        if (length * length < MIN_SEGMENT_LENGTH_SQUARED) return vx to vy

        val unitX = normalX / length
        val unitY = normalY / length
        val dot = vx * unitX + vy * unitY
        return (vx - 2 * dot * unitX) to (vy - 2 * dot * unitY)
    }

    private fun segmentLengthSquared(
        a: MviPoint,
        b: MviPoint,
    ): Double {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return dx * dx + dy * dy
    }

    private fun lineMidpoint(line: MviLine): MviPoint {
        if (line.points.isEmpty()) return MviPoint(0.0, 0.0)
        return MviPoint(
            x = line.points.sumOf { it.x } / line.points.size,
            y = line.points.sumOf { it.y } / line.points.size,
        )
    }

    private const val TELEPORT_COOLDOWN_NS = 100_000_000L
    private const val MIN_SEGMENT_LENGTH_SQUARED = 1e-9
    private const val LINE_SPIN_RETENTION = 0.70
}
