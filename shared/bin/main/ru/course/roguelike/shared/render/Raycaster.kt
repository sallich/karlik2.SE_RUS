package ru.course.roguelike.shared.render

import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.FpsConstants
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.model.WorldVertical
import ru.course.roguelike.shared.model.wallHeight
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

object Raycaster {
    data class Column(
        val wallStart: Float,
        val wallEnd: Float,
        val colorRgb: Int,
    )

    data class WallColumnMeta(
        val distance: Float,
        val side: Int,
        val wallU: Float,
        val tile: TileType?,
        val horizontalTop: Boolean = false,
        val backDistance: Float? = null,
        val backSide: Int? = null,
        val backWallU: Float? = null,
        val backTile: TileType? = null,
    )

    fun castColumns(
        map: TileMap,
        pose: PlayerPose,
        screenWidth: Int,
        screenHeight: Int,
        pitchHorizonY: Float,
        floorLevel: Int = 0,
        viewerHeight: Float = CameraProjection.viewerHeight(pose.height),
        fovRadians: Float = FpsConstants.DEFAULT_FOV_RADIANS,
    ): Array<Column> = castScene(
        map,
        pose,
        screenWidth,
        screenHeight,
        pitchHorizonY,
        floorLevel,
        viewerHeight,
        fovRadians,
    ).columns

    data class SceneCast(
        val columns: Array<Column>,
        val wallDistances: FloatArray,
        val wallMeta: Array<WallColumnMeta>,
    )

    fun castScene(
        map: TileMap,
        pose: PlayerPose,
        screenWidth: Int,
        screenHeight: Int,
        pitchHorizonY: Float,
        floorLevel: Int = 0,
        viewerHeight: Float = CameraProjection.viewerHeight(pose.height),
        fovRadians: Float = FpsConstants.DEFAULT_FOV_RADIANS,
    ): SceneCast {
        if (screenWidth <= 0) return SceneCast(emptyArray(), FloatArray(0), emptyArray())

        val eyeWorldZ = WorldVertical.eyeWorldZ(floorLevel, pose.height)
        val feetWorldZ = WorldVertical.floorWorldZ(floorLevel) + pose.height

        val distances = FloatArray(screenWidth)
        val colors = IntArray(screenWidth)
        val meta = arrayOfNulls<WallColumnMeta>(screenWidth)

        val posX = pose.x
        val posY = pose.y
        val yaw = pose.yaw
        val halfFov = fovRadians / 2f
        val planeX = -sin(yaw) * tan(halfFov)
        val planeY = cos(yaw) * tan(halfFov)
        val dirX = cos(yaw)
        val dirY = sin(yaw)

        for (col in 0 until screenWidth) {
            val cameraX = (col + 0.5f) / screenWidth * 2f - 1f
            val rayDirX = dirX + planeX * cameraX
            val rayDirY = dirY + planeY * cameraX

            var mapX = floor(posX).toInt()
            var mapY = floor(posY).toInt()

            val deltaDistX = if (abs(rayDirX) < 1e-6f) 1e30f else abs(1f / rayDirX)
            val deltaDistY = if (abs(rayDirY) < 1e-6f) 1e30f else abs(1f / rayDirY)

            var sideDistX: Float
            var sideDistY: Float
            val stepX: Int
            val stepY: Int

            if (rayDirX < 0f) {
                stepX = -1
                sideDistX = (posX - mapX) * deltaDistX
            } else {
                stepX = 1
                sideDistX = (mapX + 1f - posX) * deltaDistX
            }
            if (rayDirY < 0f) {
                stepY = -1
                sideDistY = (posY - mapY) * deltaDistY
            } else {
                stepY = 1
                sideDistY = (mapY + 1f - posY) * deltaDistY
            }

            var hit = false
            var side = 0
            var outOfBounds = false
            var hitTile: TileType? = null
            var hitMapX = 0
            var hitMapY = 0
            var hitStepX = 1
            var hitStepY = 1
            var hitHorizontalTop = false

            while (!hit) {
                if (sideDistX < sideDistY) {
                    sideDistX += deltaDistX
                    mapX += stepX
                    side = 0
                } else {
                    sideDistY += deltaDistY
                    mapY += stepY
                    side = 1
                }
                if (mapX < 0 || mapY < 0 || mapX >= map.width || mapY >= map.height) {
                    hit = true
                    outOfBounds = true
                } else {
                    val tile = map.get(GridPos(mapX, mapY))
                    if (tile != null && tile.blocksVision) {
                        if (tile == TileType.COLUMN) {
                            val topZ = WorldVertical.tileTopWorldZ(floorLevel, tile)
                            if (feetWorldZ <= topZ + 0.02f || eyeWorldZ <= topZ + 0.02f) {
                                hit = true
                                hitTile = tile
                                hitMapX = mapX
                                hitMapY = mapY
                                hitStepX = stepX
                                hitStepY = stepY
                            }
                        } else {
                            val topZ = WorldVertical.tileTopWorldZ(floorLevel, tile)
                            if (eyeWorldZ > topZ + 0.02f) {
                                hit = true
                                hitHorizontalTop = true
                                hitTile = tile
                                hitMapX = mapX
                                hitMapY = mapY
                                hitStepX = stepX
                                hitStepY = stepY
                            } else if (WorldVertical.blocksVisionAt(floorLevel, tile, eyeWorldZ)) {
                                hit = true
                                hitTile = tile
                                hitMapX = mapX
                                hitMapY = mapY
                                hitStepX = stepX
                                hitStepY = stepY
                            }
                        }
                    }
                }
            }

            var perpWallDist = if (outOfBounds) {
                24f
            } else if (side == 0) {
                (hitMapX - posX + (1 - hitStepX) / 2f) / rayDirX
            } else {
                (hitMapY - posY + (1 - hitStepY) / 2f) / rayDirY
            }
            perpWallDist = abs(perpWallDist).coerceIn(FpsConstants.COLLISION_MIN_RAY_DIST, 64f)

            distances[col] = perpWallDist
            val shade = (200 / (1f + perpWallDist * 0.28f)).toInt().coerceIn(50, 220)
            colors[col] = colorFor(hitTile, side, shade)
            if (!outOfBounds) {
                val hitCoord = TextureMapping.wallHitCoord(side, perpWallDist, rayDirX, rayDirY, posX, posY)
                var entry = WallColumnMeta(perpWallDist, side, hitCoord, hitTile, hitHorizontalTop)
                if (!hitHorizontalTop && hitTile != null && hitTile.wallHeight() < WorldVertical.WALL_HEIGHT - 0.01f) {
                    castBehindShortWall(
                        map = map,
                        floorLevel = floorLevel,
                        eyeWorldZ = eyeWorldZ,
                        feetWorldZ = feetWorldZ,
                        rayDirX = rayDirX,
                        rayDirY = rayDirY,
                        posX = posX,
                        posY = posY,
                        stepX = stepX,
                        stepY = stepY,
                        mapX = mapX,
                        mapY = mapY,
                        sideDistX = sideDistX,
                        sideDistY = sideDistY,
                        deltaDistX = deltaDistX,
                        deltaDistY = deltaDistY,
                    )?.let { back ->
                        entry = entry.copy(
                            backDistance = back.distance,
                            backSide = back.side,
                            backWallU = back.wallU,
                            backTile = back.tile,
                        )
                    }
                }
                meta[col] = entry
            }
        }

        unwrapWallTextureU(meta)
        unwrapBackWallTextureU(meta)

        val columns = Array(screenWidth) { col ->
            val perpWallDist = distances[col]
            val entry = meta[col]
            if (entry?.horizontalTop == true) {
                val floorLine = CameraProjection.worldFloorScreenY(
                    pitchHorizonY,
                    screenHeight,
                    perpWallDist,
                    viewerHeight - (entry.tile?.wallHeight() ?: 0f),
                )
                return@Array Column(floorLine, floorLine, colors[col])
            }
            val lineHeight = screenHeight / perpWallDist
            val wallH = entry?.tile?.wallHeight() ?: WorldVertical.WALL_HEIGHT
            val (drawStart, drawEnd) = projectWallSpan(
                pitchHorizonY = pitchHorizonY,
                lineHeight = lineHeight,
                wallHeight = wallH,
                screenHeight = screenHeight,
                perpDistance = perpWallDist,
                viewerHeightAboveFloor = viewerHeight,
            )
            Column(drawStart, drawEnd, colors[col])
        }
        return SceneCast(columns, distances, Array(screenWidth) { meta[it] ?: WallColumnMeta(distances[it], 0, 0f, null) })
    }

    internal fun projectWallSpan(
        pitchHorizonY: Float,
        lineHeight: Float,
        wallHeight: Float,
        screenHeight: Int,
        perpDistance: Float,
        viewerHeightAboveFloor: Float,
    ): Pair<Float, Float> = CameraProjection.projectWallSpan(
        pitchHorizonY,
        lineHeight,
        wallHeight,
        screenHeight,
        perpDistance,
        viewerHeightAboveFloor,
    )

   
    private data class BehindHit(val distance: Float, val side: Int, val wallU: Float, val tile: TileType)

    @Suppress("LongParameterList")
    private fun castBehindShortWall(
        map: TileMap,
        floorLevel: Int,
        eyeWorldZ: Float,
        feetWorldZ: Float,
        rayDirX: Float,
        rayDirY: Float,
        posX: Float,
        posY: Float,
        stepX: Int,
        stepY: Int,
        mapX: Int,
        mapY: Int,
        sideDistX: Float,
        sideDistY: Float,
        deltaDistX: Float,
        deltaDistY: Float,
    ): BehindHit? {
        var mx = mapX
        var my = mapY
        var distX = sideDistX
        var distY = sideDistY
        var side = 0
        while (true) {
            if (distX < distY) {
                distX += deltaDistX
                mx += stepX
                side = 0
            } else {
                distY += deltaDistY
                my += stepY
                side = 1
            }
            if (mx < 0 || my < 0 || mx >= map.width || my >= map.height) return null
            val tile = map.get(GridPos(mx, my)) ?: continue
            if (!tile.blocksVision) continue
            if (tile.wallHeight() < WorldVertical.WALL_HEIGHT - 0.01f) continue
            val topZ = WorldVertical.tileTopWorldZ(floorLevel, tile)
            if (feetWorldZ > topZ + 0.02f && eyeWorldZ > topZ + 0.02f) continue
            if (!WorldVertical.blocksVisionAt(floorLevel, tile, eyeWorldZ)) continue
            val perpDist = if (side == 0) {
                (mx - posX + (1 - stepX) / 2f) / rayDirX
            } else {
                (my - posY + (1 - stepY) / 2f) / rayDirY
            }.let { abs(it).coerceIn(FpsConstants.COLLISION_MIN_RAY_DIST, 64f) }
            val hitCoord = TextureMapping.wallHitCoord(side, perpDist, rayDirX, rayDirY, posX, posY)
            return BehindHit(perpDist, side, hitCoord, tile)
        }
    }

    private fun unwrapBackWallTextureU(meta: Array<WallColumnMeta?>) {
        var offset = 0
        var prevFrac = 0f
        var prevSide = -1
        var prevTile: TileType? = null
        for (col in meta.indices) {
            val entry = meta[col] ?: continue
            val backU = entry.backWallU ?: continue
            val backTile = entry.backTile ?: continue
            val backSide = entry.backSide ?: continue
            if (backSide != prevSide || backTile != prevTile) {
                offset = 0
                prevFrac = TextureMapping.wallFracU(backU)
                meta[col] = entry.copy(backWallU = TextureMapping.continuousWallU(backU, offset))
                prevSide = backSide
                prevTile = backTile
                continue
            }
            val fracU = TextureMapping.wallFracU(backU)
            if (fracU - prevFrac > 0.5f) offset--
            if (prevFrac - fracU > 0.5f) offset++
            meta[col] = entry.copy(backWallU = TextureMapping.continuousWallU(backU, offset))
            prevFrac = fracU
            prevSide = backSide
            prevTile = backTile
        }
    }

    private fun unwrapWallTextureU(meta: Array<WallColumnMeta?>) {
        var offset = 0
        var prevFrac = 0f
        var prevSide = -1
        var prevTile: TileType? = null
        for (col in meta.indices) {
            val entry = meta[col] ?: continue
            if (entry.side != prevSide || entry.tile != prevTile) {
                offset = 0
                prevFrac = TextureMapping.wallFracU(entry.wallU)
                meta[col] = entry.copy(wallU = TextureMapping.continuousWallU(entry.wallU, offset))
                prevSide = entry.side
                prevTile = entry.tile
                continue
            }
            val fracU = TextureMapping.wallFracU(entry.wallU)
            if (fracU - prevFrac > 0.5f) offset--
            if (prevFrac - fracU > 0.5f) offset++
            meta[col] = entry.copy(wallU = TextureMapping.continuousWallU(entry.wallU, offset))
            prevFrac = fracU
            prevSide = entry.side
            prevTile = entry.tile
        }
    }

    /** Цвет вертикали стены: колонны — холодный камень, обычные стены — тёплый кирпич. */
    private fun colorFor(tile: TileType?, side: Int, shade: Int): Int = when (tile) {
        TileType.COLUMN -> if (side == 0) {
            rgb(shade / 2, shade / 2, shade)
        } else {
            rgb(shade / 3, shade / 3, shade * 2 / 3)
        }
        else -> if (side == 0) {
            rgb(shade, shade * 2 / 3, shade / 2)
        } else {
            rgb(shade * 2 / 3, shade / 2, shade)
        }
    }

    /**
     * Направление луча (мировые координаты, ненормированное) для экранного
     * столбца [col]. Совпадает с базисом камеры в [castColumns] — используется
     * клиентом для проекции пола (подсветка лавы).
     */
    fun rayDirection(pose: PlayerPose, screenWidth: Int, col: Int, fovRadians: Float = FpsConstants.DEFAULT_FOV_RADIANS): FloatArray {
        val halfFov = fovRadians / 2f
        val planeX = -sin(pose.yaw) * tan(halfFov)
        val planeY = cos(pose.yaw) * tan(halfFov)
        val dirX = cos(pose.yaw)
        val dirY = sin(pose.yaw)
        val cameraX = (col + 0.5f) / screenWidth * 2f - 1f
        return floatArrayOf(dirX + planeX * cameraX, dirY + planeY * cameraX)
    }

    /**
     * Перпендикулярная дистанция до точки пола на экранной строке [screenRow]
     * (ниже [horizon]). Согласована с проекцией стен в [castColumns]:
     * нижняя грань стены на дистанции d попадает в ту же строку, что и пол на d.
     * Возвращает [Float.POSITIVE_INFINITY] для строк на/над горизонтом.
     */
    fun floorDistance(screenHeight: Int, horizon: Float, screenRow: Int, localHeight: Float = 0f): Float {
        val denom = screenRow - horizon
        if (denom <= 0f) return Float.POSITIVE_INFINITY
        return screenHeight * (0.5f + localHeight.coerceAtLeast(0f)) / denom
    }

    private fun rgb(r: Int, g: Int, b: Int): Int = (r shl 16) or (g shl 8) or b
}
