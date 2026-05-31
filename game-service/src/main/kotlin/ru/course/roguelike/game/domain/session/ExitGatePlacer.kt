package ru.course.roguelike.game.domain.session

import ru.course.roguelike.game.domain.level.GeneratedLevel
import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType

/** Размещает тайл ворот выхода в комнате босса. */
object ExitGatePlacer {
    /**
     * Ставит [TileType.EXIT_GATE] у дальней от спавна стены комнаты босса
     * (центр противоположного от входа края).
     */
    fun place(level: GeneratedLevel, bossRoom: Room): Pair<TileMap, GridPos> {
        val width = level.map.width
        val tiles = level.map.toFlatList().toMutableList()
        val cell = gateCell(bossRoom, level.playerSpawn)
        tiles[cell.y * width + cell.x] = TileType.EXIT_GATE
        return TileMap.fromFlat(width, level.map.height, tiles) to cell
    }

    /** Клетка у «дальнего» края комнаты — туда ведёт проход с ключами. */
    private fun gateCell(boss: Room, spawn: GridPos): GridPos {
        val candidates = listOf(
            GridPos(boss.x + boss.width - 2, boss.y + boss.height / 2),
            GridPos(boss.x + boss.width / 2, boss.y + boss.height - 2),
            GridPos(boss.x + 1, boss.y + boss.height / 2),
            GridPos(boss.x + boss.width / 2, boss.y + 1),
        ).filter { boss.contains(it) }

        return candidates.maxByOrNull { cell ->
            kotlin.math.hypot(
                (cell.x - spawn.x).toDouble(),
                (cell.y - spawn.y).toDouble(),
            )
        } ?: boss.center
    }
}
