package ru.course.roguelike.client

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import ru.course.roguelike.client.input.InputSampler

internal data class DebugKeyState(
    val showCollisionDebug: Boolean,
    val showLocationMap: Boolean,
    val showMiniMap: Boolean,
    val showInventoryGrid: Boolean,
    val restartRequested: Boolean = false,
)

internal object DebugKeyHandler {
    fun handle(previous: DebugKeyState): DebugKeyState {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            InputSampler.toggleMouseLook()
        }
        var restartRequested = false
        if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) {
            restartRequested = true
        }
        return previous.copy(
            showCollisionDebug = toggleIfPressed(Input.Keys.F3, previous.showCollisionDebug),
            showLocationMap = toggleIfPressed(Input.Keys.F4, previous.showLocationMap),
            showMiniMap = toggleIfPressed(Input.Keys.M, previous.showMiniMap),
            showInventoryGrid = Gdx.input.isKeyPressed(Input.Keys.TAB),
            restartRequested = restartRequested,
        )
    }

    private fun toggleIfPressed(key: Int, current: Boolean): Boolean =
        if (Gdx.input.isKeyJustPressed(key)) !current else current
}
