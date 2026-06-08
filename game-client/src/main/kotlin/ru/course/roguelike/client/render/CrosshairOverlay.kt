package ru.course.roguelike.client.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4

/** Прицел в центре экрана — точка прицеливания (yaw + pitch). */
class CrosshairOverlay(
    private val shapeRenderer: ShapeRenderer,
) {
    fun render(screenWidth: Float, screenHeight: Float) {
        val cx = screenWidth * 0.5f
        val cy = screenHeight * 0.5f
        val arm = 7f
        val gap = 3f
        val dot = 1.6f

        shapeRenderer.projectionMatrix = Matrix4().setToOrtho2D(0f, 0f, screenWidth, screenHeight)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

        shapeRenderer.color = Color(1f, 1f, 1f, 0.92f)
        shapeRenderer.rect(cx - dot * 0.5f, cy - dot * 0.5f, dot, dot)

        shapeRenderer.color = Color(0.1f, 0.1f, 0.1f, 0.55f)
        shapeRenderer.rect(cx - arm, cy - 0.6f, arm - gap, 1.2f)
        shapeRenderer.rect(cx + gap, cy - 0.6f, arm - gap, 1.2f)
        shapeRenderer.rect(cx - 0.6f, cy - arm, 1.2f, arm - gap)
        shapeRenderer.rect(cx - 0.6f, cy + gap, 1.2f, arm - gap)

        shapeRenderer.color = Color(0.2f, 1f, 0.35f, 0.95f)
        shapeRenderer.rect(cx - arm + 1f, cy - 0.4f, arm - gap - 1f, 0.8f)
        shapeRenderer.rect(cx + gap, cy - 0.4f, arm - gap - 1f, 0.8f)
        shapeRenderer.rect(cx - 0.4f, cy - arm + 1f, 0.8f, arm - gap - 1f)
        shapeRenderer.rect(cx - 0.4f, cy + gap, 0.8f, arm - gap - 1f)

        shapeRenderer.end()
    }
}
