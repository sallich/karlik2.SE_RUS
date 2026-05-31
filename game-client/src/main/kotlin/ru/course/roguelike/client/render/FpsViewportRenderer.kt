package ru.course.roguelike.client.render

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
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
    private val painter = TexturedScenePainter(pixmap, viewWidth, viewHeight, textures)
    private lateinit var texture: Texture
    private var textureReady = false

    fun render(
        map: TileMap,
        pose: PlayerPose,
        mobs: List<MobSnapshot> = emptyList(),
        projectiles: List<ProjectileSnapshot> = emptyList(),
    ): Texture {
        val horizon = SceneRenderConfig.horizonY(viewHeight, pose.pitch)
        val horizonInt = horizon.toInt()

        painter.paintSky(horizonInt, pose.yaw)
        fillFloorBase(horizonInt)

        painter.paintFloor(map, pose, horizon, horizonInt)
        val scene = Raycaster.castScene(map, pose, viewWidth, viewHeight, horizon)
        painter.paintWalls(scene)
        painter.paintSprites(pose, horizon, mobs, projectiles, scene.wallDistances)

        if (!textureReady) {
            texture = Texture(pixmap)
            textureReady = true
        } else {
            texture.draw(pixmap, 0, 0)
        }
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
        return texture
    }

    fun dispose() {
        if (textureReady) {
            texture.dispose()
        }
        pixmap.dispose()
    }

    private fun fillFloorBase(horizonInt: Int) {
        pixmap.setColor(
            ((SceneRenderConfig.FLOOR_BASE_RGB shr 16) and 0xFF) / 255f,
            ((SceneRenderConfig.FLOOR_BASE_RGB shr 8) and 0xFF) / 255f,
            (SceneRenderConfig.FLOOR_BASE_RGB and 0xFF) / 255f,
            1f,
        )
        if (horizonInt < viewHeight) {
            pixmap.fillRectangle(0, horizonInt, viewWidth, viewHeight - horizonInt)
        }
    }
}
