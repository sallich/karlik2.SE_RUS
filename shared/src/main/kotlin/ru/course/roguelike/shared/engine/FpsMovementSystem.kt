package ru.course.roguelike.shared.engine

import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.model.FpsConstants
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

object FpsMovementSystem {
    private const val SWEEP_ITERATIONS = 12
    private const val SWEEP_EPSILON = 0.0005f
    private const val MAX_SUBSTEPS = 12

    fun applyInput(map: TileMap, pose: PlayerPose, input: InputSyncRequest): PlayerPose =
        applyInputWithDebug(map, pose, input).pose

    /**
     * Движение и поворот разбиваются на подшаги (~16 ms): W всегда «вперёд по камере»,
     * даже если за пакет sync пришёл суммарный [yawDelta].
     */
    fun applyInputWithDebug(map: TileMap, pose: PlayerPose, input: InputSyncRequest): MovementOutcome {
        if (input.clientYaw != null && input.clientPitch != null) {
            return applyInputWithSyncedLook(
                map,
                pose,
                input,
                input.clientYaw,
                input.clientPitch,
            )
        }

        val totalMs = input.deltaMs.coerceIn(1, 200)
        val steps = ceil(totalMs.toFloat() / FpsConstants.MOVEMENT_SUBSTEP_MS).toInt()
            .coerceIn(1, MAX_SUBSTEPS)
        val msPerStep = totalMs / steps

        val startX = pose.x
        val startY = pose.y
        var stepPose = pose
        val allHits = linkedSetOf<GridPos>()
        var lastDebug: CollisionDebug? = null

        repeat(steps) {
            val stepInput = input.forSubstep(steps, msPerStep)
            val outcome = applyInputStep(map, stepPose, stepInput)
            stepPose = outcome.pose
            allHits.addAll(outcome.debug.hitCells)
            lastDebug = outcome.debug
        }

        val finalPose = stepPose
        val actualDx = finalPose.x - startX
        val actualDy = finalPose.y - startY
        val ld = lastDebug

        val debug = CollisionDebug(
            startX = startX,
            startY = startY,
            requestedDx = ld?.requestedDx ?: 0f,
            requestedDy = ld?.requestedDy ?: 0f,
            actualDx = actualDx,
            actualDy = actualDy,
            hitCells = allHits.toList(),
            blocked = ld?.blocked == true,
            sweepFraction = ld?.sweepFraction ?: 1f,
            lookYaw = finalPose.yaw,
            moveYaw = finalPose.yaw,
        )
        return MovementOutcome(finalPose, debug)
    }

    /**
     * Симуляция пакета sync: yaw/pitch линейно от (end − delta) до end, движение на каждом подшаге
     * строго по текущему yaw — совпадает с траекторией клиента.
     */
    private fun applyInputWithSyncedLook(
        map: TileMap,
        pose: PlayerPose,
        input: InputSyncRequest,
        endYaw: Float,
        endPitch: Float,
    ): MovementOutcome {
        val totalMs = input.deltaMs.coerceIn(1, 200)
        val steps = ceil(totalMs.toFloat() / FpsConstants.MOVEMENT_SUBSTEP_MS).toInt()
            .coerceIn(1, MAX_SUBSTEPS)
        val msPerStep = totalMs / steps

        val startYaw = endYaw - input.yawDelta
        val startPitch = (endPitch - input.pitchDelta)
            .coerceIn(-FpsConstants.MAX_PITCH, FpsConstants.MAX_PITCH)

        val startX = pose.x
        val startY = pose.y
        var stepPose = pose
        val allHits = linkedSetOf<GridPos>()
        var lastDebug: CollisionDebug? = null

        repeat(steps) { index ->
            val t = (index + 1) / steps.toFloat()
            val yaw = startYaw + (endYaw - startYaw) * t
            val pitch = startPitch + (endPitch - startPitch) * t
            val stepInput = input.forSubstep(steps, msPerStep).copy(
                yawDelta = 0f,
                pitchDelta = 0f,
            )
            val outcome = applyInputStep(
                map,
                stepPose.copy(yaw = yaw, pitch = pitch),
                stepInput,
            )
            stepPose = outcome.pose.copy(yaw = yaw, pitch = pitch)
            allHits.addAll(outcome.debug.hitCells)
            lastDebug = outcome.debug
        }

        val finalPose = stepPose.copy(yaw = endYaw, pitch = endPitch)
        val ld = lastDebug
        return MovementOutcome(
            finalPose,
            CollisionDebug(
                startX = startX,
                startY = startY,
                requestedDx = ld?.requestedDx ?: 0f,
                requestedDy = ld?.requestedDy ?: 0f,
                actualDx = finalPose.x - startX,
                actualDy = finalPose.y - startY,
                hitCells = allHits.toList(),
                blocked = ld?.blocked == true,
                sweepFraction = ld?.sweepFraction ?: 1f,
                lookYaw = endYaw,
                moveYaw = endYaw,
            ),
        )
    }

    private fun applyInputStep(map: TileMap, pose: PlayerPose, input: InputSyncRequest): MovementOutcome {
        val startX = pose.x
        val startY = pose.y
        val dt = input.deltaMs.coerceIn(1, 200) / 1000f
        var yaw = pose.yaw + input.yawDelta
        var pitch = pose.pitch + input.pitchDelta
        if (input.turnLeft) yaw -= FpsConstants.TURN_SPEED * dt
        if (input.turnRight) yaw += FpsConstants.TURN_SPEED * dt
        if (input.lookUp) pitch += FpsConstants.PITCH_SPEED * dt
        if (input.lookDown) pitch -= FpsConstants.PITCH_SPEED * dt
        pitch = pitch.coerceIn(-FpsConstants.MAX_PITCH, FpsConstants.MAX_PITCH)

        var dx = 0f
        var dy = 0f
        if (input.forward) {
            dx += cos(yaw) * FpsConstants.MOVE_SPEED * dt
            dy += sin(yaw) * FpsConstants.MOVE_SPEED * dt
        }
        if (input.backward) {
            dx -= cos(yaw) * FpsConstants.MOVE_SPEED * dt
            dy -= sin(yaw) * FpsConstants.MOVE_SPEED * dt
        }
        if (input.strafeLeft) {
            dx += sin(yaw) * FpsConstants.MOVE_SPEED * dt
            dy -= cos(yaw) * FpsConstants.MOVE_SPEED * dt
        }
        if (input.strafeRight) {
            dx -= sin(yaw) * FpsConstants.MOVE_SPEED * dt
            dy += cos(yaw) * FpsConstants.MOVE_SPEED * dt
        }

        val maxStep = FpsConstants.MOVE_SPEED * dt
        val stepLen = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (stepLen > maxStep && stepLen > 1e-6f) {
            val scale = maxStep / stepLen
            dx *= scale
            dy *= scale
        }

        val hits = linkedSetOf<GridPos>()
        val moved = moveWithCollision(map, startX, startY, dx, dy, hits)
        val actualDx = moved.first - startX
        val actualDy = moved.second - startY
        val requestedLen = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val fraction = if (requestedLen < 1e-6f) {
            1f
        } else {
            (hypot(actualDx.toDouble(), actualDy.toDouble()).toFloat() / requestedLen).coerceIn(0f, 1f)
        }

        val debug = CollisionDebug(
            startX = startX,
            startY = startY,
            requestedDx = dx,
            requestedDy = dy,
            actualDx = actualDx,
            actualDy = actualDy,
            hitCells = hits.toList(),
            blocked = fraction < 0.999f && requestedLen > 1e-6f,
            sweepFraction = fraction,
            lookYaw = yaw,
            moveYaw = yaw,
        )
        return MovementOutcome(
            pose = PlayerPose(moved.first, moved.second, yaw, pitch),
            debug = debug,
        )
    }

    private fun InputSyncRequest.forSubstep(steps: Int, msPerStep: Int): InputSyncRequest = copy(
        yawDelta = yawDelta / steps,
        pitchDelta = pitchDelta / steps,
        deltaMs = msPerStep,
    )

    private fun moveWithCollision(
        map: TileMap,
        x: Float,
        y: Float,
        dx: Float,
        dy: Float,
        hits: MutableSet<GridPos>,
    ): Pair<Float, Float> {
        if (dx == 0f && dy == 0f) return x to y

        var px = x
        var py = y
        val afterX = sweepTo(map, px, py, dx, 0f, hits)
        px = afterX.first
        py = afterX.second
        val afterY = sweepTo(map, px, py, 0f, dy, hits)
        px = afterY.first
        py = afterY.second
        return resolveOverlap(map, px, py, hits)
    }

    private fun resolveOverlap(
        map: TileMap,
        x: Float,
        y: Float,
        hits: MutableSet<GridPos>,
    ): Pair<Float, Float> {
        if (!overlapsWall(map, x, y, null)) return x to y
        val nudge = 0.04f
        val candidates = arrayOf(
            nudge to 0f,
            -nudge to 0f,
            0f to nudge,
            0f to -nudge,
        )
        for ((ox, oy) in candidates) {
            val nx = x + ox
            val ny = y + oy
            if (!overlapsWall(map, nx, ny, hits)) return nx to ny
        }
        return x to y
    }

    private fun sweepTo(
        map: TileMap,
        x: Float,
        y: Float,
        dx: Float,
        dy: Float,
        hits: MutableSet<GridPos>,
    ): Pair<Float, Float> {
        if (dx == 0f && dy == 0f) return x to y

        val endX = x + dx
        val endY = y + dy
        if (!overlapsWall(map, endX, endY, hits)) return endX to endY

        var lo = 0f
        var hi = 1f
        repeat(SWEEP_ITERATIONS) {
            val mid = (lo + hi) * 0.5f
            if (overlapsWall(map, x + dx * mid, y + dy * mid, null)) {
                hi = mid
            } else {
                lo = mid
            }
        }

        val contactX = x + dx * lo
        val contactY = y + dy * lo
        overlapsWall(map, contactX + dx * SWEEP_EPSILON, contactY + dy * SWEEP_EPSILON, hits)
        return contactX to contactY
    }

    private fun overlapsWall(
        map: TileMap,
        x: Float,
        y: Float,
        hits: MutableSet<GridPos>?,
    ): Boolean {
        val r = effectiveRadius()
        val minCellX = floor(x - r).toInt()
        val maxCellX = floor(x + r).toInt()
        val minCellY = floor(y - r).toInt()
        val maxCellY = floor(y + r).toInt()
        var blocked = false
        for (cy in minCellY..maxCellY) {
            for (cx in minCellX..maxCellX) {
                val tile = map.get(GridPos(cx, cy))
                if (tile == null || tile == TileType.FLOOR) continue
                if (circleOverlapsCell(x, y, r, cx, cy)) {
                    hits?.add(GridPos(cx, cy))
                    blocked = true
                }
            }
        }
        return blocked
    }

    private fun circleOverlapsCell(px: Float, py: Float, radius: Float, cellX: Int, cellY: Int): Boolean {
        val closestX = px.coerceIn(cellX.toFloat(), cellX + 1f)
        val closestY = py.coerceIn(cellY.toFloat(), cellY + 1f)
        val diffX = px - closestX
        val diffY = py - closestY
        return diffX * diffX + diffY * diffY <= radius * radius
    }

    private fun effectiveRadius(): Float = FpsConstants.PLAYER_RADIUS + FpsConstants.WALL_SKIN
}
