package ru.course.roguelike.client

import com.badlogic.gdx.Gdx
import ru.course.roguelike.client.net.GameApiClient
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.model.FpsConstants
import ru.course.roguelike.shared.model.PlayerPose
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.hypot

internal data class SyncRequestContext(
    val api: GameApiClient,
    val sessionId: String,
    val seq: Int,
    val input: InputSyncRequest,
    val gen: Int,
    val sessionGeneration: () -> Int,
    val syncAppliedSeq: AtomicInteger,
    val onStatusLine: (String) -> Unit,
    val onApplied: (GameSnapshot) -> Unit,
    val applyServerCorrection: (PlayerPose) -> Unit,
)

@Suppress("TooGenericExceptionCaught")
internal suspend fun runSyncRequest(context: SyncRequestContext) {
    if (context.gen != context.sessionGeneration()) return
    try {
        val result = context.api.sync(context.sessionId, context.input)
        if (context.gen != context.sessionGeneration()) return
        val snap = result.snapshot ?: return
        if (context.seq < context.syncAppliedSeq.get()) return
        context.syncAppliedSeq.set(context.seq)
        val auth = snap.player.pose
        val gen = context.gen
        Gdx.app.postRunnable {
            if (gen != context.sessionGeneration()) return@postRunnable
            context.applyServerCorrection(auth)
            context.onApplied(snap)
        }
    } catch (ex: Exception) {
        if (context.gen == context.sessionGeneration()) {
            context.onStatusLine("Sync failed: ${ex.message}")
        }
    }
}

internal fun applyServerCorrection(
    serverPose: PlayerPose,
    poseAccessor: () -> PlayerPose?,
    authoritativeMutator: (PlayerPose?) -> Unit,
    poseMutator: (PlayerPose?) -> Unit,
) {
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
