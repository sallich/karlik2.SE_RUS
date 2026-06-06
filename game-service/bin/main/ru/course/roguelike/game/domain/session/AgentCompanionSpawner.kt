package ru.course.roguelike.game.domain.session

import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType

object AgentCompanionSpawner {
    private val OFFSETS = listOf(
        GridPos(1, 0),
        GridPos(-1, 0),
        GridPos(0, 1),
        GridPos(0, -1),
    )

    fun spawnBeside(map: TileMap, playerSpawn: GridPos): PlayerPose {
        for (offset in OFFSETS) {
            val cell = GridPos(playerSpawn.x + offset.x, playerSpawn.y + offset.y)
            if (map.get(cell) == TileType.FLOOR) {
                return PlayerPose.fromGridCell(cell)
            }
        }
        return PlayerPose.fromGridCell(playerSpawn)
    }
}
