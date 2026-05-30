package ru.course.roguelike.client

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
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
    ) {
        batch.begin()
        font.draw(batch, statusLine, 12f, Gdx.graphics.height - 12f)
        pose?.let { drawPoseHud(it, fpsSmoothed) }
        if (maxHp > 0) {
            drawHp(hp, maxHp)
        }
        if (onLava) {
            drawLavaWarning()
        }
        if (showCollisionDebug && debug != null) {
            drawCollisionHud(debug)
        }
        batch.end()
    }

    /** HP игрока (с сервера) — красный текст при низком здоровье, чтобы был виден урон от лавы. */
    private fun drawHp(hp: Int, maxHp: Int) {
        val previous = font.color.cpy()
        font.color = if (hp <= maxHp / 4) Color.SCARLET else Color.LIME
        font.draw(batch, "HP $hp / $maxHp", 12f, Gdx.graphics.height - 84f)
        font.color = previous
    }

    private fun drawLavaWarning() {
        font.color = Color.SCARLET
        font.draw(batch, "!! LAVA - taking damage !!", Gdx.graphics.width / 2f - 90f, Gdx.graphics.height / 2f - 40f)
        font.color = Color.WHITE
    }

    private fun drawPoseHud(pose: PlayerPose, fpsSmoothed: Float) {
        val height = Gdx.graphics.height
        font.draw(
            batch,
            "fps~${fpsSmoothed.toInt()} | pos=${"%.1f".format(pose.x)},${"%.1f".format(pose.y)} " +
                "yaw=${"%.2f".format(pose.yaw)} pitch=${"%.2f".format(pose.pitch)}",
            12f,
            height - 36f,
        )
    }

    private fun drawCollisionHud(debug: CollisionDebug) {
        font.draw(
            batch,
            "collision: blocked=${debug.blocked} sweep=${"%.0f".format(debug.sweepFraction * 100)}% " +
                "hits=${debug.hitCells.size} | yellow=view/move green=fact orange=request",
            12f,
            Gdx.graphics.height - 60f,
        )
    }
}
