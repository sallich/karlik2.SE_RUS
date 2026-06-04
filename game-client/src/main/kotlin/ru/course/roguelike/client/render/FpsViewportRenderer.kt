package ru.course.roguelike.client.render

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import ru.course.roguelike.shared.dto.ItemSnapshot
import ru.course.roguelike.shared.dto.KeySnapshot
import ru.course.roguelike.shared.dto.MobSnapshot
import ru.course.roguelike.shared.dto.ProjectileSnapshot
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.render.Raycaster
import ru.course.roguelike.shared.render.SceneRenderConfig

class FpsViewportRenderer(
    private val viewWidth: Int,
    private val viewHeight: Int,
    textures: GameTextures,
) {
    private val pixmap = Pixmap(viewWidth, viewHeight, Pixmap.Format.RGBA8888)
    private val frameBuffer = PixelFrameBuffer(viewWidth, viewHeight)
    private val painter = TexturedScenePainter(frameBuffer, viewWidth, viewHeight, textures)
    private lateinit var texture: Texture
    private var textureReady = false

    fun render(
        map: TileMap,
        pose: PlayerPose,
        mobs: List<MobSnapshot> = emptyList(),
        projectiles: List<ProjectileSnapshot> = emptyList(),
        keyPickups: List<KeySnapshot> = emptyList(),
        items: List<ItemSnapshot> = emptyList(),
        agentPose: PlayerPose? = null,
    ): Texture {
        val horizon = SceneRenderConfig.horizonY(viewHeight, pose.pitch)
        val horizonInt = kotlin.math.ceil(horizon).toInt().coerceIn(0, viewHeight)

        painter.beginFrame()
        painter.paintSky(horizonInt, pose.yaw)
        painter.fillFloorBase(horizonInt)
        painter.paintFloor(map, pose, horizon, horizonInt)
        val scene = Raycaster.castScene(map, pose, viewWidth, viewHeight, horizon)
        painter.paintWalls(scene)
        painter.paintSprites(pose, horizon, mobs, projectiles, keyPickups, items, agentPose, scene.wallDistances)

        frameBuffer.flushTo(pixmap)

        if (!textureReady) {
            texture = Texture(pixmap)
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
            textureReady = true
        } else {
            texture.draw(pixmap, 0, 0)
        }
        return texture
    }

    fun dispose() {
        if (textureReady) {
            texture.dispose()
        }
        pixmap.dispose()
    }
}
