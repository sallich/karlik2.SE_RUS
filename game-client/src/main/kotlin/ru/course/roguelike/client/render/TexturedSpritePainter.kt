package ru.course.roguelike.client.render

import ru.course.roguelike.shared.dto.ItemSnapshot
import ru.course.roguelike.shared.dto.KeySnapshot
import ru.course.roguelike.shared.dto.MobSnapshot
import ru.course.roguelike.shared.dto.ProjectileSnapshot
import ru.course.roguelike.shared.model.MobKind
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.render.BillboardRenderer
import ru.course.roguelike.shared.render.Raycaster
import ru.course.roguelike.shared.render.RgbImageSampler
import ru.course.roguelike.shared.render.TextureMapping

internal class TexturedSpritePainter(
    private val buffer: PixelFrameBuffer,
    private val viewWidth: Int,
    private val viewHeight: Int,
    private val textures: GameTextures,
) {
    private val spriteScratch = ArrayList<BillboardRenderer.Sprite>(16)

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
        wallMeta: Array<Raycaster.WallColumnMeta>,
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
            add(it.x, it.y, texture, worldZ = it.z)
        }
        projectiles.forEach {
            add(
                it.x,
                it.y,
                BillboardRenderer.SpriteTexture.BLAST,
                if (it.fromPlayer) 0.4f else 0.35f,
                worldZ = it.z,
            )
        }

        val commands = BillboardRenderer.projectSprites(
            pose,
            viewWidth,
            viewHeight,
            pitchHorizon,
            spriteScratch,
            wallDistances,
            wallMeta,
            viewerHeightAboveFloor,
        )
        for (command in commands) {
            paintSpriteCommand(command, wallDistances, wallMeta, pitchHorizon, viewerHeightAboveFloor)
        }
    }

    private fun paintSpriteCommand(
        command: BillboardRenderer.DrawCommand,
        wallDistances: FloatArray,
        wallMeta: Array<Raycaster.WallColumnMeta>,
        pitchHorizon: Float,
        viewerHeightAboveFloor: Float,
    ) {
        val sampler = textures.samplerFor(command.texture)
        val chromaKey = textures.usesChromaKey(command.texture)
        for (x in command.left until command.right) {
            paintSpriteColumn(
                command,
                wallDistances,
                wallMeta,
                pitchHorizon,
                viewerHeightAboveFloor,
                sampler,
                chromaKey,
                x,
            )
        }
    }

    private fun paintSpriteColumn(
        command: BillboardRenderer.DrawCommand,
        wallDistances: FloatArray,
        wallMeta: Array<Raycaster.WallColumnMeta>,
        pitchHorizon: Float,
        viewerHeightAboveFloor: Float,
        sampler: RgbImageSampler?,
        chromaKey: Boolean,
        x: Int,
    ) {
        val col = x.coerceIn(0, wallDistances.size - 1)
        val meta = wallMeta.getOrNull(col)
        val u = TextureMapping.spriteColumnU(x, command.left, command.right)
        for (y in command.top until command.bottom) {
            val effectiveWall = BillboardRenderer.effectiveWallDistanceForSpriteRow(
                screenRow = y,
                spriteDistance = command.distance,
                wallDistance = wallDistances[col],
                wallMeta = meta,
                pitchHorizonY = pitchHorizon,
                screenHeight = viewHeight,
                viewerHeightAboveFloor = viewerHeightAboveFloor,
                spriteWorldZ = command.worldZ,
            )
            if (command.distance > effectiveWall + 0.05f) continue
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
