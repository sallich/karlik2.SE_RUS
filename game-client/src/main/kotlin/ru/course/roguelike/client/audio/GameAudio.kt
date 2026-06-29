package ru.course.roguelike.client.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound
import ru.course.roguelike.shared.dto.ProjectileSnapshot
import ru.course.roguelike.shared.render.AssetPaths
import ru.course.roguelike.shared.render.SceneRenderConfig

class GameAudio {
    private var ambient: Music? = null
    private var hit: Sound? = null
    private var ambientStarted = false
    private var lastPlayerHp: Int? = null
    private var knownEnemyProjectileIds: Set<Long> = emptySet()

    fun load() {
        ambient = Gdx.audio.newMusic(Gdx.files.internal(AssetPaths.SOUND_AMBIENT))
        hit = Gdx.audio.newSound(Gdx.files.internal(AssetPaths.SOUND_HIT))
    }

    fun playAmbient() {
        val music = ambient ?: return
        if (ambientStarted) return
        music.isLooping = true
        music.volume = SceneRenderConfig.AMBIENT_VOLUME
        music.play()
        ambientStarted = true
    }

    fun playHit() {
        hit?.play(SceneRenderConfig.HIT_VOLUME)
    }

    fun onCombatSnapshot(hp: Int, projectiles: List<ProjectileSnapshot>) {
        val lastHp = lastPlayerHp
        if (lastHp != null && hp < lastHp) {
            playHit()
        }
        lastPlayerHp = hp

        val enemyIds = projectiles.filter { !it.fromPlayer }.map { it.id }.toSet()
        if (enemyIds.any { it !in knownEnemyProjectileIds }) {
            playHit()
        }
        knownEnemyProjectileIds = enemyIds
    }

    fun dispose() {
        ambient?.dispose()
        hit?.dispose()
        ambient = null
        hit = null
        ambientStarted = false
        lastPlayerHp = null
        knownEnemyProjectileIds = emptySet()
    }
}
