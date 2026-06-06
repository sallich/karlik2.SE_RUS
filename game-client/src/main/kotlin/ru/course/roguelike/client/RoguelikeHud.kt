package ru.course.roguelike.client

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import ru.course.roguelike.shared.dto.HotbarSnapshot
import ru.course.roguelike.shared.dto.InventoryItemSnapshot
import ru.course.roguelike.shared.dto.InventorySnapshot
import ru.course.roguelike.shared.engine.CollisionDebug
import ru.course.roguelike.shared.model.PlayerPose

class RoguelikeHud(
    private val batch: SpriteBatch,
    private val font: BitmapFont,
) {
    @Suppress("LongParameterList")
    fun draw(
        statusLine: String,
        pose: PlayerPose?,
        fpsSmoothed: Float,
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
        batch.begin()
        font.draw(batch, statusLine, 12f, Gdx.graphics.height - 12f)
        if (maxHp > 0) drawHp(hp, maxHp)
        if (floorCount > 1) drawFloor(floorLevel, floorCount)
        drawProgression(level, experience, experienceToNextLevel)
        if (maxAmmo > 0) drawAmmo(ammo, maxAmmo, equippedWeaponName, equippedWeaponType)
        if (keysRequired > 0) drawKeys(keysCollected, keysRequired)
        hotbar?.let { drawHotbarSlotNumbers(it, inventory, inventoryOpen) }
        interactionHint?.let { drawInteractionHint(it) }
        if (onLava) drawLavaWarning()
        if (showCollisionDebug && debug != null && pose != null) drawCollisionHud(debug)
        batch.end()
    }

    private fun drawHp(hp: Int, maxHp: Int) {
        val c = font.color.cpy()
        font.color = if (hp <= maxHp / 4) Color.SCARLET else Color.LIME
        font.draw(batch, "HP $hp / $maxHp", 12f, Gdx.graphics.height - 36f)
        font.color = c
    }

    private fun drawFloor(floorLevel: Int, floorCount: Int) {
        val c = font.color.cpy()
        font.color = Color.CYAN
        font.draw(batch, "Floor ${floorLevel + 1}/$floorCount", Gdx.graphics.width - 120f, Gdx.graphics.height - 12f)
        font.color = c
    }

    private fun drawProgression(level: Int, experience: Int, experienceToNextLevel: Int) {
        val c = font.color.cpy()
        font.color = Color.GOLD
        font.draw(batch, "LV $level  XP $experience/$experienceToNextLevel", 12f, Gdx.graphics.height - 60f)
        font.color = c
    }

    private fun drawAmmo(ammo: Int, maxAmmo: Int, weaponName: String?, weaponType: String?) {
        val c = font.color.cpy()
        font.color = if (ammo <= 0) Color.SCARLET else Color.ORANGE
        val tag = when (weaponType) {
            "SHOTGUN" -> "12ga"
            "PISTOL" -> "9mm"
            else -> weaponName.orEmpty()
        }
        font.draw(batch, "$tag  mag $ammo / $maxAmmo", 12f, Gdx.graphics.height - 84f)
        font.color = c
    }

    private fun drawKeys(collected: Int, required: Int) {
        val c = font.color.cpy()
        font.color = if (collected >= required) Color.GOLD else Color.WHITE
        font.draw(batch, "Keys $collected/$required", 12f, Gdx.graphics.height - 108f)
        font.color = c
    }

    private fun drawHotbarSlotNumbers(hotbar: HotbarSnapshot, inventory: InventorySnapshot?, inventoryOpen: Boolean) {
        val slotW = 64f
        val gap = 6f
        val totalW = hotbar.slots.size * slotW + (hotbar.slots.size - 1) * gap
        var x = (Gdx.graphics.width - totalW) / 2f + 4f
        val y = 68f
        val c = font.color.cpy()
        hotbar.slots.forEachIndexed { index, itemId ->
            val item = itemId?.let { id -> inventory?.items?.find { it.id == id } }
            val suffix = item?.let { weaponLabel(it) }.orEmpty().ifEmpty { "—" }
            font.color = if (index == hotbar.selectedSlot) Color.GOLD else Color.LIGHT_GRAY
            font.draw(batch, "${index + 1}: $suffix", x, y)
            x += slotW + gap
        }
        if (inventoryOpen) {
            font.color = Color(0.75f, 0.75f, 0.8f, 1f)
            font.draw(batch, "Tab+1/2 — назначить оружие в слот", 12f, Gdx.graphics.height / 2f - 8f)
        }
        font.color = c
    }

    private fun weaponLabel(item: InventoryItemSnapshot): String = when (item.type) {
        "PISTOL" -> "Pistol"
        "SHOTGUN" -> "Shotgun"
        else -> item.displayName.take(6)
    }

    private fun drawInteractionHint(hint: String) {
        val c = font.color.cpy()
        font.color = Color.GOLD
        font.draw(batch, hint, Gdx.graphics.width / 2f - 120f, 120f)
        font.color = c
    }

    private fun drawLavaWarning() {
        font.color = Color.SCARLET
        font.draw(batch, "LAVA!", Gdx.graphics.width / 2f - 24f, Gdx.graphics.height / 2f)
        font.color = Color.WHITE
    }

    private fun drawCollisionHud(debug: CollisionDebug) {
        font.draw(batch, "blocked=${debug.blocked}", 12f, Gdx.graphics.height - 132f)
    }
}
