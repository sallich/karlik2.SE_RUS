package ru.course.roguelike.client.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import ru.course.roguelike.shared.dto.RoomClearTimerSnapshot

class RoomClearTimerOverlay(
    private val shapeRenderer: ShapeRenderer,
    private val batch: SpriteBatch,
    private val font: BitmapFont,
) {
    private val layout = GlyphLayout()

    fun render(
        screenW: Float,
        screenH: Float,
        timer: RoomClearTimerSnapshot?,
        timerReceivedAtMs: Long,
    ) {
        if (timer == null) return

        val barW = 340f
        val barH = 18f
        val panelH = 56f
        val x = (screenW - barW) / 2f
        val barY = screenH - 52f
        val panelY = screenH - panelH - 8f

        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color(0.02f, 0.02f, 0.04f, 0.92f)
        shapeRenderer.rect(x - 16f, panelY, barW + 32f, panelH)
        shapeRenderer.color = Color(0.85f, 0.65f, 0.1f, 0.9f)
        shapeRenderer.rect(x - 16f, panelY, barW + 32f, 3f)

        if (timer.reinforcementsTriggered) {
            shapeRenderer.color = Color(0.55f, 0.08f, 0.08f, 0.98f)
            shapeRenderer.rect(x, barY, barW, barH)
        } else {
            val remaining = effectiveRemainingMs(timer, timerReceivedAtMs)
            val fraction = if (timer.totalMs > 0) {
                remaining.toFloat() / timer.totalMs.toFloat()
            } else {
                0f
            }.coerceIn(0f, 1f)
            shapeRenderer.color = Color(0.1f, 0.1f, 0.12f, 0.98f)
            shapeRenderer.rect(x, barY, barW, barH)
            shapeRenderer.color = barColor(fraction)
            shapeRenderer.rect(x, barY, barW * fraction, barH)
        }
        shapeRenderer.end()

        val savedScale = font.data.scaleX
        font.data.setScale(1.35f)
        batch.begin()
        val label = if (timer.reinforcementsTriggered) {
            "REINFORCEMENTS INCOMING"
        } else {
            val remaining = effectiveRemainingMs(timer, timerReceivedAtMs)
            "CLEAR ROOM  ${formatTime(remaining)}"
        }
        font.color = if (timer.reinforcementsTriggered) Color(1f, 0.35f, 0.35f, 1f) else Color(1f, 0.95f, 0.75f, 1f)
        layout.setText(font, label)
        font.draw(batch, label, (screenW - layout.width) / 2f, screenH - 18f)
        font.color = Color.WHITE
        font.data.setScale(savedScale)
        batch.end()
        Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND)
    }

    private fun effectiveRemainingMs(timer: RoomClearTimerSnapshot, receivedAtMs: Long): Long {
        if (timer.reinforcementsTriggered) return 0L
        val drift = System.currentTimeMillis() - receivedAtMs
        return (timer.remainingMs - drift).coerceAtLeast(0L)
    }

    private fun barColor(fraction: Float): Color = when {
        fraction > 0.5f -> Color(0.15f, 0.8f, 0.3f, 0.98f)
        fraction > 0.25f -> Color(0.95f, 0.78f, 0.1f, 0.98f)
        else -> Color(0.95f, 0.18f, 0.12f, 0.98f)
    }

    private fun formatTime(remainingMs: Long): String {
        val totalSec = (remainingMs + 999) / 1000
        val minutes = totalSec / 60
        val seconds = totalSec % 60
        return "%d:%02d".format(minutes, seconds)
    }
}
