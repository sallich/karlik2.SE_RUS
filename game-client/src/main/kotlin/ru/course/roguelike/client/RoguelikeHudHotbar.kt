package ru.course.roguelike.client

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import ru.course.roguelike.shared.dto.HotbarSnapshot
import ru.course.roguelike.shared.dto.InventoryItemSnapshot
import ru.course.roguelike.shared.dto.InventorySnapshot

internal object RoguelikeHudHotbar {
    fun draw(
        batch: SpriteBatch,
        font: BitmapFont,
        hotbar: HotbarSnapshot,
        inventory: InventorySnapshot?,
        inventoryOpen: Boolean,
    ) {
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
}
