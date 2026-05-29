package ru.course.roguelike.client

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import ru.course.roguelike.client.input.InputSampler
import ru.course.roguelike.client.net.GameApiClient
import ru.course.roguelike.client.render.CollisionDebugOverlay
import ru.course.roguelike.client.render.FpsViewportRenderer
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.engine.CollisionDebug
import ru.course.roguelike.shared.engine.FpsMovementSystem
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.PlayerPose

class RoguelikeGame : ApplicationAdapter() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api = GameApiClient(System.getenv("GAME_SERVICE_URL") ?: "http://localhost:8080")

    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont
    private lateinit var hud: RoguelikeHud
    private lateinit var viewport: FpsViewportRenderer
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var collisionDebugOverlay: CollisionDebugOverlay
    private lateinit var sync: RoguelikeSync

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
    private var lastCollisionDebug: CollisionDebug? = null

    override fun create() {
        batch = SpriteBatch()
        font = BitmapFont()
        hud = RoguelikeHud(batch, font)
        shapeRenderer = ShapeRenderer()
        collisionDebugOverlay = CollisionDebugOverlay(shapeRenderer)
        viewport = FpsViewportRenderer(640, 360)
        sync = RoguelikeSync(
            scope = scope,
            api = api,
            onStatusLine = { statusLine = it },
            onSnapshot = { applySnapshotFromServer(it) },
            poseAccessor = { predictedPose },
            poseMutator = { predictedPose = it },
            authoritativeMutator = { authoritativePose = it },
        )
        Gdx.graphics.setForegroundFPS(60)
        InputSampler.enableMouseLook()
        sync.connect()
    }

    override fun render() {
        val delta = Gdx.graphics.deltaTime.coerceAtMost(0.05f)
        fpsSmoothed = fpsSmoothed * 0.9f + (1f / delta) * 0.1f
        syncAccum += delta
        handleDebugKeys()
        val pose = simulateFrame(delta)
        drawWorldFrame()
        drawCollisionOverlay(pose)
        hud.draw(statusLine, pose, fpsSmoothed, lastCollisionDebug, showCollisionDebug)
    }

    private var syncAccum = 0f

    private fun handleDebugKeys() {
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ESCAPE)) {
            InputSampler.toggleMouseLook()
        }
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.F3)) {
            showCollisionDebug = !showCollisionDebug
        }
    }

    private fun simulateFrame(delta: Float): PlayerPose? {
        val map = tileMap ?: return predictedPose
        var pose = predictedPose ?: return null

        val sample = InputSampler.sample(delta)
        accumulatedYawDelta += sample.input.yawDelta
        pendingSyncInput = sync.mergeInput(pendingSyncInput, sample.input)
        pendingSyncDeltaMs = (pendingSyncDeltaMs + sample.input.deltaMs).coerceAtMost(250)

        val movement = FpsMovementSystem.applyInputWithDebug(map, pose, sample.input)
        lastCollisionDebug = movement.debug
        val localPose = movement.pose

        maybeSendSync(localPose)
        pose = localPose
        predictedPose = pose
        frameTexture = viewport.render(map, pose)
        return pose
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
        batch.begin()
        batch.draw(tex, 0f, 0f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        batch.end()
    }

    private fun drawCollisionOverlay(pose: PlayerPose?) {
        val overlay = collisionOverlayParams(pose) ?: return
        Gdx.gl.glEnable(GL20.GL_BLEND)
        collisionDebugOverlay.render(
            Gdx.graphics.width.toFloat(),
            Gdx.graphics.height.toFloat(),
            overlay.map,
            overlay.pose,
            overlay.debug,
        )
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
        sync.applySnapshot(snap)
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

    private data class CollisionOverlayParams(
        val map: TileMap,
        val pose: PlayerPose,
        val debug: CollisionDebug,
    )
}
