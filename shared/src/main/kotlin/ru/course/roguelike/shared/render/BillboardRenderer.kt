package ru.course.roguelike.shared.render

import ru.course.roguelike.shared.model.FpsConstants
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.model.WorldVertical
import ru.course.roguelike.shared.model.wallHeight
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Проекция спрайтов-billboard в raycast-сцену.
 */
object BillboardRenderer {
    enum class SpriteTexture {
        PLAYER,
        MELEE,
        RANGED,
        BOSS,
        BLAST,
        KEY,
        ITEM_HEALTH,
        ITEM_EXPERIENCE,
        ITEM_WEAPON,
        ITEM_WEAPON_PISTOL,
        ITEM_WEAPON_SHOTGUN,
        ITEM_AMMO,
        ITEM_AMMO_PISTOL,
        ITEM_AMMO_SHOTGUN,
        COLOR_FALLBACK,
    }

    data class Sprite(
        val worldX: Float,
        val worldY: Float,
        val texture: SpriteTexture = SpriteTexture.COLOR_FALLBACK,
        val colorRgb: Int = 0xFFFFFF,
        val sizeScale: Float = 1f,
        /** Мировая Z (ноги/центр); 0 = пол яруса. */
        val worldZ: Float = 0f,
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
        val worldZ: Float = 0f,
    )

    fun projectSprites(
        pose: PlayerPose,
        screenWidth: Int,
        screenHeight: Int,
        pitchHorizonY: Float,
        sprites: List<Sprite>,
        wallDistances: FloatArray? = null,
        wallMeta: Array<Raycaster.WallColumnMeta>? = null,
        viewerHeightAboveFloor: Float = 0f,
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
                    pitchHorizonY,
                    viewerHeightAboveFloor,
                    sprite,
                    dirX,
                    dirY,
                    planeX,
                    planeY,
                    wallDistances,
                    wallMeta,
                )
            }
            .sortedByDescending { it.distance }
    }

    @Suppress("LongParameterList")
    private fun projectOne(
        pose: PlayerPose,
        screenWidth: Int,
        screenHeight: Int,
        pitchHorizonY: Float,
        viewerHeightAboveFloor: Float,
        sprite: Sprite,
        dirX: Float,
        dirY: Float,
        planeX: Float,
        planeY: Float,
        wallDistances: FloatArray?,
        wallMeta: Array<Raycaster.WallColumnMeta>?,
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
        val (drawStartY, drawEndY) = CameraProjection.projectSpriteSpan(
            pitchHorizonY,
            spriteHeight,
            screenHeight,
            transformY,
            sprite.worldZ,
            viewerHeightAboveFloor,
        )
        val drawStartX = (spriteScreenX - spriteWidth / 2).coerceIn(0, screenWidth - 1)
        val drawEndX = (spriteScreenX + spriteWidth / 2).coerceIn(drawStartX + 1, screenWidth)

        if (
            wallDistances != null &&
            isFullyOccludedByWall(
                drawStartX,
                drawStartY,
                drawEndX,
                drawEndY,
                transformY,
                wallDistances,
                wallMeta,
                pitchHorizonY,
                screenHeight,
                viewerHeightAboveFloor,
                sprite.worldZ,
            )
        ) {
            return null
        }

        return DrawCommand(
            screenX = spriteScreenX,
            left = drawStartX,
            right = drawEndX,
            top = drawStartY,
            bottom = drawEndY,
            texture = sprite.texture,
            colorRgb = fallbackColor(sprite.texture, sprite.colorRgb),
            distance = transformY,
            worldZ = sprite.worldZ,
        )
    }

    private fun fallbackColor(texture: SpriteTexture, default: Int): Int = when (texture) {
        SpriteTexture.KEY -> rgb(0xFF, 0xD7, 0x00)
        SpriteTexture.ITEM_HEALTH -> rgb(0xFF, 0x3B, 0x3B)
        SpriteTexture.ITEM_EXPERIENCE -> rgb(0x4C, 0xD9, 0x64)
        SpriteTexture.ITEM_WEAPON -> rgb(0xC0, 0xC8, 0xD0)
        SpriteTexture.ITEM_WEAPON_PISTOL -> rgb(0x66, 0xAA, 0xFF)
        SpriteTexture.ITEM_WEAPON_SHOTGUN -> rgb(0xCC, 0x44, 0x33)
        SpriteTexture.ITEM_AMMO -> rgb(0xFF, 0xB0, 0x30)
        SpriteTexture.ITEM_AMMO_PISTOL -> rgb(0x55, 0xAA, 0xFF)
        SpriteTexture.ITEM_AMMO_SHOTGUN -> rgb(0xFF, 0x77, 0x22)
        else -> default
    }

    /** Спрайт-текстура для предмета данного вида. */
    fun itemTexture(kind: ru.course.roguelike.shared.model.ItemKind): SpriteTexture = when (kind) {
        ru.course.roguelike.shared.model.ItemKind.HEALTH -> SpriteTexture.ITEM_HEALTH
        ru.course.roguelike.shared.model.ItemKind.EXPERIENCE -> SpriteTexture.ITEM_EXPERIENCE
        ru.course.roguelike.shared.model.ItemKind.WEAPON_PISTOL -> SpriteTexture.ITEM_WEAPON_PISTOL
        ru.course.roguelike.shared.model.ItemKind.WEAPON_SHOTGUN -> SpriteTexture.ITEM_WEAPON_SHOTGUN
        ru.course.roguelike.shared.model.ItemKind.AMMO_PISTOL -> SpriteTexture.ITEM_AMMO_PISTOL
        ru.course.roguelike.shared.model.ItemKind.AMMO_SHOTGUN -> SpriteTexture.ITEM_AMMO_SHOTGUN
    }

    fun itemSizeScale(kind: ru.course.roguelike.shared.model.ItemKind): Float = when (kind) {
        ru.course.roguelike.shared.model.ItemKind.WEAPON_SHOTGUN -> 1.25f
        ru.course.roguelike.shared.model.ItemKind.WEAPON_PISTOL -> 0.9f
        else -> 1f
    }

    /** true, если спрайт целиком за стеной (ни один столбец/строка не видна). */
    private fun isFullyOccludedByWall(
        drawStartX: Int,
        drawStartY: Int,
        drawEndX: Int,
        drawEndY: Int,
        spriteDistance: Float,
        wallDistances: FloatArray,
        wallMeta: Array<Raycaster.WallColumnMeta>?,
        pitchHorizonY: Float,
        screenHeight: Int,
        viewerHeightAboveFloor: Float,
        spriteWorldZ: Float,
    ): Boolean {
        if (wallDistances.isEmpty()) return false
        val left = drawStartX.coerceIn(0, wallDistances.size - 1)
        val right = (drawEndX - 1).coerceIn(left, wallDistances.size - 1)
        val top = drawStartY.coerceIn(0, screenHeight - 1)
        val bottom = drawEndY.coerceIn(top + 1, screenHeight)
        for (col in left..right) {
            val meta = wallMeta?.getOrNull(col)
            for (row in top until bottom) {
                val effective = effectiveWallDistanceForSpriteRow(
                    row,
                    spriteDistance,
                    wallDistances[col],
                    meta,
                    pitchHorizonY,
                    screenHeight,
                    viewerHeightAboveFloor,
                    spriteWorldZ,
                )
                if (spriteDistance <= effective + SPRITE_DEPTH_EPSILON) return false
            }
        }
        return true
    }

    fun isColumnOccluded(spriteDistance: Float, wallDistance: Float): Boolean =
        spriteDistance > wallDistance + SPRITE_DEPTH_EPSILON

    /**
     * Дистанция стены для проверки видимости спрайта в экранной строке [screenRow].
     * Короткие колонны закрывают только нижнюю часть — выше макушки видно то, что за ними.
     * Летающие мобы выше колонны не блокируются передним столбом.
     */
    fun effectiveWallDistanceForSpriteRow(
        screenRow: Int,
        spriteDistance: Float,
        wallDistance: Float,
        wallMeta: Raycaster.WallColumnMeta?,
        pitchHorizonY: Float,
        screenHeight: Int,
        viewerHeightAboveFloor: Float,
        spriteWorldZ: Float,
    ): Float {
        val meta = wallMeta ?: return wallDistance
        val tile = meta.tile
        if (tile == TileType.ROOM_DOOR || tile == TileType.ROOM_SEAL || tile == TileType.WALL) {
            return wallDistance - DOOR_OCCLUSION_BIAS
        }
        if (tile != TileType.COLUMN) return wallDistance

        val columnTopZ = WorldVertical.COLUMN_HEIGHT
        if (spriteWorldZ > columnTopZ + 0.02f) {
            return meta.backDistance ?: spriteDistance + SPRITE_DEPTH_EPSILON * 2f
        }

        val backDist = meta.backDistance ?: return wallDistance
        val lineHeight = screenHeight / wallDistance.coerceAtLeast(0.05f)
        val (columnTopScreenY, _) = CameraProjection.projectWallSpan(
            pitchHorizonY = pitchHorizonY,
            lineHeight = lineHeight,
            wallHeight = tile.wallHeight(),
            screenHeight = screenHeight,
            perpDistance = wallDistance,
            viewerHeightAboveFloor = viewerHeightAboveFloor,
        )
        return if (screenRow < columnTopScreenY) backDist else wallDistance
    }

    private const val SPRITE_DEPTH_EPSILON = 0.05f
    private const val DOOR_OCCLUSION_BIAS = 0.12f

    fun mobColor(kind: ru.course.roguelike.shared.model.MobKind): Int = when (kind) {
        ru.course.roguelike.shared.model.MobKind.MELEE -> rgb(0xFF, 0xD7, 0x00)
        ru.course.roguelike.shared.model.MobKind.RANGED -> rgb(0x33, 0x99, 0xFF)
        ru.course.roguelike.shared.model.MobKind.LLM_GUARD -> rgb(0xCC, 0x33, 0xFF)
    }

    fun projectileColor(): Int = rgb(0xFF, 0x22, 0x22)

    private fun rgb(r: Int, g: Int, b: Int): Int = (r shl 16) or (g shl 8) or b
}
