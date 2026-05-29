package ru.course.roguelike.shared.render

/**
 * Изометрическая проекция сетки (x, y) → экранные координаты.
 * Общая для клиента; server остаётся в grid space.
 */
object IsoProjection {
    const val TILE_WIDTH = 64f
    const val TILE_HEIGHT = 32f
    const val ELEVATION_STEP = 20f

    data class ScreenPoint(val x: Float, val y: Float)

    fun gridToScreen(gridX: Int, gridY: Int, elevation: Float = 0f, originX: Float = 0f, originY: Float = 0f): ScreenPoint {
        val sx = (gridX - gridY) * TILE_WIDTH / 2f + originX
        val sy = (gridX + gridY) * TILE_HEIGHT / 2f + elevation * ELEVATION_STEP + originY
        return ScreenPoint(sx, sy)
    }
}
