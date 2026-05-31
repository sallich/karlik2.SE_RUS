package ru.course.roguelike.client

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import ru.course.roguelike.client.audio.GameAudio
import ru.course.roguelike.client.input.InputSampler
import ru.course.roguelike.client.net.GameApiClient
import ru.course.roguelike.client.render.CollisionDebugOverlay
import ru.course.roguelike.client.render.FpsViewportRenderer
import ru.course.roguelike.client.render.GameEndOverlay
import ru.course.roguelike.client.render.GameTextures
import ru.course.roguelike.client.render.LocationMapOverlay
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.dto.KeySnapshot
import ru.course.roguelike.shared.dto.MobSnapshot
import ru.course.roguelike.shared.dto.ProjectileSnapshot
import ru.course.roguelike.shared.engine.CollisionDebug
import ru.course.roguelike.shared.engine.FpsMovementSystem
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.SessionPhase
import ru.course.roguelike.shared.model.TileType
import kotlin.math.floor
import kotlin.math.hypot

class RoguelikeGame : ApplicationAdapter() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api = GameApiClient(System.getenv("GAME_SERVICE_URL") ?: "http://localhost:8080")

    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont
    private lateinit var hud: RoguelikeHud
    private lateinit var viewport: FpsViewportRenderer
    private lateinit var gameTextures: GameTextures
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var collisionDebugOverlay: CollisionDebugOverlay
    private lateinit var locationMapOverlay: LocationMapOverlay
    private lateinit var gameEndOverlay: GameEndOverlay
    private lateinit var sync: RoguelikeSync
    private lateinit var audio: GameAudio

    private var tileMap: TileMap? = null

    /** Локальная симуляция (только render thread). */
    private var predictedPose: PlayerPose? = null

    /** Последняя поза с сервера (IO thread пишет, @Volatile для render). */
    @Volatile
    private var authoritativePose: PlayerPose? = null

    private var statusLine = "Connecting..."
    private var frameTexture: Texture? = null
    private var fpsSmoothed = 0f
    private var pendingSyncInput = InputSyncRequest()
    private var pendingSyncDeltaMs = 0
    private var accumulatedYawDelta = 0f
    private var showCollisionDebug = false
    private var showLocationMap = false
    private var lastCollisionDebug: CollisionDebug? = null

    /** HP/maxHP, присланные сервером (учитывают урон от лавы). */
    private var playerHp = 0
    private var playerMaxHp = 0
    private var keysCollected = 0
    private var keysRequired = 0

    @Volatile
    private var sessionPhase = SessionPhase.EXPLORATION

    @Volatile
    private var serverMobs: List<MobSnapshot> = emptyList()

    @Volatile
    private var serverProjectiles: List<ProjectileSnapshot> = emptyList()

    @Volatile
    private var keyPickups: List<KeySnapshot> = emptyList()

    @Volatile
    private var exitGate: GridPos? = null

    override fun create() {
        batch = SpriteBatch()
        font = BitmapFont()
        hud = RoguelikeHud(batch, font)
        shapeRenderer = ShapeRenderer()
        collisionDebugOverlay = CollisionDebugOverlay(shapeRenderer)
        locationMapOverlay = LocationMapOverlay(shapeRenderer)
        gameEndOverlay = GameEndOverlay(batch, font, shapeRenderer)
        gameTextures = GameTextures.load()
        audio = GameAudio()
        audio.load()
        audio.playAmbient()
        viewport = FpsViewportRenderer(640, 360, gameTextures)
        sync = RoguelikeSync(
            scope = scope,
            api = api,
            onStatusLine = { statusLine = it },
            onSnapshot = { applySnapshotFromServer(it) },
            bindings = SyncBindings(
                poseAccessor = { predictedPose },
                poseMutator = { predictedPose = it },
                authoritativeMutator = { authoritativePose = it },
                vitalsMutator = { hp, maxHp ->
                    playerHp = hp
                    playerMaxHp = maxHp
                },
                combatMutator = { mobs, projectiles ->
                    serverMobs = mobs
                    serverProjectiles = projectiles
                },
                progressMutator = { phase, collected, required, keys, gate ->
                    sessionPhase = parsePhase(phase)
                    keysCollected = collected
                    keysRequired = required
                    keyPickups = keys
                    exitGate = gate
                },
            ),
        )
        Gdx.graphics.setForegroundFPS(60)
        InputSampler.enableMouseLook()
        sync.connect()
    }

    override fun render() {
        val delta = Gdx.graphics.deltaTime.coerceAtMost(0.05f)
        fpsSmoothed = fpsSmoothed * 0.9f + (1f / delta) * 0.1f
        handleDebugKeys()

        if (!isSessionEnded()) {
            syncAccum += delta
            simulateFrame(delta)
        }

        drawWorldFrame()
        if (!isSessionEnded()) {
            drawDebugOverlays(predictedPose)
        }

        val pose = predictedPose
        val onLava = !isSessionEnded() && pose != null && tileMap?.getTileAt(pose.x, pose.y)?.damaging == true
        hud.draw(
            statusLine,
            pose,
            fpsSmoothed,
            lastCollisionDebug,
            showCollisionDebug && !isSessionEnded(),
            onLava = onLava,
            hp = playerHp,
            maxHp = playerMaxHp,
            keysCollected = keysCollected,
            keysRequired = keysRequired,
            interactionHint = interactionHint(pose),
        )

        if (isSessionEnded()) {
            gameEndOverlay.render(sessionPhase, keysCollected, keysRequired)
        }
    }

    private var syncAccum = 0f

    private fun isSessionEnded(): Boolean =
        sessionPhase == SessionPhase.GAME_OVER || sessionPhase == SessionPhase.LEVEL_COMPLETE

    private fun handleDebugKeys() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            InputSampler.toggleMouseLook()
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
            showCollisionDebug = !showCollisionDebug
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F4)) {
            showLocationMap = !showLocationMap
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            restartSession()
        }
    }

    private fun restartSession() {
        predictedPose = null
        authoritativePose = null
        tileMap = null
        keyPickups = emptyList()
        exitGate = null
        sessionPhase = SessionPhase.EXPLORATION
        playerHp = 0
        playerMaxHp = 0
        keysCollected = 0
        keysRequired = 0
        serverMobs = emptyList()
        serverProjectiles = emptyList()
        pendingSyncInput = InputSyncRequest()
        pendingSyncDeltaMs = 0
        accumulatedYawDelta = 0f
        syncAccum = 0f
        lastCollisionDebug = null
        statusLine = "Starting new run..."
        sync.restart()
    }

    private fun simulateFrame(delta: Float) {
        val map = tileMap ?: return
        var pose = predictedPose ?: return

        val sample = InputSampler.sample(delta)
        accumulatedYawDelta += sample.input.yawDelta
        pendingSyncInput = sync.mergeInput(pendingSyncInput, sample.input)
        pendingSyncDeltaMs = (pendingSyncDeltaMs + sample.input.deltaMs).coerceAtMost(250)

        if (sample.input.attack) {
            audio.playHit()
        }

        val movement = FpsMovementSystem.applyInputWithDebug(map, pose, sample.input)
        lastCollisionDebug = movement.debug
        val localPose = movement.pose

        maybeSendSync(localPose)
        pose = localPose
        predictedPose = pose
        frameTexture = viewport.render(map, pose, serverMobs, serverProjectiles, keyPickups)
    }

    private fun interactionHint(pose: PlayerPose?): String? {
        if (pose == null || isSessionEnded()) return null
        val map = tileMap ?: return null
        val gate = exitGate
        if (gate != null) {
            val onGate = floor(pose.x).toInt() == gate.x && floor(pose.y).toInt() == gate.y
            if (onGate) {
                return if (keysCollected >= keysRequired) {
                    "Press E — insert keys and open the exit gate"
                } else {
                    "Exit gate: need all keys ($keysCollected / $keysRequired)"
                }
            }
        }
        val nearKey = keyPickups.minByOrNull {
            hypot((it.x - pose.x).toDouble(), (it.y - pose.y).toDouble())
        } ?: return null
        if (hypot((nearKey.x - pose.x).toDouble(), (nearKey.y - pose.y).toDouble()) <= 0.65) {
            return "Press E — pick up golden key"
        }
        if (map.getTileAt(pose.x, pose.y) == TileType.EXIT_GATE) {
            return if (keysCollected >= keysRequired) {
                "Press E — insert keys and open the exit gate"
            } else {
                "Exit gate: need all keys ($keysCollected / $keysRequired)"
            }
        }
        return null
    }

    private fun maybeSendSync(localPose: PlayerPose) {
        if (!InputSampler.shouldSync(syncAccum) || sync.sessionId == null) return
        syncAccum = 0f
        val syncPayload = pendingSyncInput.copy(
            yawDelta = accumulatedYawDelta,
            deltaMs = pendingSyncDeltaMs.coerceAtLeast(1),
            clientYaw = localPose.yaw,
            clientPitch = localPose.pitch,
        )
        accumulatedYawDelta = 0f
        pendingSyncInput = InputSyncRequest()
        pendingSyncDeltaMs = 0
        sync.send(syncPayload)
    }

    private fun drawWorldFrame() {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        val tex = frameTexture ?: return
        val blendSrc = batch.blendSrcFunc
        val blendDst = batch.blendDstFunc
        batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ZERO)
        batch.begin()
        batch.draw(tex, 0f, 0f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        batch.end()
        batch.setBlendFunction(blendSrc, blendDst)
    }

    private fun drawDebugOverlays(pose: PlayerPose?) {
        if (!showCollisionDebug && !showLocationMap) return
        val screenW = Gdx.graphics.width.toFloat()
        val screenH = Gdx.graphics.height.toFloat()
        Gdx.gl.glEnable(GL20.GL_BLEND)
        if (showLocationMap) {
            tileMap?.let { locationMapOverlay.render(screenW, screenH, it, pose, serverMobs, keyPickups, exitGate) }
        }
        collisionOverlayParams(pose)?.let { overlay ->
            collisionDebugOverlay.render(
                screenW,
                screenH,
                overlay.map,
                overlay.pose,
                overlay.debug,
                serverMobs,
            )
        }
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    private fun collisionOverlayParams(pose: PlayerPose?): CollisionOverlayParams? {
        if (!showCollisionDebug) return null
        val map = tileMap
        val debug = lastCollisionDebug
        if (map == null || pose == null || debug == null) return null
        return CollisionOverlayParams(map, pose, debug)
    }

    private fun applySnapshotFromServer(snap: GameSnapshot) {
        tileMap = TileMap.fromFlat(snap.width, snap.height, snap.tiles)
        serverMobs = snap.mobs
        serverProjectiles = snap.projectiles
        sessionPhase = parsePhase(snap.phase)
        keysCollected = snap.keysCollected
        keysRequired = snap.keysRequired
        keyPickups = snap.keyPickups
        exitGate = snap.exitGate
        audio.onCombatSnapshot(snap.player.hp, snap.projectiles)
        sync.applySnapshot(snap)
    }

    private fun parsePhase(raw: String): SessionPhase =
        runCatching { SessionPhase.valueOf(raw) }.getOrDefault(SessionPhase.EXPLORATION)

    override fun dispose() {
        scope.cancel()
        api.close()
        InputSampler.disableMouseLook()
        viewport.dispose()
        audio.dispose()
        shapeRenderer.dispose()
        batch.dispose()
        font.dispose()
    }

    private data class CollisionOverlayParams(
        val map: TileMap,
        val pose: PlayerPose,
        val debug: CollisionDebug,
    )
}
