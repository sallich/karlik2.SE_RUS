package ru.course.roguelike.agent.planner

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.engine.GridPathfinder
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.FpsConstants
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.protocol.GameActions
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min

data class ToolCallDecision(
    val tool: String,
    val arguments: Map<String, kotlinx.serialization.json.JsonElement>,
)

class KeyHuntPlanner {
    fun plan(snapshot: GameSnapshot, sessionId: String, actor: String = ACTOR_PLAYER): ToolCallDecision {
        val map = TileMap.fromFlat(snapshot.width, snapshot.height, snapshot.tiles)
        val pose = actorPose(snapshot, actor) ?: return act(sessionId, GameActions.WAIT)

        if (shouldInteract(snapshot, pose)) {
            return act(sessionId, GameActions.INTERACT)
        }

        val cell = resolveCell(map, pose)
        return navigate(sessionId, map, snapshot, cell, pose)
    }

    private fun navigate(
        sessionId: String,
        map: TileMap,
        snapshot: GameSnapshot,
        cell: GridPos,
        pose: PlayerPose,
    ): ToolCallDecision {
        val goal = pickGoal(snapshot, map, cell) ?: return act(sessionId, GameActions.WAIT)
        val path = GridPathfinder.path(map, cell, goal)
        val next = when {
            cell == goal -> return act(sessionId, GameActions.INTERACT)
            path != null && path.size >= 2 -> path[1]
            else -> stepToward(cell, goal, map)
        }
        return if (next == cell) {
            act(sessionId, GameActions.TURN_LEFT)
        } else {
            moveToward(sessionId, pose, next.x + 0.5f, next.y + 0.5f)
        }
    }

    private fun moveToward(
        sessionId: String,
        pose: PlayerPose,
        targetX: Float,
        targetY: Float,
    ): ToolCallDecision {
        val dx = targetX - pose.x
        val dy = targetY - pose.y
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (dist <= ARRIVE_TOLERANCE) {
            return act(sessionId, GameActions.WAIT)
        }
        val yaw = atan2(dy.toDouble(), dx.toDouble()).toFloat()
        val stepDist = min(min(dist, MAX_STEP_DIST), dist - ARRIVE_TOLERANCE * 0.5f)
            .coerceAtLeast(MIN_STEP_DIST)
        val deltaMs = ((stepDist / FpsConstants.MOVE_SPEED) * 1000f)
            .toInt()
            .coerceIn(FpsConstants.MOVEMENT_SUBSTEP_MS, 200)
        return syncMove(sessionId, yaw, deltaMs)
    }

    private fun stepToward(from: GridPos, goal: GridPos, map: TileMap): GridPos {
        val dx = (goal.x - from.x).coerceIn(-1, 1)
        val dy = (goal.y - from.y).coerceIn(-1, 1)
        val candidates = buildList {
            if (dx != 0 || dy != 0) add(GridPos(from.x + dx, from.y + dy))
            add(GridPos(from.x + 1, from.y))
            add(GridPos(from.x - 1, from.y))
            add(GridPos(from.x, from.y + 1))
            add(GridPos(from.x, from.y - 1))
        }
        return candidates.firstOrNull { isWalkable(map, it) } ?: from
    }

    private fun shouldInteract(snapshot: GameSnapshot, pose: PlayerPose): Boolean {
        val nearKey = snapshot.keyPickups.any {
            hypot((it.x - pose.x).toDouble(), (it.y - pose.y).toDouble()) <= KEY_INTERACT_RADIUS
        }
        if (nearKey) return true
        val gate = snapshot.exitGate ?: return false
        if (snapshot.keysCollected < snapshot.keysRequired) return false
        val onGate = floor(pose.x).toInt() == gate.x && floor(pose.y).toInt() == gate.y
        return onGate
    }

    private fun pickGoal(snapshot: GameSnapshot, map: TileMap, cell: GridPos): GridPos? {
        val keyGoals = snapshot.keyPickups.map {
            GridPos(floor(it.x).toInt(), floor(it.y).toInt())
        }
        if (keyGoals.isNotEmpty()) {
            GridPathfinder.nearestReachable(map, cell, keyGoals)?.let { return it }
        }
        return snapshot.exitGate?.let { GridPathfinder.nearestReachable(map, cell, listOf(it)) }
    }

    private fun resolveCell(map: TileMap, pose: PlayerPose): GridPos {
        val primary = GridPos(floor(pose.x).toInt(), floor(pose.y).toInt())
        if (isWalkable(map, primary)) return primary
        val offsets = listOf(
            GridPos(1, 0),
            GridPos(-1, 0),
            GridPos(0, 1),
            GridPos(0, -1),
            GridPos(1, 1),
            GridPos(-1, 1),
            GridPos(1, -1),
            GridPos(-1, -1),
        )
        return offsets
            .map { GridPos(primary.x + it.x, primary.y + it.y) }
            .filter { isWalkable(map, it) }
            .minByOrNull {
                hypot((it.x + 0.5 - pose.x).toDouble(), (it.y + 0.5 - pose.y).toDouble())
            } ?: primary
    }

    private fun isWalkable(map: TileMap, pos: GridPos): Boolean =
        map.get(pos) == TileType.FLOOR || map.get(pos) == TileType.EXIT_GATE

    private fun act(sessionId: String, action: String) = ToolCallDecision(
        tool = "game_act",
        arguments = buildJsonObject {
            put("sessionId", sessionId)
            put("action", action)
        }.mapValues { it.value },
    )

    private fun syncMove(sessionId: String, desiredYaw: Float, deltaMs: Int) = ToolCallDecision(
        tool = "game_sync",
        arguments = buildJsonObject {
            put("sessionId", sessionId)
            put("clientYaw", desiredYaw)
            put("clientPitch", 0f)
            put("forward", true)
            put("deltaMs", deltaMs)
        }.mapValues { it.value },
    )

    companion object {
        const val ACTOR_PLAYER = "player"
        const val ACTOR_AGENT = "agent"
        private const val ARRIVE_TOLERANCE = 0.08f
        private const val MAX_STEP_DIST = 0.38f
        private const val MIN_STEP_DIST = 0.02f
        private const val KEY_INTERACT_RADIUS = 0.65

        private fun actorPose(snapshot: GameSnapshot, actor: String): PlayerPose? = when (actor) {
            ACTOR_AGENT -> snapshot.agent?.pose
            else -> snapshot.player.pose
        }
    }
}
