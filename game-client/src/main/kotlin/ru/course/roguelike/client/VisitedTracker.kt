package ru.course.roguelike.client

import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.PlayerPose
import kotlin.math.floor

/**
 * Туман войны для миникарты (issue #14): запоминает клетки, которые игрок уже
 * видел рядом с собой. По мере движения вокруг героя открывается диск радиусом
 * [revealRadius], так миникарта показывает только посещённые комнаты.
 */
class VisitedTracker(private val revealRadius: Int = REVEAL_RADIUS) {
    private val visited = mutableSetOf<GridPos>()

    val cells: Set<GridPos> get() = visited

    /** Открывает клетки в радиусе вокруг текущей позиции игрока. */
    fun reveal(map: TileMap, pose: PlayerPose) {
        val cx = floor(pose.x).toInt()
        val cy = floor(pose.y).toInt()
        val r2 = revealRadius * revealRadius
        for (dy in -revealRadius..revealRadius) {
            for (dx in -revealRadius..revealRadius) {
                if (dx * dx + dy * dy > r2) continue
                val pos = GridPos(cx + dx, cy + dy)
                if (map.inBounds(pos)) visited.add(pos)
            }
        }
    }

    /** Сбрасывает туман — при рестарте сессии. */
    fun clear() = visited.clear()

    companion object {
        const val REVEAL_RADIUS = 3
    }
}
