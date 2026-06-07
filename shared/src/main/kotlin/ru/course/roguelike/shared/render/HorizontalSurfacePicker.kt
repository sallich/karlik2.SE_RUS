package ru.course.roguelike.shared.render

import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.model.WorldVertical

/**
 * Выбор ближайшей горизонтальной поверхности для floor-cast (пол, верх колонны, верх стены).
 */
object HorizontalSurfacePicker {
    private val FLOOR_TILES = setOf(TileType.FLOOR, TileType.LAVA, TileType.ELEVATOR, TileType.EXIT_GATE)

    data class Hit(
        val distance: Float,
        val tile: TileType,
        val floorX: Float,
        val floorY: Float,
        val surfaceZ: Float,
    )

    fun pick(
        map: TileMap,
        pose: PlayerPose,
        ray: FloatArray,
        screenHeight: Int,
        horizon: Float,
        screenRow: Int,
        viewerHeight: Float,
    ): Hit? {
        var best: Hit? = null
        for (layer in layers(viewerHeight)) {
            val dist = Raycaster.floorDistance(screenHeight, horizon, screenRow, layer.heightAboveViewer)
            if (dist.isInfinite() || dist > SceneRenderConfig.MAX_FLOOR_DISTANCE) continue
            if (best != null && dist >= best!!.distance) continue
            val floorX = pose.x + dist * ray[0]
            val floorY = pose.y + dist * ray[1]
            val tile = map.getTileAt(floorX, floorY) ?: continue
            if (!layer.accepts(tile)) continue
            best = Hit(dist, tile, floorX, floorY, layer.surfaceZ)
        }
        return best
    }

    fun isFloorTile(tile: TileType): Boolean = tile in FLOOR_TILES

    private data class Layer(
        val surfaceZ: Float,
        val heightAboveViewer: Float,
        val accepts: (TileType) -> Boolean,
    )

    private fun layers(viewerHeight: Float): List<Layer> {
        val result = ArrayList<Layer>(3)
        result.add(
            Layer(
                surfaceZ = 0f,
                heightAboveViewer = viewerHeight.coerceAtLeast(0f),
                accepts = { isFloorTile(it) },
            ),
        )
        if (viewerHeight >= WorldVertical.COLUMN_HEIGHT - 0.05f) {
            result.add(
                Layer(
                    surfaceZ = WorldVertical.COLUMN_HEIGHT,
                    heightAboveViewer = (viewerHeight - WorldVertical.COLUMN_HEIGHT).coerceAtLeast(0f),
                    accepts = { it == TileType.COLUMN },
                ),
            )
        }
        if (viewerHeight >= WorldVertical.WALL_HEIGHT - 0.05f) {
            result.add(
                Layer(
                    surfaceZ = WorldVertical.WALL_HEIGHT,
                    heightAboveViewer = (viewerHeight - WorldVertical.WALL_HEIGHT).coerceAtLeast(0f),
                    accepts = { it == TileType.WALL },
                ),
            )
        }
        return result
    }
}
