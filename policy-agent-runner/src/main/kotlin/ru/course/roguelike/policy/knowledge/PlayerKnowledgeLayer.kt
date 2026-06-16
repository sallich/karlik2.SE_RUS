package ru.course.roguelike.policy.knowledge

import ru.course.roguelike.agent.explore.AgentDoorHelper
import ru.course.roguelike.agent.map.MapCellRenderer
import ru.course.roguelike.shared.dto.DoorMarkerSnapshot
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Fair-play knowledge: only visited cells + local visibility radius.
 * Used by LLM briefs and micro planners alike.
 */
class PlayerKnowledgeLayer(
    val visibilityRadius: Int = DEFAULT_VISIBILITY_RADIUS,
) {
    val knownCells = mutableSetOf<String>()
    private val knownTileTypes = mutableMapOf<String, TileType>()
    val knownDoors = mutableListOf<DoorMarkerSnapshot>()
    var knownExitGate: GridPos? = null
        private set
    private var lastKnownCellCount: Int = 0

    fun update(snapshot: GameSnapshot, visitedCells: Set<String>) {
        val px = floor(snapshot.player.pose.x).toInt()
        val py = floor(snapshot.player.pose.y).toInt()
        val full = TileMap.fromFlat(snapshot.width, snapshot.height, snapshot.tiles)

        knownCells.addAll(visitedCells)
        for (key in visitedCells) {
            tileAtKey(full, key)?.let { (pos, tile) ->
                knownCells.add(key)
                knownTileTypes[key] = tile
            }
        }
        for (dy in -visibilityRadius..visibilityRadius) {
            for (dx in -visibilityRadius..visibilityRadius) {
                val x = px + dx
                val y = py + dy
                if (x < 0 || y < 0 || x >= snapshot.width || y >= snapshot.height) continue
                val key = PartialTileMap.cellKey(x, y)
                knownCells.add(key)
                full.get(GridPos(x, y))?.let { knownTileTypes[key] = it }
            }
        }

        for (pickup in snapshot.keyPickups) {
            val kx = floor(pickup.x).toInt()
            val ky = floor(pickup.y).toInt()
            if (kx in 0 until snapshot.width && ky in 0 until snapshot.height) {
                val key = PartialTileMap.cellKey(kx, ky)
                knownCells.add(key)
                full.get(GridPos(kx, ky))?.let { knownTileTypes[key] = it }
            }
        }

        val visibleDoors = snapshot.doorMarkers.filter { door ->
            hypot((door.x - px).toDouble(), (door.y - py).toDouble()) <= visibilityRadius + 0.5
        }
        for (door in visibleDoors) {
            if (knownDoors.none { it.x == door.x && it.y == door.y }) {
                knownDoors.add(door)
            }
        }

        snapshot.exitGate?.let { gate ->
            val gateKey = PartialTileMap.cellKey(gate.x, gate.y)
            if (gateKey in knownCells) {
                knownExitGate = gate
            }
        }
    }

    fun partialMap(snapshot: GameSnapshot): PartialTileMap =
        PartialTileMap(
            TileMap.fromFlat(snapshot.width, snapshot.height, snapshot.tiles),
            knownCells.toSet(),
            knownTileTypes.toMap(),
        )

    fun navigableMap(snapshot: GameSnapshot, strictFairPlay: Boolean = true): TileMap {
        if (!strictFairPlay) {
            return TileMap.fromFlat(snapshot.width, snapshot.height, snapshot.tiles)
        }
        val full = TileMap.fromFlat(snapshot.width, snapshot.height, snapshot.tiles)
        val expanded = expandedKnownCells(full)
        val flat = Array(snapshot.width * snapshot.height) { TileType.WALL }
        for (y in 0 until snapshot.height) {
            for (x in 0 until snapshot.width) {
                val key = PartialTileMap.cellKey(x, y)
                if (key in expanded) {
                    flat[y * snapshot.width + x] = knownTileTypes[key] ?: full.get(GridPos(x, y)) ?: TileType.WALL
                }
            }
        }
        return TileMap(snapshot.width, snapshot.height, flat)
    }

    private fun expandedKnownCells(full: TileMap): Set<String> {
        val expanded = knownCells.toMutableSet()
        val offsets = listOf(GridPos(1, 0), GridPos(-1, 0), GridPos(0, 1), GridPos(0, -1))
        for (key in knownCells) {
            val tile = knownTileTypes[key] ?: continue
            if (!tile.walkable) continue
            val parts = key.split(",")
            if (parts.size != 2) continue
            val x = parts[0].toIntOrNull() ?: continue
            val y = parts[1].toIntOrNull() ?: continue
            for (off in offsets) {
                val nx = x + off.x
                val ny = y + off.y
                val pos = GridPos(nx, ny)
                if (full.inBounds(pos)) {
                    expanded.add(PartialTileMap.cellKey(nx, ny))
                }
            }
        }
        return expanded
    }

    fun nearestKnownDoor(snapshot: GameSnapshot): DoorMarkerSnapshot? {
        if (knownDoors.isEmpty()) return null
        val px = snapshot.player.pose.x
        val py = snapshot.player.pose.y
        return knownDoors.minByOrNull { hypot((it.x - px).toDouble(), (it.y - py).toDouble()) }
    }

    fun frontierCells(snapshot: GameSnapshot): List<GridPos> {
        val full = TileMap.fromFlat(snapshot.width, snapshot.height, snapshot.tiles)
        val offsets = listOf(GridPos(1, 0), GridPos(-1, 0), GridPos(0, 1), GridPos(0, -1))
        return knownCells.mapNotNull { key ->
            val parts = key.split(",")
            if (parts.size != 2) return@mapNotNull null
            val x = parts[0].toIntOrNull() ?: return@mapNotNull null
            val y = parts[1].toIntOrNull() ?: return@mapNotNull null
            val pos = GridPos(x, y)
            // Frontier anchors must be standable floor; a column/wall cell next to unknown space
            // is not a real exploration lead (avoids "indentation looks like a corridor").
            val tile = knownTileTypes[key] ?: full.get(pos)
            if (tile == null || !tile.walkable || tile.damaging) return@mapNotNull null
            val hasUnknownNeighbor = offsets.any { off ->
                val n = GridPos(pos.x + off.x, pos.y + off.y)
                if (!full.inBounds(n)) return@any false
                PartialTileMap.cellKey(n) !in knownCells
            }
            if (hasUnknownNeighbor) pos else null
        }
    }

    fun hasFrontier(): Boolean = knownCells.isNotEmpty()

    fun recordProgress(): Boolean {
        val grew = knownCells.size > lastKnownCellCount
        lastKnownCellCount = knownCells.size
        return grew
    }

    fun resetProgressBaseline() {
        lastKnownCellCount = knownCells.size
    }

    fun lastJunctionCell(visitedTrail: List<String>, minDegree: Int = 3): GridPos? {
        for (key in visitedTrail.asReversed()) {
            val parts = key.split(",")
            if (parts.size != 2) continue
            val x = parts[0].toIntOrNull() ?: continue
            val y = parts[1].toIntOrNull() ?: continue
            val degree = listOf(
                PartialTileMap.cellKey(x + 1, y),
                PartialTileMap.cellKey(x - 1, y),
                PartialTileMap.cellKey(x, y + 1),
                PartialTileMap.cellKey(x, y - 1),
            ).count { it in knownCells }
            if (degree >= minDegree) return GridPos(x, y)
        }
        return null
    }

    fun formatLocalMap(snapshot: GameSnapshot, radius: Int = 2): String {
        val px = floor(snapshot.player.pose.x).toInt()
        val py = floor(snapshot.player.pose.y).toInt()
        return buildString {
            for (dy in -radius..radius) {
                for (dx in -radius..radius) {
                    val x = px + dx
                    val y = py + dy
                    val key = PartialTileMap.cellKey(x, y)
                    append(
                        if (key in knownCells) {
                            MapCellRenderer.charAt(snapshot, x, y, px, py)
                        } else {
                            '?'
                        },
                    )
                }
                append('\n')
            }
        }.trimEnd()
    }

    fun formatKnownDoors(max: Int = 5): String =
        if (knownDoors.isEmpty()) {
            "none visible"
        } else {
            knownDoors.take(max).joinToString { door ->
                val kind = when {
                    door.prizeIsKey -> "key"
                    door.mobRoom -> "mob"
                    door.kind != null -> door.kind!!.name
                    else -> "prize"
                }
                "(${door.x.toInt()},${door.y.toInt()},$kind)"
            }
        }

    /** Nearest frontier anchors (boundary to unknown) as concrete "x,y" objective targets. */
    fun formatFrontierTargets(snapshot: GameSnapshot, max: Int = 4): String {
        val px = snapshot.player.pose.x
        val py = snapshot.player.pose.y
        val cells = frontierCells(snapshot)
            .sortedBy { hypot((it.x - px).toDouble(), (it.y - py).toDouble()) }
            .take(max)
        return if (cells.isEmpty()) "none" else cells.joinToString { "(${it.x},${it.y})" }
    }

    fun formatKnownLava(max: Int = 6): String {
        val cells = knownLavaCellKeys().take(max)
        return if (cells.isEmpty()) "none known" else cells.joinToString { "($it)" }
    }

    fun knownLavaCellKeys(): List<String> =
        knownCells.filter { knownTileTypes[it]?.damaging == true }.sorted()

    fun toBrief(snapshot: GameSnapshot): String = buildString {
        appendLine(MapCellRenderer.localMapLegend())
        appendLine("localMap:")
        append(formatLocalMap(snapshot))
        appendLine()
        val px = floor(snapshot.player.pose.x).toInt()
        val py = floor(snapshot.player.pose.y).toInt()
        val hazards = MapCellRenderer.hazardsNear(snapshot, px, py)
        if (hazards.isNotEmpty()) appendLine(hazards)
        appendLine("hp=${snapshot.player.hp}/${snapshot.player.maxHp}")
        appendLine("knownDoors=${formatKnownDoors()}")
        appendLine("knownLava=${formatKnownLava()}")
        appendLine("frontierTargets=${formatFrontierTargets(snapshot)}")
        appendLine("knownCells=${knownCells.size} frontier=${frontierCells(snapshot).size}")
        knownExitGate?.let { appendLine("knownExitGate=(${it.x},${it.y})") }
    }

    /** Test helper: reveal entire map for small fixture maps. */
    internal fun revealAllForTest(snapshot: GameSnapshot) {
        val full = TileMap.fromFlat(snapshot.width, snapshot.height, snapshot.tiles)
        for (y in 0 until snapshot.height) {
            for (x in 0 until snapshot.width) {
                val key = PartialTileMap.cellKey(x, y)
                knownCells.add(key)
                full.get(GridPos(x, y))?.let { knownTileTypes[key] = it }
            }
        }
        knownDoors.clear()
        knownDoors.addAll(snapshot.doorMarkers)
        knownExitGate = snapshot.exitGate
        lastKnownCellCount = knownCells.size
    }

    private fun tileAtKey(full: TileMap, key: String): Pair<GridPos, TileType>? {
        val parts = key.split(",")
        if (parts.size != 2) return null
        val x = parts[0].toIntOrNull() ?: return null
        val y = parts[1].toIntOrNull() ?: return null
        val pos = GridPos(x, y)
        val tile = full.get(pos) ?: return null
        return pos to tile
    }

    companion object {
        const val DEFAULT_VISIBILITY_RADIUS = AgentDoorHelper.DOOR_VIEW_RADIUS
    }
}
