package ru.course.roguelike.client

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType

class VisitedTrackerTest {
    private fun floorMap(width: Int, height: Int): TileMap =
        TileMap.fromFlat(width, height, List(width * height) { TileType.FLOOR })

    @Test
    fun `reveal opens a disc around the player and leaves far cells hidden`() {
        val tracker = VisitedTracker(revealRadius = 2)
        val map = floorMap(20, 20)

        tracker.reveal(map, PlayerPose(x = 10.5f, y = 10.5f, yaw = 0f))

        assertTrue(tracker.cells.contains(GridPos(10, 10)), "player cell revealed")
        assertTrue(tracker.cells.contains(GridPos(12, 10)), "cell within radius revealed")
        assertFalse(tracker.cells.contains(GridPos(13, 10)), "cell outside radius stays hidden")
        // Угол диска вне евклидова радиуса 2 (dx=2, dy=2 -> 8 > 4).
        assertFalse(tracker.cells.contains(GridPos(12, 12)), "corner outside euclidean radius hidden")
    }

    @Test
    fun `reveal accumulates as the player moves`() {
        val tracker = VisitedTracker(revealRadius = 1)
        val map = floorMap(20, 20)

        tracker.reveal(map, PlayerPose(x = 3.5f, y = 3.5f, yaw = 0f))
        val afterFirst = tracker.cells.size
        tracker.reveal(map, PlayerPose(x = 9.5f, y = 9.5f, yaw = 0f))

        assertTrue(tracker.cells.contains(GridPos(3, 3)), "first room stays revealed")
        assertTrue(tracker.cells.contains(GridPos(9, 9)), "second room revealed")
        assertTrue(tracker.cells.size > afterFirst, "moving reveals more cells")
    }

    @Test
    fun `reveal ignores out-of-bounds cells`() {
        val tracker = VisitedTracker(revealRadius = 3)
        val map = floorMap(4, 4)

        tracker.reveal(map, PlayerPose(x = 0.5f, y = 0.5f, yaw = 0f))

        assertTrue(tracker.cells.all { map.inBounds(it) }, "no revealed cell is out of bounds")
        assertFalse(tracker.cells.contains(GridPos(-1, 0)))
    }

    @Test
    fun `clear resets the fog`() {
        val tracker = VisitedTracker()
        val map = floorMap(10, 10)
        tracker.reveal(map, PlayerPose(x = 5.5f, y = 5.5f, yaw = 0f))

        tracker.clear()

        assertTrue(tracker.cells.isEmpty(), "clear removes all visited cells")
    }
}
