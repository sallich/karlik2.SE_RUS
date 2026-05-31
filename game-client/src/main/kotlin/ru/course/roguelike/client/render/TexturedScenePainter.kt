package ru.course.roguelike.client.render

import com.badlogic.gdx.graphics.Pixmap
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
    private val pixmap: Pixmap,
    private val viewWidth: Int,
    private val viewHeight: Int,
    private val textures: GameTextures,
) {
    fun paintSky(horizonInt: Int, yaw: Float) {
        val skyBottom = horizonInt.coerceIn(1, viewHeight)
        for (row in 0 until skyBottom) {
            for (col in 0 until viewWidth) {
                val (u, v) = TextureMapping.skyUv(col, row, viewWidth, skyBottom, yaw)
                val rgb = textures.sky.samplePixel(u, v).rgb
                drawRgbPixel(col, row, rgb)
            }
        }
    }

    fun paintFloor(map: TileMap, pose: PlayerPose, horizon: Float, horizonInt: Int) {
        val firstRow = horizonInt.coerceAtLeast(0)
        for (col in 0 until viewWidth) {
            val ray = Raycaster.rayDirection(pose, viewWidth, col)
            for (row in firstRow until viewHeight) {
                floorRgbAt(map, pose, horizon, ray, row)?.let { drawRgbPixel(col, row, it) }
            }
        }
    }

    private fun floorRgbAt(map: TileMap, pose: PlayerPose, horizon: Float, ray: FloatArray, row: Int): Int? {
        val dist = Raycaster.floorDistance(viewHeight, horizon, row)
        if (dist.isInfinite() || dist > SceneRenderConfig.MAX_FLOOR_DISTANCE) return null
        val floorX = pose.x + dist * ray[0]
        val floorY = pose.y + dist * ray[1]
        val tile = map.getTileAt(floorX, floorY) ?: return null
        if (tile !in FLOOR_TILES) return null
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
            for (row in top until bottom) {
                val u = TextureMapping.wallTextureUClamped(meta.wallU)
                val v = TextureMapping.wallTextureV(row, col.wallStart, meta.distance, viewHeight, meta.tile)
                val sample = textures.walls.samplePixel(u, v)
                val rgb = TextureMapping.shadeRgb(
                    sample.rgb,
                    meta.distance,
                    SceneRenderConfig.sideDarken(meta.side),
                )
                drawRgbPixel(x, row, rgb)
            }
        }
    }

    fun paintSprites(
        pose: PlayerPose,
        horizon: Float,
        mobs: List<MobSnapshot>,
        projectiles: List<ProjectileSnapshot>,
        wallDistances: FloatArray,
    ) {
        val sprites = buildList {
            mobs.forEach { mob ->
                add(
                    BillboardRenderer.Sprite(
                        worldX = mob.x,
                        worldY = mob.y,
                        texture = when (mob.kind) {
                            MobKind.MELEE -> BillboardRenderer.SpriteTexture.MELEE
                            MobKind.RANGED -> BillboardRenderer.SpriteTexture.RANGED
                        },
                    ),
                )
            }
            projectiles.forEach { projectile ->
                add(
                    BillboardRenderer.Sprite(
                        worldX = projectile.x,
                        worldY = projectile.y,
                        texture = BillboardRenderer.SpriteTexture.BLAST,
                        sizeScale = if (projectile.fromPlayer) 0.4f else 0.35f,
                    ),
                )
            }
        }
        val commands = BillboardRenderer.projectSprites(
            pose,
            viewWidth,
            viewHeight,
            horizon,
            sprites,
            wallDistances,
        )
        for (command in commands) {
            paintSpriteCommand(command)
        }
    }

    private fun paintSpriteCommand(command: BillboardRenderer.DrawCommand) {
        val sampler = textures.samplerFor(command.texture) ?: return
        val chromaKey = textures.usesChromaKey(command.texture)
        for (x in command.left until command.right) {
            val u = TextureMapping.spriteColumnU(x, command.left, command.right)
            for (y in command.top until command.bottom) {
                val sample = sampler.sampleColumnU(u, y, command.top, command.bottom)
                if (!sampler.isVisible(sample, chromaKey)) continue
                val rgb = TextureMapping.shadeRgb(sample.rgb, command.distance)
                drawRgbPixel(x, y, rgb)
            }
        }
    }

    private fun drawRgbPixel(x: Int, y: Int, rgb: Int) {
        pixmap.drawPixel(x, y, RgbImageSampler.toLibGdxPixel(rgb))
    }

    private companion object {
        val FLOOR_TILES = setOf(TileType.FLOOR, TileType.LAVA, TileType.ELEVATOR)
    }
}
