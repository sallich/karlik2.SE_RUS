package ru.course.roguelike.shared.render

import ru.course.roguelike.shared.model.FpsConstants
import ru.course.roguelike.shared.model.PlayerPose
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Проекция спрайтов-billboard в raycast-сцену.
 */
object BillboardRenderer {
    enum class SpriteTexture {
        MELEE,
        RANGED,
        BLAST,
        COLOR_FALLBACK,
    }

    data class Sprite(
        val worldX: Float,
        val worldY: Float,
        val texture: SpriteTexture = SpriteTexture.COLOR_FALLBACK,
        val colorRgb: Int = 0xFFFFFF,
        val sizeScale: Float = 1f,
    )

    data class DrawCommand(
        val screenX: Int,
        val left: Int,
        val right: Int,
        val top: Int,
        val bottom: Int,
        val texture: SpriteTexture,
        val colorRgb: Int,
        val distance: Float,
    )

    fun projectSprites(
        pose: PlayerPose,
        screenWidth: Int,
        screenHeight: Int,
        horizonY: Float,
        sprites: List<Sprite>,
        wallDistances: FloatArray? = null,
    ): List<DrawCommand> {
        if (sprites.isEmpty() || screenWidth <= 0) return emptyList()

        val yaw = pose.yaw
        val halfFov = FpsConstants.DEFAULT_FOV_RADIANS / 2f
        val planeX = -sin(yaw) * tan(halfFov)
        val planeY = cos(yaw) * tan(halfFov)
        val dirX = cos(yaw)
        val dirY = sin(yaw)

        return sprites
            .mapNotNull { sprite ->
                projectOne(
                    pose,
                    screenWidth,
                    screenHeight,
                    horizonY,
                    sprite,
                    dirX,
                    dirY,
                    planeX,
                    planeY,
                    wallDistances,
                )
            }
            .sortedByDescending { it.distance }
    }

    @Suppress("LongParameterList")
    private fun projectOne(
        pose: PlayerPose,
        screenWidth: Int,
        screenHeight: Int,
        horizonY: Float,
        sprite: Sprite,
        dirX: Float,
        dirY: Float,
        planeX: Float,
        planeY: Float,
        wallDistances: FloatArray?,
    ): DrawCommand? {
        val spriteX = sprite.worldX - pose.x
        val spriteY = sprite.worldY - pose.y
        val invDet = 1f / (planeX * dirY - dirX * planeY)
        val transformX = invDet * (dirY * spriteX - dirX * spriteY)
        val transformY = invDet * (-planeY * spriteX + planeX * spriteY)
        if (transformY <= 0.05f) return null

        val spriteScreenX = (screenWidth / 2f * (1f + transformX / transformY)).toInt()
        val spriteHeight = abs((screenHeight / transformY) * sprite.sizeScale).toInt().coerceAtLeast(2)
        val spriteWidth = spriteHeight
        val halfH = spriteHeight / 2
        val drawStartY = (horizonY - halfH).toInt().coerceIn(0, screenHeight - 1)
        val drawEndY = (horizonY + halfH).toInt().coerceIn(drawStartY + 1, screenHeight)
        val drawStartX = (spriteScreenX - spriteWidth / 2).coerceIn(0, screenWidth - 1)
        val drawEndX = (spriteScreenX + spriteWidth / 2).coerceIn(drawStartX + 1, screenWidth)

        if (wallDistances != null && isOccludedByWall(spriteScreenX, transformY, wallDistances)) {
            return null
        }

        return DrawCommand(
            screenX = spriteScreenX,
            left = drawStartX,
            right = drawEndX,
            top = drawStartY,
            bottom = drawEndY,
            texture = sprite.texture,
            colorRgb = sprite.colorRgb,
            distance = transformY,
        )
    }

    private fun isOccludedByWall(spriteScreenX: Int, spriteDistance: Float, wallDistances: FloatArray): Boolean {
        val col = spriteScreenX.coerceIn(0, wallDistances.size - 1)
        return spriteDistance > wallDistances[col] + 0.05f
    }

    fun mobColor(kind: ru.course.roguelike.shared.model.MobKind): Int = when (kind) {
        ru.course.roguelike.shared.model.MobKind.MELEE -> rgb(0xFF, 0xD7, 0x00)
        ru.course.roguelike.shared.model.MobKind.RANGED -> rgb(0x33, 0x99, 0xFF)
    }

    fun projectileColor(): Int = rgb(0xFF, 0x22, 0x22)

    private fun rgb(r: Int, g: Int, b: Int): Int = (r shl 16) or (g shl 8) or b
}
