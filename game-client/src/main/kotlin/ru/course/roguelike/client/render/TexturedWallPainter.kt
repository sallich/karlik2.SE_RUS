package ru.course.roguelike.client.render

import ru.course.roguelike.shared.dto.DoorMarkerSnapshot
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.model.wallHeight
import ru.course.roguelike.shared.render.CameraProjection
import ru.course.roguelike.shared.render.Raycaster
import ru.course.roguelike.shared.render.RgbImageSampler
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
        val floorLine = CameraProjection.worldZScreenY(
            pitchHorizon,
            viewHeight,
            meta.distance,
            viewerHeight,
            surfaceZ,
        )
        val lineHeight = viewHeight / meta.distance.coerceAtLeast(0.05f)
        val capBand = (lineHeight * SceneRenderConfig.WALL_TOP_BAND_FRACTION)
            .toInt()
            .coerceIn(SceneRenderConfig.WALL_TOP_BAND_MIN_ROWS, SceneRenderConfig.WALL_TOP_BAND_MAX_ROWS)
        val top = kotlin.math.floor(floorLine - capBand).toInt().coerceIn(0, viewHeight - 1)
        val bottom = kotlin.math.ceil(floorLine + 1f).toInt().coerceIn(top + 1, viewHeight)
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

    fun paintWalls(
        scene: Raycaster.SceneCast,
        pitchHorizon: Float,
        viewerHeight: Float,
        doorMarkers: List<DoorMarkerSnapshot>,
    ) {
        for (x in scene.columns.indices) {
            val col = scene.columns[x]
            val meta = scene.wallMeta[x]
            paintBackWallLayer(meta, col, pitchHorizon, viewerHeight, x, doorMarkers)
            if (col.wallEnd - col.wallStart >= 0.5f) {
                paintWallColumn(
                    x = x,
                    wallStart = col.wallStart,
                    wallEnd = col.wallEnd,
                    meta = meta,
                    doorMarkers = doorMarkers,
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
        doorMarkers: List<DoorMarkerSnapshot>,
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
            val rgb = wallRgb(layer.tile, u, v, row, backStart, backEnd, layer.distance, sideDarken, meta, doorMarkers)
            buffer.set(x, row, rgb)
        }
    }

    private fun paintWallColumn(
        x: Int,
        wallStart: Float,
        wallEnd: Float,
        meta: Raycaster.WallColumnMeta,
        doorMarkers: List<DoorMarkerSnapshot>,
    ) {
        val top = kotlin.math.floor(wallStart).toInt().coerceIn(0, viewHeight - 1)
        val bottom = kotlin.math.ceil(wallEnd).toInt().coerceIn(top + 1, viewHeight)
        val sideDarken = SceneRenderConfig.sideDarken(meta.side)
        val u = TextureMapping.wallTextureUClamped(meta.wallU)
        val tile = meta.tile ?: TileType.WALL
        for (row in top until bottom) {
            val v = TextureMapping.wallTextureV(
                screenRow = row,
                wallStart = wallStart,
                wallEnd = wallEnd,
                tile = tile,
            )
            val rgb = wallRgb(tile, u, v, row, wallStart, wallEnd, meta.distance, sideDarken, meta, doorMarkers)
            buffer.set(x, row, rgb)
        }
    }

    private fun wallRgb(
        tile: TileType,
        u: Float,
        v: Float,
        row: Int,
        wallStart: Float,
        wallEnd: Float,
        distance: Float,
        sideDarken: Float,
        meta: Raycaster.WallColumnMeta,
        doorMarkers: List<DoorMarkerSnapshot>,
    ): Int {
        val base = when (tile) {
            TileType.ROOM_SEAL, TileType.ROOM_DOOR ->
                TextureMapping.shadeRgb(SEAL_RGB, distance, sideDarken * 0.95f)
            else -> {
                val sample = textures.walls.samplePixel(u, v)
                TextureMapping.shadeRgb(sample.rgb, distance, sideDarken)
            }
        }
        if (tile != TileType.ROOM_SEAL && tile != TileType.ROOM_DOOR) return base
        val marker = doorMarkerAt(doorMarkers, meta.hitMapX, meta.hitMapY) ?: return base
        return blendPrize(base, marker, u, row, wallStart, wallEnd, distance)
    }

    private fun blendPrize(
        baseRgb: Int,
        marker: DoorMarkerSnapshot,
        u: Float,
        row: Int,
        wallStart: Float,
        wallEnd: Float,
        distance: Float,
    ): Int {
        val span = (wallEnd - wallStart).coerceAtLeast(1f)
        val rel = ((row + 0.5f) - wallStart) / span
        if (rel !in PRIZE_V_MIN..PRIZE_V_MAX) return baseRgb
        val prize = prizeSampler(marker)
        if (prize != null) {
            val sample = prize.samplePixel(u, rel)
            if (!prize.isVisible(sample, chromaKey = true)) return baseRgb
            val prizeRgb = TextureMapping.shadeRgb(sample.rgb, distance)
            return mixRgb(baseRgb, prizeRgb, PRIZE_BLEND)
        }
        val flat = prizeFlatRgb(marker) ?: return baseRgb
        if (u < 0.28f || u > 0.72f) return baseRgb
        val prizeRgb = TextureMapping.shadeRgb(flat, distance)
        return mixRgb(baseRgb, prizeRgb, PRIZE_BLEND)
    }

    private fun prizeSampler(marker: DoorMarkerSnapshot): RgbImageSampler? = when {
        marker.mobRoom -> null
        marker.prizeIsKey -> textures.keySprite
        else -> null
    }

    private fun prizeFlatRgb(marker: DoorMarkerSnapshot): Int? = when {
        marker.mobRoom -> COMBAT_ROOM_RGB
        marker.prizeIsKey -> null
        marker.kind == ru.course.roguelike.shared.model.ItemKind.WEAPON_PISTOL -> PISTOL_PRIZE_RGB
        marker.kind == ru.course.roguelike.shared.model.ItemKind.WEAPON_SHOTGUN -> SHOTGUN_PRIZE_RGB
        else -> null
    }

    private fun doorMarkerAt(markers: List<DoorMarkerSnapshot>, mapX: Int, mapY: Int): DoorMarkerSnapshot? =
        if (mapX < 0 || mapY < 0) {
            null
        } else {
            markers.find {
                kotlin.math.floor(it.x).toInt() == mapX && kotlin.math.floor(it.y).toInt() == mapY
            }
        }

    private fun tintBrown(rgb: Int): Int {
        val r = (((rgb shr 16) and 0xFF) * 0.85f + 0x7A * 0.15f).toInt().coerceIn(0, 255)
        val g = (((rgb shr 8) and 0xFF) * 0.7f + 0x4A * 0.3f).toInt().coerceIn(0, 255)
        val b = ((rgb and 0xFF) * 0.55f + 0x22 * 0.45f).toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }

    private fun mixRgb(base: Int, overlay: Int, overlayWeight: Float): Int {
        val t = overlayWeight.coerceIn(0f, 1f)
        val br = (base shr 16) and 0xFF
        val bg = (base shr 8) and 0xFF
        val bb = base and 0xFF
        val or = (overlay shr 16) and 0xFF
        val og = (overlay shr 8) and 0xFF
        val ob = overlay and 0xFF
        val r = (br * (1f - t) + or * t).toInt()
        val g = (bg * (1f - t) + og * t).toInt()
        val b = (bb * (1f - t) + ob * t).toInt()
        return (r shl 16) or (g shl 8) or b
    }

    fun paintWallCaps(scene: Raycaster.SceneCast) {
        for (x in scene.columns.indices) {
            val col = scene.columns[x]
            val meta = scene.wallMeta[x]
            val tile = meta.tile
            if (tile != TileType.WALL && tile != TileType.COLUMN) continue
            paintWallCapColumn(x, col, meta, tile)
        }
    }

    private fun paintWallCapColumn(
        x: Int,
        col: Raycaster.Column,
        meta: Raycaster.WallColumnMeta,
        tile: TileType,
    ) {
        val span = (col.wallEnd - col.wallStart).coerceAtLeast(1f)
        val capFraction = if (tile == TileType.COLUMN) {
            SceneRenderConfig.WALL_CAP_FRACTION * 1.6f
        } else {
            SceneRenderConfig.WALL_CAP_FRACTION
        }
        val capRows = (span * capFraction).toInt().coerceIn(1, if (tile == TileType.COLUMN) 10 else 8)
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

    private companion object {
        const val SEAL_RGB = 0xCC2A22
        const val PRIZE_V_MIN = 0.32f
        const val PRIZE_V_MAX = 0.68f
        const val PRIZE_BLEND = 0.72f
        const val PISTOL_PRIZE_RGB = 0x66AAFF
        const val SHOTGUN_PRIZE_RGB = 0xCC4433
        const val COMBAT_ROOM_RGB = 0xDDAA33
    }
}
