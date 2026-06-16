package ru.course.roguelike.agent.tracker

import java.util.concurrent.atomic.AtomicReference
import kotlin.math.floor
import ru.course.roguelike.agent.AgentRunResponse
import ru.course.roguelike.agent.map.MapCellRenderer
import ru.course.roguelike.shared.dto.DoorMarkerSnapshot
import ru.course.roguelike.shared.dto.GameSnapshot

/** In-memory state for the agent map tracker (single active run). */
object AgentLiveTracker {
    private val stateRef = AtomicReference(AgentLiveState())

    fun snapshot(): AgentLiveState = stateRef.get()

    fun onRunStart(sessionId: String, seed: Long?) {
        stateRef.set(
            AgentLiveState(
                running = true,
                sessionId = sessionId,
                seed = seed,
                startedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    fun onObserve(step: Int, game: GameSnapshot) {
        stateRef.updateAndGet { prev ->
            val pose = game.player.pose
            val trail = prev.trail.toMutableList()
            val point = PosePoint(pose.x, pose.y)
            if (trail.isEmpty() || trail.last() != point) {
                trail.add(point)
                if (trail.size > MAX_TRAIL) trail.removeAt(0)
            }
            prev.copy(
                running = true,
                step = step,
                phase = game.phase,
                keysCollected = game.keysCollected,
                keysRequired = game.keysRequired,
                hp = game.player.hp,
                maxHp = game.player.maxHp,
                ammo = game.player.ammo,
                playerX = pose.x,
                playerY = pose.y,
                playerYaw = pose.yaw,
                roomTimerMs = game.roomClearTimer?.remainingMs,
                mobCount = game.mobs.size,
                trail = trail,
                map = TrackerMapBuilder.build(game),
                doors = game.doorMarkers.map { doorView(it) },
            )
        }
    }

    fun onAction(step: Int, tool: String, detail: String, source: String, error: Boolean) {
        stateRef.updateAndGet { prev ->
            prev.copy(
                step = step,
                lastTool = tool,
                lastAction = detail,
                lastSource = source,
                lastError = error,
                updatedAtMs = System.currentTimeMillis(),
            )
        }
    }

    fun onRunFinish(response: AgentRunResponse) {
        stateRef.updateAndGet { prev ->
            prev.copy(
                running = false,
                step = response.stepsUsed,
                status = response.status,
                message = response.message,
                success = response.success,
                finalPhase = response.finalPhase,
                finishedAtMs = System.currentTimeMillis(),
            )
        }
    }

    fun onRunFailed(message: String) {
        stateRef.updateAndGet { prev ->
            prev.copy(
                running = false,
                status = "FAILED",
                message = message,
                finishedAtMs = System.currentTimeMillis(),
            )
        }
    }

    fun onReplan(replanCount: Int, reason: String) {
        stateRef.updateAndGet { prev ->
            prev.copy(
                replanCount = replanCount,
                lastSource = "replan:${reason.lowercase()}",
                updatedAtMs = System.currentTimeMillis(),
            )
        }
    }

    fun onMacroDecision(line: String) {
        stateRef.updateAndGet { prev ->
            val journal = prev.macroJournal.toMutableList()
            journal.add(line)
            if (journal.size > MAX_MACRO_JOURNAL) journal.removeAt(0)
            prev.copy(
                macroJournal = journal,
                updatedAtMs = System.currentTimeMillis(),
            )
        }
    }

    fun onPolicyDebug(
        debugLines: List<String>,
        highlights: List<TrackerHighlight>,
    ) {
        stateRef.updateAndGet { prev ->
            prev.copy(
                debugLines = debugLines,
                highlights = highlights,
                updatedAtMs = System.currentTimeMillis(),
            )
        }
    }

    private fun doorView(door: DoorMarkerSnapshot): DoorView {
        val label = when {
            door.prizeIsKey -> "key"
            door.mobRoom -> "mob"
            else -> "loot"
        }
        return DoorView(floor(door.x).toInt(), floor(door.y).toInt(), label)
    }

    private fun AtomicReference<AgentLiveState>.updateAndGet(
        transform: (AgentLiveState) -> AgentLiveState,
    ): AgentLiveState {
        while (true) {
            val current = get()
            val next = transform(current)
            if (compareAndSet(current, next)) return next
        }
    }

    private const val MAX_TRAIL = 400
    private const val MAX_MACRO_JOURNAL = 24
}

object TrackerMapBuilder {
    fun build(snapshot: GameSnapshot): TrackerMap {
        val w = snapshot.width
        val h = snapshot.height
        val cells = CharArray(w * h)
        val px = snapshot.player.pose.x.toInt()
        val py = snapshot.player.pose.y.toInt()
        for (y in 0 until h) {
            for (x in 0 until w) {
                cells[y * w + x] = cellChar(snapshot, x, y, px, py)
            }
        }
        return TrackerMap(
            width = w,
            height = h,
            cells = cells.concatToString(),
            exitX = snapshot.exitGate?.x,
            exitY = snapshot.exitGate?.y,
        )
    }

    private fun cellChar(snapshot: GameSnapshot, x: Int, y: Int, playerX: Int, playerY: Int): Char =
        MapCellRenderer.charAt(snapshot, x, y, playerX, playerY)
}
