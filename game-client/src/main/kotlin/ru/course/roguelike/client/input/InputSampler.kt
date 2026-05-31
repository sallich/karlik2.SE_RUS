package ru.course.roguelike.client.input

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Cursor
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.model.FpsConstants

object InputSampler {
    private const val MOUSE_YAW_PER_PIXEL = 0.0025f
    private const val MOUSE_PITCH_PER_PIXEL = 0.0025f

    data class Sample(val input: InputSyncRequest)

    fun sample(deltaSec: Float): Sample {
        val deltaMs = (deltaSec * 1000f).toInt().coerceIn(1, 100)
        val frameScale = (deltaSec * 60f).coerceIn(0.25f, 2.5f)

        val mouseLook = Gdx.input.isCursorCatched ||
            Gdx.input.isButtonPressed(Input.Buttons.RIGHT)
        val yawDelta = if (mouseLook) {
            FpsConstants.MOUSE_YAW_SIGN * Gdx.input.deltaX * MOUSE_YAW_PER_PIXEL * frameScale
        } else {
            0f
        }
        val pitchDelta = if (mouseLook) {
            FpsConstants.MOUSE_PITCH_SIGN * Gdx.input.deltaY * MOUSE_PITCH_PER_PIXEL * frameScale
        } else {
            0f
        }

        return Sample(
            input = InputSyncRequest(
                forward = pressed(Input.Keys.W),
                backward = pressed(Input.Keys.S),
                strafeLeft = pressed(Input.Keys.A),
                strafeRight = pressed(Input.Keys.D),
                turnLeft = pressed(Input.Keys.LEFT),
                turnRight = pressed(Input.Keys.RIGHT),
                lookUp = pressed(Input.Keys.UP, Input.Keys.Q),
                lookDown = pressed(Input.Keys.DOWN),
                yawDelta = yawDelta,
                pitchDelta = pitchDelta,
                deltaMs = deltaMs,
                attack = Gdx.input.isButtonJustPressed(Input.Buttons.LEFT) ||
                    Gdx.input.isKeyJustPressed(Input.Keys.SPACE),
                interact = Gdx.input.isKeyJustPressed(Input.Keys.E),
            ),
        )
    }

    fun shouldSync(accumulatedSec: Float): Boolean = accumulatedSec >= FpsConstants.SYNC_INTERVAL_SEC

    fun enableMouseLook() {
        Gdx.input.isCursorCatched = true
        hideCursor()
    }

    fun disableMouseLook() {
        Gdx.input.isCursorCatched = false
        showCursor()
    }

    fun toggleMouseLook() {
        if (Gdx.input.isCursorCatched) {
            disableMouseLook()
        } else {
            enableMouseLook()
        }
    }

    private fun hideCursor() {
        runCatching { Gdx.graphics.setSystemCursor(Cursor.SystemCursor.None) }
    }

    private fun showCursor() {
        runCatching { Gdx.graphics.setSystemCursor(Cursor.SystemCursor.Arrow) }
    }

    private fun pressed(vararg keys: Int): Boolean = keys.any { Gdx.input.isKeyPressed(it) }
}
