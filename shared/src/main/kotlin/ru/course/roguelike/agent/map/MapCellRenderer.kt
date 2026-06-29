package ru.course.roguelike.agent.map

import kotlin.math.floor
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.model.MobKind
import ru.course.roguelike.shared.model.ItemKind
import ru.course.roguelike.shared.model.TileType

/** Единая ASCII-отрисовка клетки карты для трекера и промптов агента. */
object MapCellRenderer {
    fun charAt(snapshot: GameSnapshot, x: Int, y: Int, playerX: Int, playerY: Int): Char {
        if (x < 0 || x >= snapshot.width || y < 0 || y >= snapshot.height) return '#'
        if (x == playerX && y == playerY) return '@'
        mobCharAt(snapshot, x, y)?.let { return it }
        itemCharAt(snapshot, x, y)?.let { return it }
        if (snapshot.keyPickups.any { floor(it.x).toInt() == x && floor(it.y).toInt() == y }) return 'K'
        if (snapshot.doorMarkers.any { floor(it.x).toInt() == x && floor(it.y).toInt() == y }) return 'D'
        val tile = snapshot.tiles[y * snapshot.width + x]
        when (tile) {
            TileType.ROOM_SEAL -> return '='
            TileType.ROOM_DOOR -> return 'd'
            TileType.COLUMN -> return 'C'
            TileType.ELEVATOR -> return '^'
            TileType.EXIT_GATE -> return 'E'
            else -> { /* below */ }
        }
        if (snapshot.exitGate?.x == x && snapshot.exitGate?.y == y) return 'E'
        return when {
            tile.damaging -> 'L'
            tile.walkable -> '.'
            else -> '#'
        }
    }

    /** Tile at grid cell (for tracker debug). */
    fun tileAt(snapshot: GameSnapshot, x: Int, y: Int): TileType? {
        if (x < 0 || x >= snapshot.width || y < 0 || y >= snapshot.height) return null
        return snapshot.tiles[y * snapshot.width + x]
    }

    fun describeCell(snapshot: GameSnapshot, x: Int, y: Int): String {
        val ch = charAt(snapshot, x, y, -1, -1)
        val tile = tileAt(snapshot, x, y)
        val tileName = tile?.name ?: "OOB"
        return when (ch) {
            '=' -> "door SEAL (=) unwalkable — press E from adjacent floor, cannot walk in"
            'D' -> "door MARKER (D) — target for interact"
            'C' -> "COLUMN (C) — low block at floor level; jump (Shift) or use lift (^) to pass"
            '^' -> "ELEVATOR (^) — auto high jump over columns (walk onto tile)"
            'P', 'W', 'H', 'X', 'a', 'b' -> "item ($ch) on floor"
            'K' -> "key pickup"
            '#' -> "wall ($tileName)"
            '.' -> "floor"
            'L' -> "LAVA (L) — walkable but ~20 HP/s while standing on it; pathfinders avoid it"
            else -> "$ch ($tileName)"
        }
    }

    fun isLavaAt(snapshot: GameSnapshot, x: Int, y: Int): Boolean =
        tileAt(snapshot, x, y)?.damaging == true

    /** Lava tiles visible in fair-play radius (damaging floor). */
    fun hazardsNear(snapshot: GameSnapshot, x: Int, y: Int, radius: Int = 2): String {
        var lava = 0
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                if (isLavaAt(snapshot, x + dx, y + dy)) lava++
            }
        }
        val onLava = isLavaAt(snapshot, x, y)
        return buildList {
            if (onLava) add("standingOnLava=true (~20 HP/s)")
            if (lava > 0) add("lavaCellsNearby=$lava")
        }.joinToString(" ")
    }

    /** Shown above localMap in LLM briefs — must match [charAt] encoding. */
    fun localMapLegend(): String =
        "mapLegend: @=you .=floor L=LAVA(20HP/s) #=wall ?=unknown C=column ^=lift D=door K=key"

    fun mobCharAt(snapshot: GameSnapshot, x: Int, y: Int): Char? {
        val at = snapshot.mobs.filter { floor(it.x).toInt() == x && floor(it.y).toInt() == y }
        if (at.isEmpty()) return null
        return when {
            at.any { it.kind == MobKind.LLM_GUARD } -> 'G'
            at.any { it.kind == MobKind.RANGED } -> 'R'
            else -> 'M'
        }
    }

    fun verticalFeaturesNear(snapshot: GameSnapshot, x: Int, y: Int, radius: Int = 2): String {
        val parts = mutableListOf<String>()
        var columns = 0
        var lifts = 0
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                when (tileAt(snapshot, x + dx, y + dy)) {
                    TileType.COLUMN -> columns++
                    TileType.ELEVATOR -> lifts++
                    else -> Unit
                }
            }
        }
        if (columns > 0) parts.add("columns=$columns")
        if (lifts > 0) parts.add("lifts=$lifts")
        return parts.joinToString(" ")
    }

    fun mobKindCounts(snapshot: GameSnapshot): String =
        listOf(
            MobKind.MELEE to "M",
            MobKind.RANGED to "R",
            MobKind.LLM_GUARD to "G",
        ).mapNotNull { (kind, label) ->
            snapshot.mobs.count { it.kind == kind }.takeIf { it > 0 }?.let { "$label=$it" }
        }.joinToString(" ")

    fun itemCharAt(snapshot: GameSnapshot, x: Int, y: Int): Char? {
        val item = snapshot.items.firstOrNull {
            floor(it.x).toInt() == x && floor(it.y).toInt() == y
        } ?: return null
        return when (item.kind) {
            ItemKind.HEALTH -> 'H'
            ItemKind.EXPERIENCE -> 'X'
            ItemKind.WEAPON_PISTOL -> 'P'
            ItemKind.WEAPON_SHOTGUN -> 'W'
            ItemKind.AMMO_PISTOL -> 'a'
            ItemKind.AMMO_SHOTGUN -> 'b'
        }
    }
}
