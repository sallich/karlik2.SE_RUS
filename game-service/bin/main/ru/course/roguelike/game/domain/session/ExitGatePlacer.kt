package ru.course.roguelike.game.domain.session

import ru.course.roguelike.game.domain.level.GeneratedLevel
import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType

/** Размещает тайл ворот выхода в комнате босса. */
object ExitGatePlacer {
    /**
     * Ставит [TileType.EXIT_GATE] в дальнем от спавна углу комнаты босса
     * (issue #13: выход должен быть у стены, желательно в углу).
     */
    fun place(level: GeneratedLevel, bossRoom: Room): Pair<TileMap, GridPos> {
        val width = level.map.width
        val tiles = level.map.toFlatList().toMutableList()
        val cell = gateCell(bossRoom, level.playerSpawn)
        tiles[cell.y * width + cell.x] = TileType.EXIT_GATE
        return TileMap.fromFlat(width, level.map.height, tiles) to cell
    }

    /**
     * Дальний от спавна угол комнаты. Угловые тайлы — это периметр комнаты,
     * который декоратор не трогает (колонны/лава ставятся только во внутренних
     * бакетах), поэтому угол гарантированно проходимый пол у двух стен.
     */
    private fun gateCell(boss: Room, spawn: GridPos): GridPos {
        val corners = listOf(
            GridPos(boss.x, boss.y),
            GridPos(boss.x + boss.width - 1, boss.y),
            GridPos(boss.x, boss.y + boss.height - 1),
            GridPos(boss.x + boss.width - 1, boss.y + boss.height - 1),
        ).filter { boss.contains(it) }

        return corners.maxByOrNull { cell ->
            kotlin.math.hypot(
                (cell.x - spawn.x).toDouble(),
                (cell.y - spawn.y).toDouble(),
            )
        } ?: boss.center
    }
}
