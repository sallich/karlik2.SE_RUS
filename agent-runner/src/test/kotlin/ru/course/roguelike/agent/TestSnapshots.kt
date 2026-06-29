package ru.course.roguelike.agent

import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.KeySnapshot
import ru.course.roguelike.shared.dto.PlayerSnapshot
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.SessionPhase
import ru.course.roguelike.shared.model.TileType

object TestSnapshots {
    fun simpleRoom(): GameSnapshot {
        val width = 5
        val height = 5
        val tiles = buildList {
            repeat(width * height) { idx ->
                val x = idx % width
                val y = idx / width
                add(
                    when {
                        x == 0 || y == 0 || x == width - 1 || y == height - 1 -> TileType.WALL
                        else -> TileType.FLOOR
                    },
                )
            }
        }
        return GameSnapshot(
            sessionId = "s1",
            seed = 1L,
            phase = SessionPhase.EXPLORATION.name,
            width = width,
            height = height,
            tiles = tiles,
            player = PlayerSnapshot(
                pose = PlayerPose(x = 2.5f, y = 2.5f, yaw = 0f, pitch = 0f),
                hp = 100,
                maxHp = 100,
            ),
            tick = 0L,
            keysCollected = 0,
            keysRequired = 1,
            keyPickups = listOf(KeySnapshot(id = 1, x = 3.5f, y = 2.5f)),
            exitGate = GridPos(3, 3),
        )
    }
}
