package ru.course.roguelike.client.render

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import ru.course.roguelike.shared.render.CameraProjection
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

    fun render(scene: ViewportRenderScene): Texture {
        val pitchHorizon = SceneRenderConfig.horizonY(viewHeight, scene.pose.pitch)
        val viewerHeight = CameraProjection.viewerHeight(scene.pose.height)
        val horizonInt = kotlin.math.ceil(pitchHorizon).toInt().coerceIn(0, viewHeight)

        painter.beginFrame()
        painter.paintSky(horizonInt, scene.pose.yaw)
        painter.fillFloorBase(horizonInt)
        painter.paintFloor(scene.map, scene.pose, pitchHorizon, horizonInt, scene.pose.height)
        val cast = Raycaster.castScene(
            scene.map,
            scene.pose,
            viewWidth,
            viewHeight,
            pitchHorizonY = pitchHorizon,
            floorLevel = scene.floorLevel,
            viewerHeight = viewerHeight,
        )
        painter.paintWalls(cast, pitchHorizon, viewerHeight, scene.doorMarkers)
        painter.paintHorizontalTops(cast, pitchHorizon, viewerHeight)
        painter.paintWallCaps(cast)
        painter.paintSprites(
            scene.pose,
            pitchHorizon,
            viewerHeight,
            scene.mobs,
            scene.projectiles,
            scene.keyPickups,
            scene.items,
            scene.agentPose,
            cast.wallDistances,
            cast.wallMeta,
        )

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
