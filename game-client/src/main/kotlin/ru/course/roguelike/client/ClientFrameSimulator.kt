package ru.course.roguelike.client

import ru.course.roguelike.client.input.InputSampler
import ru.course.roguelike.client.render.ViewportRenderScene
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.engine.ElevatorPhase
import ru.course.roguelike.shared.engine.FpsMovementSystem
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType

/** Локальная симуляция кадра: ввод → физика → sync → рендер. */
internal object ClientFrameSimulator {
    fun advance(game: RoguelikeGame, delta: Float) {
        val map = game.tileMap ?: return
        var pose = game.predictedPose ?: return

        val sample = InputSampler.sample(delta, game.showInventoryGrid)
        game.accumulatedYawDelta += sample.input.yawDelta
        game.pendingSyncInput = mergeInputSync(game.pendingSyncInput, sample.input)
        game.pendingSyncDeltaMs = (game.pendingSyncDeltaMs + sample.input.deltaMs).coerceAtMost(250)

        if (sample.input.attack) {
            game.audio.playHit()
        }

        val vertical = ClientVerticalMotion.tick(
            ClientVerticalMotion.TickInput(
                map = map,
                pose = pose,
                verticalVelocity = game.clientVerticalVelocity,
                elevatorPhase = game.clientElevatorPhase,
                wasOnElevator = game.clientWasOnElevator,
                twoLevel = game.twoLevelLocation,
                jumpRequested = sample.input.jump,
                deltaMs = sample.input.deltaMs,
            ),
        )
        val movement = FpsMovementSystem.applyInputWithDebug(
            map,
            vertical.pose.copy(yaw = pose.yaw, pitch = pose.pitch),
            sample.input,
        )
        game.lastCollisionDebug = movement.debug
        game.clientVerticalVelocity = vertical.verticalVelocity
        game.clientElevatorPhase = vertical.elevatorPhase
        if (vertical.levelSwitched) {
            game.currentLevel = 1 - game.currentLevel
            game.visitedTracker.clear()
        }
        val onElevatorTile = map.getTileAt(vertical.pose.x, vertical.pose.y) == TileType.ELEVATOR
        game.clientWasOnElevator = onElevatorTile || game.clientElevatorPhase != ElevatorPhase.IDLE
        val localPose = movement.pose

        flushSyncIfNeeded(game, localPose)
        pose = localPose
        game.predictedPose = pose
        game.visitedTracker.reveal(map, pose)
        game.frameTexture = game.viewport.render(
            ViewportRenderScene(
                map = map,
                pose = pose,
                floorLevel = game.currentLevel,
                mobs = game.serverMobs,
                projectiles = game.serverProjectiles,
                keyPickups = game.keyPickups,
                items = game.items,
                agentPose = game.agentPose,
            ),
        )
    }

    private fun flushSyncIfNeeded(game: RoguelikeGame, localPose: PlayerPose) {
        val urgent = game.pendingSyncInput.interact || game.pendingSyncInput.attack
        if ((!InputSampler.shouldSync(game.syncAccum) && !urgent) || game.sync.sessionId == null) return

        game.syncAccum = 0f
        val syncPayload = game.pendingSyncInput.copy(
            yawDelta = game.accumulatedYawDelta,
            deltaMs = game.pendingSyncDeltaMs.coerceAtLeast(1),
            clientYaw = localPose.yaw,
            clientPitch = localPose.pitch,
        )
        game.accumulatedYawDelta = 0f
        game.pendingSyncInput = InputSyncRequest()
        game.pendingSyncDeltaMs = 0
        game.sync.send(syncPayload)
    }
}
