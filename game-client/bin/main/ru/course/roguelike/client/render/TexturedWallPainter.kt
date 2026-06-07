package ru.course.roguelike.client.render

import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.model.wallHeight
import ru.course.roguelike.shared.render.CameraProjection
import ru.course.roguelike.shared.render.Raycaster
import ru.course.roguelike.shared.render.SceneRenderConfig
import ru.course.roguelike.shared.render.TextureMapping

internal class TexturedWallPainter(
    private val buffer: PixelFrameBuffer,
    private val viewHeight: Int,
    private val textures: GameTextures,
) {
    fun paintHorizontalTops(
        scene: Raycaster.SceneCast,
        pitchHorizon: Float,
        viewerHeight: Float,
    ) {
        for (x in scene.columns.indices) {
            val meta = scene.wallMeta[x]
            val tile = meta.tile
            if (!meta.horizontalTop || tile == null) continue
            paintHorizontalTopColumn(pitchHorizon, viewerHeight, x, meta, tile)
        }
    }

    private fun paintHorizontalTopColumn(
        pitchHorizon: Float,
        viewerHeight: Float,
        x: Int,
        meta: Raycaster.WallColumnMeta,
        tile: TileType,
    ) {
        val surfaceZ = tile.wallHeight()
        val floorLine = CameraProjection.worldFloorScreenY(
            pitchHorizon,
            viewHeight,
            meta.distance,
            viewerHeight - surfaceZ,
        )
        val top = kotlin.math.floor(floorLine - meta.distance * 0.06f).toInt().coerceIn(0, viewHeight - 1)
        val bottom = kotlin.math.ceil(floorLine).toInt().coerceIn(top + 1, viewHeight)
        val u = TextureMapping.wallTextureUClamped(meta.wallU)
        for (row in top until bottom) {
            val rgb = horizontalTopRgb(tile, u, meta.distance) ?: return
            buffer.set(x, row, rgb)
        }
    }

    private fun horizontalTopRgb(tile: TileType, u: Float, distance: Float): Int? = when (tile) {
        TileType.WALL -> {
            val v = TextureMapping.wallAtlasRowBase(tile) + 0.05f
            TextureMapping.shadeRgb(
                textures.walls.samplePixel(u, v).rgb,
                distance,
                SceneRenderConfig.WALL_CAP_DARKEN,
            )
        }
        TileType.COLUMN -> {
            val v = TextureMapping.wallAtlasRowBase(tile) + 0.05f
            TextureMapping.shadeRgb(textures.walls.samplePixel(u, v).rgb, distance, 0.88f)
        }
        else -> null
    }

    fun paintWalls(scene: Raycaster.SceneCast, pitchHorizon: Float, viewerHeight: Float) {
        for (x in scene.columns.indices) {
            val col = scene.columns[x]
            val meta = scene.wallMeta[x]
            paintBackWallLayer(meta, col, pitchHorizon, viewerHeight, x)
            if (!meta.horizontalTop && col.wallEnd - col.wallStart >= 0.5f) {
                paintWallColumn(
                    x = x,
                    wallStart = col.wallStart,
                    wallEnd = col.wallEnd,
                    meta = meta,
                )
            }
        }
    }

    private data class BackWallLayer(
        val distance: Float,
        val tile: TileType,
        val side: Int,
        val wallU: Float,
    )

    private fun backWallLayer(meta: Raycaster.WallColumnMeta): BackWallLayer? {
        val distance = meta.backDistance
        val tile = meta.backTile
        if (distance == null || tile == null) return null
        val side = meta.backSide
        val wallU = meta.backWallU
        if (side == null || wallU == null) return null
        return BackWallLayer(distance, tile, side, wallU)
    }

    private fun paintBackWallLayer(
        meta: Raycaster.WallColumnMeta,
        frontCol: Raycaster.Column,
        pitchHorizon: Float,
        viewerHeight: Float,
        x: Int,
    ) {
        val layer = backWallLayer(meta) ?: return

        val lineHeight = viewHeight / layer.distance
        val wallH = layer.tile.wallHeight()
        val (backStart, backEnd) = CameraProjection.projectWallSpan(
            pitchHorizonY = pitchHorizon,
            lineHeight = lineHeight,
            wallHeight = wallH,
            screenHeight = viewHeight,
            perpDistance = layer.distance,
            viewerHeightAboveFloor = viewerHeight,
        )
        val frontTop = frontCol.wallStart
        val top = kotlin.math.ceil(backStart).toInt().coerceIn(0, viewHeight - 1)
        val bottom = kotlin.math.min(kotlin.math.ceil(backEnd).toInt(), kotlin.math.floor(frontTop).toInt())
            .coerceIn(top + 1, viewHeight)
        if (bottom <= top + 1) return

        val sideDarken = SceneRenderConfig.sideDarken(layer.side)
        val u = TextureMapping.wallTextureUClamped(layer.wallU)
        for (row in top until bottom) {
            val v = TextureMapping.wallTextureV(
                screenRow = row,
                wallStart = backStart,
                wallEnd = backEnd,
                tile = layer.tile,
            )
            val sample = textures.walls.samplePixel(u, v)
            buffer.set(x, row, TextureMapping.shadeRgb(sample.rgb, layer.distance, sideDarken))
        }
    }

    private fun paintWallColumn(
        x: Int,
        wallStart: Float,
        wallEnd: Float,
        meta: Raycaster.WallColumnMeta,
    ) {
        val top = kotlin.math.floor(wallStart).toInt().coerceIn(0, viewHeight - 1)
        val bottom = kotlin.math.ceil(wallEnd).toInt().coerceIn(top + 1, viewHeight)
        val sideDarken = SceneRenderConfig.sideDarken(meta.side)
        val u = TextureMapping.wallTextureUClamped(meta.wallU)
        for (row in top until bottom) {
            val v = TextureMapping.wallTextureV(
                screenRow = row,
                wallStart = wallStart,
                wallEnd = wallEnd,
                tile = meta.tile,
            )
            val sample = textures.walls.samplePixel(u, v)
            buffer.set(
                x,
                row,
                TextureMapping.shadeRgb(sample.rgb, meta.distance, sideDarken),
            )
        }
    }

    fun paintWallCaps(scene: Raycaster.SceneCast) {
        for (x in scene.columns.indices) {
            val col = scene.columns[x]
            val meta = scene.wallMeta[x]
            val tile = meta.tile
            if (shouldSkipWallCap(meta, tile)) continue
            paintWallCapColumn(x, col, meta, tile!!)
        }
    }

    private fun shouldSkipWallCap(meta: Raycaster.WallColumnMeta, tile: TileType?): Boolean {
        if (meta.horizontalTop || tile == null) return true
        return tile != TileType.WALL && tile != TileType.COLUMN
    }

    private fun paintWallCapColumn(
        x: Int,
        col: Raycaster.Column,
        meta: Raycaster.WallColumnMeta,
        tile: TileType,
    ) {
        val span = (col.wallEnd - col.wallStart).coerceAtLeast(1f)
        val capRows = (span * SceneRenderConfig.WALL_CAP_FRACTION).toInt().coerceIn(1, 8)
        val capBottom = kotlin.math.floor(col.wallStart).toInt().coerceIn(capRows, viewHeight)
        val capTop = capBottom - capRows
        val u = TextureMapping.wallTextureUClamped(meta.wallU)
        val rowBase = TextureMapping.wallAtlasRowBase(tile)
        val capV = rowBase + 0.04f
        val sideDarken = SceneRenderConfig.sideDarken(meta.side) * SceneRenderConfig.WALL_CAP_DARKEN
        for (row in capTop until capBottom) {
            val sample = textures.walls.samplePixel(u, capV)
            buffer.set(
                x,
                row,
                TextureMapping.shadeRgb(sample.rgb, meta.distance, sideDarken),
            )
        }
    }
}
