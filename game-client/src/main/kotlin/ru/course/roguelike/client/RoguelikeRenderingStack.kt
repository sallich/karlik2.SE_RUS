package ru.course.roguelike.client

import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import ru.course.roguelike.client.audio.GameAudio
import ru.course.roguelike.client.render.CollisionDebugOverlay
import ru.course.roguelike.client.render.CrosshairOverlay
import ru.course.roguelike.client.render.FpsViewportRenderer
import ru.course.roguelike.client.render.GameEndOverlay
import ru.course.roguelike.client.render.GameTextures
import ru.course.roguelike.client.render.InventoryUiOverlay
import ru.course.roguelike.client.render.LocationMapOverlay
import ru.course.roguelike.client.render.MiniMapOverlay
import ru.course.roguelike.client.render.RoomClearTimerOverlay
import ru.course.roguelike.shared.render.SceneRenderConfig

internal fun RoguelikeGame.initRendering() {
    batch = SpriteBatch()
    font = BitmapFont()
    hud = RoguelikeHud(batch, font)
    shapeRenderer = ShapeRenderer()
    collisionDebugOverlay = CollisionDebugOverlay(shapeRenderer)
    crosshairOverlay = CrosshairOverlay(shapeRenderer)
    locationMapOverlay = LocationMapOverlay(shapeRenderer)
    miniMapOverlay = MiniMapOverlay(shapeRenderer)
    inventoryUiOverlay = InventoryUiOverlay(shapeRenderer)
    gameEndOverlay = GameEndOverlay(batch, font, shapeRenderer)
    roomClearTimerOverlay = RoomClearTimerOverlay(shapeRenderer, batch, font)
    audio = GameAudio()
    audio.load()
    audio.playAmbient()
    val gameTextures = GameTextures.load()
    viewport = FpsViewportRenderer(SceneRenderConfig.VIEW_WIDTH, SceneRenderConfig.VIEW_HEIGHT, gameTextures)
}
