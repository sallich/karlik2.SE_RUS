package ru.course.roguelike.client.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import ru.course.roguelike.shared.render.AssetPaths
import ru.course.roguelike.shared.render.BillboardRenderer
import ru.course.roguelike.shared.render.RgbImageSampler

@Suppress("LongParameterList")
class GameTextures private constructor(
    val walls: RgbImageSampler,
    val floor: RgbImageSampler,
    val sky: RgbImageSampler,
    val lava: RgbImageSampler,
    val door: RgbImageSampler,
    val blast: RgbImageSampler,
    val keySprite: RgbImageSampler,
    val meleeMob: RgbImageSampler,
    val rangedMob: RgbImageSampler,
    val playerSprite: RgbImageSampler,
) {
    fun samplerFor(texture: BillboardRenderer.SpriteTexture): RgbImageSampler? = when (texture) {
        BillboardRenderer.SpriteTexture.PLAYER -> playerSprite
        BillboardRenderer.SpriteTexture.MELEE -> meleeMob
        BillboardRenderer.SpriteTexture.RANGED -> rangedMob
        BillboardRenderer.SpriteTexture.BLAST -> blast
        BillboardRenderer.SpriteTexture.KEY -> keySprite
        BillboardRenderer.SpriteTexture.ITEM_HEALTH,
        BillboardRenderer.SpriteTexture.ITEM_EXPERIENCE,
        BillboardRenderer.SpriteTexture.ITEM_WEAPON,
        BillboardRenderer.SpriteTexture.ITEM_WEAPON_PISTOL,
        BillboardRenderer.SpriteTexture.ITEM_WEAPON_SHOTGUN,
        BillboardRenderer.SpriteTexture.ITEM_AMMO,
        BillboardRenderer.SpriteTexture.ITEM_AMMO_PISTOL,
        BillboardRenderer.SpriteTexture.ITEM_AMMO_SHOTGUN,
        -> null
        BillboardRenderer.SpriteTexture.COLOR_FALLBACK -> null
    }

    fun usesChromaKey(texture: BillboardRenderer.SpriteTexture): Boolean = when (texture) {
        BillboardRenderer.SpriteTexture.MELEE,
        BillboardRenderer.SpriteTexture.RANGED,
        BillboardRenderer.SpriteTexture.PLAYER,
        BillboardRenderer.SpriteTexture.BLAST,
        BillboardRenderer.SpriteTexture.KEY,
        -> true
        BillboardRenderer.SpriteTexture.ITEM_HEALTH,
        BillboardRenderer.SpriteTexture.ITEM_EXPERIENCE,
        BillboardRenderer.SpriteTexture.ITEM_WEAPON,
        BillboardRenderer.SpriteTexture.ITEM_WEAPON_PISTOL,
        BillboardRenderer.SpriteTexture.ITEM_WEAPON_SHOTGUN,
        BillboardRenderer.SpriteTexture.ITEM_AMMO,
        BillboardRenderer.SpriteTexture.ITEM_AMMO_PISTOL,
        BillboardRenderer.SpriteTexture.ITEM_AMMO_SHOTGUN,
        BillboardRenderer.SpriteTexture.COLOR_FALLBACK,
        -> false
    }

    companion object {
        fun load(): GameTextures = GameTextures(
            walls = loadSampler(AssetPaths.WALLS, opaque = true),
            floor = loadSampler(AssetPaths.FLOOR, opaque = true),
            sky = loadSampler(AssetPaths.SKY, opaque = true),
            lava = loadSampler(AssetPaths.LAVA, opaque = true),
            door = loadSampler(AssetPaths.DOOR, opaque = true),
            blast = loadSampler(AssetPaths.BLAST, opaque = false),
            keySprite = loadSampler(AssetPaths.KEY, opaque = false),
            meleeMob = loadSampler(AssetPaths.MELEE_MOB, opaque = false),
            rangedMob = loadSampler(AssetPaths.RANGED_MOB, opaque = false),
            playerSprite = loadSampler(AssetPaths.PLAYER, opaque = false),
        )

        private fun loadSampler(path: String, opaque: Boolean): RgbImageSampler {
            val pixmap = Pixmap(Gdx.files.internal(path))
            val width = pixmap.width
            val height = pixmap.height
            val count = width * height
            val pixels = IntArray(count)
            val alphas = IntArray(count)
            for (index in 0 until count) {
                val x = index % width
                val y = index / width
                val sample = RgbImageSampler.fromLibGdxPixel(pixmap.getPixel(x, y))
                pixels[index] = sample.rgb
                alphas[index] = if (opaque) 255 else sample.alpha
            }
            pixmap.dispose()
            return RgbImageSampler(width, height, pixels, alphas)
        }
    }
}
