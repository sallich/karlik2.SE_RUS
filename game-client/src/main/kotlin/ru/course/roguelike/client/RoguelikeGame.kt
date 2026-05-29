package ru.course.roguelike.client

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ru.course.roguelike.client.input.InputSampler
import ru.course.roguelike.client.net.GameApiClient
import ru.course.roguelike.client.render.CollisionDebugOverlay
import ru.course.roguelike.client.render.FpsViewportRenderer
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.engine.CollisionDebug
import ru.course.roguelike.shared.engine.FpsMovementSystem
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.FpsConstants
import ru.course.roguelike.shared.model.PlayerPose
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.hypot

class RoguelikeGame : ApplicationAdapter() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api = GameApiClient(System.getenv("GAME_SERVICE_URL") ?: "http://localhost:8080")

    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont
    private lateinit var viewport: FpsViewportRenderer
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var collisionDebugOverlay: CollisionDebugOverlay

    private var sessionId: String? = null
    private var tileMap: TileMap? = null
    /** Локальная симуляция (только render thread). */
    private var predictedPose: PlayerPose? = null
    /** Последняя поза с сервера (IO thread пишет, @Volatile для render). */
    @Volatile
    private var authoritativePose: PlayerPose? = null
    private var displayPose: PlayerPose? = null

    private var syncAccum = 0f
    private val syncOutboundSeq = AtomicInteger(0)
    private val syncAppliedSeq = AtomicInteger(0)

    private var statusLine = "Connecting..."
    private var frameTexture: Texture? = null
    private var fpsSmoothed = 0f
    private var pendingSyncInput = InputSyncRequest()
    private var pendingSyncDeltaMs = 0
    private var accumulatedYawDelta = 0f
    private var showCollisionDebug = false
    private var lastCollisionDebug: CollisionDebug? = null

    override fun create() {
        batch = SpriteBatch()
        font = BitmapFont()
        shapeRenderer = ShapeRenderer()
        collisionDebugOverlay = CollisionDebugOverlay(shapeRenderer)
        viewport = FpsViewportRenderer(640, 360)
        Gdx.graphics.setForegroundFPS(60)
        InputSampler.enableMouseLook()

        scope.launch {
            try {
                val created = api.createSession(seed = 42)
                Gdx.app.postRunnable { applySnapshot(created) }
                statusLine = "WASD — ходьба, мышь/ПКМ — поворот, ←→ — поворот, ↑↓ — pitch. Esc — мышь. F3 — коллизии."
            } catch (ex: Exception) {
                statusLine = "Server error: ${ex.message}. Run :game-service:run"
            }
        }
    }

    override fun render() {
        val delta = Gdx.graphics.deltaTime.coerceAtMost(0.05f)
        fpsSmoothed = fpsSmoothed * 0.9f + (1f / delta) * 0.1f
        syncAccum += delta

        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ESCAPE)) {
            InputSampler.toggleMouseLook()
        }
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.F3)) {
            showCollisionDebug = !showCollisionDebug
        }

        val map = tileMap
        var pose = predictedPose
        if (map != null && pose != null) {
            val sample = InputSampler.sample(delta)
            accumulatedYawDelta += sample.input.yawDelta
            pendingSyncInput = mergeInput(pendingSyncInput, sample.input)
            pendingSyncDeltaMs = (pendingSyncDeltaMs + sample.input.deltaMs).coerceAtMost(250)

            val movement = FpsMovementSystem.applyInputWithDebug(map, pose, sample.input)
            lastCollisionDebug = movement.debug
            var localPose = movement.pose

            if (InputSampler.shouldSync(syncAccum) && sessionId != null) {
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
                sendSync(syncPayload)
            }

            pose = localPose
            predictedPose = pose
            displayPose = pose

            frameTexture = viewport.render(map, pose)
        }

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        frameTexture?.let { tex ->
            batch.begin()
            batch.draw(tex, 0f, 0f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
            batch.end()
        }

        val debug = lastCollisionDebug
        if (showCollisionDebug && map != null && pose != null && debug != null) {
            Gdx.gl.glEnable(GL20.GL_BLEND)
            collisionDebugOverlay.render(
                Gdx.graphics.width.toFloat(),
                Gdx.graphics.height.toFloat(),
                map,
                pose,
                debug,
            )
            Gdx.gl.glDisable(GL20.GL_BLEND)
        }

        batch.begin()
        font.draw(batch, statusLine, 12f, Gdx.graphics.height - 12f)
        pose?.let {
            font.draw(
                batch,
                "fps~${fpsSmoothed.toInt()} | pos=${"%.1f".format(it.x)},${"%.1f".format(it.y)} " +
                    "yaw=${"%.2f".format(it.yaw)} pitch=${"%.2f".format(it.pitch)}",
                12f,
                Gdx.graphics.height - 36f,
            )
        }
        if (showCollisionDebug && debug != null) {
            font.draw(
                batch,
                "collision: blocked=${debug.blocked} sweep=${"%.0f".format(debug.sweepFraction * 100)}% " +
                    "hits=${debug.hitCells.size} | yellow=view/move green=fact orange=request",
                12f,
                Gdx.graphics.height - 60f,
            )
        }
        batch.end()
    }

    private fun sendSync(input: InputSyncRequest) {
        val id = sessionId ?: return
        val seq = syncOutboundSeq.incrementAndGet()
        scope.launch {
            try {
                val result = api.sync(id, input)
                val snap = result.snapshot ?: return@launch
                if (seq < syncAppliedSeq.get()) return@launch
                syncAppliedSeq.set(seq)
                val auth = snap.player.pose
                Gdx.app.postRunnable {
                    applyServerSyncCorrection(auth)
                }
            } catch (ex: Exception) {
                statusLine = "Sync failed: ${ex.message}"
            }
        }
    }

    /**
     * Коррекция только по ответу sync (~30 Hz), не каждый кадр к устаревшему authoritative.
     * Yaw/pitch остаются клиентскими — иначе дёргается камера.
     */
    private fun applyServerSyncCorrection(serverPose: PlayerPose) {
        val pred = predictedPose ?: return
        val auth = serverPose.copy(yaw = pred.yaw, pitch = pred.pitch)
        authoritativePose = auth
        val err = hypot((auth.x - pred.x).toDouble(), (auth.y - pred.y).toDouble()).toFloat()
        predictedPose = when {
            err > FpsConstants.SYNC_POSITION_HARD_SNAP ->
                pred.copy(x = auth.x, y = auth.y)
            err > FpsConstants.SYNC_POSITION_CORRECT_MIN ->
                PoseBlend.towardPosition(pred, auth, FpsConstants.SYNC_POSITION_BLEND)
            else -> pred
        }
    }

    private fun mergeInput(prev: InputSyncRequest, frame: InputSyncRequest): InputSyncRequest =
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

    private fun applySnapshot(snap: GameSnapshot) {
        sessionId = snap.sessionId
        tileMap = TileMap.fromFlat(snap.width, snap.height, snap.tiles)
        predictedPose = snap.player.pose
        authoritativePose = snap.player.pose
        displayPose = snap.player.pose
    }

    override fun dispose() {
        scope.cancel()
        api.close()
        InputSampler.disableMouseLook()
        frameTexture?.dispose()
        viewport.dispose()
        shapeRenderer.dispose()
        batch.dispose()
        font.dispose()
    }
}
