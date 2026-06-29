package ru.course.roguelike.agent.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.PlayerSnapshot
import ru.course.roguelike.shared.mcp.McpTool
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.SessionPhase
import ru.course.roguelike.shared.model.TileType
import java.util.UUID

class MockMcpClient(
    private val width: Int = 48,
    private val height: Int = 48,
    private var exitGate: GridPos = GridPos(0, 0),
    private var keysRequired: Int = 3,
    private var phase: String = SessionPhase.EXPLORATION.name
) : McpClient {

    private val json = Json { ignoreUnknownKeys = true }
    private var sessionId: String? = null

    private var keysCollected = 0

    private var playerX = 1.0f
    private var playerY = 1.0f
    private var playerYaw = 0f
//    private val walls = setOf(5 to 6)

    private val tiles = List(width * height) { TileType.FLOOR }

    override suspend fun callTool(
        name: String,
        arguments: Map<String, JsonElement>,
    ): McpToolResult = when (name) {
        "game_new_session" -> handleNewSession()
        "game_observe" -> handleObserve()
        "game_act" -> handleAct(arguments)
        "game_sync" -> handleSync(arguments)
        else -> errorResult("Unknown tool: $name")
    }

    private fun handleSync(arguments: Map<String, JsonElement>): McpToolResult {
        val yawDelta = arguments["yawDelta"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
        playerYaw += yawDelta
        val snapshot = createSnapshot()
        val response = buildJsonObject {
            put("accepted", true)
            put("snapshot", json.encodeToJsonElement(GameSnapshot.serializer(), snapshot))
        }
        return McpToolResult(json.encodeToString(response), false)
    }

    private fun handleNewSession(): McpToolResult {
        sessionId = UUID.randomUUID().toString()
        val snapshot = createSnapshot()
        return McpToolResult(json.encodeToString(GameSnapshot.serializer(), snapshot), false)
    }

    private fun handleObserve(): McpToolResult {
        val snapshot = createSnapshot()
        return McpToolResult(json.encodeToString(GameSnapshot.serializer(), snapshot), false)
    }

    private fun handleAct(arguments: Map<String, JsonElement>): McpToolResult {
        val action = arguments["action"]?.jsonPrimitive?.content ?: return errorResult("Missing action")
        when (action) {
            "move_east" -> playerY += 1f
            "move_west" -> playerY -= 1f
            "move_north" -> playerX += 1f
            "move_south" -> playerX -= 1f
            "interact" -> handleInteract()
            "wait" -> {}
            else -> return errorResult("Unknown action: $action")
        }
        val snapshot = createSnapshot()
        val response = buildJsonObject {
            put("accepted", true)
            put("snapshot", json.encodeToJsonElement(GameSnapshot.serializer(), snapshot))
        }
        return McpToolResult(json.encodeToString(response), false)
    }

    private fun handleInteract() {
        if (playerX.toInt() == 39 && playerY.toInt() == 5 && keysCollected < keysRequired) {
            keysCollected++
        } else if (playerX.toInt() == exitGate.x && playerY.toInt() == exitGate.y && keysCollected == keysRequired) {
            phase = SessionPhase.LEVEL_COMPLETE.name
        }
    }

    override suspend fun getTools(): List<McpTool> = listOf(
        McpTool(
            name = "game_act",
            description = "Apply a discrete agent action.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put(
                    "properties",
                    buildJsonObject {
                        put(
                            "sessionId",
                            buildJsonObject {
                                put("type", "string")
                            },
                        )
                        put(
                            "action",
                            buildJsonObject {
                                put("type", "string")
                                put(
                                    "enum",
                                    buildJsonArray {
                                        add("interact")
                                        add("wait")
                                        add("move_north")
                                        add("move_south")
                                        add("move_east")
                                        add("move_west")
                                    },
                                )
                            },
                        )
                    },
                )
                put(
                    "required",
                    buildJsonArray {
                        add("sessionId")
                        add("action")
                    },
                )
                put("additionalProperties", false)
            },
        ),
    )

    override fun close() {
        return
    }

    private fun createSnapshot(): GameSnapshot = GameSnapshot(
        sessionId = sessionId ?: "test-session",
        seed = 42,
        phase = phase,
        width = width,
        height = height,
        tiles = tiles,
        player = PlayerSnapshot(
            pose = PlayerPose(playerX, playerY, 0f),
            hp = 100,
            maxHp = 100,
        ),
        agent = null,
        tick = 0,
        serverTimeMs = 0,
        currentLevel = 0,
        mobs = emptyList(),
        projectiles = emptyList(),
        keysCollected = keysCollected,
        keysRequired = keysRequired,
        keyPickups = listOf(),
        bossRoom = null,
        exitGate = exitGate,
    )

    private fun errorResult(msg: String) = McpToolResult(msg, true)
}
