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
        mergeInput = ::mergeInputSync,
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
                    applyServerCorrection = { auth, verticalVelocity ->
                        applyServerCorrection(
                            auth,
                            verticalVelocity,
                            bindings.poseAccessor,
                            bindings.authoritativeMutator,
                            bindings.poseMutator,
                            bindings.verticalMutator,
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
                        "1/2 — оружие | Tab+1/2 — в слот | F — перезарядка | " +
                        "Shift — прыжок | Tab — инвентарь | F5 — новый забег.",
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
            snap.player.ammo,
            snap.player.maxAmmo,
            snap.player.equippedWeaponName,
            snap.player.equippedWeaponType,
        )
        bindings.inventoryMutator(snap.player.inventory, snap.player.hotbar)
        bindings.combatMutator(snap.mobs, snap.projectiles)
        bindings.progressMutator(
            snap.phase,
            snap.keysCollected,
            snap.keysRequired,
            snap.keyPickups,
            snap.items,
            snap.exitGate,
        )
        bindings.agentMutator(snap.agent?.pose)
        bindings.verticalMutator(snap.player.verticalVelocity)
        bindings.elevatorPhaseMutator(snap.elevatorPhase)
        bindings.roomTimerMutator(snap.roomClearTimer, snap.serverTimeMs)
        bindings.worldMutator(snap)
        currentLevel = snap.currentLevel
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

    private fun applyObserveUpdate(snap: GameSnapshot) {
        // observe — только мобы/агент/лут; карту, таймер и двери обновляем через sync,
        // иначе опрос затирает состояние боя устаревшим снимком.
        bindings.agentMutator(snap.agent?.pose)
        bindings.combatMutator(snap.mobs, snap.projectiles)
        bindings.progressMutator(
            snap.phase,
            snap.keysCollected,
            snap.keysRequired,
            snap.keyPickups,
            snap.items,
            snap.exitGate,
        )
    }

    fun restart(seed: Long? = null) {
        observeJob?.cancel()
        sessionGeneration.incrementAndGet()
        sessionId = null
        pendingInput.set(null)
        syncOutboundSeq.set(0)
        syncAppliedSeq.set(0)
        currentLevel = 0
        connect(seed)
    }

    private fun applySyncSnapshot(snap: GameSnapshot) {
        bindings.worldMutator(snap)
        bindings.vitalsMutator(
            snap.player.hp,
            snap.player.maxHp,
            snap.player.level,
            snap.player.experience,
            snap.player.experienceToNextLevel,
            snap.player.ammo,
            snap.player.maxAmmo,
            snap.player.equippedWeaponName,
            snap.player.equippedWeaponType,
        )
        bindings.inventoryMutator(snap.player.inventory, snap.player.hotbar)
        bindings.combatMutator(snap.mobs, snap.projectiles)
        bindings.agentMutator(snap.agent?.pose)
        bindings.verticalMutator(snap.player.verticalVelocity)
        bindings.elevatorPhaseMutator(snap.elevatorPhase)
        bindings.progressMutator(
            snap.phase,
            snap.keysCollected,
            snap.keysRequired,
            snap.keyPickups,
            snap.items,
            snap.exitGate,
        )
        bindings.roomTimerMutator(snap.roomClearTimer, snap.serverTimeMs)
    }
}
