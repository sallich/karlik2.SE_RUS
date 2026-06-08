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
import ru.course.roguelike.client.audio.GameAudio
import ru.course.roguelike.client.input.InputSampler
import ru.course.roguelike.client.net.GameApiClient
import ru.course.roguelike.client.render.CollisionDebugOverlay
import ru.course.roguelike.client.render.CrosshairOverlay
import ru.course.roguelike.client.render.FpsViewportRenderer
import ru.course.roguelike.client.render.GameEndOverlay
import ru.course.roguelike.client.render.InventoryUiOverlay
import ru.course.roguelike.client.render.LocationMapOverlay
import ru.course.roguelike.client.render.MiniMapOverlay
import ru.course.roguelike.client.render.RoomClearTimerOverlay
import ru.course.roguelike.shared.dto.HotbarSnapshot
import ru.course.roguelike.shared.dto.RoomClearTimerSnapshot
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.dto.InventorySnapshot
import ru.course.roguelike.shared.dto.ItemSnapshot
import ru.course.roguelike.shared.dto.KeySnapshot
import ru.course.roguelike.shared.dto.MobSnapshot
import ru.course.roguelike.shared.dto.ProjectileSnapshot
import ru.course.roguelike.shared.engine.CollisionDebug
import ru.course.roguelike.shared.engine.ElevatorPhase
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.SessionPhase

class RoguelikeGame : ApplicationAdapter() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api = GameApiClient(System.getenv("GAME_SERVICE_URL") ?: "http://localhost:8080")

    internal lateinit var batch: SpriteBatch
    internal lateinit var font: BitmapFont
    internal lateinit var hud: RoguelikeHud
    internal lateinit var viewport: FpsViewportRenderer
    internal lateinit var shapeRenderer: ShapeRenderer
    internal lateinit var collisionDebugOverlay: CollisionDebugOverlay
    internal lateinit var crosshairOverlay: CrosshairOverlay
    internal lateinit var locationMapOverlay: LocationMapOverlay
    internal lateinit var miniMapOverlay: MiniMapOverlay
    internal lateinit var inventoryUiOverlay: InventoryUiOverlay
    internal lateinit var gameEndOverlay: GameEndOverlay
    internal lateinit var roomClearTimerOverlay: RoomClearTimerOverlay
    internal lateinit var sync: RoguelikeSync
    internal lateinit var audio: GameAudio

    internal var tileMap: TileMap? = null
    internal var predictedPose: PlayerPose? = null

    @Volatile
    internal var authoritativePose: PlayerPose? = null

    internal var statusLine = "Connecting..."
    internal var frameTexture: Texture? = null
    internal var pendingSyncInput = InputSyncRequest()
    internal var pendingSyncDeltaMs = 0
    internal var accumulatedYawDelta = 0f
    internal var showCollisionDebug = false
    internal var showLocationMap = false
    internal var showMiniMap = true
    internal val visitedTracker = VisitedTracker()
    internal var currentLevel = 0
    internal var lastCollisionDebug: CollisionDebug? = null

    internal var playerHp = 0
    internal var playerMaxHp = 0
    internal var playerLevel = 1
    internal var playerExperience = 0
    internal var playerExperienceToNextLevel = 100
    internal var playerAmmo = 0
    internal var playerMaxAmmo = 0
    internal var equippedWeaponName: String? = null
    internal var equippedWeaponType: String? = null
    internal var playerInventory: InventorySnapshot? = null
    internal var playerHotbar: HotbarSnapshot? = null
    internal var showInventoryGrid = false
    internal var clientVerticalVelocity = 0f
    internal var clientElevatorPhase = ElevatorPhase.IDLE
    internal var clientWasOnElevator = false
    internal var twoLevelLocation = true
    internal var keysCollected = 0
    internal var keysRequired = 0

    @Volatile
    internal var sessionPhase = SessionPhase.EXPLORATION

    @Volatile
    internal var agentPose: PlayerPose? = null

    @Volatile
    internal var serverMobs: List<MobSnapshot> = emptyList()

    @Volatile
    internal var serverProjectiles: List<ProjectileSnapshot> = emptyList()

    @Volatile
    internal var keyPickups: List<KeySnapshot> = emptyList()

    @Volatile
    internal var items: List<ItemSnapshot> = emptyList()

    @Volatile
    internal var exitGate: GridPos? = null

    @Volatile
    internal var roomClearTimer: RoomClearTimerSnapshot? = null

    @Volatile
    internal var roomClearTimerReceivedAtMs: Long = 0L

    internal var syncAccum = 0f

    internal val isSessionEnded: Boolean
        get() = sessionPhase == SessionPhase.GAME_OVER || sessionPhase == SessionPhase.LEVEL_COMPLETE

    override fun create() {
        initRendering()
        sync = RoguelikeSync(
            scope = scope,
            api = api,
            onStatusLine = { statusLine = it },
            onSnapshot = { applyServerSnapshot(it) },
            bindings = buildSyncBindings(),
        )
        Gdx.graphics.setForegroundFPS(60)
        InputSampler.enableMouseLook()
        sync.connect()
    }

    override fun render() {
        val delta = Gdx.graphics.deltaTime.coerceAtMost(0.05f)
        handleDebugKeys()

        if (!isSessionEnded) {
            syncAccum += delta
            ClientFrameSimulator.advance(this, delta)
        }

        drawWorldFrame()
        if (!isSessionEnded && !showInventoryGrid) {
            crosshairOverlay.render(
                Gdx.graphics.width.toFloat(),
                Gdx.graphics.height.toFloat(),
            )
        }
        if (!isSessionEnded) {
            drawDebugOverlays(
                DebugOverlayContext(
                    showCollisionDebug = showCollisionDebug,
                    showLocationMap = showLocationMap,
                    showMiniMap = showMiniMap,
                    screenW = Gdx.graphics.width.toFloat(),
                    screenH = Gdx.graphics.height.toFloat(),
                    tileMap = tileMap,
                    pose = predictedPose,
                    visitedTiles = visitedTracker.cells,
                    serverMobs = serverMobs,
                    keyPickups = keyPickups,
                    items = items,
                    exitGate = exitGate,
                    lastCollisionDebug = lastCollisionDebug,
                    locationMapOverlay = locationMapOverlay,
                    miniMapOverlay = miniMapOverlay,
                    collisionDebugOverlay = collisionDebugOverlay,
                ),
            )
        }

        drawInventoryPanel()
        drawGameHud()
        if (!isSessionEnded) {
            roomClearTimerOverlay.render(
                screenW = Gdx.graphics.width.toFloat(),
                screenH = Gdx.graphics.height.toFloat(),
                timer = roomClearTimer,
                timerReceivedAtMs = roomClearTimerReceivedAtMs,
            )
        }

        if (isSessionEnded) {
            gameEndOverlay.render(sessionPhase)
        }
    }

    private fun handleDebugKeys() {
        val keys = DebugKeyHandler.handle(
            DebugKeyState(
                showCollisionDebug = showCollisionDebug,
                showLocationMap = showLocationMap,
                showMiniMap = showMiniMap,
                showInventoryGrid = showInventoryGrid,
            ),
        )
        showCollisionDebug = keys.showCollisionDebug
        showLocationMap = keys.showLocationMap
        showMiniMap = keys.showMiniMap
        showInventoryGrid = keys.showInventoryGrid
        if (keys.restartRequested) {
            resetSessionState()
            sync.restart()
        }
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
}
