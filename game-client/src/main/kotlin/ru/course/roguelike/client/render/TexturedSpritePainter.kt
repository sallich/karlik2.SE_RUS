package ru.course.roguelike.client.render

import ru.course.roguelike.shared.dto.DoorMarkerSnapshot
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
import kotlin.math.hypot

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
        doorMarkers: List<DoorMarkerSnapshot>,
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
        // Двери незачищенных комнат (issue #24): коричневый блок (красный в бою) с иконкой приза.
        doorMarkers.forEach { marker -> addDoor(marker, pose) }
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

    private data class SpriteColumnContext(
        val command: BillboardRenderer.DrawCommand,
        val wallDistances: FloatArray,
        val wallMeta: Array<Raycaster.WallColumnMeta>,
        val pitchHorizon: Float,
        val viewerHeightAboveFloor: Float,
        val sampler: RgbImageSampler?,
        val chromaKey: Boolean,
        val x: Int,
    )

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
                SpriteColumnContext(
                    command = command,
                    wallDistances = wallDistances,
                    wallMeta = wallMeta,
                    pitchHorizon = pitchHorizon,
                    viewerHeightAboveFloor = viewerHeightAboveFloor,
                    sampler = sampler,
                    chromaKey = chromaKey,
                    x = x,
                ),
            )
        }
    }

    private fun paintSpriteColumn(context: SpriteColumnContext) {
        val col = context.x.coerceIn(0, context.wallDistances.size - 1)
        val meta = context.wallMeta.getOrNull(col)
        val u = TextureMapping.spriteColumnU(context.x, context.command.left, context.command.right)
        for (y in context.command.top until context.command.bottom) {
            visibleSpritePixel(context, col, meta, u, y)?.let { buffer.set(context.x, y, it) }
        }
    }

    private fun visibleSpritePixel(
        context: SpriteColumnContext,
        col: Int,
        meta: Raycaster.WallColumnMeta?,
        u: Float,
        y: Int,
    ): Int? {
        val effectiveWall = BillboardRenderer.effectiveWallDistanceForSpriteRow(
            screenRow = y,
            spriteDistance = context.command.distance,
            wallDistance = context.wallDistances[col],
            wallMeta = meta,
            pitchHorizonY = context.pitchHorizon,
            screenHeight = viewHeight,
            viewerHeightAboveFloor = context.viewerHeightAboveFloor,
            spriteWorldZ = context.command.worldZ,
        )
        if (context.command.distance > effectiveWall + 0.05f) return null
        return spritePixelRgb(context.command, context.sampler, context.chromaKey, u, y)
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

    /** Дверь: цветная панель-блок + иконка приза чуть ближе к игроку (рисуется поверх панели). */
    private fun addDoor(marker: DoorMarkerSnapshot, pose: PlayerPose) {
        spriteScratch.add(
            BillboardRenderer.Sprite(
                worldX = marker.x,
                worldY = marker.y,
                texture = BillboardRenderer.SpriteTexture.COLOR_FALLBACK,
                colorRgb = if (marker.sealed) DOOR_SEALED_RGB else DOOR_RGB,
                sizeScale = DOOR_SIZE,
                worldZ = DOOR_Z,
            ),
        )
        val len = hypot((pose.x - marker.x).toDouble(), (pose.y - marker.y).toDouble())
            .toFloat()
            .coerceAtLeast(0.001f)
        val prizeTexture = marker.kind?.let(BillboardRenderer::itemTexture) ?: BillboardRenderer.SpriteTexture.KEY
        spriteScratch.add(
            BillboardRenderer.Sprite(
                worldX = marker.x + (pose.x - marker.x) / len * PRIZE_NUDGE,
                worldY = marker.y + (pose.y - marker.y) / len * PRIZE_NUDGE,
                texture = prizeTexture,
                sizeScale = PRIZE_SIZE,
                worldZ = DOOR_Z,
            ),
        )
    }

    private companion object {
        const val DOOR_RGB = 0x7A4A22
        const val DOOR_SEALED_RGB = 0xCC2A22
        const val DOOR_SIZE = 0.9f
        const val DOOR_Z = 0.5f
        const val PRIZE_SIZE = 0.4f
        const val PRIZE_NUDGE = 0.08f
    }
}
