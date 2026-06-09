package ru.course.roguelike.client.render

import ru.course.roguelike.shared.dto.ItemSnapshot
import ru.course.roguelike.shared.dto.KeySnapshot
import ru.course.roguelike.shared.dto.MobSnapshot
import ru.course.roguelike.shared.dto.ProjectileSnapshot
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.model.WorldVertical
import ru.course.roguelike.shared.render.CameraProjection
import ru.course.roguelike.shared.render.Raycaster
import ru.course.roguelike.shared.render.SceneRenderConfig
import ru.course.roguelike.shared.render.TextureMapping

internal class TexturedScenePainter(
    private val buffer: PixelFrameBuffer,
    private val viewWidth: Int,
    private val viewHeight: Int,
    private val textures: GameTextures,
) {
    private val wallPainter = TexturedWallPainter(buffer, viewHeight, textures)
    private val spritePainter = TexturedSpritePainter(buffer, viewWidth, viewHeight, textures)

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
                TexturedFloorSampling.horizontalSurfaceRgb(
                    FloorSampleRequest(
                        textures = textures,
                        map = map,
                        pose = pose,
                        ray = ray,
                        viewHeight = viewHeight,
                        horizon = horizon,
                        screenRow = row,
                        viewerHeight = viewerHeight,
                    ),
                )?.let { buffer.set(col, row, it) }
            }
        }
    }

    fun paintHorizontalTops(
        scene: Raycaster.SceneCast,
        pitchHorizon: Float,
        viewerHeight: Float,
    ) = wallPainter.paintHorizontalTops(scene, pitchHorizon, viewerHeight)

    fun paintWalls(scene: Raycaster.SceneCast, pitchHorizon: Float, viewerHeight: Float) =
        wallPainter.paintWalls(scene, pitchHorizon, viewerHeight)

    fun paintWallCaps(scene: Raycaster.SceneCast) = wallPainter.paintWallCaps(scene)

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
    ) = spritePainter.paintSprites(
        pose,
        pitchHorizon,
        viewerHeightAboveFloor,
        mobs,
        projectiles,
        keyPickups,
        items,
        agentPose,
        wallDistances,
        wallMeta,
    )
}
