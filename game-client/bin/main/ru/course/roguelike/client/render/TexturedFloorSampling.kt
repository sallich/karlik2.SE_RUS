package ru.course.roguelike.client.render

import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.model.WorldVertical
import ru.course.roguelike.shared.render.HorizontalSurfacePicker
import ru.course.roguelike.shared.render.Raycaster
import ru.course.roguelike.shared.render.SceneRenderConfig
import ru.course.roguelike.shared.render.TextureMapping

internal data class FloorSampleRequest(
    val textures: GameTextures,
    val map: TileMap,
    val pose: PlayerPose,
    val ray: FloatArray,
    val viewHeight: Int,
    val horizon: Float,
    val screenRow: Int,
    val viewerHeight: Float,
)

internal object TexturedFloorSampling {
    fun horizontalSurfaceRgb(request: FloorSampleRequest): Int? {
        val hit = HorizontalSurfacePicker.pick(
            request.map,
            request.pose,
            request.ray,
            request.viewHeight,
            request.horizon,
            request.screenRow,
            request.viewerHeight,
        ) ?: return fallbackFloorRgb(request)
        return rgbForHorizontalHit(request.textures, hit)
    }

    private fun rgbForHorizontalHit(textures: GameTextures, hit: HorizontalSurfacePicker.Hit): Int {
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

    private fun fallbackFloorRgb(request: FloorSampleRequest): Int? {
        val dist = Raycaster.floorDistance(
            request.viewHeight,
            request.horizon,
            request.screenRow,
            request.viewerHeight.coerceAtLeast(0f),
        )
        if (dist.isInfinite() || dist > SceneRenderConfig.MAX_FLOOR_DISTANCE) return null
        val (u, v) = 0.5f to 0.5f
        return TextureMapping.shadeRgb(request.textures.floor.samplePixel(u, v).rgb, dist)
    }
}
