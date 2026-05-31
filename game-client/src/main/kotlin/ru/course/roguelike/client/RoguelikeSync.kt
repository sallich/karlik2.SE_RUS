package ru.course.roguelike.client

import com.badlogic.gdx.Gdx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.course.roguelike.client.net.GameApiClient
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.model.FpsConstants
import ru.course.roguelike.shared.model.PlayerPose
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.hypot

class RoguelikeSync(
    private val scope: CoroutineScope,
    private val api: GameApiClient,
    private val onStatusLine: (String) -> Unit,
    private val onSnapshot: (GameSnapshot) -> Unit,
    private val bindings: SyncBindings,
) {
    var sessionId: String? = null
        private set

    private val syncOutboundSeq = AtomicInteger(0)
    private val syncAppliedSeq = AtomicInteger(0)

    /** Активный ярус локации по последнему снимку — для уведомления о смене лифтом. */
    private var currentLevel = 0

    @Suppress("TooGenericExceptionCaught")
    fun connect(seed: Long? = null) {
        scope.launch {
            try {
                val created = api.createSession(seed = seed, twoLevel = true)
                Gdx.app.postRunnable {
                    applySnapshot(created)
                    onSnapshot(created)
                }
                onStatusLine(
                    "WASD — ходьба, ЛКМ/Space — атака, мышь/ПКМ — yaw/pitch, ←→ — поворот, ↑↓/Q/E — pitch. " +
                        "Esc — мышь. F3 — миникарта. F4 — карта локации.",
                )
            } catch (ex: Exception) {
                onStatusLine("Server error: ${ex.message}. Run :game-service:run")
            }
        }
    }

    fun send(input: InputSyncRequest) {
        val id = sessionId ?: return
        val seq = syncOutboundSeq.incrementAndGet()
        scope.launch {
            runSyncRequest(id, seq, input)
        }
    }

    fun applySnapshot(snap: GameSnapshot) {
        sessionId = snap.sessionId
        val pose = snap.player.pose
        bindings.poseMutator(pose)
        bindings.authoritativeMutator(pose)
        bindings.vitalsMutator(snap.player.hp, snap.player.maxHp)
        bindings.combatMutator(snap.mobs, snap.projectiles)
        currentLevel = snap.currentLevel
    }

    /** Сообщает о смене яруса лифтом, если активный уровень в снимке изменился. */
    private fun notifyLevelChange(level: Int) {
        if (level == currentLevel) return
        currentLevel = level
        onStatusLine(
            if (level == 1) "Elevator: went up to the 2nd floor" else "Elevator: went down to the 1st floor",
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runSyncRequest(sessionId: String, seq: Int, input: InputSyncRequest) {
        try {
            val result = api.sync(sessionId, input)
            val snap = result.snapshot ?: return
            if (seq < syncAppliedSeq.get()) return
            syncAppliedSeq.set(seq)
            val auth = snap.player.pose
            val hp = snap.player.hp
            val maxHp = snap.player.maxHp
            val level = snap.currentLevel
            val mobs = snap.mobs
            val projectiles = snap.projectiles
            Gdx.app.postRunnable {
                applyServerCorrection(auth)
                bindings.vitalsMutator(hp, maxHp)
                bindings.combatMutator(mobs, projectiles)
                notifyLevelChange(level)
            }
        } catch (ex: Exception) {
            onStatusLine("Sync failed: ${ex.message}")
        }
    }

    private fun applyServerCorrection(serverPose: PlayerPose) {
        val pred = bindings.poseAccessor() ?: return
        val auth = serverPose.copy(yaw = pred.yaw, pitch = pred.pitch)
        bindings.authoritativeMutator(auth)
        val err = hypot((auth.x - pred.x).toDouble(), (auth.y - pred.y).toDouble()).toFloat()
        bindings.poseMutator(
            when {
                err > FpsConstants.SYNC_POSITION_HARD_SNAP ->
                    pred.copy(x = auth.x, y = auth.y)
                err > FpsConstants.SYNC_POSITION_CORRECT_MIN ->
                    PoseBlend.towardPosition(pred, auth, FpsConstants.SYNC_POSITION_BLEND)
                else -> pred
            },
        )
    }

    fun mergeInput(prev: InputSyncRequest, frame: InputSyncRequest): InputSyncRequest =
        InputSyncRequest(
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
            attack = prev.attack || frame.attack,
        )
}
