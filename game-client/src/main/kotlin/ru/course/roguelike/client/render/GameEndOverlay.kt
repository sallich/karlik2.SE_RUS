package ru.course.roguelike.client.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import ru.course.roguelike.shared.model.SessionPhase

class GameEndOverlay(
    private val batch: SpriteBatch,
    private val font: BitmapFont,
    private val shapeRenderer: ShapeRenderer,
) {
    private val layout = GlyphLayout()

    fun render(phase: SessionPhase) {
        val screenW = Gdx.graphics.width.toFloat()
        val screenH = Gdx.graphics.height.toFloat()

        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color(0f, 0f, 0f, 0.72f)
        shapeRenderer.rect(0f, 0f, screenW, screenH)
        shapeRenderer.end()

        val (title, subtitle, accent) = when (phase) {
            SessionPhase.GAME_OVER -> Triple("YOU DIED", "HP reached zero", Color.SCARLET)
            SessionPhase.LEVEL_COMPLETE -> Triple(
                "LEVEL COMPLETE",
                "All keys inserted into the exit gate",
                Color.GOLD,
            )
            else -> return
        }

        batch.begin()
        font.color = accent
        layout.setText(font, title)
        font.draw(batch, title, (screenW - layout.width) / 2f, screenH * 0.58f)

        font.color = Color.LIGHT_GRAY
        layout.setText(font, subtitle)
        font.draw(batch, subtitle, (screenW - layout.width) / 2f, screenH * 0.52f)

        font.color = Color.WHITE
        val hint = "Press R — new run    Esc — release mouse"
        layout.setText(font, hint)
        font.draw(batch, hint, (screenW - layout.width) / 2f, screenH * 0.38f)
        font.color = Color.WHITE
        batch.end()
        Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND)
    }
}
