package ru.course.roguelike.client

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import ru.course.roguelike.shared.dto.HotbarSnapshot
import ru.course.roguelike.shared.dto.InventorySnapshot
import ru.course.roguelike.shared.engine.CollisionDebug
import ru.course.roguelike.shared.model.PlayerPose

class RoguelikeHud(
    private val batch: SpriteBatch,
    private val font: BitmapFont,
    private val shapeRenderer: ShapeRenderer,
) {
    private val layout = GlyphLayout()

    @Suppress("LongParameterList")
    fun draw(
        statusLine: String,
        pose: PlayerPose?,
        debug: CollisionDebug?,
        showCollisionDebug: Boolean,
        onLava: Boolean = false,
        hp: Int = 0,
        maxHp: Int = 0,
        level: Int = 1,
        experience: Int = 0,
        experienceToNextLevel: Int = 100,
        ammo: Int = 0,
        maxAmmo: Int = 0,
        keysCollected: Int = 0,
        keysRequired: Int = 0,
        interactionHint: String? = null,
        equippedWeaponName: String? = null,
        equippedWeaponType: String? = null,
        hotbar: HotbarSnapshot? = null,
        inventory: InventorySnapshot? = null,
        inventoryOpen: Boolean = false,
        floorLevel: Int = 0,
        floorCount: Int = 1,
    ) {
        val screenH = Gdx.graphics.height.toFloat()
        val screenW = Gdx.graphics.width.toFloat()

        Gdx.gl.glEnable(GL20.GL_BLEND)
        if (maxHp > 0) RoguelikeHudVitals.drawHpBar(shapeRenderer, hp, maxHp, screenH)
        RoguelikeHudVitals.drawXpBar(shapeRenderer, experience, experienceToNextLevel, screenH)
        if (maxAmmo > 0) RoguelikeHudVitals.drawAmmoBar(shapeRenderer, ammo, maxAmmo, equippedWeaponType, screenH)

        batch.begin()
        font.draw(batch, statusLine, 12f, screenH - 12f)
        if (maxHp > 0) RoguelikeHudVitals.drawHpLabel(batch, font, hp, maxHp, screenH)
        RoguelikeHudVitals.drawXpLabel(batch, font, level, experience, experienceToNextLevel, screenH)
        if (maxAmmo > 0) {
            RoguelikeHudVitals.drawAmmoLabel(
                batch,
                font,
                layout,
                RoguelikeHudVitals.AmmoHudState(ammo, maxAmmo, equippedWeaponName, equippedWeaponType, screenH),
            )
        }
        if (keysRequired > 0) drawKeys(keysCollected, keysRequired, screenH)
        if (floorCount > 1) drawFloor(floorLevel, floorCount, screenW)
        hotbar?.let { RoguelikeHudHotbar.draw(batch, font, it, inventory, inventoryOpen) }
        interactionHint?.let { drawInteractionHint(it, screenW) }
        if (onLava) drawLavaWarning(screenW, screenH)
        if (showCollisionDebug && debug != null && pose != null) drawCollisionHud(debug, screenH)
        batch.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    private fun drawKeys(collected: Int, required: Int, screenH: Float) {
        val c = font.color.cpy()
        font.color = if (collected >= required) Color.GOLD else Color.WHITE
        font.draw(batch, "Keys $collected / $required", 12f, screenH - 132f)
        font.color = c
    }

    private fun drawFloor(floorLevel: Int, floorCount: Int, screenW: Float) {
        val c = font.color.cpy()
        font.color = Color.CYAN
        font.draw(batch, "Floor ${floorLevel + 1}/$floorCount", screenW - 120f, Gdx.graphics.height - 12f)
        font.color = c
    }

    private fun drawInteractionHint(hint: String, screenW: Float) {
        val c = font.color.cpy()
        font.color = Color.GOLD
        font.draw(batch, hint, screenW / 2f - 120f, 120f)
        font.color = c
    }

    private fun drawLavaWarning(screenW: Float, screenH: Float) {
        font.color = Color.SCARLET
        font.draw(batch, "LAVA!", screenW / 2f - 24f, screenH / 2f)
        font.color = Color.WHITE
    }

    private fun drawCollisionHud(debug: CollisionDebug, screenH: Float) {
        font.draw(batch, "blocked=${debug.blocked}", 12f, screenH - 156f)
    }
}
