package ru.course.roguelike.policy.planner

import ru.course.roguelike.agent.explore.AgentDoorHelper
import ru.course.roguelike.agent.llm.AgentPromptBuilder
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.shared.dto.DoorMarkerSnapshot
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.policy.loop.PolicyContext
import ru.course.roguelike.policy.knowledge.PlayerKnowledgeLayer
import ru.course.roguelike.shared.engine.GridPathfinder
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import kotlin.math.floor

/**
 * Policy-only executor for `exit_room`: BFS on snapshot + frozen room region.
 * Movement via [PolicyFpsNavigation] (game_sync), not agent-runner compass helpers.
 */
object PolicyRoomExitPlanner {
    private const val MAX_REGION = 200
    private const val ROOM_CAPTURE_MAX = MAX_REGION
    private const val MIN_ROOM_FLOOR = 9

    // Room exit is mandatory after a clear; allow crossing damaging tiles (lava) when needed, and
    // allow climbing over COLUMNs (jump up / run across / drop) so column halls do not trap us.
    // Region capture and exit-goal geometry below stay ground-only on purpose (columns must not be
    // misread as part of the room or as doorways) — only the route to a chosen goal goes vertical.
    private fun fpsPath(map: TileMap, start: GridPos, goal: GridPos): List<GridPos>? =
        PolicyFpsPathfinder.path(map, start, goal, allowDamaging = true, allowVertical = true)

    private fun fpsNeighbors(map: TileMap, cell: GridPos): List<GridPos> =
        PolicyFpsPathfinder.navigableNeighbors(map, cell, allowDamaging = true)

    private fun fpsNavigable(map: TileMap, pos: GridPos): Boolean =
        PolicyFpsPathfinder.isNavigable(map, pos, allowDamaging = true)

    private fun fpsNearest(map: TileMap, start: GridPos, goals: List<GridPos>): GridPos? =
        PolicyFpsPathfinder.nearestReachable(map, start, goals, allowDamaging = true, allowVertical = true)

    fun isInsideEnclosedRoom(snapshot: GameSnapshot): Boolean {
        val map = tileMap(snapshot)
        val start = playerCell(snapshot)
        if (!isWalkable(map, start)) return false
        val region = floodFill(map, start, MAX_REGION)
        if (region.size < MIN_ROOM_FLOOR) return false
        return findExitGoals(map, region, start, snapshot).isNotEmpty()
    }

    fun captureRoomRegion(snapshot: GameSnapshot): Set<String> =
        floodFillBoundedByDoorways(tileMap(snapshot), playerCell(snapshot), ROOM_CAPTURE_MAX)
            .map { "${it.x},${it.y}" }
            .toSet()

    fun captureExitGoalKeys(snapshot: GameSnapshot, roomRegion: Set<String>): Set<String> {
        val map = tileMap(snapshot)
        val region = parseRegion(roomRegion) ?: return emptySet()
        val room = roomInteriorFromRegion(map, region)
        val keys = linkedSetOf<String>()
        for (door in mobDoorsForRegion(snapshot, room)) {
            val seal = doorCell(door)
            for (offset in OFFSETS) {
                val neighbor = GridPos(seal.x + offset.x, seal.y + offset.y)
                if (!isWalkable(map, neighbor)) continue
                keys.add("${neighbor.x},${neighbor.y}")
            }
        }
        return keys
    }

    fun navigateToExit(
        sessionId: String,
        snapshot: GameSnapshot,
        frozenRegion: Set<String>? = null,
        frozenExitGoals: Set<String>? = null,
        stepIndex: Int = 0,
        lastBlockedMove: String? = null,
        avoidCellKey: String? = null,
        avoidTargetKeys: Set<String> = emptySet(),
        stuckAttempt: Int = 0,
        context: PolicyContext? = null,
    ): ToolCallDecision {
        if (AgentDoorHelper.canPressE(snapshot)) {
            return AgentPromptBuilder.interactDecision(sessionId)
        }
        val map = tileMap(snapshot)
        val cell = playerCell(snapshot)
        val pose = snapshot.player.pose
        val region = parseRegion(frozenRegion) ?: floodFillBoundedByDoorways(map, cell, MAX_REGION)
        val room = roomInteriorFromRegion(map, region)
        val origin = cell
        val trapped = stuckAttempt >= 1 ||
            PolicyFpsPathfinder.isCornerTrap(map, cell) ||
            avoidTargetKeys.isNotEmpty()
        val pingPong = context?.isPingPong() == true
        if (pingPong) {
            context?.knowledge?.lastJunctionCell(context.visitedTrailList())?.let { junction ->
                val path = fpsPath(map, cell, junction)
                if (path != null && path.size >= 2) {
                    val next = pickNextStep(path, cell, avoidCellKey, avoidTargetKeys)
                    if (next != null) {
                        return stepToCell(sessionId, pose, cell, next, map, context, avoidTargetKeys)
                    }
                }
            }
        }

        if (trapped) {
            val openCell = room
                .filter { fpsNavigable(map, it) }
                .maxByOrNull { fpsNeighbors(map, it).size }
            if (openCell != null && openCell != cell) {
                val retreat = fpsPath(map, cell, openCell)
                if (retreat != null && retreat.size >= 2) {
                    return stepToCell(sessionId, pose, cell, retreat[1], map, context, avoidTargetKeys)
                }
            }
        }

        val insideDoorways = listInsideDoorways(map, room, snapshot)
        val doorwayInside = when {
            trapped && insideDoorways.size > 1 ->
                insideDoorways
                    .filter { fpsPath(map, cell, it) != null }
                    .maxByOrNull { manhattan(cell, it) }
            else ->
                insideDoorways
                    .filter { fpsPath(map, cell, it) != null }
                    .minByOrNull { manhattan(cell, it) }
        }
        val baseGoals = reachableGoalsFps(
            map,
            origin,
            resolveExitGoals(map, region, origin, snapshot, frozenExitGoals, room),
        )
        val orderedGoals = orderGoalsForTrap(origin, baseGoals, doorwayInside, trapped)
        if (orderedGoals.isEmpty()) {
            return exitFallback(sessionId, snapshot, cell, map, region, pose, context, avoidTargetKeys)
        }
        val goal = fpsNearest(map, cell, orderedGoals)
            ?: orderedGoals.firstOrNull()
            ?: return exitFallback(sessionId, snapshot, cell, map, region, pose, context, avoidTargetKeys)

        val path = fpsPath(map, cell, goal)
        if (path != null && path.size >= 2) {
            val next = pickNextStep(path, cell, avoidCellKey, avoidTargetKeys) ?: path[1]
            return stepToCell(sessionId, pose, cell, next, map, context, avoidTargetKeys)
        }

        if (doorwayInside != null && cell != doorwayInside) {
            val toDoor = fpsPath(map, cell, doorwayInside)
            if (toDoor != null && toDoor.size >= 2) {
                val next = pickNextStep(toDoor, cell, avoidCellKey, avoidTargetKeys) ?: toDoor[1]
                return stepToCell(sessionId, pose, cell, next, map, context, avoidTargetKeys)
            }
        }

        if (trapped) {
            val sidestep = fpsNeighbors(map, cell)
                .filter { cellKey(it) !in avoidTargetKeys && cellKey(it) != avoidCellKey }
                .maxByOrNull { fpsNeighbors(map, it).size }
            if (sidestep != null) {
                return stepToCell(sessionId, pose, cell, sidestep, map, context, avoidTargetKeys)
            }
            val fallbackSidestep = PolicyUnstuckPlanner.pickTargetCell(
                snapshot = snapshot,
                knowledge = context?.knowledge ?: PlayerKnowledgeLayer().also { it.revealAllForTest(snapshot) },
                map = map,
                cell = cell,
                stepIndex = stepIndex,
                lastBlockedMove = lastBlockedMove,
                avoidCellKey = avoidCellKey,
                avoidTargetKeys = avoidTargetKeys,
                stuckAttempt = stuckAttempt,
                unstuckMode = context?.currentPolicy?.params?.unstuckMode
                    ?: ru.course.roguelike.policy.dsl.PolicyParams.UNSTUCK_RETREAT,
                visitedTrail = context?.visitedTrailList() ?: emptyList(),
                pingPong = context?.isPingPong() == true,
                visited = context?.visitedCells ?: emptySet(),
            )
            return stepToCell(sessionId, pose, cell, fallbackSidestep, map, context, avoidTargetKeys)
        }

        return exitFallback(sessionId, snapshot, cell, map, region, pose, context, avoidTargetKeys)
    }

    private fun pickNextStep(
        path: List<GridPos>,
        from: GridPos,
        avoidCellKey: String?,
        avoidTargetKeys: Set<String>,
    ): GridPos? {
        val candidates = path.drop(1)
        return candidates.firstOrNull { step ->
            cellKey(step) !in avoidTargetKeys && cellKey(step) != avoidCellKey
        } ?: candidates.firstOrNull { cellKey(it) !in avoidTargetKeys }
            ?: candidates.firstOrNull { cellKey(it) != avoidCellKey }
    }

    private fun stepToCell(
        sessionId: String,
        pose: ru.course.roguelike.shared.model.PlayerPose,
        from: GridPos,
        next: GridPos,
        map: TileMap,
        context: PolicyContext?,
        avoidTargetKeys: Set<String>,
    ): ToolCallDecision {
        val target = if (cellKey(next) in avoidTargetKeys) {
            fpsNeighbors(map, from)
                .filter { cellKey(it) !in avoidTargetKeys }
                .maxByOrNull { fpsNeighbors(map, it).size }
                ?: next
        } else {
            next
        }
        context?.noteUnstuckTarget(cellKey(target))
        return PolicyFpsNavigation.syncStepTowardCell(sessionId, pose, target, map)
    }

    private fun orderGoalsForTrap(
        origin: GridPos,
        baseGoals: List<GridPos>,
        doorwayInside: GridPos?,
        trapped: Boolean,
    ): List<GridPos> = buildList {
        if (doorwayInside != null) add(doorwayInside)
        val rest = baseGoals.filter { it != doorwayInside }
        if (trapped) {
            addAll(rest.sortedByDescending { manhattan(origin, it) })
        } else {
            addAll(rest.sortedBy { manhattan(origin, it) })
        }
    }.distinct()

    private fun cellKey(cell: GridPos): String = "${cell.x},${cell.y}"

    internal fun findExitGoals(
        map: TileMap,
        region: Set<GridPos>,
        origin: GridPos,
        snapshot: GameSnapshot,
    ): List<GridPos> = resolveExitGoals(map, region, origin, snapshot, null)

    private fun resolveExitGoals(
        map: TileMap,
        region: Set<GridPos>,
        origin: GridPos,
        snapshot: GameSnapshot,
        frozenExitGoals: Set<String>?,
        room: Set<GridPos> = roomInteriorFromRegion(map, region),
    ): List<GridPos> {
        val fromMarkers = markerExitGoals(map, region, snapshot, frozenExitGoals, room)
        if (fromMarkers.isNotEmpty()) return fromMarkers.sortedBy { manhattan(origin, it) }

        val doorwayExits = room.filter { cell -> isDoorwayCell(map, cell, room) }
            .flatMap { doorway -> corridorExitNeighbors(map, doorway, room) }
            .distinct()
        if (doorwayExits.isNotEmpty()) return doorwayExits.sortedBy { manhattan(origin, it) }

        return room.flatMap { cell -> corridorExitNeighbors(map, cell, room) }
            .distinct()
            .sortedBy { manhattan(origin, it) }
    }

    /**
     * Real, reachable exit signal for a forced room-exit: a frozen exit goal (captured while the
     * mob door was still sealed) or a `mobRoom` door marker whose interior approach is reachable.
     * Deliberately NOT geometry-based: an open COLUMN/ELEVATOR hall has neither, so the caller can
     * stop forcing exit instead of ping-ponging between fake column-gap "doorways".
     */
    fun hasResolvableExit(snapshot: GameSnapshot, frozenExitGoals: Set<String>?): Boolean {
        val map = tileMap(snapshot)
        val cell = playerCell(snapshot)
        if (!isWalkable(map, cell)) return false
        val frozenReachable = frozenExitGoals?.any { key ->
            parseCell(key)?.let { isWalkable(map, it) && fpsPath(map, cell, it) != null } == true
        } ?: false
        if (frozenReachable) return true
        return snapshot.doorMarkers.filter { it.mobRoom }.any { door ->
            val seal = doorCell(door)
            OFFSETS.map { GridPos(seal.x + it.x, seal.y + it.y) }
                .any { isWalkable(map, it) && fpsPath(map, cell, it) != null }
        }
    }

    private fun parseCell(key: String): GridPos? {
        val parts = key.split(",")
        if (parts.size != 2) return null
        val x = parts[0].toIntOrNull() ?: return null
        val y = parts[1].toIntOrNull() ?: return null
        return GridPos(x, y)
    }

    /**
     * Outside neighbors that are genuine corridor mouths (a pinch with <=2 navigable neighbors), not
     * open floor reachable through a gap between COLUMNs. Without this, a column hall reads every gap
     * as a doorway and the exit planner ping-pongs between fake exits.
     */
    private fun corridorExitNeighbors(map: TileMap, cell: GridPos, region: Set<GridPos>): List<GridPos> =
        outsideWalkableNeighbors(map, cell, region)
            .filter { fpsNavigable(map, it) && fpsNeighbors(map, it).size <= 2 }

    private fun reachableGoalsFps(map: TileMap, origin: GridPos, goals: List<GridPos>): List<GridPos> =
        goals.filter { fpsPath(map, origin, it) != null }

    private fun markerExitGoals(
        map: TileMap,
        region: Set<GridPos>,
        snapshot: GameSnapshot,
        frozenExitGoals: Set<String>?,
        room: Set<GridPos>,
    ): List<GridPos> {
        val markers = mobDoorsForRegion(snapshot, room)
        if (markers.isEmpty() && frozenExitGoals.isNullOrEmpty()) return emptyList()

        val goals = mutableListOf<GridPos>()
        frozenExitGoals?.forEach { key ->
            val parts = key.split(",")
            val pos = GridPos(parts[0].toInt(), parts[1].toInt())
            if (isWalkable(map, pos)) goals.add(pos)
        }

        for (door in markers) {
            val seal = doorCell(door)
            for (offset in OFFSETS) {
                val neighbor = GridPos(seal.x + offset.x, seal.y + offset.y)
                if (isWalkable(map, neighbor)) goals.add(neighbor)
            }
        }
        return goals.distinct()
    }

    private fun mobDoorsForRegion(snapshot: GameSnapshot, region: Set<GridPos>): List<DoorMarkerSnapshot> {
        val map = tileMap(snapshot)
        return snapshot.doorMarkers.filter { door ->
            if (!door.mobRoom || region.isEmpty()) return@filter false
            val seal = doorCell(door)
            OFFSETS.map { GridPos(seal.x + it.x, seal.y + it.y) }
                .any { it in region && isWalkable(map, it) }
        }
    }

    private fun listInsideDoorways(
        map: TileMap,
        room: Set<GridPos>,
        snapshot: GameSnapshot,
    ): List<GridPos> {
        val sealCells = mobDoorsForRegion(snapshot, room).map { doorCell(it) }
        val fromSeals = sealCells.flatMap { seal ->
            OFFSETS.map { GridPos(seal.x + it.x, seal.y + it.y) }
                .filter { it in room && isWalkable(map, it) }
        }.distinct()
        if (fromSeals.isNotEmpty()) return fromSeals
        return room.filter { isDoorwayCell(map, it, room) }
    }

    private fun nearestInsideDoorway(
        map: TileMap,
        room: Set<GridPos>,
        snapshot: GameSnapshot,
        origin: GridPos,
    ): GridPos? = listInsideDoorways(map, room, snapshot)
        .minByOrNull { manhattan(origin, it) }

    private fun exitFallback(
        sessionId: String,
        snapshot: GameSnapshot,
        cell: GridPos,
        map: TileMap,
        region: Set<GridPos>,
        pose: ru.course.roguelike.shared.model.PlayerPose,
        context: PolicyContext?,
        avoidTargetKeys: Set<String>,
    ): ToolCallDecision {
        val mobDoor = mobDoorsForRegion(snapshot, region)
            .minByOrNull { manhattan(cell, doorCell(it)) }
        if (mobDoor != null) {
            val dc = doorCell(mobDoor)
            val approach = listOf(
                GridPos(dc.x - 1, dc.y),
                GridPos(dc.x + 1, dc.y),
                GridPos(dc.x, dc.y - 1),
                GridPos(dc.x, dc.y + 1),
            ).filter { fpsNavigable(map, it) }
                .minByOrNull { manhattan(cell, it) }
            if (approach != null) {
                return stepToCell(sessionId, pose, cell, approach, map, context, avoidTargetKeys)
            }
        }
        val room = roomInteriorFromRegion(map, region)
        val doorway = room.filter { isDoorwayCell(map, it, room) }
            .minByOrNull { manhattan(cell, it) }
        if (doorway != null) {
            val path = fpsPath(map, cell, doorway)
            if (path != null && path.size >= 2) {
                return stepToCell(sessionId, pose, cell, path[1], map, context, avoidTargetKeys)
            }
        }
        val neighbor = fpsNeighbors(map, cell)
            .firstOrNull { cellKey(it) !in avoidTargetKeys }
        return if (neighbor != null) {
            stepToCell(sessionId, pose, cell, neighbor, map, context, avoidTargetKeys)
        } else {
            PolicyFpsNavigation.syncStepToward(sessionId, pose, cell.x + 0.5f, cell.y + 0.5f)
        }
    }

    private fun doorCell(door: DoorMarkerSnapshot): GridPos =
        GridPos(floor(door.x).toInt(), floor(door.y).toInt())

    private fun parseRegion(frozenRegion: Set<String>?): Set<GridPos>? =
        frozenRegion?.map { key ->
            val parts = key.split(",")
            GridPos(parts[0].toInt(), parts[1].toInt())
        }?.toSet()

    private fun roomInteriorFromRegion(map: TileMap, region: Set<GridPos>): Set<GridPos> {
        if (region.isEmpty()) return emptySet()
        val corridor = region.filter { touchesMapEdge(map, it) }
        return if (corridor.isEmpty()) region else region - corridor.toSet()
    }

    private fun touchesMapEdge(map: TileMap, cell: GridPos): Boolean =
        cell.x <= 0 || cell.y <= 0 || cell.x >= map.width - 1 || cell.y >= map.height - 1

    private fun outsideWalkableNeighbors(map: TileMap, cell: GridPos, region: Set<GridPos>): List<GridPos> =
        OFFSETS.map { GridPos(cell.x + it.x, cell.y + it.y) }
            .filter { it !in region && isWalkable(map, it) }

    private fun isDoorwayCell(map: TileMap, cell: GridPos, region: Set<GridPos>): Boolean {
        // A real doorway leads OUT of the room: it has a walkable neighbor that is not part of
        // the room interior. Columns / lava pockets are non-walkable or in-region, so an internal
        // cell merely sitting next to a column is NOT a doorway (avoids fake-corridor ping-pong).
        return outsideWalkableNeighbors(map, cell, region).isNotEmpty()
    }

    private fun floodFillBoundedByDoorways(map: TileMap, start: GridPos, maxCells: Int): Set<GridPos> {
        if (!isWalkable(map, start)) return emptySet()
        val visited = linkedSetOf(start)
        val queue = ArrayDeque<GridPos>()
        queue.addLast(start)
        while (queue.isNotEmpty() && visited.size < maxCells) {
            val cur = queue.removeFirst()
            for (offset in OFFSETS) {
                val next = GridPos(cur.x + offset.x, cur.y + offset.y)
                if (next in visited || !isWalkable(map, next)) continue
                if (crossesDoorwayOut(map, cur, next, visited)) continue
                visited.add(next)
                queue.addLast(next)
            }
        }
        return visited
    }

    /** Do not flood into the corridor when capturing the mob room interior. */
    private fun crossesDoorwayOut(map: TileMap, from: GridPos, to: GridPos, visited: Set<GridPos>): Boolean {
        if (from !in visited || to in visited) return false
        val unvisitedWalkable = OFFSETS.map { GridPos(from.x + it.x, from.y + it.y) }
            .filter { isWalkable(map, it) && it !in visited }
        if (unvisitedWalkable.isEmpty()) return false
        val visitedWalkable = OFFSETS.map { GridPos(from.x + it.x, from.y + it.y) }
            .count { isWalkable(map, it) && it in visited }
        if (visitedWalkable < 1) return false
        return to in unvisitedWalkable
    }

    private fun floodFill(map: TileMap, start: GridPos, maxCells: Int = MAX_REGION): Set<GridPos> {
        if (!isWalkable(map, start)) return emptySet()
        val visited = linkedSetOf(start)
        val queue = ArrayDeque<GridPos>()
        queue.addLast(start)
        while (queue.isNotEmpty() && visited.size < maxCells) {
            val cur = queue.removeFirst()
            for (offset in OFFSETS) {
                val next = GridPos(cur.x + offset.x, cur.y + offset.y)
                if (next in visited || !isWalkable(map, next)) continue
                visited.add(next)
                queue.addLast(next)
            }
        }
        return visited
    }

    private fun playerCell(snapshot: GameSnapshot): GridPos {
        val p = snapshot.player.pose
        return GridPos(floor(p.x).toInt(), floor(p.y).toInt())
    }

    private fun tileMap(snapshot: GameSnapshot): TileMap =
        TileMap.fromFlat(snapshot.width, snapshot.height, snapshot.tiles)

    private fun isWalkable(map: TileMap, pos: GridPos): Boolean = map.get(pos)?.walkable == true

    private fun manhattan(a: GridPos, b: GridPos): Int =
        kotlin.math.abs(a.x - b.x) + kotlin.math.abs(a.y - b.y)

    private val OFFSETS = listOf(GridPos(1, 0), GridPos(-1, 0), GridPos(0, 1), GridPos(0, -1))
}
