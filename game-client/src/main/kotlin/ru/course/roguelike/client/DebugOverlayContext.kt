package ru.course.roguelike.client

import com.badlogic.gdx.graphics.GL20
import ru.course.roguelike.client.render.CollisionDebugOverlay
import ru.course.roguelike.client.render.LocationMapOverlay
import ru.course.roguelike.client.render.MiniMapOverlay
import ru.course.roguelike.shared.dto.ItemSnapshot
import ru.course.roguelike.shared.dto.KeySnapshot
import ru.course.roguelike.shared.dto.MobSnapshot
import ru.course.roguelike.shared.engine.CollisionDebug
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.PlayerPose

data class DebugOverlayContext(
    val showCollisionDebug: Boolean,
    val showLocationMap: Boolean,
    val showMiniMap: Boolean,
    val screenW: Float,
    val screenH: Float,
    val tileMap: TileMap?,
    val pose: PlayerPose?,
    val visitedTiles: Set<GridPos>,
    val serverMobs: List<MobSnapshot>,
    val keyPickups: List<KeySnapshot>,
    val items: List<ItemSnapshot>,
    val exitGate: GridPos?,
    val lastCollisionDebug: CollisionDebug?,
    val locationMapOverlay: LocationMapOverlay,
    val miniMapOverlay: MiniMapOverlay,
    val collisionDebugOverlay: CollisionDebugOverlay,
)

fun drawDebugOverlays(context: DebugOverlayContext) {
    if (!context.showCollisionDebug && !context.showLocationMap && !context.showMiniMap) return
    com.badlogic.gdx.Gdx.gl.glEnable(GL20.GL_BLEND)
    if (context.showMiniMap) {
        context.tileMap?.let { map ->
            context.miniMapOverlay.render(
                context.screenW,
                context.screenH,
                map,
                context.visitedTiles,
                context.pose,
                context.keyPickups,
                context.items,
                context.exitGate,
            )
        }
    }
    if (context.showLocationMap) {
        context.tileMap?.let { map ->
            context.locationMapOverlay.render(
                context.screenW,
                context.screenH,
                map,
                context.pose,
                context.serverMobs,
                context.keyPickups,
                context.items,
                context.exitGate,
            )
        }
    }
    collisionOverlayParams(context)?.let { overlay ->
        context.collisionDebugOverlay.render(
            context.screenW,
            context.screenH,
            overlay.map,
            overlay.pose,
            overlay.debug,
            context.serverMobs,
        )
    }
    com.badlogic.gdx.Gdx.gl.glDisable(GL20.GL_BLEND)
}

private fun collisionOverlayParams(context: DebugOverlayContext): CollisionOverlayParams? {
    if (!context.showCollisionDebug) return null
    val map = context.tileMap
    val debug = context.lastCollisionDebug
    val pose = context.pose
    return if (map != null && debug != null && pose != null) {
        CollisionOverlayParams(map, pose, debug)
    } else {
        null
    }
}

private data class CollisionOverlayParams(
    val map: TileMap,
    val pose: PlayerPose,
    val debug: CollisionDebug,
)
