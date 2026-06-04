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
import ru.course.roguelike.shared.render.Raycaster
import ru.course.roguelike.shared.render.RgbImageSampler
import ru.course.roguelike.shared.render.SceneRenderConfig
import ru.course.roguelike.shared.render.TextureMapping

internal class TexturedScenePainter(
    private val buffer: PixelFrameBuffer,
    private val viewWidth: Int,
    private val viewHeight: Int,
    private val textures: GameTextures,
) {
    private val floorDistByRow = FloatArray(viewHeight)
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

    fun fillFloorBase(horizonInt: Int) {
        if (horizonInt < viewHeight) {
            buffer.fillRect(0, horizonInt, viewWidth, viewHeight - horizonInt, SceneRenderConfig.FLOOR_BASE_RGB)
        }
    }

    fun paintFloor(map: TileMap, pose: PlayerPose, horizon: Float, horizonInt: Int) {
        val firstRow = horizonInt.coerceAtLeast(0)
        for (row in firstRow until viewHeight) {
            floorDistByRow[row] = Raycaster.floorDistance(viewHeight, horizon, row)
        }
        for (col in 0 until viewWidth) {
            val ray = Raycaster.rayDirection(pose, viewWidth, col)
            for (row in firstRow until viewHeight) {
                floorRgbAt(map, pose, ray, floorDistByRow[row])?.let { buffer.set(col, row, it) }
            }
        }
    }

    private fun floorRgbAt(
        map: TileMap,
        pose: PlayerPose,
        ray: FloatArray,
        dist: Float,
    ): Int? {
        if (dist.isInfinite() || dist > SceneRenderConfig.MAX_FLOOR_DISTANCE) return null
        val floorX = pose.x + dist * ray[0]
        val floorY = pose.y + dist * ray[1]
        val tile = map.getTileAt(floorX, floorY)?.takeIf { it in FLOOR_TILES } ?: return null
        if (tile == TileType.EXIT_GATE) {
            return TextureMapping.shadeRgb(SceneRenderConfig.EXIT_GATE_RGB, dist)
        }
        val (u, v) = TextureMapping.floorUv(floorX, floorY)
        val sampler = when (tile) {
            TileType.LAVA -> textures.lava
            TileType.ELEVATOR -> textures.door
            else -> textures.floor
        }
        return TextureMapping.shadeRgb(sampler.samplePixel(u, v).rgb, dist)
    }

    fun paintWalls(scene: Raycaster.SceneCast) {
        for (x in scene.columns.indices) {
            val col = scene.columns[x]
            val meta = scene.wallMeta[x]
            val top = kotlin.math.floor(col.wallStart).toInt().coerceIn(0, viewHeight - 1)
            val bottom = kotlin.math.ceil(col.wallEnd).toInt().coerceIn(top + 1, viewHeight)
            val sideDarken = SceneRenderConfig.sideDarken(meta.side)
            val u = TextureMapping.wallTextureUClamped(meta.wallU)
            for (row in top until bottom) {
                val v = TextureMapping.wallTextureV(row, col.wallStart, meta.distance, viewHeight, meta.tile)
                val sample = textures.walls.samplePixel(u, v)
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
        horizon: Float,
        mobs: List<MobSnapshot>,
        projectiles: List<ProjectileSnapshot>,
        keyPickups: List<KeySnapshot>,
        items: List<ItemSnapshot>,
        agentPose: PlayerPose? = null,
        wallDistances: FloatArray,
    ) {
        spriteScratch.clear()
        fun add(x: Float, y: Float, texture: BillboardRenderer.SpriteTexture, scale: Float = 1f) {
            spriteScratch.add(BillboardRenderer.Sprite(worldX = x, worldY = y, texture = texture, sizeScale = scale))
        }

        agentPose?.let { add(it.x, it.y, BillboardRenderer.SpriteTexture.PLAYER, 0.85f) }
        keyPickups.forEach { add(it.x, it.y, BillboardRenderer.SpriteTexture.KEY, 0.55f) }
        items.forEach { add(it.x, it.y, BillboardRenderer.itemTexture(it.kind), 0.45f) }
        mobs.forEach {
            val texture = when (it.kind) {
                MobKind.MELEE -> BillboardRenderer.SpriteTexture.MELEE
                MobKind.RANGED, MobKind.LLM_GUARD -> BillboardRenderer.SpriteTexture.RANGED
            }
            add(it.x, it.y, texture)
        }
        projectiles.forEach {
            add(it.x, it.y, BillboardRenderer.SpriteTexture.BLAST, if (it.fromPlayer) 0.4f else 0.35f)
        }

        val commands = BillboardRenderer.projectSprites(
            pose,
            viewWidth,
            viewHeight,
            horizon,
            spriteScratch,
            wallDistances,
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

    private companion object {
        val FLOOR_TILES = setOf(TileType.FLOOR, TileType.LAVA, TileType.ELEVATOR, TileType.EXIT_GATE)
    }
}
