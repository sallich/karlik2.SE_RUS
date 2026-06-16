package ru.course.roguelike.agent.tracker

import kotlinx.serialization.Serializable

@Serializable
data class AgentLiveState(
    val running: Boolean = false,
    val sessionId: String? = null,
    val seed: Long? = null,
    val step: Int = 0,
    val phase: String? = null,
    val keysCollected: Int = 0,
    val keysRequired: Int = 0,
    val hp: Int = 0,
    val maxHp: Int = 0,
    val ammo: Int = 0,
    val playerX: Float = 0f,
    val playerY: Float = 0f,
    val playerYaw: Float = 0f,
    val roomTimerMs: Long? = null,
    val mobCount: Int = 0,
    val trail: List<PosePoint> = emptyList(),
    val map: TrackerMap? = null,
    val doors: List<DoorView> = emptyList(),
    val lastTool: String? = null,
    val lastAction: String? = null,
    val lastSource: String? = null,
    val lastError: Boolean = false,
    val status: String? = null,
    val message: String? = null,
    val success: Boolean = false,
    val finalPhase: String? = null,
    val startedAtMs: Long? = null,
    val updatedAtMs: Long? = null,
    val finishedAtMs: Long? = null,
    /** Policy DSL agent: macro replans during run. */
    val replanCount: Int = 0,
    /** LLM macro decision journal (one line per initial policy / replan). */
    val macroJournal: List<String> = emptyList(),
    /** Human-readable debug: cell under player, aim target, policy flags. */
    val debugLines: List<String> = emptyList(),
    /** Cells to highlight on map overlay: exit goals, frozen region, stuck target. */
    val highlights: List<TrackerHighlight> = emptyList(),
)

@Serializable
data class TrackerHighlight(
    val x: Int,
    val y: Int,
    val kind: String,
)

@Serializable
data class PosePoint(val x: Float, val y: Float)

@Serializable
data class TrackerMap(
    val width: Int,
    val height: Int,
    val cells: String,
    val exitX: Int? = null,
    val exitY: Int? = null,
)

@Serializable
data class DoorView(val x: Int, val y: Int, val label: String)

@Serializable
data class LlmHealthResponse(
    val provider: String,
    val reachable: Boolean,
    val ollamaBaseUrl: String? = null,
    val models: List<String> = emptyList(),
    val configuredModel: String? = null,
    val configuredFallback: String? = null,
    val modelAvailable: Boolean = false,
    val fallbackAvailable: Boolean = false,
    val error: String? = null,
    val hint: String? = null,
)
