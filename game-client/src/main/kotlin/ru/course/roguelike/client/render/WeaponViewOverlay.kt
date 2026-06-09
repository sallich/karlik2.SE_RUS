package ru.course.roguelike.client.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import ru.course.roguelike.shared.render.AssetPaths

class WeaponViewOverlay {
    private var pistolTexture: Texture? = null
    private var shotgunTexture: Texture? = null

    fun load() {
        pistolTexture = loadTexture(AssetPaths.PISTOL_FIRST)
        shotgunTexture = loadTexture(AssetPaths.SHOTGUN_FIRST)
    }

    fun render(batch: SpriteBatch, screenW: Float, screenH: Float, weaponType: String?) {
        val texture = when (weaponType) {
            "PISTOL" -> pistolTexture
            "SHOTGUN" -> shotgunTexture
            else -> return
        } ?: return

        val aspect = texture.width.toFloat() / texture.height.coerceAtLeast(1)
        val drawH = screenH * 0.32f
        val drawW = drawH * aspect
        val x = (screenW - drawW) / 2f
        val y = 0f

        Gdx.gl.glEnable(GL20.GL_BLEND)
        batch.begin()
        batch.draw(texture, x, y, drawW, drawH)
        batch.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    fun dispose() {
        pistolTexture?.dispose()
        shotgunTexture?.dispose()
        pistolTexture = null
        shotgunTexture = null
    }

    private fun loadTexture(path: String): Texture {
        val pixmap = Pixmap(Gdx.files.internal(path))
        val texture = Texture(pixmap)
        pixmap.dispose()
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        return texture
    }
}
