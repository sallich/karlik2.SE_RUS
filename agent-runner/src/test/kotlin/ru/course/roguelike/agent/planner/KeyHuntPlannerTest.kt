package ru.course.roguelike.agent.planner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.engine.GridPathfinder
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType

class KeyHuntPlannerTest {
    @Test
    fun `BFS finds path to key cell on open floor`() {
        val map = TileMap.fromFlat(5, 5, openRoomTiles(5, 5))
        val start = GridPos(2, 2)
        val goal = GridPos(3, 2)
        val path = GridPathfinder.path(map, start, goal)
        assertNotNull(path)
        assertEquals(start, path!!.first())
        assertEquals(goal, path.last())
    }

    @Test
    fun `planner returns a tool call`() {
        val planner = KeyHuntPlanner()
        val decisions =
            planner.plan(
                ru.course.roguelike.agent.TestSnapshots
                    .simpleRoom(),
                "session-1",
            )
        val decision = decisions.first()
        assertNotNull(decision.tool)
    }

    private fun openRoomTiles(
        width: Int,
        height: Int,
    ): List<TileType> =
        buildList {
            repeat(width * height) { idx ->
                val x = idx % width
                val y = idx / width
                val isBorder = x == 0 || y == 0 || x == width - 1 || y == height - 1
                add(if (isBorder) TileType.WALL else TileType.FLOOR)
            }
        }
}
