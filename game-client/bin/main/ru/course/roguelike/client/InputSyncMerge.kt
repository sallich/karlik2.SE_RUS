package ru.course.roguelike.client

import ru.course.roguelike.shared.dto.InputSyncRequest

internal fun mergeInputSync(prev: InputSyncRequest, frame: InputSyncRequest): InputSyncRequest {
    val movement = mergeMovement(prev, frame)
    val actions = mergeActions(prev, frame)
    return InputSyncRequest(
        forward = movement.forward,
        backward = movement.backward,
        strafeLeft = movement.strafeLeft,
        strafeRight = movement.strafeRight,
        turnLeft = movement.turnLeft,
        turnRight = movement.turnRight,
        lookUp = movement.lookUp,
        lookDown = movement.lookDown,
        yawDelta = movement.yawDelta,
        pitchDelta = movement.pitchDelta,
        deltaMs = movement.deltaMs,
        attack = actions.attack,
        interact = actions.interact,
        hotbarSelect = actions.hotbarSelect,
        hotbarAssign = actions.hotbarAssign,
        reload = actions.reload,
        jump = actions.jump,
    )
}

private data class MovementMerge(
    val forward: Boolean,
    val backward: Boolean,
    val strafeLeft: Boolean,
    val strafeRight: Boolean,
    val turnLeft: Boolean,
    val turnRight: Boolean,
    val lookUp: Boolean,
    val lookDown: Boolean,
    val yawDelta: Float,
    val pitchDelta: Float,
    val deltaMs: Int,
)

private data class ActionMerge(
    val attack: Boolean,
    val interact: Boolean,
    val hotbarSelect: Int?,
    val hotbarAssign: Int?,
    val reload: Boolean,
    val jump: Boolean,
)

private fun mergeMovement(prev: InputSyncRequest, frame: InputSyncRequest): MovementMerge =
    MovementMerge(
        forward = prev.forward || frame.forward,
        backward = prev.backward || frame.backward,
        strafeLeft = prev.strafeLeft || frame.strafeLeft,
        strafeRight = prev.strafeRight || frame.strafeRight,
        turnLeft = prev.turnLeft || frame.turnLeft,
        turnRight = prev.turnRight || frame.turnRight,
        lookUp = prev.lookUp || frame.lookUp,
        lookDown = prev.lookDown || frame.lookDown,
        yawDelta = prev.yawDelta + frame.yawDelta,
        pitchDelta = prev.pitchDelta + frame.pitchDelta,
        deltaMs = prev.deltaMs + frame.deltaMs,
    )

private fun mergeActions(prev: InputSyncRequest, frame: InputSyncRequest): ActionMerge =
    ActionMerge(
        attack = prev.attack || frame.attack,
        interact = prev.interact || frame.interact,
        hotbarSelect = frame.hotbarSelect ?: prev.hotbarSelect,
        hotbarAssign = frame.hotbarAssign ?: prev.hotbarAssign,
        reload = prev.reload || frame.reload,
        jump = prev.jump || frame.jump,
    )
