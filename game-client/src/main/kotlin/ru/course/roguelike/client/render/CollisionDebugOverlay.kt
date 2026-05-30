package ru.course.roguelike.client.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import ru.course.roguelike.shared.engine.CollisionDebug
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.FpsConstants
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class CollisionDebugOverlay(
    private val shapeRenderer: ShapeRenderer,
) {
    fun render(
        screenWidth: Float,
        screenHeight: Float,
        map: TileMap,
        pose: PlayerPose,
        debug: CollisionDebug,
    ) {
        val layout = miniMapLayout(pose)
        shapeRenderer.projectionMatrix = Matrix4().setToOrtho2D(0f, 0f, screenWidth, screenHeight)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        drawMiniMapFrame(layout)
        drawWallCells(map, layout)
        drawHitCells(debug, layout)
        drawPlayerAndVectors(pose, debug, layout)
        shapeRenderer.end()
    }

    private fun drawMiniMapFrame(layout: MiniMapLayout) {
        shapeRenderer.color = Color.WHITE
        shapeRenderer.rect(layout.left, layout.bottom, layout.mapSize, layout.mapSize)
    }

    private fun drawWallCells(map: TileMap, layout: MiniMapLayout) {
        val half = layout.cellsVisible / 2
        for (dy in -half..half) {
            for (dx in -half..half) {
                val gx = layout.centerGx + dx
                val gy = layout.centerGy + dy
                val cellColor = miniMapCellColor(map.get(GridPos(gx, gy))) ?: continue
                val x0 = layout.originX + dx * layout.cellPx - layout.cellPx / 2f
                val y0 = layout.originY + dy * layout.cellPx - layout.cellPx / 2f
                shapeRenderer.color = cellColor
                shapeRenderer.rect(x0, y0, layout.cellPx, layout.cellPx)
            }
        }
    }

    /** Цвет клетки миникарты: стены/колонны — серый, лава — красная, пол — не рисуем. */
    private fun miniMapCellColor(tile: TileType?): Color? = when (tile) {
        TileType.WALL -> Color.DARK_GRAY
        TileType.COLUMN -> Color.GRAY
        TileType.LAVA -> Color.RED
        TileType.ELEVATOR -> Color.CYAN
        else -> null
    }

    private fun drawHitCells(debug: CollisionDebug, layout: MiniMapLayout) {
        for (cell in debug.hitCells) {
            val dx = cell.x - layout.centerGx
            val dy = cell.y - layout.centerGy
            val x0 = layout.originX + dx * layout.cellPx - layout.cellPx / 2f
            val y0 = layout.originY + dy * layout.cellPx - layout.cellPx / 2f
            shapeRenderer.color = Color.RED
            shapeRenderer.rect(x0, y0, layout.cellPx, layout.cellPx)
        }
    }

    private fun drawPlayerAndVectors(pose: PlayerPose, debug: CollisionDebug, layout: MiniMapLayout) {
        val playerSx = layout.originX + (pose.x - layout.centerGx - 0.5f) * layout.cellPx
        val playerSy = layout.originY + (pose.y - layout.centerGy - 0.5f) * layout.cellPx
        val radiusPx = FpsConstants.PLAYER_RADIUS * layout.cellPx
        shapeRenderer.color = Color.SKY
        shapeRenderer.circle(playerSx, playerSy, radiusPx, 32)

        val endSx = playerSx + debug.actualDx * layout.cellPx
        val endSy = playerSy + debug.actualDy * layout.cellPx
        shapeRenderer.color = Color.GREEN
        shapeRenderer.line(playerSx, playerSy, endSx, endSy)

        val aimLen = 0.45f * layout.cellPx
        val aimEndSx = playerSx + cos(debug.moveYaw) * aimLen
        val aimEndSy = playerSy + sin(debug.moveYaw) * aimLen
        shapeRenderer.color = Color.YELLOW
        shapeRenderer.line(playerSx, playerSy, aimEndSx, aimEndSy)

        if (debug.blocked) {
            drawRequestedMove(playerSx, playerSy, debug, layout.cellPx)
        }
    }

    private fun drawRequestedMove(
        playerSx: Float,
        playerSy: Float,
        debug: CollisionDebug,
        cellPx: Float,
    ) {
        val reqLen = hypot(debug.requestedDx.toDouble(), debug.requestedDy.toDouble()).toFloat()
        if (reqLen <= 1e-5f) return
        val reqEndSx = playerSx + (debug.requestedDx / reqLen) * reqLen * cellPx
        val reqEndSy = playerSy + (debug.requestedDy / reqLen) * reqLen * cellPx
        shapeRenderer.color = Color.ORANGE
        shapeRenderer.line(playerSx, playerSy, reqEndSx, reqEndSy)
    }

    private fun miniMapLayout(pose: PlayerPose): MiniMapLayout {
        val pad = 12f
        val mapSize = 140f
        val left = pad
        val bottom = pad
        val cellsVisible = 7
        val cellPx = mapSize / cellsVisible
        return MiniMapLayout(
            left = left,
            bottom = bottom,
            mapSize = mapSize,
            cellPx = cellPx,
            cellsVisible = cellsVisible,
            centerGx = pose.x.toInt(),
            centerGy = pose.y.toInt(),
            originX = left + mapSize / 2f,
            originY = bottom + mapSize / 2f,
        )
    }

    private data class MiniMapLayout(
        val left: Float,
        val bottom: Float,
        val mapSize: Float,
        val cellPx: Float,
        val cellsVisible: Int,
        val centerGx: Int,
        val centerGy: Int,
        val originX: Float,
        val originY: Float,
    )
}
