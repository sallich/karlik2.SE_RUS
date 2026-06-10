package ru.course.roguelike.client

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer

internal object RoguelikeHudVitals {
    data class AmmoHudState(
        val ammo: Int,
        val maxAmmo: Int,
        val weaponName: String?,
        val weaponType: String?,
        val screenH: Float,
    )

    fun drawHpBar(shapeRenderer: ShapeRenderer, hp: Int, maxHp: Int, screenH: Float) {
        val barX = 12f
        val barY = screenH - 40f
        val barW = 180f
        val barH = 12f
        val fraction = if (maxHp > 0) hp.toFloat() / maxHp else 0f

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color(0.08f, 0.08f, 0.1f, 0.9f)
        shapeRenderer.rect(barX, barY, barW, barH)
        shapeRenderer.color = when {
            fraction <= 0.25f -> Color(0.9f, 0.15f, 0.12f, 0.95f)
            fraction <= 0.5f -> Color(0.95f, 0.55f, 0.1f, 0.95f)
            else -> Color(0.2f, 0.85f, 0.25f, 0.95f)
        }
        shapeRenderer.rect(barX, barY, barW * fraction.coerceIn(0f, 1f), barH)
        shapeRenderer.end()
    }

    fun drawHpLabel(batch: SpriteBatch, font: BitmapFont, hp: Int, maxHp: Int, screenH: Float) {
        val c = font.color.cpy()
        font.color = if (hp <= maxHp / 4) Color.SCARLET else Color.LIME
        font.draw(batch, "HP $hp / $maxHp", 12f, screenH - 28f)
        font.color = c
    }

    fun drawXpBar(shapeRenderer: ShapeRenderer, experience: Int, experienceToNextLevel: Int, screenH: Float) {
        val barX = 12f
        val barY = screenH - 68f
        val barW = 180f
        val barH = 10f
        val fraction = if (experienceToNextLevel > 0) experience.toFloat() / experienceToNextLevel else 0f

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color(0.08f, 0.08f, 0.1f, 0.9f)
        shapeRenderer.rect(barX, barY, barW, barH)
        shapeRenderer.color = Color(0.85f, 0.7f, 0.15f, 0.95f)
        shapeRenderer.rect(barX, barY, barW * fraction.coerceIn(0f, 1f), barH)
        shapeRenderer.end()
    }

    fun drawXpLabel(
        batch: SpriteBatch,
        font: BitmapFont,
        level: Int,
        experience: Int,
        experienceToNextLevel: Int,
        screenH: Float,
    ) {
        val c = font.color.cpy()
        font.color = Color.GOLD
        font.draw(batch, "LV $level  XP $experience / $experienceToNextLevel", 12f, screenH - 56f)
        font.color = c
    }

    fun drawAmmoBar(shapeRenderer: ShapeRenderer, ammo: Int, maxAmmo: Int, weaponType: String?, screenH: Float) {
        val barX = 12f
        val barY = screenH - 108f
        val barW = 220f
        val barH = 16f
        val fraction = if (maxAmmo > 0) ammo.toFloat() / maxAmmo else 0f

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color(0.05f, 0.05f, 0.07f, 0.92f)
        shapeRenderer.rect(barX - 4f, barY - 4f, barW + 8f, barH + 8f)
        shapeRenderer.color = Color(0.1f, 0.1f, 0.12f, 0.95f)
        shapeRenderer.rect(barX, barY, barW, barH)
        shapeRenderer.color = when {
            ammo <= 0 -> Color(0.85f, 0.1f, 0.1f, 0.98f)
            fraction <= 0.25f -> Color(0.95f, 0.45f, 0.1f, 0.98f)
            weaponType == "SHOTGUN" -> Color(0.9f, 0.35f, 0.15f, 0.98f)
            else -> Color(0.3f, 0.6f, 0.95f, 0.98f)
        }
        shapeRenderer.rect(barX, barY, barW * fraction.coerceIn(0f, 1f), barH)
        shapeRenderer.end()
    }

    fun drawAmmoLabel(batch: SpriteBatch, font: BitmapFont, layout: GlyphLayout, state: AmmoHudState) {
        val savedScale = font.data.scaleX
        font.data.setScale(1.4f)
        val c = font.color.cpy()
        font.color = when {
            state.ammo <= 0 -> Color.SCARLET
            state.ammo <= state.maxAmmo / 4 -> Color.ORANGE
            else -> Color.WHITE
        }
        val label = "${ammoTag(state.weaponName, state.weaponType)}  ${state.ammo} / ${state.maxAmmo}"
        layout.setText(font, label)
        font.draw(batch, label, 12f, state.screenH - 82f)
        font.color = c
        font.data.setScale(savedScale)
    }

    private fun ammoTag(weaponName: String?, weaponType: String?): String = when (weaponType) {
        "SHOTGUN" -> "12ga"
        "PISTOL" -> "9mm"
        else -> weaponName.orEmpty()
    }
}
