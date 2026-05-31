package ru.course.roguelike.client.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import ru.course.roguelike.shared.dto.MobSnapshot
import ru.course.roguelike.shared.engine.CollisionDebug
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.FpsConstants
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.MobKind
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.render.MiniMapProjection

class CollisionDebugOverlay(
    private val shapeRenderer: ShapeRenderer,
) {
    fun render(
        screenWidth: Float,
        screenHeight: Float,
        map: TileMap,
        pose: PlayerPose,
        debug: CollisionDebug,
        mobs: List<MobSnapshot> = emptyList(),
    ) {
        val layout = miniMapLayout()
        shapeRenderer.projectionMatrix = Matrix4().setToOrtho2D(0f, 0f, screenWidth, screenHeight)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        drawMiniMapFrame(layout)
        drawWallCells(map, pose, layout)
        drawHitCells(pose, debug, layout)
        drawMobs(pose, mobs, layout)
        drawPlayerAndVectors(pose, debug, layout)
        shapeRenderer.end()
    }

    private fun drawMiniMapFrame(layout: MiniMapLayout) {
        shapeRenderer.color = Color.WHITE
        shapeRenderer.rect(layout.left, layout.bottom, layout.mapSize, layout.mapSize)
    }

    private fun drawWallCells(map: TileMap, pose: PlayerPose, layout: MiniMapLayout) {
        val half = layout.cellsVisible / 2
        for (gy in 0 until map.height) {
            for (gx in 0 until map.width) {
                drawWallCell(map, pose, layout, gx, gy, half)
            }
        }
    }

    private fun drawWallCell(
        map: TileMap,
        pose: PlayerPose,
        layout: MiniMapLayout,
        gx: Int,
        gy: Int,
        half: Int,
    ) {
        val point = MiniMapProjection.worldToMinimap(pose, gx + 0.5f, gy + 0.5f)
        if (!MiniMapProjection.isVisible(point, half.toFloat())) return
        val cellColor = miniMapCellColor(map.get(GridPos(gx, gy))) ?: return
        val (sx, sy) = MiniMapProjection.toScreen(layout.originX, layout.originY, layout.cellPx, point)
        shapeRenderer.color = cellColor
        shapeRenderer.rect(
            sx - layout.cellPx / 2f,
            sy - layout.cellPx / 2f,
            layout.cellPx,
            layout.cellPx,
        )
    }

    private fun miniMapCellColor(tile: TileType?): Color? = when (tile) {
        TileType.WALL -> Color.DARK_GRAY
        TileType.COLUMN -> Color.GRAY
        TileType.LAVA -> Color.RED
        TileType.ELEVATOR -> Color.CYAN
        TileType.FLOOR -> Color(0.16f, 0.18f, 0.22f, 0.55f)
        else -> null
    }

    private fun drawHitCells(pose: PlayerPose, debug: CollisionDebug, layout: MiniMapLayout) {
        for (cell in debug.hitCells) {
            val point = MiniMapProjection.worldToMinimap(pose, cell.x + 0.5f, cell.y + 0.5f)
            val (sx, sy) = MiniMapProjection.toScreen(layout.originX, layout.originY, layout.cellPx, point)
            shapeRenderer.color = Color.RED
            shapeRenderer.rect(
                sx - layout.cellPx / 2f,
                sy - layout.cellPx / 2f,
                layout.cellPx,
                layout.cellPx,
            )
        }
    }

    private fun drawMobs(pose: PlayerPose, mobs: List<MobSnapshot>, layout: MiniMapLayout) {
        val half = layout.cellsVisible / 2
        for (mob in mobs) {
            val point = MiniMapProjection.worldToMinimap(pose, mob.x, mob.y)
            if (!MiniMapProjection.isVisible(point, half.toFloat())) continue
            val (sx, sy) = MiniMapProjection.toScreen(layout.originX, layout.originY, layout.cellPx, point)
            shapeRenderer.color = when (mob.kind) {
                MobKind.MELEE -> Color.GOLD
                MobKind.RANGED -> Color.SKY
                MobKind.LLM_GUARD -> Color.MAGENTA
            }
            shapeRenderer.circle(sx, sy, layout.cellPx * 0.35f, 10)
        }
    }

    private fun drawPlayerAndVectors(pose: PlayerPose, debug: CollisionDebug, layout: MiniMapLayout) {
        val playerPoint = MiniMapProjection.MinimapPoint(0f, 0f)
        val (playerSx, playerSy) = MiniMapProjection.toScreen(
            layout.originX,
            layout.originY,
            layout.cellPx,
            playerPoint,
        )
        val radiusPx = FpsConstants.PLAYER_RADIUS * layout.cellPx
        shapeRenderer.color = Color.SKY
        shapeRenderer.circle(playerSx, playerSy, radiusPx, 32)

        val movePoint = MiniMapProjection.worldToMinimap(
            pose,
            pose.x + debug.actualDx,
            pose.y + debug.actualDy,
        )
        val (endSx, endSy) = MiniMapProjection.toScreen(layout.originX, layout.originY, layout.cellPx, movePoint)
        shapeRenderer.color = Color.GREEN
        shapeRenderer.line(playerSx, playerSy, endSx, endSy)

        val aim = MiniMapProjection.aimEnd(pose)
        val (aimEndSx, aimEndSy) = MiniMapProjection.toScreen(layout.originX, layout.originY, layout.cellPx, aim)
        shapeRenderer.color = Color.YELLOW
        shapeRenderer.line(playerSx, playerSy, aimEndSx, aimEndSy)

        if (debug.blocked) {
            drawRequestedMove(playerSx, playerSy, pose, debug, layout)
        }
    }

    private fun drawRequestedMove(
        playerSx: Float,
        playerSy: Float,
        pose: PlayerPose,
        debug: CollisionDebug,
        layout: MiniMapLayout,
    ) {
        val reqLen = kotlin.math.hypot(debug.requestedDx.toDouble(), debug.requestedDy.toDouble()).toFloat()
        if (reqLen <= 1e-5f) return
        val reqPoint = MiniMapProjection.worldToMinimap(
            pose,
            pose.x + debug.requestedDx,
            pose.y + debug.requestedDy,
        )
        val (reqEndSx, reqEndSy) = MiniMapProjection.toScreen(
            layout.originX,
            layout.originY,
            layout.cellPx,
            reqPoint,
        )
        shapeRenderer.color = Color.ORANGE
        shapeRenderer.line(playerSx, playerSy, reqEndSx, reqEndSy)
    }

    private fun miniMapLayout(): MiniMapLayout {
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
        val originX: Float,
        val originY: Float,
    )
}
