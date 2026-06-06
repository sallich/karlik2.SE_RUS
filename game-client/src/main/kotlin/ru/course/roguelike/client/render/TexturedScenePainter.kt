package ru.course.roguelike.client.render

import ru.course.roguelike.shared.dto.ItemSnapshot
import ru.course.roguelike.shared.dto.KeySnapshot
import ru.course.roguelike.shared.dto.MobSnapshot
import ru.course.roguelike.shared.dto.ProjectileSnapshot
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.MobKind
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.render.BillboardRenderer
import ru.course.roguelike.shared.render.CameraProjection
import ru.course.roguelike.shared.render.HorizontalSurfacePicker
import ru.course.roguelike.shared.render.Raycaster
import ru.course.roguelike.shared.render.RgbImageSampler
import ru.course.roguelike.shared.render.SceneRenderConfig
import ru.course.roguelike.shared.render.TextureMapping
import ru.course.roguelike.shared.model.WorldVertical
import ru.course.roguelike.shared.model.wallHeight

internal class TexturedScenePainter(
    private val buffer: PixelFrameBuffer,
    private val viewWidth: Int,
    private val viewHeight: Int,
    private val textures: GameTextures,
) {
    private val spriteScratch = ArrayList<BillboardRenderer.Sprite>(16)

    fun beginFrame() {
        buffer.clear(SceneRenderConfig.SKY_RGB)
    }

    fun paintSky(horizonInt: Int, yaw: Float) {
        val skyBottom = horizonInt.coerceIn(1, viewHeight)
        for (row in 0 until skyBottom) {
            val v = TextureMapping.skyV(row, skyBottom)
            for (col in 0 until viewWidth) {
                val u = TextureMapping.skyU(col, viewWidth, yaw)
                buffer.set(col, row, textures.sky.samplePixel(u, v).rgb)
            }
        }
    }

    /** Площадка колонны под ногами (текстура стены), когда стоим сверху. */
    fun paintColumnUnderfoot(
        map: TileMap,
        pose: PlayerPose,
        pitchHorizon: Float,
        viewerHeight: Float,
    ) {
        if (viewerHeight < WorldVertical.COLUMN_HEIGHT - 0.05f) return
        if (map.getTileAt(pose.x, pose.y) != TileType.COLUMN) return
        val nearDist = 0.8f
        val platformY = CameraProjection.worldFloorScreenY(
            pitchHorizon,
            viewHeight,
            nearDist,
            viewerHeight - WorldVertical.COLUMN_HEIGHT,
        )
        val halfW = (viewWidth * 0.2f).toInt()
        val centerX = viewWidth / 2
        val top = (platformY - viewHeight * 0.1f).toInt().coerceIn(0, viewHeight - 2)
        val bottom = (platformY + 2f).toInt().coerceIn(top + 1, viewHeight)
        val rowBase = TextureMapping.wallAtlasRowBase(TileType.COLUMN)
        val u = TextureMapping.wallTextureUClamped(TextureMapping.frac(pose.x))
        for (row in top until bottom) {
            val v = rowBase + 0.04f
            val rgb = TextureMapping.shadeRgb(textures.walls.samplePixel(u, v).rgb, nearDist, 0.9f)
            for (col in (centerX - halfW) until (centerX + halfW)) {
                if (col in 0 until viewWidth) buffer.set(col, row, rgb)
            }
        }
    }

    fun fillFloorBase(horizonInt: Int) {
        if (horizonInt >= viewHeight) return
        val baseRgb = textures.floor.samplePixel(0.5f, 0.5f).rgb
        val shaded = TextureMapping.shadeRgb(baseRgb, SceneRenderConfig.MAX_FLOOR_DISTANCE)
        buffer.fillRect(0, horizonInt, viewWidth, viewHeight - horizonInt, shaded)
    }

    fun paintFloor(map: TileMap, pose: PlayerPose, horizon: Float, horizonInt: Int, viewerHeight: Float = 0f) {
        val firstRow = horizonInt.coerceAtLeast(0)
        for (col in 0 until viewWidth) {
            val ray = Raycaster.rayDirection(pose, viewWidth, col)
            for (row in firstRow until viewHeight) {
                horizontalSurfaceRgb(map, pose, ray, horizon, row, viewerHeight)?.let { buffer.set(col, row, it) }
            }
        }
    }

    private fun horizontalSurfaceRgb(
        map: TileMap,
        pose: PlayerPose,
        ray: FloatArray,
        horizon: Float,
        screenRow: Int,
        viewerHeight: Float,
    ): Int? {
        val hit = HorizontalSurfacePicker.pick(map, pose, ray, viewHeight, horizon, screenRow, viewerHeight)
            ?: return fallbackFloorRgb(viewerHeight, horizon, screenRow)
        return rgbForHorizontalHit(hit)
    }

    private fun rgbForHorizontalHit(hit: HorizontalSurfacePicker.Hit): Int {
        val dist = hit.distance
        if (hit.tile == TileType.EXIT_GATE) {
            return TextureMapping.shadeRgb(SceneRenderConfig.EXIT_GATE_RGB, dist)
        }
        if (hit.tile == TileType.WALL) {
            val u = TextureMapping.wallTextureUClamped(TextureMapping.frac(hit.floorX))
            val v = TextureMapping.wallAtlasRowBase(TileType.WALL) + 0.05f
            val sample = textures.walls.samplePixel(u, v)
            return TextureMapping.shadeRgb(sample.rgb, dist, SceneRenderConfig.WALL_CAP_DARKEN)
        }
        if (hit.tile == TileType.COLUMN && hit.surfaceZ >= WorldVertical.COLUMN_HEIGHT - 0.01f) {
            val u = TextureMapping.wallTextureUClamped(TextureMapping.frac(hit.floorX))
            val v = TextureMapping.wallAtlasRowBase(TileType.COLUMN) + 0.05f
            return TextureMapping.shadeRgb(textures.walls.samplePixel(u, v).rgb, dist, 0.88f)
        }
        val (u, v) = TextureMapping.floorUv(hit.floorX, hit.floorY)
        val sampler = when (hit.tile) {
            TileType.LAVA -> textures.lava
            TileType.ELEVATOR -> textures.door
            else -> textures.floor
        }
        return TextureMapping.shadeRgb(sampler.samplePixel(u, v).rgb, dist)
    }

    private fun fallbackFloorRgb(viewerHeight: Float, horizon: Float, screenRow: Int): Int? {
        val dist = Raycaster.floorDistance(viewHeight, horizon, screenRow, viewerHeight.coerceAtLeast(0f))
        if (dist.isInfinite() || dist > SceneRenderConfig.MAX_FLOOR_DISTANCE) return null
        val (u, v) = 0.5f to 0.5f
        return TextureMapping.shadeRgb(textures.floor.samplePixel(u, v).rgb, dist)
    }

    fun paintHorizontalTops(
        scene: Raycaster.SceneCast,
        pose: PlayerPose,
        pitchHorizon: Float,
        viewerHeight: Float,
    ) {
        for (x in scene.columns.indices) {
            val meta = scene.wallMeta[x]
            if (!meta.horizontalTop) continue
            val tile = meta.tile ?: continue
            val surfaceZ = tile.wallHeight()
            val floorLine = ru.course.roguelike.shared.render.CameraProjection.worldFloorScreenY(
                pitchHorizon,
                viewHeight,
                meta.distance,
                viewerHeight - surfaceZ,
            )
            val top = kotlin.math.floor(floorLine - meta.distance * 0.06f).toInt().coerceIn(0, viewHeight - 1)
            val bottom = kotlin.math.ceil(floorLine).toInt().coerceIn(top + 1, viewHeight)
            val ray = Raycaster.rayDirection(pose, viewWidth, x)
            val floorX = pose.x + meta.distance * ray[0]
            val floorY = pose.y + meta.distance * ray[1]
            val (fu, fv) = TextureMapping.floorUv(floorX, floorY)
            val u = TextureMapping.wallTextureUClamped(meta.wallU)
            for (row in top until bottom) {
                val rgb = when (tile) {
                    TileType.WALL -> {
                        val v = TextureMapping.wallAtlasRowBase(tile) + 0.05f
                        TextureMapping.shadeRgb(
                            textures.walls.samplePixel(u, v).rgb,
                            meta.distance,
                            SceneRenderConfig.WALL_CAP_DARKEN,
                        )
                    }
                    TileType.COLUMN -> {
                        val v = TextureMapping.wallAtlasRowBase(tile) + 0.05f
                        TextureMapping.shadeRgb(textures.walls.samplePixel(u, v).rgb, meta.distance, 0.88f)
                    }
                    else -> continue
                }
                buffer.set(x, row, rgb)
            }
        }
    }

    fun paintWalls(scene: Raycaster.SceneCast, pitchHorizon: Float, viewerHeight: Float) {
        for (x in scene.columns.indices) {
            val col = scene.columns[x]
            val meta = scene.wallMeta[x]
            paintBackWallLayer(meta, col, pitchHorizon, viewerHeight, x)
            if (meta.horizontalTop) continue
            if (col.wallEnd - col.wallStart < 0.5f) continue
            paintWallColumn(
                x = x,
                wallStart = col.wallStart,
                wallEnd = col.wallEnd,
                meta = meta,
            )
        }
    }

    private fun paintBackWallLayer(
        meta: Raycaster.WallColumnMeta,
        frontCol: Raycaster.Column,
        pitchHorizon: Float,
        viewerHeight: Float,
        x: Int,
    ) {
        val backDist = meta.backDistance ?: return
        val backTile = meta.backTile ?: return
        val backSide = meta.backSide ?: return
        val backU = meta.backWallU ?: return
        val lineHeight = viewHeight / backDist
        val wallH = backTile.wallHeight()
        val (backStart, backEnd) = CameraProjection.projectWallSpan(
            pitchHorizonY = pitchHorizon,
            lineHeight = lineHeight,
            wallHeight = wallH,
            screenHeight = viewHeight,
            perpDistance = backDist,
            viewerHeightAboveFloor = viewerHeight,
        )
        val frontTop = frontCol.wallStart
        val top = kotlin.math.ceil(backStart).toInt().coerceIn(0, viewHeight - 1)
        val bottom = kotlin.math.min(kotlin.math.ceil(backEnd).toInt(), kotlin.math.floor(frontTop).toInt())
            .coerceIn(top + 1, viewHeight)
        if (bottom <= top + 1) return
        val sideDarken = SceneRenderConfig.sideDarken(backSide)
        val u = TextureMapping.wallTextureUClamped(backU)
        for (row in top until bottom) {
            val v = TextureMapping.wallTextureV(
                screenRow = row,
                wallStart = backStart,
                wallEnd = backEnd,
                tile = backTile,
            )
            val sample = textures.walls.samplePixel(u, v)
            buffer.set(x, row, TextureMapping.shadeRgb(sample.rgb, backDist, sideDarken))
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

    /** Горизонтальная «крышка» стены / колонны — объём сверху. */
    fun paintWallCaps(scene: Raycaster.SceneCast) {
        for (x in scene.columns.indices) {
            val col = scene.columns[x]
            val meta = scene.wallMeta[x]
            val tile = meta.tile ?: continue
            if (meta.horizontalTop) continue
            if (tile != TileType.WALL && tile != TileType.COLUMN) continue
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

    @Suppress("LongParameterList")
    fun paintSprites(
        pose: PlayerPose,
        pitchHorizon: Float,
        viewerHeightAboveFloor: Float,
        mobs: List<MobSnapshot>,
        projectiles: List<ProjectileSnapshot>,
        keyPickups: List<KeySnapshot>,
        items: List<ItemSnapshot>,
        agentPose: PlayerPose? = null,
        wallDistances: FloatArray,
    ) {
        spriteScratch.clear()
        fun add(
            x: Float,
            y: Float,
            texture: BillboardRenderer.SpriteTexture,
            scale: Float = 1f,
            worldZ: Float = 0f,
        ) {
            spriteScratch.add(
                BillboardRenderer.Sprite(
                    worldX = x,
                    worldY = y,
                    texture = texture,
                    sizeScale = scale,
                    worldZ = worldZ,
                ),
            )
        }

        agentPose?.let { add(it.x, it.y, BillboardRenderer.SpriteTexture.PLAYER, 0.85f) }
        keyPickups.forEach { add(it.x, it.y, BillboardRenderer.SpriteTexture.KEY, 0.55f) }
        items.forEach {
            add(it.x, it.y, BillboardRenderer.itemTexture(it.kind), 0.45f * BillboardRenderer.itemSizeScale(it.kind))
        }
        mobs.forEach {
            val texture = when (it.kind) {
                MobKind.MELEE -> BillboardRenderer.SpriteTexture.MELEE
                MobKind.RANGED, MobKind.LLM_GUARD -> BillboardRenderer.SpriteTexture.RANGED
            }
            add(it.x, it.y, texture)
        }
        projectiles.forEach {
            add(
                it.x,
                it.y,
                BillboardRenderer.SpriteTexture.BLAST,
                if (it.fromPlayer) 0.4f else 0.35f,
                worldZ = WorldVertical.EYE_HEIGHT,
            )
        }

        val commands = BillboardRenderer.projectSprites(
            pose,
            viewWidth,
            viewHeight,
            pitchHorizon,
            spriteScratch,
            wallDistances,
            viewerHeightAboveFloor,
        )
        for (command in commands) {
            paintSpriteCommand(command, wallDistances)
        }
    }

    private fun paintSpriteCommand(command: BillboardRenderer.DrawCommand, wallDistances: FloatArray) {
        val sampler = textures.samplerFor(command.texture)
        val chromaKey = textures.usesChromaKey(command.texture)
        for (x in command.left until command.right) {
            paintSpriteColumn(command, wallDistances, sampler, chromaKey, x)
        }
    }

    private fun paintSpriteColumn(
        command: BillboardRenderer.DrawCommand,
        wallDistances: FloatArray,
        sampler: RgbImageSampler?,
        chromaKey: Boolean,
        x: Int,
    ) {
        val col = x.coerceIn(0, wallDistances.size - 1)
        if (BillboardRenderer.isColumnOccluded(command.distance, wallDistances[col])) return
        val u = TextureMapping.spriteColumnU(x, command.left, command.right)
        for (y in command.top until command.bottom) {
            val rgb = spritePixelRgb(command, sampler, chromaKey, u, y) ?: continue
            buffer.set(x, y, rgb)
        }
    }

    private fun spritePixelRgb(
        command: BillboardRenderer.DrawCommand,
        sampler: RgbImageSampler?,
        chromaKey: Boolean,
        u: Float,
        y: Int,
    ): Int? {
        if (sampler != null) {
            val sample = sampler.sampleColumnU(u, y, command.top, command.bottom)
            if (!sampler.isVisible(sample, chromaKey)) return null
            return TextureMapping.shadeRgb(sample.rgb, command.distance)
        }
        return TextureMapping.shadeRgb(command.colorRgb, command.distance)
    }

}
