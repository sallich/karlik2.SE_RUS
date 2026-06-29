package ru.course.roguelike.agent.explore

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import ru.course.roguelike.shared.dto.DoorMarkerSnapshot
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.engine.DoorInteraction
import ru.course.roguelike.shared.engine.GridPathfinder
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.InteractionConstants
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.protocol.GameActions

/** Door / E-interact helpers — same rules as human player (DoorInteraction + InteractionConstants). */
object AgentDoorHelper {
    /** Local map window for "D visible" checks — matches COMPACT prompt radius. */
    const val DOOR_VIEW_RADIUS = 2

    fun tileMap(snapshot: GameSnapshot): TileMap =
        TileMap.fromFlat(snapshot.width, snapshot.height, snapshot.tiles)

    fun doorCell(door: DoorMarkerSnapshot): GridPos =
        GridPos(floor(door.x).toInt(), floor(door.y).toInt())

    /** Seal cell where game_act interact (= key E) would open a room door. */
    fun interactableDoorSeal(snapshot: GameSnapshot): GridPos? {
        val seal = DoorInteraction.findInteractable(tileMap(snapshot), snapshot.player.pose) ?: return null
        return seal.takeIf { hasDoorMarker(snapshot, seal) }
    }

    /** Nearest door marker whose D cell is shown in the local map window. */
    fun nearestVisibleDoor(snapshot: GameSnapshot, radius: Int = DOOR_VIEW_RADIUS): DoorMarkerSnapshot? {
        val px = snapshot.player.pose.x.toInt()
        val py = snapshot.player.pose.y.toInt()
        return snapshot.doorMarkers
            .filter { door ->
                val cx = floor(door.x).toInt()
                val cy = floor(door.y).toInt()
                abs(cx - px) <= radius && abs(cy - py) <= radius
            }
            .minByOrNull { distanceToDoor(snapshot.player.pose, it) }
    }

    fun hasDoorSealInView(snapshot: GameSnapshot, radius: Int = DOOR_VIEW_RADIUS): Boolean {
        val px = floor(snapshot.player.pose.x).toInt()
        val py = floor(snapshot.player.pose.y).toInt()
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                if (isDoorSealCell(snapshot, px + dx, py + dy)) return true
            }
        }
        return false
    }

    fun isDoorSealCell(snapshot: GameSnapshot, x: Int, y: Int): Boolean {
        if (x < 0 || x >= snapshot.width || y < 0 || y >= snapshot.height) return false
        if (snapshot.doorMarkers.any { floor(it.x).toInt() == x && floor(it.y).toInt() == y }) return true
        val tile = snapshot.tiles[y * snapshot.width + x]
        return tile == TileType.ROOM_SEAL || tile == TileType.ROOM_DOOR
    }

    fun distanceToDoor(pose: PlayerPose, door: DoorMarkerSnapshot): Float =
        hypot((door.x - pose.x).toDouble(), (door.y - pose.y).toDouble()).toFloat()

    fun isNearDoor(pose: PlayerPose, door: DoorMarkerSnapshot): Boolean =
        distanceToDoor(pose, door) <= InteractionConstants.DOOR_INTERACT_RADIUS

    fun isAdjacentToDoorCell(pose: PlayerPose, door: DoorMarkerSnapshot): Boolean {
        val dc = doorCell(door)
        val px = floor(pose.x).toInt()
        val py = floor(pose.y).toInt()
        return abs(px - dc.x) + abs(py - dc.y) == 1
    }

    fun isAdjacentToAnyDoorSeal(snapshot: GameSnapshot): Boolean {
        val door = nearestVisibleDoor(snapshot) ?: return false
        return isAdjacentToDoorCell(snapshot.player.pose, door)
    }

    /**
     * True only when D is visible in the local map AND player is close to that visible seal.
     * Avoids treating a distant global door or a plain # wall as "at the door".
     */
    fun isNearAnyDoorSeal(snapshot: GameSnapshot): Boolean {
        if (!hasDoorSealInView(snapshot)) return false
        val door = nearestVisibleDoor(snapshot) ?: return false
        val pose = snapshot.player.pose
        return isNearDoor(pose, door) || isAdjacentToDoorCell(pose, door)
    }

    fun isFacingTarget(pose: PlayerPose, targetX: Float, targetY: Float, toleranceRad: Float = 0.55f): Boolean {
        val targetYaw = atan2((targetY - pose.y).toDouble(), (targetX - pose.x).toDouble()).toFloat()
        return abs(angleDelta(pose.yaw, targetYaw)) <= toleranceRad
    }

    fun suggestTurnToward(pose: PlayerPose, targetX: Float, targetY: Float): String {
        val targetYaw = atan2((targetY - pose.y).toDouble(), (targetX - pose.x).toDouble()).toFloat()
        return if (angleDelta(pose.yaw, targetYaw) >= 0f) {
            GameActions.TURN_RIGHT
        } else {
            GameActions.TURN_LEFT
        }
    }

    /**
     * Next grid action for a door: stand on floor beside D, face D, press E.
     * Never returns a move that steps onto the seal tile (D is unwalkable).
     */
    fun suggestDoorAction(snapshot: GameSnapshot, door: DoorMarkerSnapshot): String {
        if (canPressE(snapshot)) return GameActions.INTERACT
        val pose = snapshot.player.pose
        if (isAdjacentToDoorCell(pose, door) && !isFacingTarget(pose, door.x, door.y)) {
            return suggestTurnToward(pose, door.x, door.y)
        }
        return suggestApproachMove(snapshot, door)
    }

    fun suggestApproachMove(snapshot: GameSnapshot, door: DoorMarkerSnapshot): String {
        val map = tileMap(snapshot)
        val pose = snapshot.player.pose
        val dc = doorCell(door)
        val px = floor(pose.x).toInt()
        val py = floor(pose.y).toInt()

        if (isAdjacentToDoorCell(pose, door)) {
            return suggestTurnToward(pose, door.x, door.y)
        }

        val approachTiles = listOf(
            dc.x - 1 to dc.y,
            dc.x + 1 to dc.y,
            dc.x to dc.y - 1,
            dc.x to dc.y + 1,
        ).filter { (x, y) ->
            map.get(GridPos(x, y))?.walkable == true
        }

        if (approachTiles.isEmpty()) return GameActions.WAIT

        val (tx, ty) = approachTiles.minByOrNull { (x, y) ->
            hypot((x + 0.5 - pose.x).toDouble(), (y + 0.5 - pose.y).toDouble())
        }!!
        pathfinderStepToward(snapshot, GridPos(tx, ty))?.let { return it }
        return gridStep(px, py, tx, ty)
    }

    /** Walkable grid exits from current cell — for prompt and unstuck logic. */
    fun formatOpenExits(snapshot: GameSnapshot): String {
        val map = tileMap(snapshot)
        val cell = playerCell(snapshot)
        val exits = listOf(
            "north" to GridPos(cell.x + 1, cell.y),
            "south" to GridPos(cell.x - 1, cell.y),
            "east" to GridPos(cell.x, cell.y + 1),
            "west" to GridPos(cell.x, cell.y - 1),
        ).map { (name, pos) ->
            if (isWalkableCell(map, pos)) {
                "$name=OPEN→game_act move_$name"
            } else {
                "$name=blocked(#/D)"
            }
        }
        return "Corridor exits from @: ${exits.joinToString(", ")}"
    }

    /**
     * When a direct compass move hits a wall, pick the next step along a BFS path
     * or any open corridor — may detour away from the door temporarily.
     */
    fun suggestUnstuckMove(snapshot: GameSnapshot, lastBlockedMove: String?, stepIndex: Int): String {
        val map = tileMap(snapshot)
        val cell = playerCell(snapshot)
        val door = snapshot.doorMarkers.minByOrNull { distanceToDoor(snapshot.player.pose, it) }

        if (door != null) {
            val dc = doorCell(door)
            val approachGoals = listOf(
                GridPos(dc.x - 1, dc.y),
                GridPos(dc.x + 1, dc.y),
                GridPos(dc.x, dc.y - 1),
                GridPos(dc.x, dc.y + 1),
            ).filter { isWalkableCell(map, it) }
            val goal = GridPathfinder.nearestReachable(map, cell, approachGoals)
            if (goal != null) {
                val path = GridPathfinder.path(map, cell, goal)
                if (path != null && path.size >= 2) {
                    for (idx in 1 until path.size) {
                        val step = gridStep(cell.x, cell.y, path[idx].x, path[idx].y)
                        if (step != GameActions.WAIT && step != lastBlockedMove) return step
                    }
                }
            }
        }

        val open = walkableNeighborActions(map, cell)
        if (open.isEmpty()) return GameActions.TURN_LEFT
        val withoutBlocked = open.filter { it != lastBlockedMove }
        val pool = withoutBlocked.ifEmpty { open }
        if (door != null) {
            val dc = doorCell(door)
            return pool.minByOrNull { action ->
                val next = neighborCell(cell, action)
                hypot((next.x - dc.x).toDouble(), (next.y - dc.y).toDouble())
            } ?: pool[stepIndex % pool.size]
        }
        return pool[stepIndex % pool.size]
    }

    private fun pathfinderStepToward(snapshot: GameSnapshot, goal: GridPos): String? {
        val map = tileMap(snapshot)
        val cell = playerCell(snapshot)
        val path = GridPathfinder.path(map, cell, goal) ?: return null
        if (path.size < 2) return null
        return gridStep(cell.x, cell.y, path[1].x, path[1].y)
    }

    private fun playerCell(snapshot: GameSnapshot): GridPos {
        val map = tileMap(snapshot)
        val pose = snapshot.player.pose
        val primary = GridPos(floor(pose.x).toInt(), floor(pose.y).toInt())
        if (isWalkableCell(map, primary)) return primary
        return listOf(
            GridPos(1, 0),
            GridPos(-1, 0),
            GridPos(0, 1),
            GridPos(0, -1),
            GridPos(1, 1),
            GridPos(-1, 1),
            GridPos(1, -1),
            GridPos(-1, -1),
        )
            .map { GridPos(primary.x + it.x, primary.y + it.y) }
            .filter { isWalkableCell(map, it) }
            .minByOrNull {
                hypot((it.x + 0.5 - pose.x).toDouble(), (it.y + 0.5 - pose.y).toDouble())
            } ?: primary
    }

    private fun walkableNeighborActions(map: TileMap, cell: GridPos): List<String> = buildList {
        if (isWalkableCell(map, GridPos(cell.x + 1, cell.y))) add(GameActions.MOVE_NORTH)
        if (isWalkableCell(map, GridPos(cell.x - 1, cell.y))) add(GameActions.MOVE_SOUTH)
        if (isWalkableCell(map, GridPos(cell.x, cell.y + 1))) add(GameActions.MOVE_EAST)
        if (isWalkableCell(map, GridPos(cell.x, cell.y - 1))) add(GameActions.MOVE_WEST)
    }

    private fun neighborCell(cell: GridPos, action: String): GridPos = when (action) {
        GameActions.MOVE_NORTH -> GridPos(cell.x + 1, cell.y)
        GameActions.MOVE_SOUTH -> GridPos(cell.x - 1, cell.y)
        GameActions.MOVE_EAST -> GridPos(cell.x, cell.y + 1)
        GameActions.MOVE_WEST -> GridPos(cell.x, cell.y - 1)
        else -> cell
    }

    private fun isWalkableCell(map: TileMap, pos: GridPos): Boolean {
        val tile = map.get(pos) ?: return false
        return tile.walkable || tile == TileType.EXIT_GATE
    }

    /** Doors visible in local map window — helps LLM match D cells to goals. */
    fun doorsInView(snapshot: GameSnapshot, radius: Int = DOOR_VIEW_RADIUS): String {
        if (!hasDoorSealInView(snapshot, radius)) {
            return "Doors in view: none — if map shows only # you hit a WALL; do NOT use interact. Mobs nearby ≠ door."
        }
        val px = snapshot.player.pose.x.toInt()
        val py = snapshot.player.pose.y.toInt()
        val visible = snapshot.doorMarkers
            .map { door ->
                val cx = floor(door.x).toInt()
                val cy = floor(door.y).toInt()
                Triple(cx, cy, doorLabel(door))
            }
            .filter { (x, y, _) -> abs(x - px) <= radius && abs(y - py) <= radius }
            .distinct()
            .sortedBy { (x, y, _) -> hypot((x - px).toDouble(), (y - py).toDouble()) }
            .joinToString { (x, y, label) -> "($x,$y $label)" }
        return "Doors in view: [$visible] — interact ONLY on D (not #). Stand on floor beside D, face D, action=interact."
    }

    fun doorLabel(door: DoorMarkerSnapshot): String = when {
        door.prizeIsKey -> "keyRoom"
        door.mobRoom -> "mobRoom"
        door.kind != null -> "loot"
        else -> "room"
    }

    fun nearestDoor(snapshot: GameSnapshot): DoorMarkerSnapshot? =
        snapshot.doorMarkers.minByOrNull { distanceToDoor(snapshot.player.pose, it) }

    /** Sorted door list for the prompt — agent navigates by coordinates when D is off-map. */
    fun formatAllDoorsList(snapshot: GameSnapshot, limit: Int = 6): String {
        if (snapshot.doorMarkers.isEmpty()) return "All doors: none on level."
        val pose = snapshot.player.pose
        val listed = snapshot.doorMarkers
            .sortedBy { distanceToDoor(pose, it) }
            .take(limit)
            .joinToString { door ->
                val dc = doorCell(door)
                val dist = distanceToDoor(pose, door)
                "(${dc.x},${dc.y} ${doorLabel(door)} dist=${"%.1f".format(dist)})"
            }
        return "All doors nearest-first: [$listed]"
    }

    /** Grid delta from player cell to door cell — helps LLM pick move_* toward D. */
    fun relativeDoorOffset(pose: PlayerPose, door: DoorMarkerSnapshot): Pair<Int, Int> {
        val dc = doorCell(door)
        val px = floor(pose.x).toInt()
        val py = floor(pose.y).toInt()
        return dc.x - px to dc.y - py
    }

    fun formatDoorCompass(snapshot: GameSnapshot, door: DoorMarkerSnapshot): String {
        val (dx, dy) = relativeDoorOffset(snapshot.player.pose, door)
        val axis = buildList {
            if (dx > 0) add("move_north +$dx x")
            if (dx < 0) add("move_south ${abs(dx)} x")
            if (dy > 0) add("move_east +$dy y")
            if (dy < 0) add("move_west ${abs(dy)} y")
        }.joinToString(", ")
        val step = suggestApproachMove(snapshot, door)
        return "Toward door (${doorCell(door).x},${doorCell(door).y}): offset dx=$dx dy=$dy ($axis). " +
            "Next: game_act action=$step."
    }

    fun doorStuckHint(snapshot: GameSnapshot): String? {
        if (!hasDoorSealInView(snapshot)) {
            return "STUCK on # wall — no D in map window. Do NOT interact; mobs nearby are NOT doors. Use move_* to explore."
        }
        val door = nearestVisibleDoor(snapshot) ?: return null
        if (!isNearDoor(snapshot.player.pose, door) && !isAdjacentToDoorCell(snapshot.player.pose, door)) {
            return null
        }
        val action = suggestDoorAction(snapshot, door)
        val dc = doorCell(door)
        return when (action) {
            GameActions.INTERACT ->
                "STUCK beside visible D ($dc.x,$dc.y) — game_act action=interact (E) NOW."
            GameActions.TURN_LEFT, GameActions.TURN_RIGHT ->
                "STUCK beside visible D ($dc.x,$dc.y) — face D: game_act action=$action, then interact."
            else ->
                "STUCK near visible D ($dc.x,$dc.y) — step to floor beside D: game_act action=$action."
        }
    }

    /** True when game_act action=interact should work (door seal in reach, key, or exit). */
    fun canPressE(snapshot: GameSnapshot): Boolean {
        if (interactableDoorSeal(snapshot) != null) return true
        val pose = snapshot.player.pose
        snapshot.keyPickups.forEach { key ->
            if (hypot((key.x - pose.x).toDouble(), (key.y - pose.y).toDouble()) <=
                InteractionConstants.INTERACT_RADIUS
            ) {
                return true
            }
        }
        if (snapshot.keysCollected >= snapshot.keysRequired) {
            snapshot.exitGate?.let { gate ->
                val px = floor(pose.x).toInt()
                val py = floor(pose.y).toInt()
                if (px == gate.x && py == gate.y) return true
            }
        }
        return false
    }

    fun isTurnAction(action: String?): Boolean =
        action == GameActions.TURN_LEFT || action == GameActions.TURN_RIGHT

    private fun hasDoorMarker(snapshot: GameSnapshot, seal: GridPos): Boolean =
        snapshot.doorMarkers.any {
            floor(it.x).toInt() == seal.x && floor(it.y).toInt() == seal.y
        }

    private fun gridStep(px: Int, py: Int, tx: Int, ty: Int): String {
        val dx = (tx - px).coerceIn(-1, 1)
        val dy = (ty - py).coerceIn(-1, 1)
        return when {
            abs(dx) >= abs(dy) && dx > 0 -> GameActions.MOVE_NORTH
            abs(dx) >= abs(dy) && dx < 0 -> GameActions.MOVE_SOUTH
            dy > 0 -> GameActions.MOVE_EAST
            dy < 0 -> GameActions.MOVE_WEST
            else -> GameActions.WAIT
        }
    }

    private fun angleDelta(from: Float, to: Float): Float {
        var d = to - from
        while (d > Math.PI) d -= (2 * Math.PI).toFloat()
        while (d < -Math.PI) d += (2 * Math.PI).toFloat()
        return d
    }
}
