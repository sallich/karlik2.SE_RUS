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
    private val poseAccessor: () -> PlayerPose?,
    private val poseMutator: (PlayerPose?) -> Unit,
    private val authoritativeMutator: (PlayerPose?) -> Unit,
) {
    var sessionId: String? = null
        private set

    private val syncOutboundSeq = AtomicInteger(0)
    private val syncAppliedSeq = AtomicInteger(0)

    @Suppress("TooGenericExceptionCaught")
    fun connect(seed: Long = 42) {
        scope.launch {
            try {
                val created = api.createSession(seed = seed)
                Gdx.app.postRunnable {
                    applySnapshot(created)
                    onSnapshot(created)
                }
                onStatusLine(
                    "WASD — ходьба, мышь/ПКМ — поворот, ←→ — поворот, ↑↓ — pitch. Esc — мышь. F3 — коллизии.",
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
        poseMutator(pose)
        authoritativeMutator(pose)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runSyncRequest(sessionId: String, seq: Int, input: InputSyncRequest) {
        try {
            val result = api.sync(sessionId, input)
            val snap = result.snapshot ?: return
            if (seq < syncAppliedSeq.get()) return
            syncAppliedSeq.set(seq)
            val auth = snap.player.pose
            Gdx.app.postRunnable {
                applyServerCorrection(auth)
            }
        } catch (ex: Exception) {
            onStatusLine("Sync failed: ${ex.message}")
        }
    }

    private fun applyServerCorrection(serverPose: PlayerPose) {
        val pred = poseAccessor() ?: return
        val auth = serverPose.copy(yaw = pred.yaw, pitch = pred.pitch)
        authoritativeMutator(auth)
        val err = hypot((auth.x - pred.x).toDouble(), (auth.y - pred.y).toDouble()).toFloat()
        poseMutator(
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
        )
}
