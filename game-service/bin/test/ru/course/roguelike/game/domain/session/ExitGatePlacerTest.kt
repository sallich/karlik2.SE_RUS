package ru.course.roguelike.game.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.infrastructure.level.LabyrinthLevelGenerator
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType

class ExitGatePlacerTest {
    @Test
    fun `exit gate sits in a corner of the boss room on a walkable tile`() {
        // issue #13: выход должен быть у стены, в углу комнаты босса.
        for (seed in 1L..50L) {
            val level = LabyrinthLevelGenerator.generate(seed)
            val boss = level.rooms.single { it.isBoss }
            val (map, gate) = ExitGatePlacer.place(level, boss)

            assertEquals(TileType.EXIT_GATE, map.get(gate), "seed=$seed gate tile not placed")
            assertTrue(isCorner(boss, gate), "seed=$seed gate $gate is not a corner of $boss")
        }
    }

    @Test
    fun `exit gate is the boss corner farthest from spawn`() {
        for (seed in 1L..50L) {
            val level = LabyrinthLevelGenerator.generate(seed)
            val boss = level.rooms.single { it.isBoss }
            val (_, gate) = ExitGatePlacer.place(level, boss)

            val corners = listOf(
                GridPos(boss.x, boss.y),
                GridPos(boss.x + boss.width - 1, boss.y),
                GridPos(boss.x, boss.y + boss.height - 1),
                GridPos(boss.x + boss.width - 1, boss.y + boss.height - 1),
            )
            val farthest = corners.maxBy { distance(it, level.playerSpawn) }
            assertEquals(farthest, gate, "seed=$seed gate is not the farthest corner from spawn")
        }
    }

    private fun isCorner(boss: ru.course.roguelike.game.domain.level.Room, cell: GridPos): Boolean {
        val onVerticalWall = cell.x == boss.x || cell.x == boss.x + boss.width - 1
        val onHorizontalWall = cell.y == boss.y || cell.y == boss.y + boss.height - 1
        return onVerticalWall && onHorizontalWall
    }

    private fun distance(a: GridPos, b: GridPos): Double =
        kotlin.math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
}
