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
        val pad = 12f
        val mapSize = 140f
        val left = pad
        val bottom = pad
        val cellsVisible = 7
        val cellPx = mapSize / cellsVisible
        val centerGx = pose.x.toInt()
        val centerGy = pose.y.toInt()

        shapeRenderer.projectionMatrix = Matrix4().setToOrtho2D(0f, 0f, screenWidth, screenHeight)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)

        shapeRenderer.color = Color.WHITE
        shapeRenderer.rect(left, bottom, mapSize, mapSize)

        val originX = left + mapSize / 2f
        val originY = bottom + mapSize / 2f

        for (dy in -cellsVisible / 2..cellsVisible / 2) {
            for (dx in -cellsVisible / 2..cellsVisible / 2) {
                val gx = centerGx + dx
                val gy = centerGy + dy
                val tile = map.get(GridPos(gx, gy)) ?: continue
                if (tile != TileType.WALL) continue
                val x0 = originX + dx * cellPx - cellPx / 2f
                val y0 = originY + dy * cellPx - cellPx / 2f
                shapeRenderer.color = Color.DARK_GRAY
                shapeRenderer.rect(x0, y0, cellPx, cellPx)
            }
        }

        for (cell in debug.hitCells) {
            val dx = cell.x - centerGx
            val dy = cell.y - centerGy
            val x0 = originX + dx * cellPx - cellPx / 2f
            val y0 = originY + dy * cellPx - cellPx / 2f
            shapeRenderer.color = Color.RED
            shapeRenderer.rect(x0, y0, cellPx, cellPx)
        }

        val playerSx = originX + (pose.x - centerGx - 0.5f) * cellPx
        val playerSy = originY + (pose.y - centerGy - 0.5f) * cellPx
        val radiusPx = FpsConstants.PLAYER_RADIUS * cellPx
        shapeRenderer.color = Color.SKY
        shapeRenderer.circle(playerSx, playerSy, radiusPx, 32)

        val endSx = playerSx + debug.actualDx * cellPx
        val endSy = playerSy + debug.actualDy * cellPx
        shapeRenderer.color = Color.GREEN
        shapeRenderer.line(playerSx, playerSy, endSx, endSy)

        val aimLen = 0.45f * cellPx
        val aimEndSx = playerSx + cos(debug.moveYaw) * aimLen
        val aimEndSy = playerSy + sin(debug.moveYaw) * aimLen
        shapeRenderer.color = Color.YELLOW
        shapeRenderer.line(playerSx, playerSy, aimEndSx, aimEndSy)

        if (debug.blocked) {
            val reqLen = hypot(debug.requestedDx.toDouble(), debug.requestedDy.toDouble()).toFloat()
            if (reqLen > 1e-5f) {
                val reqEndSx = playerSx + (debug.requestedDx / reqLen) * reqLen * cellPx
                val reqEndSy = playerSy + (debug.requestedDy / reqLen) * reqLen * cellPx
                shapeRenderer.color = Color.ORANGE
                shapeRenderer.line(playerSx, playerSy, reqEndSx, reqEndSy)
            }
        }

        shapeRenderer.end()
    }
}
