package ru.course.roguelike.client.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import ru.course.roguelike.shared.dto.HotbarSnapshot
import ru.course.roguelike.shared.dto.InventoryItemSnapshot
import ru.course.roguelike.shared.dto.InventorySnapshot

class InventoryUiOverlay(
    private val shapes: ShapeRenderer,
) {
    fun render(
        screenW: Float,
        screenH: Float,
        inventory: InventorySnapshot?,
        hotbar: HotbarSnapshot?,
        equippedWeaponType: String?,
        expanded: Boolean,
    ) {
        if (inventory == null || hotbar == null) return

        shapes.begin(ShapeRenderer.ShapeType.Filled)
        drawHotbar(screenW, hotbar, inventory, equippedWeaponType)
        if (expanded) drawInventoryPanel(screenH, inventory)
        shapes.end()

        shapes.begin(ShapeRenderer.ShapeType.Line)
        drawHotbarOutlines(screenW, hotbar)
        if (expanded) drawInventoryOutlines(screenH, inventory)
        shapes.end()
    }

    private fun drawHotbar(
        screenW: Float,
        hotbar: HotbarSnapshot,
        inventory: InventorySnapshot,
        equippedWeaponType: String?,
    ) {
        val slotW = 64f
        val slotH = 52f
        val gap = 6f
        val totalW = hotbar.slots.size * slotW + (hotbar.slots.size - 1) * gap
        var x = (screenW - totalW) / 2f
        val y = 20f

        hotbar.slots.forEachIndexed { index, itemId ->
            val selected = index == hotbar.selectedSlot
            shapes.color = if (selected) Color(0.28f, 0.24f, 0.06f, 0.95f) else Color(0.07f, 0.07f, 0.09f, 0.9f)
            shapes.rect(x, y, slotW, slotH)
            itemId?.let { id ->
                inventory.items.find { it.id == id }?.let { item ->
                    drawItemIcon(x + 5f, y + 6f, slotW - 10f, slotH - 12f, item, equippedWeaponType)
                }
            }
            x += slotW + gap
        }
    }

    private fun drawHotbarOutlines(screenW: Float, hotbar: HotbarSnapshot) {
        val slotW = 64f
        val slotH = 52f
        val gap = 6f
        val totalW = hotbar.slots.size * slotW + (hotbar.slots.size - 1) * gap
        var x = (screenW - totalW) / 2f
        val y = 20f
        hotbar.slots.forEachIndexed { index, _ ->
            shapes.color = if (index == hotbar.selectedSlot) Color.GOLD else Color(0.4f, 0.4f, 0.45f, 1f)
            shapes.rect(x, y, slotW, slotH)
            x += slotW + gap
        }
    }

    /** Сетка инвентаря — слева по центру экрана. */
    private fun drawInventoryPanel(screenH: Float, inventory: InventorySnapshot) {
        val cell = 38f
        val pad = 10f
        val panelW = inventory.columns * cell + pad * 2
        val panelH = inventory.rows * cell + pad * 2
        val originX = 12f
        val originY = (screenH - panelH) / 2f

        shapes.color = Color(0.04f, 0.05f, 0.07f, 0.94f)
        shapes.rect(originX, originY, panelW, panelH)

        for (row in 0 until inventory.rows) {
            for (col in 0 until inventory.columns) {
                val cx = originX + pad + col * cell
                val cy = originY + pad + (inventory.rows - 1 - row) * cell
                shapes.color = Color(0.11f, 0.12f, 0.15f, 1f)
                shapes.rect(cx, cy, cell - 2f, cell - 2f)
            }
        }
        for (item in inventory.items) {
            val ix = originX + pad + item.col * cell
            val iy = originY + pad + (inventory.rows - item.row - item.height) * cell
            shapes.color = itemFillColor(item.type)
            shapes.rect(ix, iy, item.width * cell - 2f, item.height * cell - 2f)
        }
    }

    private fun drawInventoryOutlines(screenH: Float, inventory: InventorySnapshot) {
        val cell = 38f
        val pad = 10f
        val panelW = inventory.columns * cell + pad * 2
        val panelH = inventory.rows * cell + pad * 2
        val originX = 12f
        val originY = (screenH - panelH) / 2f
        shapes.color = Color(0.5f, 0.55f, 0.65f, 1f)
        shapes.rect(originX, originY, panelW, panelH)
    }

    private fun drawItemIcon(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        item: InventoryItemSnapshot,
        equipped: String?,
    ) {
        shapes.color = itemFillColor(item.type)
        shapes.rect(x, y, w, h)
        if (item.type == equipped) {
            shapes.color = Color(1f, 0.85f, 0.2f, 0.4f)
            shapes.rect(x, y, w, h)
        }
    }

    private fun itemFillColor(type: String): Color = when (type) {
        "PISTOL" -> Color(0.35f, 0.55f, 0.95f, 1f)
        "SHOTGUN" -> Color(0.85f, 0.28f, 0.22f, 1f)
        "PISTOL_AMMO" -> Color(0.4f, 0.65f, 1f, 1f)
        "SHOTGUN_AMMO" -> Color(1f, 0.55f, 0.15f, 1f)
        "HEALTH_KIT" -> Color(0.25f, 0.85f, 0.35f, 1f)
        else -> Color(0.5f, 0.5f, 0.55f, 1f)
    }
}
