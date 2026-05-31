package ru.course.roguelike.client

import com.badlogic.gdx.Gdx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.course.roguelike.client.net.GameApiClient
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.InputSyncRequest
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

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
    private val sessionGeneration = AtomicInteger(0)
    private val pendingInput = AtomicReference<InputSyncRequest?>(null)
    private val inputDispatcher = SyncInputDispatcher(
        scope = scope,
        sessionGeneration = sessionGeneration,
        syncOutboundSeq = syncOutboundSeq,
        pendingInput = pendingInput,
        sessionIdProvider = { sessionId },
        mergeInput = ::mergeInput,
        onBatch = { id, seq, input, gen ->
            runSyncRequest(
                SyncRequestContext(
                    api = api,
                    sessionId = id,
                    seq = seq,
                    input = input,
                    gen = gen,
                    sessionGeneration = sessionGeneration::get,
                    syncAppliedSeq = syncAppliedSeq,
                    onStatusLine = onStatusLine,
                    onApplied = ::applySyncSnapshot,
                    applyServerCorrection = { auth ->
                        applyServerCorrection(
                            auth,
                            bindings.poseAccessor,
                            bindings.authoritativeMutator,
                            bindings.poseMutator,
                        )
                    },
                ),
            )
        },
    )

    /** Активный ярус локации по последнему снимку — для уведомления о смене лифтом. */
    private var currentLevel = 0
    private var observeJob: kotlinx.coroutines.Job? = null

    @Suppress("TooGenericExceptionCaught")
    fun connect(seed: Long? = null) {
        val gen = sessionGeneration.get()
        scope.launch {
            try {
                val created = api.createSession(seed = seed, twoLevel = true, coopAgent = true)
                if (gen != sessionGeneration.get()) return@launch
                Gdx.app.postRunnable {
                    if (gen != sessionGeneration.get()) return@postRunnable
                    applySnapshot(created)
                    onSnapshot(created)
                }
                startObservePoll(created.sessionId, gen)
                onStatusLine(
                    "Co-op agent enabled. Session ${created.sessionId.take(8)}… — " +
                        "curl agent: POST /api/v1/agent/run with sessionId. " +
                        "Esc — мышь, F4 — карта, R — новый забег.",
                )
            } catch (ex: Exception) {
                if (gen == sessionGeneration.get()) {
                    onStatusLine("Server error: ${ex.message}. Run :game-service:run")
                }
            }
        }
    }

    fun send(input: InputSyncRequest) {
        if (sessionId == null) return
        inputDispatcher.enqueue(input)
        inputDispatcher.pump()
    }

    fun applySnapshot(snap: GameSnapshot) {
        sessionId = snap.sessionId
        val pose = snap.player.pose
        bindings.poseMutator(pose)
        bindings.authoritativeMutator(pose)
        bindings.vitalsMutator(
            snap.player.hp,
            snap.player.maxHp,
            snap.player.level,
            snap.player.experience,
            snap.player.experienceToNextLevel,
        )
        bindings.combatMutator(snap.mobs, snap.projectiles)
        bindings.progressMutator(snap.phase, snap.keysCollected, snap.keysRequired, snap.keyPickups, snap.exitGate)
        bindings.agentMutator(snap.agent?.pose)
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
            val level = snap.player.level
            val experience = snap.player.experience
            val experienceToNextLevel = snap.player.experienceToNextLevel
            val mapLevel = snap.currentLevel
            val mobs = snap.mobs
            val projectiles = snap.projectiles
            Gdx.app.postRunnable {
                applyServerCorrection(auth)
                bindings.vitalsMutator(hp, maxHp, level, experience, experienceToNextLevel)
                bindings.combatMutator(mobs, projectiles)
                notifyLevelChange(mapLevel)
            }
        } catch (ex: Exception) {
            onStatusLine("Sync failed: ${ex.message}")
        }
    private fun startObservePoll(session: String, generation: Int) {
        observeJob?.cancel()
        observeJob = scope.launch {
            while (isActive && generation == sessionGeneration.get()) {
                delay(120)
                try {
                    val snap = api.observe(session)
                    if (generation != sessionGeneration.get()) return@launch
                    Gdx.app.postRunnable {
                        if (generation != sessionGeneration.get()) return@postRunnable
                        applyObserveUpdate(snap)
                    }
                } catch (_: Exception) {
                    // ignore transient network errors
                }
            }
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
            interact = prev.interact || frame.interact,
        )

    private fun applySyncSnapshot(snap: GameSnapshot) {
        bindings.vitalsMutator(snap.player.hp, snap.player.maxHp)
        bindings.combatMutator(snap.mobs, snap.projectiles)
        bindings.agentMutator(snap.agent?.pose)
        bindings.progressMutator(
            snap.phase,
            snap.keysCollected,
            snap.keysRequired,
            snap.keyPickups,
            snap.exitGate,
        )
        notifyLevelChange(snap.currentLevel)
    }

    private fun notifyLevelChange(level: Int) {
        if (level == currentLevel) return
        currentLevel = level
        onStatusLine(
            if (level == 1) "Elevator: went up to the 2nd floor" else "Elevator: went down to the 1st floor",
        )
    }
}
