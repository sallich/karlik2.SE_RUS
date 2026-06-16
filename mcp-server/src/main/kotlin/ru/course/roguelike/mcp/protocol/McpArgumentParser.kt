package ru.course.roguelike.mcp.protocol

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.InputSyncRequest
import kotlin.math.floor

object SessionSummaryBuilder {
    fun fromSnapshot(snap: GameSnapshot): JsonObject {
        val cellX = floor(snap.player.pose.x).toInt()
        val cellY = floor(snap.player.pose.y).toInt()
        return buildJsonObject {
            put("sessionId", snap.sessionId)
            put("phase", snap.phase)
            put("hp", snap.player.hp)
            put("maxHp", snap.player.maxHp)
            put("keysCollected", snap.keysCollected)
            put("keysRequired", snap.keysRequired)
            put(
                "playerCell",
                buildJsonObject {
                    put("x", cellX)
                    put("y", cellY)
                },
            )
            put("playerYaw", snap.player.pose.yaw)
            snap.exitGate?.let { gate ->
                put(
                    "exitGate",
                    buildJsonObject {
                        put("x", gate.x)
                        put("y", gate.y)
                    },
                )
            }
            put(
                "keys",
                buildJsonArray {
                    snap.keyPickups.forEach { key ->
                        add(
                            buildJsonObject {
                                put("id", key.id)
                                put("x", key.x)
                                put("y", key.y)
                            },
                        )
                    }
                },
            )
            put("mobCount", snap.mobs.size)
        }
    }
}

object McpArgumentParser {
    fun requireString(arguments: Map<String, JsonElement>, key: String): String? =
        arguments[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    fun optionalLong(arguments: Map<String, JsonElement>, key: String): Long? =
        arguments[key]?.jsonPrimitive?.longOrNull()

    fun optionalBoolean(arguments: Map<String, JsonElement>, key: String, default: Boolean): Boolean =
        arguments[key]?.jsonPrimitive?.booleanOrNull ?: default

    fun optionalActor(arguments: Map<String, JsonElement>): String =
        requireString(arguments, "actor")?.takeIf { it in ACTORS } ?: ACTOR_PLAYER

    fun parseSyncInput(arguments: Map<String, JsonElement>): InputSyncRequest =
        InputSyncRequest(
            forward = optionalBoolean(arguments, "forward", false),
            backward = optionalBoolean(arguments, "backward", false),
            strafeLeft = optionalBoolean(arguments, "strafeLeft", false),
            strafeRight = optionalBoolean(arguments, "strafeRight", false),
            turnLeft = optionalBoolean(arguments, "turnLeft", false),
            turnRight = optionalBoolean(arguments, "turnRight", false),
            lookUp = optionalBoolean(arguments, "lookUp", false),
            lookDown = optionalBoolean(arguments, "lookDown", false),
            yawDelta = arguments["yawDelta"]?.jsonPrimitive?.floatOrNull ?: 0f,
            pitchDelta = arguments["pitchDelta"]?.jsonPrimitive?.floatOrNull ?: 0f,
            deltaMs = arguments["deltaMs"]?.jsonPrimitive?.intOrNull ?: 50,
            clientYaw = arguments["clientYaw"]?.jsonPrimitive?.floatOrNull,
            clientPitch = arguments["clientPitch"]?.jsonPrimitive?.floatOrNull,
            attack = optionalBoolean(arguments, "attack", false),
            interact = optionalBoolean(arguments, "interact", false),
            reload = optionalBoolean(arguments, "reload", false),
            hotbarSelect = arguments["hotbarSelect"]?.jsonPrimitive?.intOrNull,
            hotbarAssign = arguments["hotbarAssign"]?.jsonPrimitive?.intOrNull,
            inventoryCycle = optionalBoolean(arguments, "inventoryCycle", false),
            inventoryDrop = optionalBoolean(arguments, "inventoryDrop", false),
            jump = optionalBoolean(arguments, "jump", false),
            actor = optionalActor(arguments),
        )

    private const val ACTOR_PLAYER = "player"
    private const val ACTOR_AGENT = "agent"
    private val ACTORS = setOf(ACTOR_PLAYER, ACTOR_AGENT)
}

private fun JsonElement.longOrNull(): Long? = jsonPrimitive.contentOrNull?.toLongOrNull()
