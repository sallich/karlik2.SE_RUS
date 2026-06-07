package ru.course.roguelike.shared.engine

import ru.course.roguelike.shared.model.FpsConstants
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.model.WorldVertical
import kotlin.math.floor

/**
 * Вертикальное движение с опорой на поверхности (пол, верх колонны).
 * Общая логика для сервера и клиента.
 */
object VerticalMotion {
    private const val LANDING_EPS = 0.07f
    private const val LEDGE_FALL_VELOCITY = -1.2f

    /** Высота опорной поверхности под кругом коллизии игрока. */
    fun surfaceSupport(map: TileMap, x: Float, y: Float): Float {
        val r = FpsConstants.PLAYER_RADIUS
        val minCellX = floor(x - r).toInt()
        val maxCellX = floor(x + r).toInt()
        val minCellY = floor(y - r).toInt()
        val maxCellY = floor(y + r).toInt()
        var support = 0f
        for (cy in minCellY..maxCellY) {
            for (cx in minCellX..maxCellX) {
                if (!circleOverlapsCell(x, y, r, cx, cy)) continue
                support = maxOf(support, cellSupport(map.get(GridPos(cx, cy))))
            }
        }
        return support
    }

    fun snapHeightToSupport(map: TileMap, x: Float, y: Float, height: Float): Float {
        val support = surfaceSupport(map, x, y)
        return if (height < support - 0.01f && support > 0f) support else height
    }

    fun tick(
        map: TileMap,
        x: Float,
        y: Float,
        height: Float,
        verticalVelocity: Float,
        jumpRequested: Boolean,
        deltaMs: Int,
    ): JumpPhysics.State {
        val support = surfaceSupport(map, x, y)
        var h = height
        var v = verticalVelocity

        if (h < support - 0.01f && v <= 0.05f && support > 0f) {
            return JumpPhysics.State(support, 0f)
        }

        if (h > support + 0.04f && kotlin.math.abs(v) < 0.02f) {
            v = LEDGE_FALL_VELOCITY
        }
        val airborne = h > support + 0.02f || v > 0.02f
        val jump = jumpRequested && !airborne
        val state = JumpPhysics.tick(h, v, jump, deltaMs, support)
        return resolveLanding(state, support)
    }

    private fun cellSupport(tile: TileType?): Float = when (tile) {
        TileType.COLUMN -> WorldVertical.COLUMN_HEIGHT
        else -> 0f
    }

    private fun resolveLanding(state: JumpPhysics.State, support: Float): JumpPhysics.State {
        if (state.verticalVelocity > 0.05f) return state
        if (support > 0f && state.height <= support + LANDING_EPS) {
            return JumpPhysics.State(support, 0f)
        }
        if (support <= 0f && state.height <= LANDING_EPS) {
            return JumpPhysics.State(0f, 0f)
        }
        return state
    }

    private fun circleOverlapsCell(px: Float, py: Float, radius: Float, cellX: Int, cellY: Int): Boolean {
        val closestX = px.coerceIn(cellX.toFloat(), cellX + 1f)
        val closestY = py.coerceIn(cellY.toFloat(), cellY + 1f)
        val diffX = px - closestX
        val diffY = py - closestY
        return diffX * diffX + diffY * diffY <= radius * radius
    }
}
