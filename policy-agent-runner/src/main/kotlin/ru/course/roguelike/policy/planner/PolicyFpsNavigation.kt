package ru.course.roguelike.policy.planner

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.shared.model.FpsConstants
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.protocol.GameActions
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * Policy-agent micro navigation: one [game_sync] step with explicit yaw (same idea as KeyHuntPlanner).
 * Works after combat aim without turn_left chains or legacy compass [game_act] moves.
 */
object PolicyFpsNavigation {
    fun syncStepTowardCell(
        sessionId: String,
        pose: PlayerPose,
        cell: GridPos,
        map: TileMap? = null,
    ): ToolCallDecision = syncStepToward(
        sessionId = sessionId,
        pose = pose,
        targetX = cell.x + 0.5f,
        targetY = cell.y + 0.5f,
        map = map,
        targetCell = cell,
    )

    fun syncStepToward(
        sessionId: String,
        pose: PlayerPose,
        targetX: Float,
        targetY: Float,
        map: TileMap? = null,
        targetCell: GridPos? = null,
    ): ToolCallDecision {
        val dx = targetX - pose.x
        val dy = targetY - pose.y
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (dist <= ARRIVE_TOLERANCE) {
            return gridAct(sessionId, GameActions.WAIT)
        }
        val yaw = atan2(dy.toDouble(), dx.toDouble()).toFloat()
        val stepDist = min(min(dist, MAX_STEP_DIST), dist - ARRIVE_TOLERANCE * 0.5f)
            .coerceAtLeast(MIN_STEP_DIST)
        val deltaMs = ((stepDist / FpsConstants.MOVE_SPEED) * 1000f)
            .toInt()
            .coerceIn(FpsConstants.MOVEMENT_SUBSTEP_MS, 200)
        val jump = map != null && targetCell != null &&
            PolicyVerticalHelper.shouldJumpForMove(map, pose, targetCell)
        return ToolCallDecision(
            tool = "game_sync",
            arguments = buildJsonObject {
                put("sessionId", sessionId)
                put("clientYaw", yaw)
                put("clientPitch", 0f)
                put("forward", true)
                put("jump", jump)
                put("deltaMs", deltaMs)
            }.mapValues { it.value },
        )
    }

    fun syncAimToward(
        sessionId: String,
        pose: PlayerPose,
        targetX: Float,
        targetY: Float,
    ): ToolCallDecision {
        val dx = targetX - pose.x
        val dy = targetY - pose.y
        val yaw = atan2(dy.toDouble(), dx.toDouble()).toFloat()
        return PolicySyncActions.sync(
            sessionId = sessionId,
            yaw = yaw,
            forward = false,
            deltaMs = 80,
        )
    }

    private fun gridAct(sessionId: String, action: String) = ToolCallDecision(
        tool = "game_act",
        arguments = buildJsonObject {
            put("sessionId", sessionId)
            put("action", action)
        }.mapValues { it.value },
    )

    private const val ARRIVE_TOLERANCE = 0.08f
    private const val MAX_STEP_DIST = 0.38f
    private const val MIN_STEP_DIST = 0.02f
}
